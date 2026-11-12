package com.ewaste.server.service;

import com.ewaste.model.*;
import com.ewaste.server.repo.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Business rules and audit trail for recycler auctions. */
public class AuctionService {
    private final Repository repo;
    private final NotificationService notes;
    private final ItemService items;

    public AuctionService(Repository repo, NotificationService notes, ItemService items) {
        this.repo = repo;
        this.notes = notes;
        this.items = items;
    }

    public Auction publish(User admin, int itemId, double minimumBid, LocalDateTime closesAt) {
        if (admin == null || !admin.isAdmin()) throw new IllegalArgumentException("Admin access required.");
        if (!Double.isFinite(minimumBid) || minimumBid <= 0)
            throw new IllegalArgumentException("Minimum bid must be a positive amount.");
        if (closesAt == null || !closesAt.isAfter(LocalDateTime.now()))
            throw new IllegalArgumentException("Auction closing time must be in the future.");
        EWasteItem item = items.require(itemId);
        if (item.status != ItemStatus.COLLECTED || item.decision == null || !item.decision.isRecycling())
            throw new IllegalArgumentException("Only collected items with a RECYCLE / HAZARDOUS_DISPOSAL plan can be auctioned.");
        if (reserved(itemId)) throw new IllegalArgumentException("This item already has an active or awarded recycler auction.");

        Auction auction = new Auction();
        auction.itemId = itemId;
        auction.createdBy = admin.id;
        auction.minimumBid = minimumBid;
        auction.closesAt = closesAt;
        repo.addAuction(auction);
        history(auction.id, admin.id, "AUCTION_OPENED", "Minimum bid " + minimumBid + "; closes " + closesAt);
        items.log(item, "Published in recycler auction #" + auction.id);
        return auction;
    }

    public AuctionBid placeBid(User recycler, int auctionId, double amount, LocalDate collectionDate, String method) {
        if (recycler == null || recycler.role != Role.RECYCLER)
            throw new IllegalArgumentException("Recycler role required.");
        closeExpired();
        Auction auction = require(auctionId);
        if (auction.status != AuctionStatus.OPEN)
            throw new IllegalArgumentException("This auction is not open for bids.");
        if (!Double.isFinite(amount) || amount <= 0 || amount < auction.minimumBid)
            throw new IllegalArgumentException("Bid amount must be at least the minimum bid of " + auction.minimumBid + ".");
        EWasteItem item = items.require(auction.itemId);
        if (item.ownerId == recycler.id) throw new IllegalArgumentException("You cannot bid on your own item.");
        if (collectionDate == null || collectionDate.isBefore(LocalDate.now()))
            throw new IllegalArgumentException("Proposed collection date must be today or later.");
        if (method == null || method.trim().isEmpty() || method.trim().length() > 200)
            throw new IllegalArgumentException("Recycling method is required and must be 200 characters or fewer.");
        for (AuctionBid existing : repo.auctionBids(auction.id))
            if (existing.recyclerId == recycler.id) throw new IllegalArgumentException("You have already placed a bid on this auction.");

        AuctionBid bid = new AuctionBid();
        bid.auctionId = auction.id;
        bid.recyclerId = recycler.id;
        bid.itemId = item.id;
        bid.amount = amount;
        bid.proposedCollectionDate = collectionDate;
        bid.recyclingMethod = method.trim();
        repo.addAuctionBid(bid);
        history(auction.id, recycler.id, "BID_PLACED", "Bid #" + bid.id + " submitted for " + amount);
        return bid;
    }

    public List<Auction> visibleTo(User recycler) {
        if (recycler == null || recycler.role != Role.RECYCLER)
            throw new IllegalArgumentException("Recycler role required.");
        closeExpired();
        List<Auction> out = new ArrayList<>();
        for (Auction a : repo.auctions()) {
            if (a.status == AuctionStatus.CANCELLED) continue;
            if (a.status == AuctionStatus.OPEN || hasBid(a.id, recycler.id)) out.add(a);
        }
        return out;
    }

    public List<Auction> all() {
        closeExpired();
        return repo.auctions();
    }

    public List<EWasteItem> eligibleItems() {
        List<EWasteItem> out = new ArrayList<>();
        for (EWasteItem item : repo.items())
            if (item.status == ItemStatus.COLLECTED && item.decision != null && item.decision.isRecycling()
                    && !reserved(item.id)) out.add(item);
        return out;
    }

    public List<AuctionBid> bids(int auctionId) {
        closeExpired();
        require(auctionId);
        return repo.auctionBids(auctionId);
    }

    public List<AuctionHistoryEntry> history(int auctionId) {
        closeExpired();
        require(auctionId);
        return repo.auctionHistory(auctionId);
    }

    public Auction award(User admin, int auctionId, int bidId) {
        if (admin == null || !admin.isAdmin()) throw new IllegalArgumentException("Admin access required.");
        closeExpired();
        Auction auction = require(auctionId);
        if (auction.status != AuctionStatus.OPEN && auction.status != AuctionStatus.CLOSED)
            throw new IllegalArgumentException("Only an open or closed auction can be awarded.");
        AuctionBid winner = null;
        for (AuctionBid bid : repo.auctionBids(auction.id)) if (bid.id == bidId) winner = bid;
        if (winner == null || winner.status != BidStatus.PENDING)
            throw new IllegalArgumentException("Select a pending bid from this auction.");

        winner.status = BidStatus.WINNER;
        repo.saveAuctionBid(winner);
        for (AuctionBid bid : repo.auctionBids(auction.id)) {
            if (bid.id == winner.id) continue;
            bid.status = BidStatus.REJECTED;
            repo.saveAuctionBid(bid);
            notes.push(bid.recyclerId, "Your bid #" + bid.id + " for item #" + auction.itemId
                    + " was not selected. The auction has been awarded to another recycler.");
        }
        auction.status = AuctionStatus.AWARDED;
        repo.saveAuction(auction);
        notes.push(winner.recyclerId, "Congratulations! Your bid #" + winner.id + " for item #" + auction.itemId
                + " was selected. Proposed collection date: " + winner.proposedCollectionDate + ".");
        history(auction.id, admin.id, "AUCTION_AWARDED", "Bid #" + winner.id + " selected; competing bids rejected");
        return auction;
    }

    public Auction cancel(User admin, int auctionId) {
        if (admin == null || !admin.isAdmin()) throw new IllegalArgumentException("Admin access required.");
        closeExpired();
        Auction auction = require(auctionId);
        if (auction.status != AuctionStatus.OPEN && auction.status != AuctionStatus.CLOSED)
            throw new IllegalArgumentException("Only an open or closed auction can be cancelled.");
        auction.status = AuctionStatus.CANCELLED;
        repo.saveAuction(auction);
        for (AuctionBid bid : repo.auctionBids(auction.id)) {
            if (bid.status == BidStatus.PENDING) {
                bid.status = BidStatus.REJECTED;
                repo.saveAuctionBid(bid);
                notes.push(bid.recyclerId, "Auction #" + auction.id + " for item #" + auction.itemId + " was cancelled.");
            }
        }
        history(auction.id, admin.id, "AUCTION_CANCELLED", "Auction cancelled by admin");
        return auction;
    }

    public Auction require(int id) {
        Auction auction = repo.auction(id);
        if (auction == null) throw new IllegalArgumentException("No auction with id " + id + ".");
        return auction;
    }

    public AuctionBid myBid(int auctionId, int recyclerId) {
        for (AuctionBid bid : repo.auctionBids(auctionId)) if (bid.recyclerId == recyclerId) return bid;
        return null;
    }

    public AuctionBid winner(int auctionId) {
        for (AuctionBid bid : repo.auctionBids(auctionId)) if (bid.status == BidStatus.WINNER) return bid;
        return null;
    }

    public boolean reserved(int itemId) {
        closeExpired();
        for (Auction auction : repo.auctions())
            if (auction.itemId == itemId && (auction.status == AuctionStatus.OPEN || auction.status == AuctionStatus.CLOSED
                    || auction.status == AuctionStatus.AWARDED)) return true;
        return false;
    }

    public void closeExpired() {
        LocalDateTime now = LocalDateTime.now();
        for (Auction auction : repo.auctions()) {
            if (auction.status == AuctionStatus.OPEN && !now.isBefore(auction.closesAt)) {
                auction.status = AuctionStatus.CLOSED;
                repo.saveAuction(auction);
                history(auction.id, null, "AUCTION_CLOSED", "Auction reached its closing time");
            }
        }
    }

    private boolean hasBid(int auctionId, int recyclerId) { return myBid(auctionId, recyclerId) != null; }

    private void history(int auctionId, Integer actorId, String action, String details) {
        AuctionHistoryEntry entry = new AuctionHistoryEntry();
        entry.auctionId = auctionId;
        entry.actorId = actorId;
        entry.action = action;
        entry.details = details;
        repo.addAuctionHistory(entry);
    }
}

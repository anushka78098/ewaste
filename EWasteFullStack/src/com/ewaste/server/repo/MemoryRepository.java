package com.ewaste.server.repo;

import com.ewaste.model.*;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Dependency-free repository. Data lives in memory and is written to a snapshot file (Java
 * serialization) after every atomic block. Used when the SQLite driver is not installed, and by tests.
 */
public class MemoryRepository implements Repository {
    private static final Logger LOGGER = Logger.getLogger(MemoryRepository.class.getName());

    private static class Snapshot implements Serializable {
        private static final long serialVersionUID = 1L;
        int seq = 0;
        final Map<Integer, User> users = new LinkedHashMap<>();
        final Map<Integer, EWasteItem> items = new LinkedHashMap<>();
        final Map<Integer, Listing> listings = new LinkedHashMap<>();
        final List<Order> orders = new ArrayList<>();
        List<MarketMessage> marketMessages = new ArrayList<>();
        Map<Integer, SupportMessage> supportMessages = new LinkedHashMap<>();
        final Map<Integer, Notification> notifications = new LinkedHashMap<>();
        final List<RecyclingBatch> batches = new ArrayList<>();
        Map<Integer, Campaign> campaigns = new LinkedHashMap<>();   // not final: absent in snapshots written by older versions
        Map<Integer, Auction> auctions = new LinkedHashMap<>();
        Map<Integer, AuctionBid> auctionBids = new LinkedHashMap<>();
        List<AuctionHistoryEntry> auctionHistory = new ArrayList<>();
        Map<Integer, EcoChallenge> ecoChallenges = new LinkedHashMap<>();
        Map<Integer, ChallengeEnrollment> challengeEnrollments = new LinkedHashMap<>();
        Map<Integer, Achievement> achievements = new LinkedHashMap<>();
        Map<Integer, ChallengeReward> challengeRewards = new LinkedHashMap<>();
    }

    private Snapshot d = new Snapshot();
    private final Path file;
    private boolean dirty;

    /** @param file snapshot file, or null for a purely in-memory store */
    public MemoryRepository(Path file) {
        this.file = file;
        if (file != null && Files.exists(file)) {
            try (ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
                d = (Snapshot) in.readObject();
                if (d.campaigns == null) d.campaigns = new LinkedHashMap<>();
                if (d.marketMessages == null) d.marketMessages = new ArrayList<>();
                if (d.supportMessages == null) d.supportMessages = new LinkedHashMap<>();
                if (d.auctions == null) d.auctions = new LinkedHashMap<>();
                if (d.auctionBids == null) d.auctionBids = new LinkedHashMap<>();
                if (d.auctionHistory == null) d.auctionHistory = new ArrayList<>();
                if (d.ecoChallenges == null) d.ecoChallenges = new LinkedHashMap<>();
                if (d.challengeEnrollments == null) d.challengeEnrollments = new LinkedHashMap<>();
                if (d.achievements == null) d.achievements = new LinkedHashMap<>();
                if (d.challengeRewards == null) d.challengeRewards = new LinkedHashMap<>();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Could not read snapshot " + file + "; starting with an empty store.", e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T copy(T o) {
        if (o == null) return null;
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            try (ObjectOutputStream oo = new ObjectOutputStream(bo)) { oo.writeObject(o); }
            try (ObjectInputStream oi = new ObjectInputStream(new ByteArrayInputStream(bo.toByteArray()))) {
                return (T) oi.readObject();
            }
        } catch (IOException | ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    private <T> List<T> copies(Collection<T> c) {
        List<T> out = new ArrayList<>();
        for (T t : c) out.add(copy(t));
        return out;
    }

    // ---- users
    @Override public User addUser(User u) {
        User c = copy(u); c.id = ++d.seq; d.users.put(c.id, c); dirty = true; u.id = c.id; return copy(c);
    }
    @Override public User user(int id) { return copy(d.users.get(id)); }
    @Override public User userByName(String name) {
        for (User u : d.users.values()) if (u.username.equalsIgnoreCase(name)) return copy(u);
        return null;
    }
    @Override public List<User> users() { return copies(d.users.values()); }
    @Override public void saveUser(User u) { d.users.put(u.id, copy(u)); dirty = true; }

    // ---- items
    @Override public EWasteItem addItem(EWasteItem i) {
        EWasteItem c = copy(i); c.id = ++d.seq; c.history = new ArrayList<>(); d.items.put(c.id, c); dirty = true;
        i.id = c.id; return copy(c);
    }
    @Override public EWasteItem item(int id) { return copy(d.items.get(id)); }
    @Override public List<EWasteItem> items() { return copies(d.items.values()); }
    @Override public void saveItem(EWasteItem i) {
        EWasteItem old = d.items.get(i.id);
        EWasteItem c = copy(i);
        c.history = old == null ? new ArrayList<>() : old.history;   // history is managed by addHistory only
        d.items.put(c.id, c);
        dirty = true;
    }
    @Override public void addHistory(int itemId, String line) {
        EWasteItem i = d.items.get(itemId);
        if (i != null) { i.history.add(line); dirty = true; }
    }

    // ---- market
    @Override public Listing addListing(Listing l) {
        Listing c = copy(l); c.id = ++d.seq; d.listings.put(c.id, c); dirty = true; l.id = c.id; return copy(c);
    }
    @Override public Listing listing(int id) { return copy(d.listings.get(id)); }
    @Override public List<Listing> listings() { return copies(d.listings.values()); }
    @Override public void saveListing(Listing l) { d.listings.put(l.id, copy(l)); dirty = true; }
    @Override public Order addOrder(Order o) {
        Order c = copy(o); c.id = ++d.seq; d.orders.add(c); dirty = true; o.id = c.id; return copy(c);
    }
    @Override public List<Order> orders() { return copies(d.orders); }
    @Override public MarketMessage addMarketMessage(MarketMessage message) {
        MarketMessage c = copy(message); c.id = ++d.seq; d.marketMessages.add(c); dirty = true; message.id = c.id; return copy(c);
    }
    @Override public List<MarketMessage> marketMessages() { return copies(d.marketMessages); }
    @Override public SupportMessage addSupportMessage(SupportMessage message) {
        SupportMessage copy = copy(message);
        copy.id = ++d.seq;
        d.supportMessages.put(copy.id, copy);
        dirty = true;
        message.id = copy.id;
        return copy(copy);
    }
    @Override public List<SupportMessage> supportMessages() { return copies(d.supportMessages.values()); }
    @Override public void saveSupportMessage(SupportMessage message) {
        d.supportMessages.put(message.id, copy(message));
        dirty = true;
    }

    // ---- notifications
    @Override public Notification addNotification(Notification n) {
        Notification c = copy(n); c.id = ++d.seq; d.notifications.put(c.id, c); dirty = true; n.id = c.id; return copy(c);
    }
    @Override public List<Notification> notifications() { return copies(d.notifications.values()); }
    @Override public void saveNotification(Notification n) { d.notifications.put(n.id, copy(n)); dirty = true; }

    // ---- recycling
    @Override public RecyclingBatch addBatch(RecyclingBatch b) {
        RecyclingBatch c = copy(b); c.id = ++d.seq; d.batches.add(c); dirty = true; b.id = c.id; return copy(c);
    }
    @Override public List<RecyclingBatch> batches() { return copies(d.batches); }

    // ---- recycler auctions
    @Override public Auction addAuction(Auction a) {
        Auction c = copy(a); c.id = ++d.seq; d.auctions.put(c.id, c); dirty = true; a.id = c.id; return copy(c);
    }
    @Override public Auction auction(int id) { return copy(d.auctions.get(id)); }
    @Override public List<Auction> auctions() { return copies(d.auctions.values()); }
    @Override public void saveAuction(Auction a) { d.auctions.put(a.id, copy(a)); dirty = true; }
    @Override public AuctionBid addAuctionBid(AuctionBid b) {
        AuctionBid c = copy(b); c.id = ++d.seq; d.auctionBids.put(c.id, c); dirty = true; b.id = c.id; return copy(c);
    }
    @Override public void saveAuctionBid(AuctionBid b) { d.auctionBids.put(b.id, copy(b)); dirty = true; }
    @Override public List<AuctionBid> auctionBids(int auctionId) {
        List<AuctionBid> out = new ArrayList<>();
        for (AuctionBid b : d.auctionBids.values()) if (b.auctionId == auctionId) out.add(copy(b));
        out.sort((a, b) -> {
            int amount = Double.compare(a.amount, b.amount);
            return amount != 0 ? amount : Integer.compare(a.id, b.id);
        });
        return out;
    }
    @Override public List<AuctionBid> auctionBids() { return copies(d.auctionBids.values()); }
    @Override public AuctionHistoryEntry addAuctionHistory(AuctionHistoryEntry entry) {
        AuctionHistoryEntry c = copy(entry); c.id = ++d.seq; d.auctionHistory.add(c); dirty = true; entry.id = c.id; return copy(c);
    }
    @Override public List<AuctionHistoryEntry> auctionHistory(int auctionId) {
        List<AuctionHistoryEntry> out = new ArrayList<>();
        for (AuctionHistoryEntry h : d.auctionHistory) if (h.auctionId == auctionId) out.add(copy(h));
        return out;
    }

    // ---- campaigns
    @Override public Campaign addCampaign(Campaign c) {
        Campaign k = copy(c); k.id = ++d.seq; d.campaigns.put(k.id, k); dirty = true; c.id = k.id; return copy(k);
    }
    @Override public Campaign campaign(int id) { return copy(d.campaigns.get(id)); }
    @Override public List<Campaign> campaigns() { return copies(d.campaigns.values()); }
    @Override public void saveCampaign(Campaign c) { d.campaigns.put(c.id, copy(c)); dirty = true; }

    // ---- eco challenges
    @Override public EcoChallenge addEcoChallenge(EcoChallenge challenge) {
        EcoChallenge c = copy(challenge); c.id = ++d.seq; d.ecoChallenges.put(c.id, c); dirty = true; challenge.id = c.id; return copy(c);
    }
    @Override public EcoChallenge ecoChallenge(int id) { return copy(d.ecoChallenges.get(id)); }
    @Override public List<EcoChallenge> ecoChallenges() { return copies(d.ecoChallenges.values()); }
    @Override public void saveEcoChallenge(EcoChallenge challenge) { d.ecoChallenges.put(challenge.id, copy(challenge)); dirty = true; }
    @Override public ChallengeEnrollment addChallengeEnrollment(ChallengeEnrollment enrollment) {
        for (ChallengeEnrollment existing : d.challengeEnrollments.values())
            if (existing.challengeId == enrollment.challengeId && existing.userId == enrollment.userId)
                throw new IllegalArgumentException("You are already enrolled in this challenge.");
        ChallengeEnrollment e = copy(enrollment); e.id = ++d.seq; d.challengeEnrollments.put(e.id, e); dirty = true; enrollment.id = e.id; return copy(e);
    }
    @Override public ChallengeEnrollment challengeEnrollment(int challengeId, int userId) {
        for (ChallengeEnrollment e : d.challengeEnrollments.values())
            if (e.challengeId == challengeId && e.userId == userId) return copy(e);
        return null;
    }
    @Override public List<ChallengeEnrollment> challengeEnrollments() { return copies(d.challengeEnrollments.values()); }
    @Override public void saveChallengeEnrollment(ChallengeEnrollment enrollment) {
        d.challengeEnrollments.put(enrollment.id, copy(enrollment)); dirty = true;
    }
    @Override public Achievement addAchievement(Achievement achievement) {
        for (Achievement existing : d.achievements.values())
            if (existing.userId == achievement.userId && existing.key.equals(achievement.key))
                throw new IllegalArgumentException("Achievement has already been awarded.");
        Achievement a = copy(achievement); a.id = ++d.seq; d.achievements.put(a.id, a); dirty = true; achievement.id = a.id; return copy(a);
    }
    @Override public List<Achievement> achievements() { return copies(d.achievements.values()); }
    @Override public ChallengeReward addChallengeReward(ChallengeReward reward) {
        for (ChallengeReward existing : d.challengeRewards.values())
            if (existing.challengeId == reward.challengeId && existing.userId == reward.userId)
                throw new IllegalArgumentException("Challenge reward has already been awarded.");
        ChallengeReward r = copy(reward); r.id = ++d.seq; d.challengeRewards.put(r.id, r); dirty = true; reward.id = r.id; return copy(r);
    }
    @Override public List<ChallengeReward> challengeRewards() { return copies(d.challengeRewards.values()); }

    @Override public <T> T atomic(Callable<T> work) {
        try {
            T r = work.call();
            flush();
            return r;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void flush() {
        if (file == null || !dirty) return;
        try {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            try (ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(Files.newOutputStream(tmp)))) {
                out.writeObject(d);
            }
            Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            dirty = false;
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Could not save snapshot file " + file, e);
            throw new IllegalStateException("Could not save snapshot file " + file, e);
        }
    }

    @Override public String describe() { return file == null ? "in-memory (not persisted)" : "snapshot file " + file; }
    @Override public void close() { flush(); }
}

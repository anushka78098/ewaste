package com.ewaste.server.service;

import com.ewaste.model.*;
import com.ewaste.server.repo.Repository;

import java.util.ArrayList;
import java.util.List;

public class MarketService {
    public static final double POINT_VALUE = 0.10;
    public static final double MAX_POINT_DISCOUNT = 0.30;
    public static final int SELLER_BONUS_PERCENT = 10;

    private final Repository repo;
    private final NotificationService notes;
    private final ItemService items;

    public MarketService(Repository repo, NotificationService notes, ItemService items) {
        this.repo = repo;
        this.notes = notes;
        this.items = items;
    }

    public Listing publish(int itemId, double price) {
        EWasteItem i = items.require(itemId);
        if (i.status != ItemStatus.COLLECTED || i.decision != Decision.REUSE)
            throw new IllegalArgumentException("Only collected items with plan REUSE can be listed.");
        if (price <= 0 || price > 10_000_000) throw new IllegalArgumentException("Price must be greater than zero.");
        Listing l = new Listing();
        l.itemId = itemId;
        l.price = DecisionEngine.round2(price);
        repo.addListing(l);
        i.status = ItemStatus.LISTED;
        repo.saveItem(i);
        items.log(i, "Listed in market at " + l.price);
        notes.broadcast("New in the market: " + i.name + " (" + i.category.label + ", " + i.condition.label + ") for $"
                + String.format("%.2f", l.price) + " - listing #" + l.id);
        return l;
    }

    public void updatePrice(int listingId, double newPrice) {
        Listing l = activeListing(listingId);
        if (newPrice <= 0 || newPrice > 10_000_000) throw new IllegalArgumentException("Price must be greater than zero.");
        double old = l.price;
        l.price = DecisionEngine.round2(newPrice);
        repo.saveListing(l);
        EWasteItem i = items.require(l.itemId);
        items.log(i, "Price changed from " + old + " to " + l.price);
        if (l.price < old) {
            notes.broadcast("Price drop! " + i.name + " is now $" + String.format("%.2f", l.price)
                    + " (was $" + String.format("%.2f", old) + ") - listing #" + l.id);
        }
    }

    public void delist(int listingId) {
        Listing l = activeListing(listingId);
        l.active = false;
        repo.saveListing(l);
        EWasteItem i = items.require(l.itemId);
        i.status = ItemStatus.COLLECTED;
        repo.saveItem(i);
        items.log(i, "Removed from market");
    }

    public List<Listing> browse(String keyword, Category category, double maxPrice) {
        List<Listing> out = new ArrayList<>();
        String k = keyword == null ? "" : keyword.trim().toLowerCase();
        for (Listing l : repo.listings()) {
            if (!l.active) continue;
            EWasteItem i = repo.item(l.itemId);
            if (i == null) continue;
            if (category != null && i.category != category) continue;
            if (maxPrice > 0 && l.price > maxPrice) continue;
            if (!k.isEmpty() && !(i.name + " " + i.brand + " " + i.description).toLowerCase().contains(k)) continue;
            out.add(l);
        }
        return out;
    }

    public Listing activeListing(int id) {
        Listing l = repo.listing(id);
        if (l == null || !l.active) throw new IllegalArgumentException("Listing #" + id + " is not available.");
        return l;
    }

    public Listing activeListingOrAny(int id) {
        Listing l = repo.listing(id);
        if (l == null) throw new IllegalArgumentException("Listing #" + id + " was not found.");
        return l;
    }

    public int maxRedeemable(User buyer, double price) {
        int byCap = (int) Math.floor(price * MAX_POINT_DISCOUNT / POINT_VALUE);
        return Math.max(0, Math.min(buyer.points, byCap));
    }

    public Order buy(User buyer, int listingId, int pointsToUse) {
        Listing l = activeListing(listingId);
        EWasteItem item = items.require(l.itemId);
        if (item.ownerId == buyer.id) throw new IllegalArgumentException("You cannot buy an item you donated.");
        int max = maxRedeemable(buyer, l.price);
        if (pointsToUse < 0 || pointsToUse > max) throw new IllegalArgumentException("You can redeem at most " + max + " points on this item.");

        double discount = DecisionEngine.round2(pointsToUse * POINT_VALUE);
        Order o = new Order();
        o.listingId = l.id;
        o.itemId = item.id;
        o.buyerId = buyer.id;
        o.listPrice = l.price;
        o.pointsUsed = pointsToUse;
        o.discount = discount;
        o.amountPaid = DecisionEngine.round2(l.price - discount);
        repo.addOrder(o);

        buyer.points -= pointsToUse;
        repo.saveUser(buyer);
        l.active = false;
        repo.saveListing(l);
        item.status = ItemStatus.SOLD;
        repo.saveItem(item);
        items.log(item, "Sold to " + buyer.username + " for " + o.amountPaid);

        User seller = repo.user(item.ownerId);
        int bonus = (int) Math.round(l.price * SELLER_BONUS_PERCENT / 100.0 / POINT_VALUE);
        if (seller != null) {
            seller.points += bonus;
            repo.saveUser(seller);
            notes.push(seller.id, "Your donated item #" + item.id + " (" + item.name + ") found a new home! Bonus: +" + bonus + " points.");
        }
        notes.push(buyer.id, "Order #" + o.id + " confirmed: " + item.name + " for $" + String.format("%.2f", o.amountPaid)
                + ". Pay on delivery. Thank you for choosing reuse!");
        return o;
    }

    public List<Order> ordersOf(int buyerId) {
        List<Order> out = new ArrayList<>();
        for (Order o : repo.orders()) if (o.buyerId == buyerId) out.add(0, o);
        return out;
    }
}

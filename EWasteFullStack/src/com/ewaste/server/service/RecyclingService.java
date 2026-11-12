package com.ewaste.server.service;

import com.ewaste.model.*;
import com.ewaste.server.repo.Repository;

import java.util.ArrayList;
import java.util.List;

public class RecyclingService {
    private final Repository repo;
    private final NotificationService notes;
    private final ItemService items;
    private final AuctionService auctions;

    public RecyclingService(Repository repo, NotificationService notes, ItemService items, AuctionService auctions) {
        this.repo = repo;
        this.notes = notes;
        this.items = items;
        this.auctions = auctions;
    }

    public void queue(int itemId) {
        EWasteItem i = items.require(itemId);
        if (auctions.reserved(i.id))
            throw new IllegalArgumentException("This item is reserved by a recycler auction. Cancel or award the auction first.");
        if (i.status != ItemStatus.COLLECTED || i.decision == null || !i.decision.isRecycling())
            throw new IllegalArgumentException("Only collected items with a RECYCLE / HAZARDOUS_DISPOSAL plan can be queued.");
        i.status = ItemStatus.QUEUED_RECYCLE;
        repo.saveItem(i);
        items.log(i, "Queued for recycling");
    }

    public List<EWasteItem> eligible() {
        List<EWasteItem> out = new ArrayList<>();
        for (EWasteItem i : repo.items())
            if (i.status == ItemStatus.COLLECTED && i.decision != null && i.decision.isRecycling() && !auctions.reserved(i.id)) out.add(i);
        return out;
    }

    public int queueAllEligible() {
        int n = 0;
        for (EWasteItem i : eligible()) { queue(i.id); n++; }
        return n;
    }

    public List<EWasteItem> queued(boolean hazardous) {
        List<EWasteItem> out = new ArrayList<>();
        for (EWasteItem i : repo.items())
            if (i.status == ItemStatus.QUEUED_RECYCLE && (i.decision == Decision.HAZARDOUS_DISPOSAL) == hazardous
                    && !auctions.reserved(i.id)) out.add(i);
        return out;
    }

    public RecyclingBatch processBatch(String partner, boolean certifiedHazardous) {
        if (partner == null || partner.trim().isEmpty()) throw new IllegalArgumentException("Partner name is required.");
        List<EWasteItem> list = queued(certifiedHazardous);
        if (list.isEmpty()) throw new IllegalArgumentException("No queued " + (certifiedHazardous ? "hazardous" : "regular") + " items.");

        RecyclingBatch b = new RecyclingBatch();
        b.partner = partner.trim();
        b.certifiedHazardous = certifiedHazardous;
        for (EWasteItem i : list) {
            Category c = i.category;
            b.itemIds.add(i.id);
            b.totalKg += i.weightKg;
            b.metalKg += i.weightKg * c.metalPct;
            b.plasticKg += i.weightKg * c.plasticPct;
            b.glassKg += i.weightKg * c.glassPct;
            b.hazardKg += i.weightKg * c.hazardPct;
            b.otherKg += i.weightKg * c.otherPct();
            b.co2SavedKg += i.weightKg * c.co2PerKg;
        }
        repo.addBatch(b);
        for (EWasteItem i : list) {
            i.status = ItemStatus.RECYCLED;
            repo.saveItem(i);
            items.log(i, "Recycled in batch #" + b.id + " at " + b.partner);
            notes.push(i.ownerId, "Item #" + i.id + " (" + i.name + ") has been responsibly recycled at " + b.partner
                    + (certifiedHazardous ? " (certified hazardous-waste facility)" : "") + ". Thank you!");
        }
        return b;
    }
}

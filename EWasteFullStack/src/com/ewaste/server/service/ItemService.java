package com.ewaste.server.service;

import com.ewaste.model.*;
import com.ewaste.server.repo.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Lifecycle of an item from submission to collection and refurbishment. */
public class ItemService {
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final Repository repo;
    private final NotificationService notes;
    private final DecisionEngine decisionEngine;

    public ItemService(Repository repo, NotificationService notes) {
        this(repo, notes, new DecisionEngine(DecisionPolicy.defaults()));
    }

    public ItemService(Repository repo, NotificationService notes, DecisionEngine decisionEngine) {
        this.repo = repo;
        this.notes = notes;
        this.decisionEngine = decisionEngine;
    }

    void log(EWasteItem i, String msg) { repo.addHistory(i.id, LocalDateTime.now().format(TS) + "  " + msg); }

    public EWasteItem submit(User owner, String name, String brand, Category cat, int ageYears, double weightKg,
                             Condition cond, String description) {
        if (name == null || name.trim().isEmpty()) throw new IllegalArgumentException("Item name is required.");
        if (cat == null || cond == null) throw new IllegalArgumentException("Category and condition are required.");
        if (weightKg <= 0 || weightKg > 500) throw new IllegalArgumentException("Weight must be between 0.01 and 500 kg.");
        if (ageYears < 0 || ageYears > 40) throw new IllegalArgumentException("Age must be between 0 and 40 years.");

        EWasteItem i = new EWasteItem();
        i.ownerId = owner.id;
        i.name = name.trim();
        i.brand = brand == null ? "" : brand.trim();
        i.category = cat;
        i.ageYears = ageYears;
        i.weightKg = weightKg;
        i.condition = cond;
        i.description = description == null ? "" : description.trim();
        DecisionReport report = decisionEngine.assess(cat, ageYears, cond, null, null, null, null, weightKg);
        i.suggestedDecision = report.recommendedDecision;
        i.estimatedValue = report.estimatedResaleValue * cond.valueFactor;
        repo.addItem(i);
        log(i, "Submitted by " + owner.username);
        notes.push(owner.id, "Your item #" + i.id + " (" + i.name + ") was received and is awaiting admin review.");
        return i;
    }

    public EWasteItem require(int id) {
        EWasteItem i = repo.item(id);
        if (i == null) throw new IllegalArgumentException("No item with id " + id + ".");
        return i;
    }

    public List<EWasteItem> search(ItemStatus status, String keyword, Integer ownerId) {
        List<EWasteItem> out = new ArrayList<>();
        String k = keyword == null ? "" : keyword.trim().toLowerCase();
        for (EWasteItem i : repo.items()) {
            if (status != null && i.status != status) continue;
            if (ownerId != null && i.ownerId != ownerId) continue;
            if (!k.isEmpty()) {
                User o = repo.user(i.ownerId);
                String hay = (i.name + " " + i.brand + " " + Tracking.code(i) + " " + (o == null ? "" : o.username + " " + o.fullName)).toLowerCase();
                if (!hay.contains(k)) continue;
            }
            out.add(i);
        }
        return out;
    }

    // ---- admin actions
    public void approve(int itemId, Decision decision, LocalDate pickup, String note) {
        EWasteItem i = require(itemId);
        expect(i, ItemStatus.SUBMITTED);
        if (decision == null) throw new IllegalArgumentException("A plan is required.");
        if (pickup == null || pickup.isBefore(LocalDate.now())) throw new IllegalArgumentException("Pickup date must be today or later.");
        i.decision = decision;
        i.pickupDate = pickup;
        i.adminNote = note == null ? "" : note.trim();
        i.status = ItemStatus.PICKUP_SCHEDULED;
        repo.saveItem(i);
        log(i, "Approved. Plan: " + decision.name() + ". Pickup on " + pickup);
        notes.push(i.ownerId, "Item #" + i.id + " (" + i.name + ") approved for " + decision.name() + ". Pickup scheduled on " + pickup
                + (i.adminNote.isEmpty() ? "" : ". Note: " + i.adminNote));
    }

    public void reject(int itemId, String reason) {
        EWasteItem i = require(itemId);
        expect(i, ItemStatus.SUBMITTED);
        if (reason == null || reason.trim().isEmpty()) throw new IllegalArgumentException("A reason is required.");
        i.status = ItemStatus.REJECTED;
        i.adminNote = reason.trim();
        repo.saveItem(i);
        log(i, "Rejected: " + i.adminNote);
        notes.push(i.ownerId, "Item #" + i.id + " (" + i.name + ") was rejected. Reason: " + i.adminNote);
    }

    public int markCollected(int itemId) {
        EWasteItem i = require(itemId);
        expect(i, ItemStatus.PICKUP_SCHEDULED);
        i.status = ItemStatus.COLLECTED;
        int pts = (int) Math.round(i.weightKg * i.category.pointsPerKg);
        if (i.decision == Decision.REUSE || i.decision == Decision.REPAIR_REUSE) pts += pts / 2;
        i.pointsAwarded = pts;
        repo.saveItem(i);
        User owner = repo.user(i.ownerId);
        if (owner != null) { owner.points += pts; repo.saveUser(owner); }
        log(i, "Collected. " + pts + " reward points awarded");
        notes.push(i.ownerId, "Item #" + i.id + " (" + i.name + ") collected. You earned " + pts + " reward points!");
        return pts;
    }

    public void changeDecision(int itemId, Decision decision) {
        EWasteItem i = require(itemId);
        expect(i, ItemStatus.COLLECTED);
        if (decision == null) throw new IllegalArgumentException("A plan is required.");
        i.decision = decision;
        repo.saveItem(i);
        log(i, "Plan changed to " + decision.name());
    }

    public void startRefurbish(int itemId, double repairCost) {
        EWasteItem i = require(itemId);
        expect(i, ItemStatus.COLLECTED);
        if (i.decision != Decision.REPAIR_REUSE) throw new IllegalArgumentException("Only items with plan REPAIR_REUSE can be refurbished.");
        if (repairCost < 0) throw new IllegalArgumentException("Repair cost cannot be negative.");
        i.repairCost = repairCost;
        i.status = ItemStatus.REFURBISHING;
        repo.saveItem(i);
        log(i, "Refurbishment started (repair cost " + repairCost + ")");
    }

    public void completeRefurbish(int itemId) {
        EWasteItem i = require(itemId);
        expect(i, ItemStatus.REFURBISHING);
        i.condition = Condition.WORKING;
        i.decision = Decision.REUSE;
        double v = decisionEngine.estimateValueForPolicy(i.category, i.ageYears, Condition.WORKING);
        i.estimatedValue = Math.max(v, DecisionEngine.round2(i.repairCost * 1.3));
        i.status = ItemStatus.COLLECTED;
        repo.saveItem(i);
        log(i, "Refurbishment complete. Ready to list in the market");
    }

    private void expect(EWasteItem i, ItemStatus s) {
        if (i.status != s)
            throw new IllegalArgumentException("Item #" + i.id + " is '" + i.status + "', but '" + s + "' is required.");
    }
}

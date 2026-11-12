package com.ewaste.server;

import com.ewaste.model.*;
import com.ewaste.server.service.CircularityPassport;
import com.ewaste.server.service.DecisionEngine;

import java.util.LinkedHashMap;
import java.util.Map;

/** Converts model objects to JSON-friendly maps. Passwords and salts are never exposed. */
final class Dto {
    private Dto() {}

    static Map<String, Object> user(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.id);
        m.put("username", u.username);
        m.put("fullName", u.fullName);
        m.put("email", u.email);
        m.put("phone", u.phone);
        m.put("address", u.address);
        m.put("role", u.role.name());
        m.put("points", u.points);
        m.put("active", u.active);
        m.put("emailOptOut", u.emailOptOut);
        return m;
    }

    static Map<String, Object> item(EWasteItem i, Map<Integer, String> ownerNames, DecisionEngine engine) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", i.id);
        m.put("trackingCode", com.ewaste.server.service.Tracking.code(i));
        m.put("ownerId", i.ownerId);
        m.put("ownerName", ownerNames.getOrDefault(i.ownerId, "?"));
        m.put("name", i.name);
        m.put("brand", i.brand);
        m.put("category", i.category.name());
        m.put("categoryLabel", i.category.label);
        m.put("hazardous", i.category.hazardous);
        m.put("ageYears", i.ageYears);
        m.put("weightKg", i.weightKg);
        m.put("condition", i.condition.name());
        m.put("conditionLabel", i.condition.label);
        m.put("description", i.description);
        m.put("status", i.status.name());
        m.put("statusLabel", i.status.label);
        m.put("suggestedDecision", i.suggestedDecision == null ? null : i.suggestedDecision.name());
        m.put("decision", i.decision == null ? null : i.decision.name());
        m.put("estimatedValue", i.estimatedValue);
        m.put("repairCost", i.repairCost);
        m.put("pickupDate", i.pickupDate == null ? null : i.pickupDate.toString());
        m.put("adminNote", i.adminNote);
        m.put("pointsAwarded", i.pointsAwarded);
        m.put("createdAt", i.createdAt.toString());
        m.put("history", i.history);
        m.put("decisionReport", decisionReport(engine.assess(i.category, i.ageYears, i.condition,
                null, i.repairCost > 0 ? i.repairCost : null,
                null, i.category.hazardous, i.weightKg)));
        return m;
    }

    static Map<String, Object> decisionReport(DecisionReport report) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("recommendedAction", report.recommendedAction);
        m.put("recommendedDecision", report.recommendedDecision.name());
        m.put("remainingUsefulLifeYears", report.remainingUsefulLifeYears);
        m.put("estimatedResaleValue", report.estimatedResaleValue);
        m.put("repairCost", report.repairCost);
        m.put("recyclingValue", report.recyclingValue);
        m.put("resaleValueAsIs", report.resaleValueAsIs);
        m.put("repairNetValue", report.repairNetValue);
        m.put("repairCostRatio", report.repairCostRatio);
        m.put("financialComparison", report.financialComparison);
        m.put("environmentalImpactEstimateKgCo2", report.environmentalImpactEstimateKgCo2);
        m.put("environmentalImpactMethod", report.environmentalImpactMethod);
        m.put("reasons", report.reasons);
        m.put("alternativeActions", report.alternativeActions);
        m.put("warnings", report.warnings);
        m.put("inputCompletenessPercent", report.inputCompletenessPercent);
        m.put("qualityIndicator", report.qualityIndicator);
        return m;
    }

    static Map<String, Object> listing(Listing l, EWasteItem i, String ownerName) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", l.id);
        m.put("itemId", l.itemId);
        m.put("ownerId", i.ownerId);
        m.put("sellerName", ownerName);
        m.put("trackingCode", com.ewaste.server.service.Tracking.code(i));
        m.put("price", l.price);
        m.put("itemName", i.name);
        m.put("brand", i.brand);
        m.put("category", i.category.name());
        m.put("categoryLabel", i.category.label);
        m.put("condition", i.condition.name());
        m.put("conditionLabel", i.condition.label);
        m.put("ageYears", i.ageYears);
        m.put("description", i.description);
        m.put("photoUrl", l.photoData == null ? null : "/api/market/" + l.id + "/photo");
        Map<String, Object> passport = CircularityPassport.create(i, l);
        m.put("circularityScore", passport.get("circularityScore"));
        m.put("scoreLabel", passport.get("scoreLabel"));
        m.put("co2PotentialKg", passport.get("co2PotentialKg"));
        return m;
    }

    static Map<String, Object> order(Order o, String itemName) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", o.id);
        m.put("itemName", itemName);
        m.put("listPrice", o.listPrice);
        m.put("pointsUsed", o.pointsUsed);
        m.put("discount", o.discount);
        m.put("amountPaid", o.amountPaid);
        m.put("createdAt", o.time.toString());
        return m;
    }

    static Map<String, Object> notification(Notification n) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", n.id);
        m.put("message", n.message);
        m.put("createdAt", n.time.toString());
        m.put("read", n.read);
        return m;
    }

    static Map<String, Object> auction(Auction a, EWasteItem item, int bidCount, AuctionBid winner) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.id);
        m.put("itemId", a.itemId);
        m.put("itemName", item.name);
        m.put("minimumBid", a.minimumBid);
        m.put("closesAt", a.closesAt.toString());
        m.put("status", a.status.name());
        m.put("createdAt", a.createdAt.toString());
        m.put("bidCount", bidCount);
        m.put("winningBid", winner == null ? null : map("id", winner.id, "amount", winner.amount,
                "proposedCollectionDate", winner.proposedCollectionDate.toString(), "recyclingMethod", winner.recyclingMethod));
        return m;
    }

    static Map<String, Object> auctionBid(AuctionBid b, User recycler) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", b.id);
        m.put("auctionId", b.auctionId);
        m.put("recyclerId", b.recyclerId);
        m.put("recyclerName", recycler == null ? "?" : recycler.fullName);
        m.put("itemId", b.itemId);
        m.put("amount", b.amount);
        m.put("proposedCollectionDate", b.proposedCollectionDate.toString());
        m.put("recyclingMethod", b.recyclingMethod);
        m.put("bidTimestamp", b.time.toString());
        m.put("status", b.status.name());
        return m;
    }

    static Map<String, Object> auctionHistory(AuctionHistoryEntry h, User actor) {
        return map("id", h.id, "auctionId", h.auctionId, "actorId", h.actorId,
                "actorName", actor == null ? "System" : actor.fullName, "action", h.action,
                "details", h.details, "timestamp", h.time.toString());
    }

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    static Map<String, Object> batch(RecyclingBatch b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", b.id);
        m.put("partner", b.partner);
        m.put("certified", b.certifiedHazardous);
        m.put("createdAt", b.time.toString());
        m.put("itemCount", b.itemIds.size());
        m.put("totalKg", b.totalKg);
        m.put("metalKg", b.metalKg);
        m.put("plasticKg", b.plasticKg);
        m.put("glassKg", b.glassKg);
        m.put("hazardKg", b.hazardKg);
        m.put("otherKg", b.otherKg);
        m.put("co2SavedKg", b.co2SavedKg);
        return m;
    }
}

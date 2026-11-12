package com.ewaste.server.service;

import com.ewaste.model.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Transparent, privacy-safe product provenance and circularity scoring for marketplace items. */
public final class CircularityPassport {
    private CircularityPassport() {}

    public static Map<String, Object> create(EWasteItem item, Listing listing) {
        int conditionScore = conditionScore(item.condition);
        int ageScore = item.category.maxReuseAge <= 0 ? 0
                : Math.max(0, Math.min(25, 25 * (item.category.maxReuseAge - item.ageYears + 1) / (item.category.maxReuseAge + 1)));
        int reuseScore = item.decision == Decision.REUSE ? 30 : item.decision == Decision.REPAIR_REUSE ? 24 : 0;
        int repairScore = item.repairCost > 0 && (item.status == ItemStatus.LISTED || item.status == ItemStatus.SOLD) ? 10 : 0;
        int score = conditionScore + ageScore + reuseScore + repairScore;
        double weight = item.weightKg;
        boolean sold = item.status == ItemStatus.SOLD;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("trackingCode", Tracking.code(item));
        out.put("itemName", item.name);
        out.put("brand", item.brand);
        out.put("category", item.category.name());
        out.put("categoryLabel", item.category.label);
        out.put("ageYears", item.ageYears);
        out.put("weightKg", weight);
        out.put("condition", item.condition.name());
        out.put("conditionLabel", item.condition.label);
        out.put("status", item.status.name());
        out.put("statusLabel", item.status.label);
        out.put("decision", item.decision == null ? null : item.decision.name());
        out.put("decisionLabel", item.decision == null ? "Awaiting review" : item.decision.label);
        out.put("circularityScore", score);
        out.put("scoreLabel", scoreLabel(score));
        out.put("scoreFactors", List.of(
                factor("Condition", conditionScore, 35, "Working devices retain more reuse value."),
                factor("Remaining useful life", ageScore, 25, "Based on the category reuse-age guideline."),
                factor("Circular pathway", reuseScore, 30, "Reuse scores highest; repair-and-reuse also earns credit."),
                factor("Repair verification", repairScore, 10, "Credit for a recorded refurbishment before listing.")));
        out.put("co2AvoidedKg", sold ? round2(weight * item.category.co2PerKg * 2) : 0.0);
        out.put("co2PotentialKg", item.decision == Decision.REUSE || item.decision == Decision.REPAIR_REUSE
                ? round2(weight * item.category.co2PerKg * 2) : 0.0);
        out.put("co2FactorKgPerKg", item.category.co2PerKg);
        out.put("impactMethod", "Estimated from category-specific CO2 factors; realized reuse uses the same 2x factor as the user impact dashboard.");
        out.put("materialsKg", materials(item));
        out.put("journey", journey(item));
        out.put("listingId", listing == null ? null : listing.id);
        out.put("price", listing == null || !listing.active ? null : listing.price);
        out.put("photoUrl", listing == null || listing.photoData == null ? null : "/api/market/" + listing.id + "/photo");
        out.put("privacyNote", "No donor or buyer contact details are included.");
        return out;
    }

    private static int conditionScore(Condition condition) {
        switch (condition) {
            case WORKING: return 35;
            case MINOR_FAULT: return 25;
            case MAJOR_FAULT: return 12;
            default: return 0;
        }
    }

    private static String scoreLabel(int score) {
        if (score >= 85) return "Excellent circularity";
        if (score >= 65) return "Strong circularity";
        if (score >= 40) return "Moderate circularity";
        return "Limited reuse potential";
    }

    private static Map<String, Object> factor(String name, int score, int max, String explanation) {
        Map<String, Object> factor = new LinkedHashMap<>();
        factor.put("name", name);
        factor.put("score", score);
        factor.put("max", max);
        factor.put("explanation", explanation);
        return factor;
    }

    private static Map<String, Object> materials(EWasteItem item) {
        Map<String, Object> material = new LinkedHashMap<>();
        material.put("metalKg", round2(item.weightKg * item.category.metalPct));
        material.put("plasticKg", round2(item.weightKg * item.category.plasticPct));
        material.put("glassKg", round2(item.weightKg * item.category.glassPct));
        material.put("hazardousKg", round2(item.weightKg * item.category.hazardPct));
        material.put("otherKg", round2(item.weightKg * item.category.otherPct()));
        return material;
    }

    private static List<Object> journey(EWasteItem item) {
        List<Object> journey = new ArrayList<>();
        for (Object raw : Tracking.steps(item)) {
            Map<?, ?> step = (Map<?, ?>) raw;
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("label", step.get("label"));
            entry.put("complete", step.get("done"));
            journey.add(entry);
        }
        return journey;
    }

    private static double round2(double value) { return Math.round(value * 100.0) / 100.0; }
}

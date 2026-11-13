package com.ewaste.server.service;

import com.ewaste.model.*;

/** Explainable financial and safety rules for reuse, repair, recycling and hazardous disposal. */
public final class DecisionEngine {
    private static final DecisionEngine DEFAULT = new DecisionEngine(DecisionPolicy.defaults());
    private final DecisionPolicy policy;

    public DecisionEngine(DecisionPolicy policy) {
        if (policy == null) throw new IllegalArgumentException("Decision policy is required.");
        this.policy = policy;
    }

    /** Compatibility entry point; recommendation now uses the same financial and safety assessment as reports. */
    public static Decision recommend(Category cat, int ageYears, Condition cond) {
        return DEFAULT.assess(cat, ageYears, cond, null, null, null, null, null).recommendedDecision;
    }

    /** Estimated resale value: base price x condition x age depreciation. */
    public static double estimateValue(Category cat, int ageYears, Condition cond) {
        double depreciation = Math.max(0.1, 1.0 - (double) ageYears / (cat.maxReuseAge + 1));
        return round2(cat.baseValue * cond.valueFactor * depreciation);
    }

    /** Estimate resale using this engine's category-specific useful-life policy. */
    public double estimateValueForPolicy(Category category, int ageYears, Condition condition) {
        if (category == null || condition == null) throw new IllegalArgumentException("Category and condition are required.");
        if (ageYears < 0 || ageYears > 40) throw new IllegalArgumentException("Age must be between 0 and 40 years.");
        DecisionPolicy.CategoryRule rule = policy.forCategory(category);
        double depreciation = Math.max(0.1, 1.0 - (double) ageYears / (rule.usefulLifeYears + 1));
        return round2(category.baseValue * condition.valueFactor * depreciation);
    }

    public DecisionReport assess(Category category, int ageYears, Condition condition, Double estimatedResaleValue,
                                 Double repairCost, Double recyclingValue, Boolean hazardousClassification,
                                 Double weightKg) {
        if (category == null || condition == null) throw new IllegalArgumentException("Category and condition are required.");
        if (ageYears < 0 || ageYears > 40) throw new IllegalArgumentException("Age must be between 0 and 40 years.");
        validateOptional("estimatedResaleValue", estimatedResaleValue);
        validateOptional("repairCost", repairCost);
        validateOptional("recyclingValue", recyclingValue);
        validateOptional("weightKg", weightKg);
        if (weightKg != null && weightKg <= 0) throw new IllegalArgumentException("Weight must be greater than zero.");

        DecisionPolicy.CategoryRule rule = policy.forCategory(category);
        DecisionReport report = new DecisionReport();
        report.remainingUsefulLifeYears = Math.max(0, (int) Math.ceil(rule.usefulLifeYears - ageYears));
        boolean hasResale = estimatedResaleValue != null;
        report.estimatedResaleValue = round2(hasResale ? estimatedResaleValue
                : estimateValueForPolicy(category, ageYears, Condition.WORKING));
        report.resaleValueAsIs = round2(hasResale ? estimatedResaleValue * condition.valueFactor
                : report.estimatedResaleValue * condition.valueFactor);
        boolean hasRepair = repairCost != null;
        report.repairCost = round2(hasRepair ? repairCost : report.estimatedResaleValue * rule.estimatedRepairCostRatio);
        boolean hasRecovery = recyclingValue != null;
        double materialRecoveryEstimate = weightKg == null ? 0 : round2(weightKg * rule.recyclingValuePerKg
                * (category.metalPct + category.plasticPct * 0.2 + category.glassPct * 0.1 + category.otherPct() * 0.05));
        report.recyclingValue = round2(hasRecovery ? recyclingValue : materialRecoveryEstimate);
        report.repairNetValue = round2(report.estimatedResaleValue - report.repairCost);
        report.repairCostRatio = report.estimatedResaleValue == 0 ? 0
                : round2(report.repairCost / report.estimatedResaleValue);
        report.financialComparison.put("reuse", report.resaleValueAsIs);
        report.financialComparison.put("repairAndReuse", report.repairNetValue);
        report.financialComparison.put("recycle", report.recyclingValue);

        boolean hazardous = category.hazardous || Boolean.TRUE.equals(hazardousClassification);
        if (hazardousClassification == null && !category.hazardous)
            report.warnings.add("Hazard classification was not independently supplied; category classification is used.");
        if (hazardous) {
            report.recommendedDecision = Decision.HAZARDOUS_DISPOSAL;
            report.reasons.add("Hazardous material classification takes precedence over financial value.");
            report.reasons.add("Reuse and ordinary recycling are not recommended for a hazardous item.");
        } else if (ageYears > rule.usefulLifeYears || report.remainingUsefulLifeYears < rule.minimumRemainingLifeYears) {
            report.recommendedDecision = Decision.RECYCLE;
            report.reasons.add("Estimated remaining useful life (" + report.remainingUsefulLifeYears
                    + " year(s)) is below this category's configured reuse threshold.");
        } else if (condition == Condition.WORKING && report.resaleValueAsIs >= report.recyclingValue) {
            report.recommendedDecision = Decision.REUSE;
            report.reasons.add("Working device resale estimate (" + report.resaleValueAsIs
                    + ") is at least the estimated material recovery value (" + report.recyclingValue + ").");
        } else if (report.repairNetValue > report.recyclingValue
                && report.estimatedResaleValue > 0
                && report.repairCost / report.estimatedResaleValue <= rule.maxRepairCostRatio) {
            report.recommendedDecision = Decision.REPAIR_REUSE;
            report.reasons.add("Estimated repair cost is " + round2(100 * report.repairCostRatio)
                    + "% of functional resale value, within the configured " + round2(100 * rule.maxRepairCostRatio)
                    + "% threshold.");
            report.reasons.add("Estimated resale after repair less repair cost (" + report.repairNetValue
                    + ") exceeds material recovery value (" + report.recyclingValue + ").");
        } else {
            report.recommendedDecision = Decision.RECYCLE;
            report.reasons.add("Repair/resale does not exceed the estimated material recovery value or the category repair-cost threshold.");
        }
        report.recommendedAction = report.recommendedDecision == Decision.REPAIR_REUSE
                ? "REPAIR_AND_REUSE" : report.recommendedDecision.name();

        if (hazardous) {
            report.alternativeActions.add("No safe alternative without specialist hazard assessment.");
        } else {
            if (report.recommendedDecision != Decision.REUSE) report.alternativeActions.add("REUSE");
            if (report.recommendedDecision != Decision.REPAIR_REUSE) report.alternativeActions.add("REPAIR_AND_REUSE");
            if (report.recommendedDecision != Decision.RECYCLE) report.alternativeActions.add("RECYCLE");
        }
        if (condition == Condition.DEAD) report.warnings.add("Device is reported dead; a repair-and-reuse recommendation depends on confirming repair feasibility.");
        if (ageYears > rule.usefulLifeYears) report.warnings.add("Device age exceeds the configured category useful life.");
        if (!hasResale) report.warnings.add("Resale value is an age-depreciated category estimate, not a market quote.");
        if (!hasRepair) report.warnings.add("Repair cost is estimated using the category's configured repair ratio.");
        if (!hasRecovery) report.warnings.add(weightKg == null
                ? "Weight was not provided; recycling value is zero until material weight is known."
                : "Material recovery value is an estimate using category material fractions and the configured rate.");
        if (hazardousClassification != null && !hazardousClassification && category.hazardous)
            report.warnings.add("The explicit non-hazardous input conflicts with the category's hazardous classification.");

        report.environmentalImpactEstimateKgCo2 = round2(weightKg == null ? 0 : weightKg * category.co2PerKg);
        report.environmentalImpactMethod = "Single indicative category-factor estimate (weight x "
                + category.co2PerKg + " kg CO2/kg). It is not added to realized analytics and is not an avoided-emissions claim.";
        int known = 3 + (weightKg != null ? 1 : 0) + (hasResale ? 1 : 0) + (hasRepair ? 1 : 0)
                + (hasRecovery ? 1 : 0) + (hazardousClassification != null ? 1 : 0);
        report.inputCompletenessPercent = (int) Math.round(100.0 * known / 8.0);
        report.qualityIndicator = report.inputCompletenessPercent >= 88 ? "HIGH INPUT COMPLETENESS"
                : report.inputCompletenessPercent >= 63 ? "MEDIUM INPUT COMPLETENESS" : "LOW INPUT COMPLETENESS";
        return report;
    }

    private static void validateOptional(String name, Double value) {
        if (value != null && (!Double.isFinite(value) || value < 0))
            throw new IllegalArgumentException(name + " must be a finite, non-negative amount.");
    }

    public static double round2(double v) { return Math.round(v * 100.0) / 100.0; }
}

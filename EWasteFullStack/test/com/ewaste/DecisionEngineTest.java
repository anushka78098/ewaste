package com.ewaste;

import com.ewaste.model.Category;
import com.ewaste.model.Condition;
import com.ewaste.model.Decision;
import com.ewaste.model.DecisionReport;
import com.ewaste.server.service.DecisionEngine;
import com.ewaste.server.service.DecisionPolicy;

import java.nio.file.Files;
import java.nio.file.Path;

/** Focused rule and report tests; the quality indicator measures input completeness, not probability. */
public class DecisionEngineTest {
    private static int passed, failed;

    private static void check(String name, boolean ok) {
        if (ok) { passed++; System.out.println("  PASS " + name); }
        else { failed++; System.out.println("  FAIL " + name); }
    }

    public static void main(String[] args) throws Exception {
        DecisionEngine engine = new DecisionEngine(DecisionPolicy.defaults());
        DecisionReport working = engine.assess(Category.MOBILE, 1, Condition.WORKING,
                null, null, null, false, 0.2);
        check("working device recommends reuse", working.recommendedDecision == Decision.REUSE);
        check("useful life and depreciated resale are reported", working.remainingUsefulLifeYears == 5
                && working.estimatedResaleValue > 0 && working.resaleValueAsIs > 0);
        check("financial comparison includes all three viable pathways",
                working.financialComparison.containsKey("reuse") && working.financialComparison.containsKey("repairAndReuse")
                        && working.financialComparison.containsKey("recycle"));
        check("reasons, alternatives and transparent environmental method are provided",
                !working.reasons.isEmpty() && !working.alternativeActions.isEmpty()
                        && working.environmentalImpactMethod.contains("not an avoided-emissions claim"));

        DecisionReport damaged = engine.assess(Category.LAPTOP, 1, Condition.MAJOR_FAULT,
                400.0, 40.0, 10.0, false, 5.0);
        check("financially viable damaged device recommends repair and reuse",
                damaged.recommendedDecision == Decision.REPAIR_REUSE
                        && "REPAIR_AND_REUSE".equals(damaged.recommendedAction));
        check("repair threshold and net financial comparison are explained",
                damaged.repairNetValue == 360.0 && damaged.repairCostRatio == 0.1
                        && damaged.reasons.toString().contains("threshold"));

        DecisionReport expensiveRepair = engine.assess(Category.LAPTOP, 1, Condition.MINOR_FAULT,
                400.0, 300.0, 20.0, false, 5.0);
        check("uneconomic repair is redirected to recycling", expensiveRepair.recommendedDecision == Decision.RECYCLE);
        DecisionReport old = engine.assess(Category.TELEVISION, Category.TELEVISION.maxReuseAge + 1,
                Condition.WORKING, null, null, null, false, 10.0);
        check("device at end of category useful life recommends recycling", old.recommendedDecision == Decision.RECYCLE
                && old.warnings.toString().contains("exceeds"));
        check("configured maximum-use age remains eligible as in the legacy rule",
                engine.assess(Category.MOBILE, Category.MOBILE.maxReuseAge, Condition.WORKING,
                        null, null, null, false, 0.2).recommendedDecision == Decision.REUSE);

        DecisionReport hazardous = engine.assess(Category.BATTERY, 1, Condition.WORKING,
                500.0, 1.0, 100.0, true, 1.0);
        check("hazardous classification blocks reuse despite high resale value",
                hazardous.recommendedDecision == Decision.HAZARDOUS_DISPOSAL);
        check("hazardous report does not advertise reuse alternatives",
                hazardous.alternativeActions.toString().contains("No safe alternative")
                        && !hazardous.alternativeActions.toString().contains("REUSE"));

        DecisionReport complete = engine.assess(Category.LAPTOP, 2, Condition.WORKING,
                200.0, 20.0, 8.0, false, 2.0);
        DecisionReport estimated = engine.assess(Category.LAPTOP, 2, Condition.WORKING,
                null, null, null, null, null);
        check("complete user-supplied values receive full completeness quality",
                complete.inputCompletenessPercent == 100 && complete.qualityIndicator.startsWith("HIGH"));
        check("missing values lower quality and produce explicit estimate warnings",
                estimated.inputCompletenessPercent < complete.inputCompletenessPercent
                        && estimated.qualityIndicator.startsWith("LOW") && estimated.warnings.size() >= 4);
        check("existing Decision enum recommendation API remains compatible",
                DecisionEngine.recommend(Category.LAPTOP, 2, Condition.MINOR_FAULT) == Decision.REPAIR_REUSE);

        DecisionPolicy custom = DecisionPolicy.defaults().withRule(Category.LAPTOP,
                new DecisionPolicy.CategoryRule(7, 1, 0.09, 0.25, 0.38));
        DecisionReport overridden = new DecisionEngine(custom).assess(Category.LAPTOP, 1, Condition.MINOR_FAULT,
                400.0, 40.0, 10.0, false, 5.0);
        check("category-specific repair threshold is overrideable",
                overridden.recommendedDecision == Decision.RECYCLE);

        Path config = Files.createTempFile("ewaste-decision", ".properties");
        try {
            Files.write(config, "decision.MOBILE.maxRepairCostRatio=not-a-number\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            boolean invalidConfigRejected = false;
            try { DecisionPolicy.load(config); }
            catch (IllegalArgumentException e) { invalidConfigRejected = true; }
            check("invalid policy configuration fails explicitly", invalidConfigRejected);
        } finally { Files.deleteIfExists(config); }

        boolean invalidInputRejected = false;
        try { engine.assess(Category.LAPTOP, 1, Condition.WORKING, -1.0, null, null, null, 2.0); }
        catch (IllegalArgumentException e) { invalidInputRejected = true; }
        check("negative financial inputs are rejected", invalidInputRejected);
        System.out.println("\n" + passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }
}

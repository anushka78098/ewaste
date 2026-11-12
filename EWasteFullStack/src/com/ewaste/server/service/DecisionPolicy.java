package com.ewaste.server.service;

import com.ewaste.model.Category;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.Properties;

/** Category-specific, overrideable thresholds and estimate rates for the rule-based decision engine. */
public final class DecisionPolicy {
    public static final class CategoryRule {
        public final int usefulLifeYears;
        public final double minimumRemainingLifeYears;
        public final double maxRepairCostRatio;
        public final double estimatedRepairCostRatio;
        public final double recyclingValuePerKg;

        public CategoryRule(int usefulLifeYears, double minimumRemainingLifeYears, double maxRepairCostRatio,
                            double estimatedRepairCostRatio, double recyclingValuePerKg) {
            if (usefulLifeYears <= 0 || !finiteNonNegative(minimumRemainingLifeYears)
                    || !finiteNonNegative(maxRepairCostRatio) || maxRepairCostRatio > 1
                    || !finiteNonNegative(estimatedRepairCostRatio) || estimatedRepairCostRatio > 1
                    || !finiteNonNegative(recyclingValuePerKg))
                throw new IllegalArgumentException("Invalid decision policy threshold.");
            this.usefulLifeYears = usefulLifeYears;
            this.minimumRemainingLifeYears = minimumRemainingLifeYears;
            this.maxRepairCostRatio = maxRepairCostRatio;
            this.estimatedRepairCostRatio = estimatedRepairCostRatio;
            this.recyclingValuePerKg = recyclingValuePerKg;
        }
    }

    private final Map<Category, CategoryRule> rules = new EnumMap<>(Category.class);

    private DecisionPolicy() {
        for (Category category : Category.values())
            rules.put(category, defaults(category));
    }

    public static DecisionPolicy defaults() { return new DecisionPolicy(); }

    public CategoryRule forCategory(Category category) { return rules.get(category); }

    /** Returns a copy with one category rule replaced. */
    public DecisionPolicy withRule(Category category, CategoryRule rule) {
        if (category == null || rule == null) throw new IllegalArgumentException("Category and decision rule are required.");
        DecisionPolicy copy = new DecisionPolicy();
        copy.rules.putAll(rules);
        copy.rules.put(category, rule);
        return copy;
    }

    /** Loads optional keys such as decision.LAPTOP.maxRepairCostRatio; absent keys retain defaults. */
    public static DecisionPolicy load(Path file) {
        DecisionPolicy policy = defaults();
        if (file == null || !Files.exists(file)) return policy;
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            properties.load(input);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read decision policy " + file + ": " + e.getMessage(), e);
        }
        for (Category category : Category.values()) {
            CategoryRule current = policy.forCategory(category);
            String prefix = "decision." + category.name() + ".";
            int life = integer(properties, prefix + "usefulLifeYears", current.usefulLifeYears);
            double minimumLife = number(properties, prefix + "minimumRemainingLifeYears", current.minimumRemainingLifeYears);
            double maxRepair = number(properties, prefix + "maxRepairCostRatio", current.maxRepairCostRatio);
            double estimatedRepair = number(properties, prefix + "estimatedRepairCostRatio", current.estimatedRepairCostRatio);
            double recovery = number(properties, prefix + "recyclingValuePerKg", current.recyclingValuePerKg);
            policy.rules.put(category, new CategoryRule(life, minimumLife, maxRepair, estimatedRepair, recovery));
        }
        return policy;
    }

    private static CategoryRule defaults(Category category) {
        double repairLimit;
        double repairEstimate;
        double recoveryRate;
        switch (category) {
            case MOBILE: case TABLET: case LAPTOP:
                repairLimit = 0.45; repairEstimate = 0.25; recoveryRate = 0.38; break;
            case DESKTOP: case TELEVISION: case APPLIANCE:
                repairLimit = 0.55; repairEstimate = 0.35; recoveryRate = 0.32; break;
            case PRINTER:
                repairLimit = 0.35; repairEstimate = 0.30; recoveryRate = 0.22; break;
            case BATTERY:
                repairLimit = 0.0; repairEstimate = 0.0; recoveryRate = 0.18; break;
            case ACCESSORY:
                repairLimit = 0.20; repairEstimate = 0.15; recoveryRate = 0.12; break;
            default:
                repairLimit = 0.30; repairEstimate = 0.25; recoveryRate = 0.20; break;
        }
        return new CategoryRule(category.maxReuseAge, 0,
                repairLimit, repairEstimate, recoveryRate);
    }

    private static int integer(Properties properties, String key, int fallback) {
        String value = properties.getProperty(key);
        if (value == null) return fallback;
        try { return Integer.parseInt(value.trim()); }
        catch (NumberFormatException e) { throw new IllegalArgumentException(key + " must be a whole number.", e); }
    }

    private static double number(Properties properties, String key, double fallback) {
        String value = properties.getProperty(key);
        if (value == null) return fallback;
        try {
            double parsed = Double.parseDouble(value.trim());
            if (!Double.isFinite(parsed)) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException e) { throw new IllegalArgumentException(key + " must be a finite number.", e); }
    }

    private static boolean finiteNonNegative(double value) { return Double.isFinite(value) && value >= 0; }
}

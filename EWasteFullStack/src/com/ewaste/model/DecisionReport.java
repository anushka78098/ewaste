package com.ewaste.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Explainable, non-persisted decision estimate. Financial values are estimates, not quotes. */
public class DecisionReport {
    public Decision recommendedDecision;
    public String recommendedAction;
    public int remainingUsefulLifeYears;
    public double estimatedResaleValue;
    public double repairCost;
    public double recyclingValue;
    public double resaleValueAsIs;
    public double repairNetValue;
    public double repairCostRatio;
    public final Map<String, Double> financialComparison = new LinkedHashMap<>();
    public final List<String> reasons = new ArrayList<>();
    public final List<String> alternativeActions = new ArrayList<>();
    public final List<String> warnings = new ArrayList<>();
    public double environmentalImpactEstimateKgCo2;
    public String environmentalImpactMethod;
    public int inputCompletenessPercent;
    public String qualityIndicator;
}

package com.ewaste.model;

/**
 * Device categories with the data used for valuation, rewards and material recovery.
 * Material fractions are approximate shares of the item's weight; the remainder is "other".
 */
public enum Category {
    //          label                 haz    base   maxAge pts/kg metal  plastic glass  hazard co2/kg
    MOBILE     ("Mobile phone",       false, 150.0, 6,     20,    0.35,  0.35,   0.10,  0.03,  30.0),
    TABLET     ("Tablet",             false, 200.0, 6,     18,    0.35,  0.30,   0.15,  0.03,  25.0),
    LAPTOP     ("Laptop",             false, 400.0, 7,     15,    0.40,  0.30,   0.10,  0.03,  20.0),
    DESKTOP    ("Desktop computer",   false, 250.0, 8,     10,    0.55,  0.25,   0.05,  0.02,  15.0),
    TELEVISION ("TV / Monitor",       false, 200.0, 10,     8,    0.25,  0.30,   0.30,  0.05,  10.0),
    APPLIANCE  ("Large appliance",    false, 150.0, 12,     5,    0.60,  0.25,   0.05,  0.03,   6.0),
    PRINTER    ("Printer / Scanner",  false,  80.0, 8,      8,    0.30,  0.50,   0.05,  0.03,   8.0),
    BATTERY    ("Battery / Power bank", true, 20.0, 3,     30,    0.45,  0.15,   0.00,  0.30,  12.0),
    ACCESSORY  ("Cables & accessories", false, 10.0, 10,    6,    0.60,  0.35,   0.00,  0.00,   5.0),
    OTHER      ("Other electronics",  false,  30.0, 8,      5,    0.30,  0.40,   0.05,  0.03,   6.0);

    public final String label;
    public final boolean hazardous;
    public final double baseValue;
    public final int maxReuseAge;
    public final int pointsPerKg;
    public final double metalPct, plasticPct, glassPct, hazardPct;
    public final double co2PerKg;

    Category(String label, boolean hazardous, double baseValue, int maxReuseAge, int pointsPerKg,
             double metalPct, double plasticPct, double glassPct, double hazardPct, double co2PerKg) {
        this.label = label;
        this.hazardous = hazardous;
        this.baseValue = baseValue;
        this.maxReuseAge = maxReuseAge;
        this.pointsPerKg = pointsPerKg;
        this.metalPct = metalPct;
        this.plasticPct = plasticPct;
        this.glassPct = glassPct;
        this.hazardPct = hazardPct;
        this.co2PerKg = co2PerKg;
    }

    public double otherPct() { return Math.max(0, 1.0 - metalPct - plasticPct - glassPct - hazardPct); }

    @Override public String toString() { return label; }
}

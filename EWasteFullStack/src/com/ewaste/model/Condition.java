package com.ewaste.model;

/** Physical/functional condition of a submitted device. */
public enum Condition {
    WORKING("Working", 1.0),
    MINOR_FAULT("Minor fault (repairable)", 0.6),
    MAJOR_FAULT("Major fault", 0.3),
    DEAD("Dead / not working", 0.0);

    public final String label;
    public final double valueFactor;

    Condition(String label, double valueFactor) {
        this.label = label;
        this.valueFactor = valueFactor;
    }

    @Override public String toString() { return label; }
}

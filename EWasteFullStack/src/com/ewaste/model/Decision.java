package com.ewaste.model;

/** What the admin decides to do with a collected item. */
public enum Decision {
    REUSE("Reuse - sell as-is in the market"),
    REPAIR_REUSE("Repair, then reuse - refurbish and sell"),
    RECYCLE("Recycle - material recovery"),
    HAZARDOUS_DISPOSAL("Hazardous disposal - certified recycler");

    public final String label;
    Decision(String label) { this.label = label; }
    @Override public String toString() { return label; }

    public boolean isRecycling() { return this == RECYCLE || this == HAZARDOUS_DISPOSAL; }
}

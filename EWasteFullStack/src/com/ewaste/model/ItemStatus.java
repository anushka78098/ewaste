package com.ewaste.model;

public enum ItemStatus {
    SUBMITTED("Submitted - awaiting admin review"),
    REJECTED("Rejected"),
    PICKUP_SCHEDULED("Pickup scheduled"),
    COLLECTED("Collected at facility"),
    REFURBISHING("Being refurbished"),
    LISTED("Listed in market"),
    SOLD("Sold / reused"),
    QUEUED_RECYCLE("Queued for recycling"),
    RECYCLED("Recycled");

    public final String label;
    ItemStatus(String label) { this.label = label; }
    @Override public String toString() { return label; }
}

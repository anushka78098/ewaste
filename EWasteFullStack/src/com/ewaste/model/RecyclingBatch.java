package com.ewaste.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** One shipment of items sent to a recycling partner. */
public class RecyclingBatch implements Serializable {
    private static final long serialVersionUID = 1L;

    public int id;
    public String partner;
    public boolean certifiedHazardous;
    public LocalDateTime time = LocalDateTime.now();
    public List<Integer> itemIds = new ArrayList<>();
    public double totalKg, metalKg, plasticKg, glassKg, hazardKg, otherKg, co2SavedKg;
}

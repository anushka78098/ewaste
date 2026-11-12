package com.ewaste.model;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class EWasteItem implements Serializable {
    private static final long serialVersionUID = 2L;

    public int id;
    public int ownerId;
    public String name;
    public String brand = "";
    public Category category;
    public int ageYears;
    public double weightKg;
    public Condition condition;
    public String description = "";

    public ItemStatus status = ItemStatus.SUBMITTED;
    public Decision suggestedDecision;
    public Decision decision;
    public double estimatedValue;
    public double repairCost;
    public LocalDate pickupDate;
    public String adminNote = "";
    public int pointsAwarded;
    public LocalDateTime createdAt = LocalDateTime.now();

    /** Timeline lines; only filled when an item is loaded individually with Repository.item(id). */
    public List<String> history = new ArrayList<>();
}

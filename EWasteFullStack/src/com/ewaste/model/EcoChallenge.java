package com.ewaste.model;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class EcoChallenge implements Serializable {
    private static final long serialVersionUID = 1L;

    public int id;
    public String title;
    public String description = "";
    public EcoChallengeType type;
    public EcoChallengePeriod period;
    public double targetValue;
    public int rewardPoints;
    public LocalDate startDate;
    public LocalDate endDate;
    public boolean active = true;
    public LocalDateTime createdAt = LocalDateTime.now();
}

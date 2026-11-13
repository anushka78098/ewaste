package com.ewaste.model;

import java.io.Serializable;
import java.time.LocalDateTime;

public class ChallengeReward implements Serializable {
    private static final long serialVersionUID = 1L;

    public int id;
    public int challengeId;
    public int userId;
    public int points;
    public LocalDateTime awardedAt = LocalDateTime.now();
}

package com.ewaste.model;

import java.io.Serializable;
import java.time.LocalDateTime;

public class ChallengeEnrollment implements Serializable {
    private static final long serialVersionUID = 1L;

    public int id;
    public int challengeId;
    public int userId;
    public LocalDateTime enrolledAt = LocalDateTime.now();
    public double progress;
    public EcoChallengeStatus status = EcoChallengeStatus.ACTIVE;
    public LocalDateTime completedAt;
    public boolean halfwayNotified;
}

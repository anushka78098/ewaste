package com.ewaste.model;

import java.io.Serializable;
import java.time.LocalDateTime;

public class Achievement implements Serializable {
    private static final long serialVersionUID = 1L;

    public int id;
    public int userId;
    public String key;
    public String label;
    public String description;
    public LocalDateTime earnedAt = LocalDateTime.now();
}

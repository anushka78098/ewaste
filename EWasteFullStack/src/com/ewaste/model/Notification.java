package com.ewaste.model;

import java.io.Serializable;
import java.time.LocalDateTime;

public class Notification implements Serializable {
    private static final long serialVersionUID = 1L;

    public int id;
    public int userId;
    public String message;
    public LocalDateTime time = LocalDateTime.now();
    public boolean read;
}

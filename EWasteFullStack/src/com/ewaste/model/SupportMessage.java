package com.ewaste.model;

import java.io.Serializable;
import java.time.LocalDateTime;

public class SupportMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    public int id;
    public int userId;
    public int senderId;
    public String text = "";
    public LocalDateTime time = LocalDateTime.now();
    public boolean readByAdmin;
    public boolean readByUser;
}

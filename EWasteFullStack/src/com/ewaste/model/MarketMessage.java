package com.ewaste.model;

import java.io.Serializable;
import java.time.LocalDateTime;

public class MarketMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    public int id;
    public int listingId;
    public int senderId;
    public int recipientId;
    public String text = "";
    public String fileName = "";
    public String mediaType = "";
    public byte[] fileData;
    public LocalDateTime time = LocalDateTime.now();
}

package com.ewaste.model;

import java.io.Serializable;
import java.time.LocalDateTime;

public class AuctionHistoryEntry implements Serializable {
    private static final long serialVersionUID = 1L;

    public int id;
    public int auctionId;
    public Integer actorId;
    public String action = "";
    public String details = "";
    public LocalDateTime time = LocalDateTime.now();
}

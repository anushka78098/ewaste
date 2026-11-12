package com.ewaste.model;

import java.io.Serializable;
import java.time.LocalDateTime;

public class Auction implements Serializable {
    private static final long serialVersionUID = 1L;

    public int id;
    public int itemId;
    public int createdBy;
    public double minimumBid;
    public LocalDateTime closesAt = LocalDateTime.now();
    public LocalDateTime createdAt = LocalDateTime.now();
    public AuctionStatus status = AuctionStatus.OPEN;
}

package com.ewaste.model;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class AuctionBid implements Serializable {
    private static final long serialVersionUID = 1L;

    public int id;
    public int auctionId;
    public int recyclerId;
    public int itemId;
    public double amount;
    public LocalDate proposedCollectionDate;
    public String recyclingMethod = "";
    public LocalDateTime time = LocalDateTime.now();
    public BidStatus status = BidStatus.PENDING;
}

package com.ewaste.model;

import java.io.Serializable;
import java.time.LocalDateTime;

public class Order implements Serializable {
    private static final long serialVersionUID = 1L;

    public int id;
    public int listingId;
    public int itemId;
    public int buyerId;
    public double listPrice;
    public int pointsUsed;
    public double discount;
    public double amountPaid;
    public LocalDateTime time = LocalDateTime.now();
}

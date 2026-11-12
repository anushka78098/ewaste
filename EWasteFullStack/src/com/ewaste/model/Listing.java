package com.ewaste.model;

import java.io.Serializable;

/** A refurbished/reusable item offered in the marketplace. */
public class Listing implements Serializable {
    private static final long serialVersionUID = 1L;

    public int id;
    public int itemId;
    public double price;
    public boolean active = true;
    public String photoFileName;
    public String photoMediaType;
    public byte[] photoData;
}

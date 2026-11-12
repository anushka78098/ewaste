package com.ewaste.model;

import java.io.Serializable;
import java.time.LocalDate;

/** A collection drive with a weight goal, run by the admin. */
public class Campaign implements Serializable {
    private static final long serialVersionUID = 1L;

    public int id;
    public String name;
    public String description = "";
    public double goalKg;
    public LocalDate startDate;
    public LocalDate endDate;
    public boolean active = true;
}

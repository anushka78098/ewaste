package com.ewaste.model;

import java.io.Serializable;

public class User implements Serializable {
    private static final long serialVersionUID = 1L;

    public int id;
    public String username;
    public String salt;
    public String passwordHash;
    public String fullName;
    public String email;
    public String phone;
    public String address;
    public Role role = Role.USER;
    public int points;
    public boolean active = true;
    /** true = the user turned email notifications off (in-app notifications are always kept). */
    public boolean emailOptOut;

    public boolean isAdmin() { return role == Role.ADMIN; }
}

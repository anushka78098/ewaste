package com.ewaste.server.service;

import com.ewaste.common.Hash;
import com.ewaste.model.*;
import com.ewaste.server.repo.Repository;

public class AuthService {
    public static final String DEFAULT_ADMIN = "admin";
    public static final String DEFAULT_ADMIN_PASSWORD = "admin123";
    private static final String EMAIL = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";

    private final Repository repo;

    public AuthService(Repository repo) {
        this.repo = repo;
        repo.atomic(() -> {
            for (User u : repo.users()) if (u.isAdmin()) return null;
            String initial = System.getenv("EWASTE_ADMIN_PASSWORD");
            if (initial == null || initial.length() < 6) initial = DEFAULT_ADMIN_PASSWORD;
            repo.addUser(build(DEFAULT_ADMIN, initial, "System Administrator", "admin@ewaste.local", "", "", Role.ADMIN));
            return null;
        });
    }

    private User build(String username, String password, String name, String email, String phone, String address, Role role) {
        User u = new User();
        u.username = username;
        u.salt = Hash.newSalt();
        u.passwordHash = Hash.sha256(u.salt, password);
        u.fullName = name;
        u.email = email;
        u.phone = phone == null ? "" : phone;
        u.address = address == null ? "" : address;
        u.role = role;
        return u;
    }

    /** True while an admin account still has the well-known default password. */
    public boolean adminHasDefaultPassword() { return usesDefaultPassword(repo); }

    public static boolean usesDefaultPassword(Repository repo) {
        for (User u : repo.users())
            if (u.isAdmin() && u.passwordHash.equals(Hash.sha256(u.salt, DEFAULT_ADMIN_PASSWORD))) return true;
        return false;
    }

    public User register(String username, String password, String name, String email, String phone, String address) {
        if (username == null || !username.matches("[A-Za-z0-9_]{3,20}"))
            throw new IllegalArgumentException("Username must be 3-20 letters, digits or underscores.");
        if (password == null || password.length() < 6)
            throw new IllegalArgumentException("Password must be at least 6 characters.");
        if (name == null || name.trim().isEmpty()) throw new IllegalArgumentException("Full name is required.");
        if (email == null || !email.matches(EMAIL)) throw new IllegalArgumentException("Please enter a valid email address.");
        if (repo.userByName(username) != null) throw new IllegalArgumentException("That username is already taken.");
        return repo.addUser(build(username, password, name.trim(), email, phone, address, Role.USER));
    }

    public User login(String username, String password) {
        User u = username == null ? null : repo.userByName(username);
        if (u == null || password == null || !u.passwordHash.equals(Hash.sha256(u.salt, password)))
            throw new IllegalArgumentException("Invalid username or password.");
        if (!u.active) throw new IllegalArgumentException("This account has been deactivated. Contact the admin.");
        return u;
    }

    public void changePassword(User u, String oldPw, String newPw) {
        if (oldPw == null || !u.passwordHash.equals(Hash.sha256(u.salt, oldPw)))
            throw new IllegalArgumentException("Current password is incorrect.");
        if (newPw == null || newPw.length() < 6) throw new IllegalArgumentException("Password must be at least 6 characters.");
        u.salt = Hash.newSalt();
        u.passwordHash = Hash.sha256(u.salt, newPw);
        repo.saveUser(u);
    }

    public User updateProfile(User u, String fullName, String email, String phone, String address, Boolean emailOptOut) {
        if (fullName != null && !fullName.trim().isEmpty()) u.fullName = fullName.trim();
        if (email != null) {
            if (!email.matches(EMAIL)) throw new IllegalArgumentException("Please enter a valid email address.");
            u.email = email;
        }
        if (phone != null) u.phone = phone;
        if (address != null) u.address = address;
        if (emailOptOut != null) u.emailOptOut = emailOptOut;
        repo.saveUser(u);
        return u;
    }

    public void setActive(int userId, boolean active) {
        User u = repo.user(userId);
        if (u == null || u.isAdmin()) throw new IllegalArgumentException("No such user.");
        u.active = active;
        repo.saveUser(u);
    }

    public User setRole(int userId, Role role) {
        User u = repo.user(userId);
        if (u == null || u.isAdmin()) throw new IllegalArgumentException("No such user.");
        if (role != Role.USER && role != Role.RECYCLER)
            throw new IllegalArgumentException("Users can only be assigned USER or RECYCLER.");
        u.role = role;
        repo.saveUser(u);
        return u;
    }

    public User awardPoints(int userId, int points, NotificationService notes) {
        User u = repo.user(userId);
        if (u == null || u.isAdmin()) throw new IllegalArgumentException("No such user.");
        if (points < 1 || points > 10000) throw new IllegalArgumentException("Points must be between 1 and 10000.");
        u.points += points;
        repo.saveUser(u);
        notes.push(u.id, "The admin awarded you " + points + " bonus reward points!");
        return u;
    }
}

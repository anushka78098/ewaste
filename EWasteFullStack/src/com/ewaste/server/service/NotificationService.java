package com.ewaste.server.service;

import com.ewaste.model.*;
import com.ewaste.server.mail.Mailer;
import com.ewaste.server.repo.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * In-app notifications plus optional email. Emails are collected while a request runs and only handed to the
 * mailer by flushMail() after the request succeeded, so a failed (rolled back) request never sends mail.
 */
public class NotificationService {
    private static final String EMAIL = "^[^@\\s<>\"]+@[^@\\s<>\"]+\\.[^@\\s<>\"]+$";

    private final Repository repo;
    private final Mailer mailer;
    private final List<String[]> pending = new ArrayList<>();   // {to, subject, body}
    private boolean emailMarketAlerts;

    public NotificationService(Repository repo) { this(repo, null); }

    public NotificationService(Repository repo, Mailer mailer) {
        this.repo = repo;
        this.mailer = mailer;
    }

    public void setEmailMarketAlerts(boolean b) { emailMarketAlerts = b; }

    /** In-app notification, plus an email when the user has an address and has not opted out. */
    public void push(int userId, String message) { push(userId, message, true); }

    private void push(int userId, String message, boolean email) {
        Notification n = new Notification();
        n.userId = userId;
        n.message = message;
        repo.addNotification(n);
        if (email) queueMail(userId, message);
    }

    /** Market alerts: in-app for everyone; by email only if mail.marketAlerts=true. */
    public int broadcast(String message) { return broadcast(message, emailMarketAlerts); }

    /** Admin announcement: in-app and email to every active user. */
    public int announce(String message) { return broadcast("[Announcement] " + message, true); }

    private int broadcast(String message, boolean email) {
        int count = 0;
        for (User u : repo.users()) {
            if (u.active && !u.isAdmin()) { push(u.id, message, email); count++; }
        }
        return count;
    }

    // ---- email
    private void queueMail(int userId, String message) {
        if (mailer == null || !mailer.enabled()) return;
        User u = repo.user(userId);
        if (u == null || !u.active || u.emailOptOut || u.email == null || !u.email.matches(EMAIL)) return;
        String subject = message.length() > 70 ? message.substring(0, 67) + "..." : message;
        String body = "Hello " + u.fullName + ",\n\n" + message + "\n\n"
                + "Open the E-Waste Manager app for details.\n\n--\n"
                + "You receive this email because you have an E-Waste Manager account.\n"
                + "Turn it off any time under Profile > \"Email me notifications\".";
        pending.add(new String[]{u.email, "E-Waste Manager: " + subject, body});
    }

    /** Hands queued emails to the mailer. Call only after the request committed. */
    public void flushMail() {
        for (String[] m : pending) mailer.send(m[0], m[1], m[2]);
        pending.clear();
    }

    /** Drops queued emails (request failed and was rolled back). */
    public void discardMail() { pending.clear(); }

    // ---- reading
    public List<Notification> forUser(int userId) {
        List<Notification> out = new ArrayList<>();
        for (Notification n : repo.notifications()) if (n.userId == userId) out.add(n);
        Collections.reverse(out);
        return out;
    }

    public int unread(int userId) {
        int c = 0;
        for (Notification n : repo.notifications()) if (n.userId == userId && !n.read) c++;
        return c;
    }

    public void markAllRead(int userId) {
        for (Notification n : repo.notifications()) {
            if (n.userId == userId && !n.read) { n.read = true; repo.saveNotification(n); }
        }
    }
}

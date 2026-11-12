package com.ewaste.server.mail;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** SMTP settings, read from config/mail.properties (see config/mail.properties.example). */
public class MailConfig {
    public enum Security { NONE, STARTTLS, SSL }

    public boolean enabled;
    public String host = "";
    public int port = 587;
    public Security security = Security.STARTTLS;
    public String username = "";
    public String password = "";
    public String from = "";
    /** Also email users about new market listings and price drops (can be chatty). */
    public boolean marketAlerts;

    private static String pick(Properties p, String key, String env, String dflt) {
        String e = System.getenv(env);
        if (e != null && !e.isEmpty()) return e.trim();
        return p.getProperty(key, dflt).trim();
    }

    /** Reads the optional properties file, then lets EWASTE_MAIL_* environment variables override each setting. */
    public static MailConfig load(Path file) {
        MailConfig c = new MailConfig();
        Properties p = new Properties();
        if (file != null && Files.exists(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                p.load(in);
            } catch (IOException e) {
                System.err.println("Could not read mail settings " + file + ": " + e.getMessage());
            }
        }
        c.enabled = Boolean.parseBoolean(pick(p, "mail.enabled", "EWASTE_MAIL_ENABLED", "false"));
        c.host = pick(p, "mail.host", "EWASTE_MAIL_HOST", "");
        try { c.security = Security.valueOf(pick(p, "mail.security", "EWASTE_MAIL_SECURITY", "STARTTLS").toUpperCase()); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("mail.security must be NONE, STARTTLS or SSL"); }
        int defaultPort = c.security == Security.SSL ? 465 : c.security == Security.STARTTLS ? 587 : 25;
        try { c.port = Integer.parseInt(pick(p, "mail.port", "EWASTE_MAIL_PORT", String.valueOf(defaultPort))); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("mail.port must be a number"); }
        c.username = pick(p, "mail.username", "EWASTE_MAIL_USERNAME", "");
        c.password = System.getenv("EWASTE_MAIL_PASSWORD") != null && !System.getenv("EWASTE_MAIL_PASSWORD").isEmpty()
                ? System.getenv("EWASTE_MAIL_PASSWORD") : p.getProperty("mail.password", "");
        c.from = pick(p, "mail.from", "EWASTE_MAIL_FROM", c.username);
        c.marketAlerts = Boolean.parseBoolean(pick(p, "mail.marketAlerts", "EWASTE_MAIL_MARKET_ALERTS", "false"));
        if (c.enabled && (c.host.isEmpty() || c.from.isEmpty()))
            throw new IllegalArgumentException("Email is enabled but mail.host and mail.from (or mail.username) are missing.");
        return c;
    }
}

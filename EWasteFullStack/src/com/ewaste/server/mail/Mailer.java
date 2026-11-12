package com.ewaste.server.mail;

import java.util.Map;

/** Sends plain-text emails. Implementations must never throw from send(): delivery problems are logged and counted. */
public interface Mailer {
    boolean enabled();

    /** Queues an email for background delivery. */
    void send(String to, String subject, String body);

    /** Delivers immediately and reports problems to the caller (used by the admin "send test email" button). */
    void sendNow(String to, String subject, String body) throws java.io.IOException;

    /** Status for the admin screen: enabled, host, port, security, from, sent, failed, queued, lastError. */
    Map<String, Object> status();

    void close();
}

package com.ewaste.server.mail;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A small SMTP client using only the JDK: plain, STARTTLS or implicit SSL; AUTH PLAIN / LOGIN;
 * UTF-8 subject and body. Mail is delivered by one background thread so web requests never wait for SMTP.
 */
public class SmtpMailer implements Mailer {
    private static final String EMAIL = "^[^@\\s<>\"]+@[^@\\s<>\"]+\\.[^@\\s<>\"]+$";
    private static final int CONNECT_TIMEOUT_MS = 15_000, READ_TIMEOUT_MS = 20_000;

    private final MailConfig cfg;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "mail-sender");
        t.setDaemon(true);
        return t;
    });
    private final AtomicInteger sent = new AtomicInteger(), failed = new AtomicInteger(), queued = new AtomicInteger();
    private volatile String lastError = "";

    public SmtpMailer(MailConfig cfg) { this.cfg = cfg; }

    public MailConfig config() { return cfg; }

    @Override public boolean enabled() { return cfg.enabled; }

    @Override public void send(String to, String subject, String body) {
        if (!cfg.enabled) return;
        queued.incrementAndGet();
        try {
            worker.submit(() -> {
                try {
                    deliver(to, subject, body);
                    sent.incrementAndGet();
                } catch (Exception e) {
                    failed.incrementAndGet();
                    lastError = e.getMessage() == null ? e.toString() : e.getMessage();
                    System.err.println("Email to " + to + " failed: " + lastError);
                } finally {
                    queued.decrementAndGet();
                }
            });
        } catch (RuntimeException e) {          // executor already shut down
            queued.decrementAndGet();
        }
    }

    @Override public void sendNow(String to, String subject, String body) throws IOException {
        if (!cfg.enabled) throw new IOException("Email is not enabled in config/mail.properties.");
        try {
            deliver(to, subject, body);
            sent.incrementAndGet();
        } catch (IOException e) {
            failed.incrementAndGet();
            lastError = e.getMessage();
            throw e;
        }
    }

    @Override public Map<String, Object> status() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", cfg.enabled);
        m.put("host", cfg.host);
        m.put("port", cfg.port);
        m.put("security", cfg.security.name());
        m.put("from", cfg.from);
        m.put("marketAlerts", cfg.marketAlerts);
        m.put("sent", sent.get());
        m.put("failed", failed.get());
        m.put("queued", queued.get());
        m.put("lastError", lastError);
        return m;
    }

    @Override public void close() {
        worker.shutdown();
        try { worker.awaitTermination(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    // ------------------------------------------------------------------ SMTP conversation
    private static final class Conn {
        Socket socket;
        BufferedReader in;
        BufferedWriter out;

        Conn(Socket s) throws IOException { attach(s); }

        void attach(Socket s) throws IOException {
            socket = s;
            in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.ISO_8859_1));
            out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.ISO_8859_1));
        }

        /** Reads one (possibly multi-line) reply and checks its code. Returns the full text. */
        String reply(int expect) throws IOException {
            StringBuilder all = new StringBuilder();
            while (true) {
                String line = in.readLine();
                if (line == null) throw new IOException("SMTP server closed the connection unexpectedly");
                all.append(line).append('\n');
                if (line.length() < 3) throw new IOException("Bad SMTP reply: " + line);
                if (line.length() == 3 || line.charAt(3) == ' ') {
                    int code;
                    try { code = Integer.parseInt(line.substring(0, 3)); }
                    catch (NumberFormatException e) { throw new IOException("Bad SMTP reply: " + line); }
                    if (code != expect) throw new IOException("SMTP server said: " + all.toString().trim());
                    return all.toString();
                }
            }
        }

        String cmd(String line, int expect) throws IOException {
            out.write(line);
            out.write("\r\n");
            out.flush();
            return reply(expect);
        }
    }

    private void deliver(String to, String subject, String body) throws IOException {
        if (to == null || !to.matches(EMAIL)) throw new IOException("Invalid recipient address: " + to);
        if (!cfg.from.matches(EMAIL)) throw new IOException("Invalid mail.from address: " + cfg.from);
        String message = buildMessage(to, subject, body);

        Socket plain = new Socket();
        Socket s = plain;
        try {
            plain.connect(new InetSocketAddress(cfg.host, cfg.port), CONNECT_TIMEOUT_MS);
            plain.setSoTimeout(READ_TIMEOUT_MS);
            if (cfg.security == MailConfig.Security.SSL) s = tls(plain);
            Conn c = new Conn(s);
            c.reply(220);
            String caps = c.cmd("EHLO ewaste.local", 250);

            if (cfg.security == MailConfig.Security.STARTTLS) {
                if (!caps.toUpperCase().contains("STARTTLS")) throw new IOException("Server does not offer STARTTLS; refusing to send credentials in clear text.");
                c.cmd("STARTTLS", 220);
                s = tls(plain);
                c.attach(s);
                caps = c.cmd("EHLO ewaste.local", 250);
            }
            if (!cfg.username.isEmpty()) authenticate(c, caps);

            c.cmd("MAIL FROM:<" + cfg.from + ">", 250);
            c.cmd("RCPT TO:<" + to + ">", 250);
            c.cmd("DATA", 354);
            c.out.write(message);
            c.out.write("\r\n.\r\n");
            c.out.flush();
            c.reply(250);
            try { c.cmd("QUIT", 221); } catch (IOException ignored) { }
        } finally {
            try { s.close(); } catch (IOException ignored) { }
            try { plain.close(); } catch (IOException ignored) { }
        }
    }

    private SSLSocket tls(Socket plain) throws IOException {
        SSLSocket s = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(plain, cfg.host, cfg.port, true);
        javax.net.ssl.SSLParameters p = s.getSSLParameters();
        p.setEndpointIdentificationAlgorithm("HTTPS");           // verify the server certificate matches the host name
        s.setSSLParameters(p);
        s.startHandshake();
        return s;
    }

    private void authenticate(Conn c, String caps) throws IOException {
        String up = caps.toUpperCase();
        Base64.Encoder b64 = Base64.getEncoder();
        if (up.contains("AUTH") && up.contains("PLAIN")) {
            String token = b64.encodeToString(("\0" + cfg.username + "\0" + cfg.password).getBytes(StandardCharsets.UTF_8));
            c.cmd("AUTH PLAIN " + token, 235);
        } else if (up.contains("AUTH") && up.contains("LOGIN")) {
            c.cmd("AUTH LOGIN", 334);
            c.cmd(b64.encodeToString(cfg.username.getBytes(StandardCharsets.UTF_8)), 334);
            c.cmd(b64.encodeToString(cfg.password.getBytes(StandardCharsets.UTF_8)), 235);
        } else {
            throw new IOException("SMTP server does not offer AUTH PLAIN or LOGIN but mail.username is set.");
        }
    }

    // ------------------------------------------------------------------ message formatting
    static String buildMessage(String to, String subject, String body, String from) {
        String cleanSubject = subject == null ? "" : subject.replaceAll("[\\r\\n]+", " ").trim();
        StringBuilder sb = new StringBuilder();
        sb.append("From: E-Waste Manager <").append(from).append(">\r\n");
        sb.append("To: <").append(to).append(">\r\n");
        sb.append("Subject: ").append(encodeHeader(cleanSubject)).append("\r\n");
        sb.append("Date: ").append(ZonedDateTime.now().format(DateTimeFormatter.RFC_1123_DATE_TIME)).append("\r\n");
        sb.append("Message-ID: <").append(UUID.randomUUID()).append('@').append(from.substring(from.indexOf('@') + 1)).append(">\r\n");
        sb.append("MIME-Version: 1.0\r\n");
        sb.append("Content-Type: text/plain; charset=UTF-8\r\n");
        sb.append("Content-Transfer-Encoding: base64\r\n\r\n");
        String normalised = (body == null ? "" : body).replace("\r\n", "\n").replace("\n", "\r\n");
        sb.append(Base64.getMimeEncoder(76, new byte[]{'\r', '\n'}).encodeToString(normalised.getBytes(StandardCharsets.UTF_8)));
        return sb.toString();
    }

    private String buildMessage(String to, String subject, String body) { return buildMessage(to, subject, body, cfg.from); }

    private static String encodeHeader(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) > 126 || s.charAt(i) < 32) {
                return "=?UTF-8?B?" + Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8)) + "?=";
            }
        }
        return s;
    }
}

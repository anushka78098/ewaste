package com.ewaste;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/** Minimal SMTP server for tests: speaks EHLO / AUTH PLAIN|LOGIN / MAIL / RCPT / DATA / QUIT and records messages. */
public class FakeSmtpServer implements Closeable {
    public static final class Mail {
        public final String from, to, raw, authUser, authPass;
        Mail(String from, String to, String raw, String u, String p) { this.from = from; this.to = to; this.raw = raw; this.authUser = u; this.authPass = p; }

        public String header(String name) {
            for (String line : raw.split("\r\n")) {
                if (line.isEmpty()) break;
                if (line.toLowerCase().startsWith(name.toLowerCase() + ":")) return line.substring(name.length() + 1).trim();
            }
            return null;
        }

        public String body() {
            int i = raw.indexOf("\r\n\r\n");
            String b64 = raw.substring(i + 4).replaceAll("\\s+", "");
            return new String(Base64.getDecoder().decode(b64), StandardCharsets.UTF_8);
        }
    }

    private final ServerSocket server;
    public final List<Mail> mails = new CopyOnWriteArrayList<>();
    public volatile boolean rejectAuth;
    public volatile boolean useLoginAuth;      // advertise only AUTH LOGIN
    private volatile boolean closed;

    public FakeSmtpServer() throws IOException {
        server = new ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"));
        Thread t = new Thread(this::acceptLoop, "fake-smtp");
        t.setDaemon(true);
        t.start();
    }

    public int port() { return server.getLocalPort(); }

    private void acceptLoop() {
        while (!closed) {
            try {
                Socket s = server.accept();
                Thread t = new Thread(() -> serve(s));
                t.setDaemon(true);
                t.start();
            } catch (IOException e) { return; }
        }
    }

    private void serve(Socket s) {
        try (Socket sock = s;
             BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream(), StandardCharsets.ISO_8859_1));
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(sock.getOutputStream(), StandardCharsets.ISO_8859_1))) {
            String from = null, to = null, user = null, pass = null;
            send(out, "220 fake.smtp ready");
            String line;
            while ((line = in.readLine()) != null) {
                String u = line.toUpperCase();
                if (u.startsWith("EHLO")) {
                    out.write("250-fake.smtp\r\n250-SIZE 1000000\r\n250 AUTH " + (useLoginAuth ? "LOGIN" : "PLAIN LOGIN") + "\r\n");
                    out.flush();
                } else if (u.startsWith("AUTH PLAIN")) {
                    String tok = line.length() > 11 ? line.substring(11).trim() : new String(readB64Line(in, out, "334 "));
                    String[] parts = new String(Base64.getDecoder().decode(tok), StandardCharsets.UTF_8).split("\0", -1);
                    user = parts[1]; pass = parts[2];
                    send(out, rejectAuth ? "535 5.7.8 bad credentials" : "235 ok");
                } else if (u.startsWith("AUTH LOGIN")) {
                    send(out, "334 VXNlcm5hbWU6");
                    user = new String(Base64.getDecoder().decode(in.readLine().trim()), StandardCharsets.UTF_8);
                    send(out, "334 UGFzc3dvcmQ6");
                    pass = new String(Base64.getDecoder().decode(in.readLine().trim()), StandardCharsets.UTF_8);
                    send(out, rejectAuth ? "535 5.7.8 bad credentials" : "235 ok");
                } else if (u.startsWith("MAIL FROM:")) { from = line.substring(10).trim().replaceAll("[<>]", ""); send(out, "250 ok");
                } else if (u.startsWith("RCPT TO:")) { to = line.substring(8).trim().replaceAll("[<>]", ""); send(out, "250 ok");
                } else if (u.equals("DATA")) {
                    send(out, "354 end with <CRLF>.<CRLF>");
                    StringBuilder sb = new StringBuilder();
                    while ((line = in.readLine()) != null && !line.equals(".")) {
                        sb.append(line.startsWith("..") ? line.substring(1) : line).append("\r\n");
                    }
                    mails.add(new Mail(from, to, sb.toString().replaceAll("\r\n$", ""), user, pass));
                    send(out, "250 queued");
                } else if (u.equals("QUIT")) { send(out, "221 bye"); return;
                } else send(out, "500 unknown command");
            }
        } catch (IOException ignored) { }
    }

    private static byte[] readB64Line(BufferedReader in, BufferedWriter out, String prompt) throws IOException {
        send(out, prompt);
        return in.readLine().trim().getBytes(StandardCharsets.ISO_8859_1);
    }

    private static void send(BufferedWriter out, String s) throws IOException { out.write(s + "\r\n"); out.flush(); }

    /** Waits until at least n mails have arrived (background delivery is asynchronous). */
    public boolean await(int n, long timeoutMs) {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            if (mails.size() >= n) return true;
            try { Thread.sleep(25); } catch (InterruptedException e) { return false; }
        }
        return mails.size() >= n;
    }

    @Override public void close() throws IOException { closed = true; server.close(); }
}

package com.ewaste.client;

import com.ewaste.common.Json;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Thin HTTP/JSON client for the E-Waste REST API. Used by the Swing UI (and the integration test). */
public class ApiClient {
    public static class ApiException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        public final int status;
        public ApiException(int status, String message) { super(message); this.status = status; }
    }

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private String baseUrl;
    private String token;

    public ApiClient(String baseUrl) { setBaseUrl(baseUrl); }

    public void setBaseUrl(String url) {
        String u = url.trim();
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "http://" + u;
        this.baseUrl = u;
    }

    public String baseUrl() { return baseUrl; }
    public boolean loggedIn() { return token != null; }

    // ---- convenience wrappers ----------------------------------------------------------------
    public Map<String, Object> login(String username, String password) {
        Map<String, Object> r = obj(post("/api/auth/login", map("username", username, "password", password)));
        token = (String) r.get("token");
        return obj(r.get("user"));
    }

    public void logout() {
        try { if (token != null) post("/api/auth/logout", null); } catch (RuntimeException ignored) { }
        token = null;
    }

    public static String enc(String s) { return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8); }

    public static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> obj(Object o) { return (Map<String, Object>) o; }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> list(Object o) { return (List<Map<String, Object>>) o; }

    // ---- raw verbs -----------------------------------------------------------------------------
    public Object get(String path) { return parse(send("GET", path, null)); }
    public Object post(String path, Object body) { return parse(send("POST", path, body)); }
    public Object put(String path, Object body) { return parse(send("PUT", path, body)); }
    public Object delete(String path) { return parse(send("DELETE", path, null)); }
    public String getText(String path) { return send("GET", path, null); }
    public byte[] getBytes(String path) { return sendBytes(path); }

    public static Map<String, Object> filePayload(Path path) throws IOException {
        if (Files.size(path) > 4_000_000) throw new IOException("Files must be 4 MB or smaller.");
        byte[] bytes = Files.readAllBytes(path);
        String name = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        String type = name.endsWith(".png") ? "image/png" : name.endsWith(".pdf") ? "application/pdf"
                : name.endsWith(".jpg") || name.endsWith(".jpeg") ? "image/jpeg" : Files.probeContentType(path);
        if (type == null) type = "application/octet-stream";
        return map("attachmentName", path.getFileName().toString(), "attachmentType", type,
                "attachmentBase64", java.util.Base64.getEncoder().encodeToString(bytes));
    }

    private byte[] sendBytes(String path) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(Duration.ofSeconds(20));
        if (token != null) b.header("Authorization", "Bearer " + token);
        b.GET();
        try {
            HttpResponse<byte[]> r = http.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
            if (r.statusCode() >= 200 && r.statusCode() < 300) return r.body();
            String msg = "Server error (" + r.statusCode() + ")";
            try {
                Object err = Json.parse(new String(r.body(), StandardCharsets.UTF_8));
                if (err instanceof Map && ((Map<?, ?>) err).get("error") != null) msg = String.valueOf(((Map<?, ?>) err).get("error"));
            } catch (RuntimeException ignored) { }
            throw new ApiException(r.statusCode(), msg);
        } catch (IOException e) {
            throw new ApiException(0, "Cannot reach the server at " + baseUrl + ". Is it running?");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(0, "Request interrupted.");
        }
    }

    private Object parse(String body) { return body == null || body.isEmpty() ? null : Json.parse(body); }

    private String send(String method, String path, Object body) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(Duration.ofSeconds(20));
        if (token != null) b.header("Authorization", "Bearer " + token);
        if (body != null) {
            b.header("Content-Type", "application/json");
            b.method(method, HttpRequest.BodyPublishers.ofString(Json.write(body)));
        } else if (method.equals("GET") || method.equals("DELETE")) {
            b.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            b.method(method, HttpRequest.BodyPublishers.ofString("{}"));
        }
        try {
            HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (r.statusCode() >= 200 && r.statusCode() < 300) return r.body();
            String msg = "Server error (" + r.statusCode() + ")";
            try {
                Object err = Json.parse(r.body());
                if (err instanceof Map && ((Map<?, ?>) err).get("error") != null) msg = String.valueOf(((Map<?, ?>) err).get("error"));
            } catch (RuntimeException ignored) { }
            throw new ApiException(r.statusCode(), msg);
        } catch (IOException e) {
            throw new ApiException(0, "Cannot reach the server at " + baseUrl + ". Is it running?");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(0, "Request interrupted.");
        }
    }
}

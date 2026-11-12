package com.ewaste.server;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Exit code 0 if the local server answers /api/health. Used by the Docker HEALTHCHECK. Usage: HealthCheck [url] */
public class HealthCheck {
    public static void main(String[] args) {
        String port = System.getenv("EWASTE_PORT") == null ? "8080" : System.getenv("EWASTE_PORT");
        String url = args.length > 0 ? args[0] : "http://127.0.0.1:" + port + "/api/health";
        try {
            HttpResponse<String> r = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()
                    .send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(4)).build(), HttpResponse.BodyHandlers.ofString());
            System.exit(r.statusCode() == 200 && r.body().contains("\"ok\"") ? 0 : 1);
        } catch (Exception e) {
            System.exit(1);
        }
    }
}

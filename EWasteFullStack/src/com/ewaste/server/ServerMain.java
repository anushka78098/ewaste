package com.ewaste.server;

import com.ewaste.server.mail.MailConfig;
import com.ewaste.server.mail.Mailer;
import com.ewaste.server.mail.SmtpMailer;
import com.ewaste.server.repo.MemoryRepository;
import com.ewaste.server.repo.Repository;
import com.ewaste.server.repo.SqliteRepository;
import com.ewaste.server.service.AuthService;
import com.ewaste.server.service.DecisionPolicy;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Executors;

/**
 * Starts the REST backend.
 * Options: --port=8080  --host=127.0.0.1  --data=data  --store=auto|sqlite|file  --mail=config/mail.properties
 * Each option can also be set with an environment variable: EWASTE_PORT, EWASTE_HOST, EWASTE_DATA, EWASTE_STORE, EWASTE_MAIL_FILE
 * (command-line options win). Mail settings: EWASTE_MAIL_ENABLED/HOST/PORT/SECURITY/USERNAME/PASSWORD/FROM.
 * Decision thresholds: EWASTE_DECISION_FILE. First admin password: EWASTE_ADMIN_PASSWORD.
 */
public class ServerMain {
    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(env("EWASTE_PORT", "8080"));
        String host = env("EWASTE_HOST", "127.0.0.1");
        String dataDir = env("EWASTE_DATA", "data");
        String store = env("EWASTE_STORE", "auto");
        String mailFile = env("EWASTE_MAIL_FILE", "config/mail.properties");
        String decisionFile = env("EWASTE_DECISION_FILE", "config/decision.properties");
        for (String a : args) {
            if (a.startsWith("--port=")) port = Integer.parseInt(a.substring(7));
            else if (a.startsWith("--host=")) host = a.substring(7);
            else if (a.startsWith("--data=")) dataDir = a.substring(7);
            else if (a.startsWith("--store=")) store = a.substring(8);
            else if (a.startsWith("--mail=")) mailFile = a.substring(7);
            else { System.err.println("Unknown option " + a); System.exit(2); }
        }
        Files.createDirectories(Paths.get(dataDir));

        Repository repo = null;
        boolean wantSqlite = store.equals("sqlite") || (store.equals("auto") && SqliteRepository.driverAvailable());
        if (wantSqlite) {
            try {
                repo = new SqliteRepository(Paths.get(dataDir, "ewaste.db").toString());
            } catch (RuntimeException | LinkageError e) {
                if (store.equals("sqlite")) throw e;
                System.err.println("WARNING: SQLite could not be opened (" + e.getMessage() + "). Falling back to the file-backed store.");
            }
        } else if (store.equals("auto")) {
            System.out.println("NOTE: SQLite driver not found in lib/. Using the file-backed store. Run scripts/setup-sqlite to enable SQLite.");
        }
        if (repo == null) repo = new MemoryRepository(Paths.get(dataDir, "ewaste.snapshot"));
        new AuthService(repo);
        final Repository seedRepo = repo;
        int demoListings = seedRepo.atomic(() -> DemoData.seedMarketplaceIfEmpty(seedRepo));
        if (demoListings > 0) System.out.println("Added " + demoListings + " demo marketplace listings.");
        final Repository store0 = repo;

        MailConfig mailCfg = MailConfig.load(Paths.get(mailFile));
        final Mailer mailer = new SmtpMailer(mailCfg);
        DecisionPolicy decisionPolicy = DecisionPolicy.load(Paths.get(decisionFile));
        HttpServer server = start(store0, host, port, mailer, decisionPolicy);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> { server.stop(0); mailer.close(); store0.close(); }));
        System.out.println("E-Waste server running at http://" + host + ":" + port + "/api  (storage: " + store0.describe() + ")");
        System.out.println(mailCfg.enabled ? "Email notifications: ON via " + mailCfg.host + ":" + mailCfg.port + " (" + mailCfg.security + "), from " + mailCfg.from
                : "Email notifications: off (copy config/mail.properties.example to config/mail.properties to enable)");
        if (com.ewaste.server.service.AuthService.usesDefaultPassword(store0))
            System.out.println("WARNING: the admin account still uses the default password (admin / admin123). Change it in the app (Profile tab) or set EWASTE_ADMIN_PASSWORD before the first start.");
        else
            System.out.println("Admin account: admin. Press Ctrl+C to stop.");
    }

    private static String env(String name, String dflt) {
        String v = System.getenv(name);
        return v == null || v.trim().isEmpty() ? dflt : v.trim();
    }

    public static HttpServer start(Repository repo, String host, int port) throws IOException { return start(repo, host, port, null); }

    public static HttpServer start(Repository repo, String host, int port, Mailer mailer) throws IOException {
        return start(repo, host, port, mailer, DecisionPolicy.defaults());
    }

    public static HttpServer start(Repository repo, String host, int port, Mailer mailer, DecisionPolicy policy) throws IOException {
        Api api = new Api(repo, mailer, policy);
        HttpServer server = HttpServer.create(new InetSocketAddress(host, port), 0);
        server.createContext("/", api);
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();
        return server;
    }
}

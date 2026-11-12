package com.ewaste;

import com.ewaste.client.ApiClient;
import com.ewaste.client.ApiClient.ApiException;
import com.ewaste.server.ServerMain;
import com.ewaste.server.mail.MailConfig;
import com.ewaste.server.mail.SmtpMailer;
import com.ewaste.server.repo.MemoryRepository;
import com.sun.net.httpserver.HttpServer;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.Callable;

import static com.ewaste.client.ApiClient.map;
import static com.ewaste.client.ApiClient.obj;

/** Email notifications, tested end to end against an in-process fake SMTP server. */
public class MailTest {
    static int passed, failed;

    static void check(String name, boolean ok) {
        if (ok) { passed++; System.out.println("  PASS " + name); } else { failed++; System.out.println("  FAIL " + name); }
    }

    static int statusOf(Callable<?> c) {
        try { c.call(); return 200; } catch (ApiException e) { return e.status; } catch (Exception e) { return -1; }
    }

    static MailConfig config(int port, boolean auth) {
        MailConfig c = new MailConfig();
        c.enabled = true; c.host = "127.0.0.1"; c.port = port; c.security = MailConfig.Security.NONE;
        c.from = "noreply@ewaste.test";
        if (auth) { c.username = "mailuser"; c.password = "p@ss word"; }
        return c;
    }

    public static void main(String[] args) throws Exception {
        FakeSmtpServer smtp = new FakeSmtpServer();
        SmtpMailer mailer = new SmtpMailer(config(smtp.port(), true));
        HttpServer server = ServerMain.start(new MemoryRepository(null), "127.0.0.1", 0, mailer);
        String url = "http://127.0.0.1:" + server.getAddress().getPort();
        try { run(url, smtp, mailer); }
        finally { server.stop(0); mailer.close(); smtp.close(); }
        System.out.println("\n" + passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }

    static void run(String url, FakeSmtpServer smtp, SmtpMailer mailer) throws Exception {
        ApiClient anon = new ApiClient(url), admin = new ApiClient(url), alice = new ApiClient(url), bob = new ApiClient(url);
        admin.login("admin", "admin123");

        System.out.println("Registration and item emails");
        anon.post("/api/auth/register", map("username", "alice", "password", "secret1", "fullName", "Alice Ünal", "email", "alice@example.com"));
        check("welcome email arrives", smtp.await(1, 5000));
        FakeSmtpServer.Mail m = smtp.mails.get(0);
        check("sent to the user's address from the configured sender", "alice@example.com".equals(m.to) && "noreply@ewaste.test".equals(m.from));
        check("SMTP login used the configured credentials", "mailuser".equals(m.authUser) && "p@ss word".equals(m.authPass));
        check("subject and greeting", m.header("Subject").startsWith("E-Waste Manager: Welcome") && m.body().startsWith("Hello Alice Ünal,"));
        check("body is UTF-8 base64 text/plain", m.header("Content-Type").contains("UTF-8") && "base64".equals(m.header("Content-Transfer-Encoding")));
        check("Date and Message-ID headers present", m.header("Date") != null && m.header("Message-ID").endsWith("@ewaste.test>"));

        alice.login("alice", "secret1");
        Map<String, Object> it = obj(alice.post("/api/items", map("name", "Pixel 4", "category", "MOBILE", "ageYears", 2, "weightKg", 0.2, "condition", "WORKING")));
        int id = ((Number) it.get("id")).intValue();
        check("item-received email", smtp.await(2, 5000) && smtp.mails.get(1).body().contains("received and is awaiting admin review"));
        admin.post("/api/admin/items/" + id + "/approve", map("decision", "REUSE", "pickupDate", LocalDate.now().plusDays(1).toString(), "note", "bring charger"));
        check("approval email mentions plan and note", smtp.await(3, 5000) && smtp.mails.get(2).body().contains("approved for REUSE") && smtp.mails.get(2).body().contains("bring charger"));

        System.out.println("Failed requests send nothing");
        int before = smtp.mails.size();
        check("bad request -> 400", statusOf(() -> admin.post("/api/admin/items/" + id + "/approve", map("decision", "REUSE", "pickupDate", "2000-01-01"))) == 400);
        Thread.sleep(400);
        check("no email for the failed request", smtp.mails.size() == before);

        System.out.println("Opt-out, announcements, market alerts");
        anon.post("/api/auth/register", map("username", "bob", "password", "secret2", "fullName", "Bob", "email", "bob@example.com"));
        bob.login("bob", "secret2");
        smtp.await(before + 1, 5000);
        bob.put("/api/me", map("emailOptOut", true));
        check("preference stored", Boolean.TRUE.equals(obj(bob.get("/api/me")).get("emailOptOut")));
        int n = smtp.mails.size();
        admin.post("/api/admin/announce", map("message", "Drive on Saturday"));
        check("announcement emailed to opted-in user only", smtp.await(n + 1, 5000));
        Thread.sleep(400);
        check("exactly one announcement email (bob opted out)", smtp.mails.size() == n + 1 && "alice@example.com".equals(smtp.mails.get(n).to)
                && smtp.mails.get(n).header("Subject").contains("[Announcement]"));
        check("opted-out user still gets the in-app notification", bob.get("/api/notifications").toString().contains("Drive on Saturday"));
        n = smtp.mails.size();
        admin.post("/api/admin/items/" + id + "/collect", null);
        smtp.await(n + 1, 5000);
        n = smtp.mails.size();
        admin.post("/api/admin/listings", map("itemId", id, "price", 90));
        Thread.sleep(500);
        check("market alerts are in-app only by default", smtp.mails.size() == n && alice.get("/api/notifications").toString().contains("New in the market"));
        bob.put("/api/me", map("emailOptOut", false));

        System.out.println("Admin mail endpoints");
        Map<String, Object> st = obj(admin.get("/api/admin/mail"));
        check("status reports enabled and counters", Boolean.TRUE.equals(st.get("enabled")) && ((Number) st.get("sent")).intValue() >= 5);
        check("non-admin cannot read mail status", statusOf(() -> alice.get("/api/admin/mail")) == 403);
        n = smtp.mails.size();
        admin.post("/api/admin/mail/test", map("to", "someone@example.com"));
        check("test email delivered", smtp.mails.size() == n + 1 && "someone@example.com".equals(smtp.mails.get(n).to));
        check("invalid test address -> 400", statusOf(() -> admin.post("/api/admin/mail/test", map("to", "not-an-email"))) == 400);
        check("header injection stripped from subject", checkInjection(smtp, mailer));

        System.out.println("Resilience");
        smtp.rejectAuth = true;
        check("SMTP auth rejection reported to admin", statusOf(() -> admin.post("/api/admin/mail/test", map("to", "x@example.com"))) == 400);
        smtp.rejectAuth = false;
        MailConfig dead = config(1, true);
        SmtpMailer deadMailer = new SmtpMailer(dead);
        HttpServer server2 = ServerMain.start(new MemoryRepository(null), "127.0.0.1", 0, deadMailer);
        String url2 = "http://127.0.0.1:" + server2.getAddress().getPort();
        ApiClient anon2 = new ApiClient(url2), admin2 = new ApiClient(url2);
        long t0 = System.currentTimeMillis();
        anon2.post("/api/auth/register", map("username", "carol", "password", "secret3", "fullName", "Carol", "email", "carol@example.com"));
        check("API call succeeds quickly even if the mail server is down", System.currentTimeMillis() - t0 < 3000);
        admin2.login("admin", "admin123");
        Thread.sleep(1500);
        Map<String, Object> st2 = obj(admin2.get("/api/admin/mail"));
        check("failure counted with a message", ((Number) st2.get("failed")).intValue() >= 1 && !String.valueOf(st2.get("lastError")).isEmpty());
        server2.stop(0);
        deadMailer.close();

        System.out.println("AUTH LOGIN fallback");
        smtp.useLoginAuth = true;
        n = smtp.mails.size();
        admin.post("/api/admin/mail/test", map("to", "login@example.com"));
        check("works when the server only offers AUTH LOGIN", smtp.mails.size() == n + 1 && "mailuser".equals(smtp.mails.get(n).authUser));

        System.out.println("Disabled mode");
        HttpServer server3 = ServerMain.start(new MemoryRepository(null), "127.0.0.1", 0, null);
        ApiClient admin3 = new ApiClient("http://127.0.0.1:" + server3.getAddress().getPort());
        admin3.login("admin", "admin123");
        check("no mailer -> status says disabled", Boolean.FALSE.equals(obj(admin3.get("/api/admin/mail")).get("enabled")));
        check("no mailer -> test endpoint refuses", statusOf(() -> admin3.post("/api/admin/mail/test", map("to", "a@b.co"))) == 400);
        server3.stop(0);
    }

    static boolean checkInjection(FakeSmtpServer smtp, SmtpMailer mailer) throws Exception {
        int n = smtp.mails.size();
        mailer.sendNow("inject@example.com", "Hello\r\nBcc: evil@example.com", "body");
        FakeSmtpServer.Mail m = smtp.mails.get(n);
        return m.header("Bcc") == null && m.header("Subject").contains("Hello") && m.raw.indexOf("evil@example.com") == m.raw.lastIndexOf("evil@example.com")
                && m.raw.split("\r\n\r\n")[0].lines().noneMatch(l -> l.startsWith("Bcc:"));
    }
}

package com.ewaste;

import com.ewaste.client.ApiClient;
import com.ewaste.client.ApiClient.ApiException;
import com.ewaste.model.User;
import com.ewaste.server.ServerMain;
import com.ewaste.server.DemoData;
import com.ewaste.server.repo.MemoryRepository;
import com.ewaste.server.repo.Repository;
import com.ewaste.server.service.AuthService;
import com.sun.net.httpserver.HttpServer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import static com.ewaste.client.ApiClient.*;

/** End-to-end test: boots the real HTTP server and drives it through the same client the Swing app uses. */
public class ApiTest {
    static int passed, failed;

    static void check(String name, boolean ok) {
        if (ok) { passed++; System.out.println("  PASS " + name); } else { failed++; System.out.println("  FAIL " + name); }
    }

    static int statusOf(Callable<?> c) {
        try { c.call(); return 200; } catch (ApiException e) { return e.status; } catch (Exception e) { return -1; }
    }

    static double num(Object o) { return ((Number) o).doubleValue(); }

    public static void main(String[] args) throws Exception {
        Repository demo = new MemoryRepository(null);
        new AuthService(demo);
        check("fresh installation seeds 100 marketplace listings", DemoData.seedMarketplaceIfEmpty(demo) == 100
                && demo.listings().size() == 100 && DemoData.seedMarketplaceIfEmpty(demo) == 0);
        demo.close();
        Path blockedParent = Files.createTempFile("ewaste-snapshot-blocker", ".tmp");
        try {
            MemoryRepository fileRepo = new MemoryRepository(blockedParent.resolve("snapshot"));
            User user = new User();
            user.username = "snapshot-test";
            boolean writeFailureReported = false;
            try { fileRepo.atomic(() -> fileRepo.addUser(user)); }
            catch (IllegalStateException e) {
                writeFailureReported = e.getMessage() != null && e.getMessage().contains("Could not save snapshot file");
            }
            check("snapshot write failures are surfaced", writeFailureReported);
        } finally {
            Files.deleteIfExists(blockedParent);
        }
        Repository repo;
        if (args.length > 0 && args[0].equals("sqlite")) {
            repo = new com.ewaste.server.repo.SqliteRepository(Files.createTempFile("ewaste", ".db").toString());
        } else if (args.length > 0 && args[0].equals("postgres")) {
            String jdbcUrl = System.getenv("EWASTE_TEST_POSTGRES_API_URL");
            if (jdbcUrl == null || jdbcUrl.trim().isEmpty())
                throw new IllegalArgumentException("EWASTE_TEST_POSTGRES_API_URL must point to a disposable PostgreSQL test database.");
            repo = new com.ewaste.server.repo.PostgresRepository(jdbcUrl,
                    System.getenv("EWASTE_TEST_POSTGRES_USER"), System.getenv("EWASTE_TEST_POSTGRES_PASSWORD"));
        } else {
            repo = new MemoryRepository(null);
        }
        System.out.println("Storage: " + repo.describe());
        HttpServer server = ServerMain.start(repo, "127.0.0.1", 0);
        String url = "http://127.0.0.1:" + server.getAddress().getPort();
        try {
            run(url);
        } finally {
            server.stop(0);
            repo.close();
        }
        System.out.println("\n" + passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }

    static void run(String url) {
        ApiClient anon = new ApiClient(url);
        ApiClient admin = new ApiClient(url), alice = new ApiClient(url), bob = new ApiClient(url);

        System.out.println("Meta & auth");
        check("health", "ok".equals(obj(anon.get("/api/health")).get("status")));
        check("meta lists categories", list(obj(anon.get("/api/meta")).get("categories")).size() == 10);
        check("unauthenticated -> 401", statusOf(() -> anon.get("/api/items/mine")) == 401);
        check("unknown route -> 404", statusOf(() -> anon.get("/api/nope")) == 404);
        check("wrong method -> 405", statusOf(() -> anon.delete("/api/health")) == 405);
        check("bad login -> 400", statusOf(() -> anon.login("admin", "wrong")) == 400);
        check("admin login", "ADMIN".equals(admin.login("admin", "admin123").get("role")));
        check("decision report requires authentication", statusOf(() -> anon.post("/api/decision/report",
                map("category", "LAPTOP", "ageYears", 2, "condition", "WORKING"))) == 401);
        anon.post("/api/auth/register", map("username", "mallory", "password", "secret0", "fullName", "Mallory", "email", "m@x.com"));
        for (int k = 0; k < 5; k++) statusOf(() -> new ApiClient(url).login("mallory", "wrong-guess"));
        check("5 wrong passwords lock the account (429), even for the right password", statusOf(() -> new ApiClient(url).login("mallory", "secret0")) == 429);
        check("the lock is per user: admin is unaffected", admin.loggedIn());
        check("weak password rejected", statusOf(() -> anon.post("/api/auth/register",
                map("username", "x1", "password", "123", "fullName", "X", "email", "x@x.com"))) == 400);
        anon.post("/api/auth/register", map("username", "alice", "password", "secret1", "fullName", "Alice A", "email", "a@x.com", "phone", "1", "address", "home"));
        anon.post("/api/auth/register", map("username", "bob", "password", "secret2", "fullName", "Bob B", "email", "b@x.com"));
        check("duplicate user rejected", statusOf(() -> anon.post("/api/auth/register",
                map("username", "ALICE", "password", "secret1", "fullName", "A", "email", "a@x.com"))) == 400);
        alice.login("alice", "secret1");
        bob.login("bob", "secret2");
        Map<String, Object> decisionReport = obj(alice.post("/api/decision/report", map("category", "LAPTOP",
                "ageYears", 2, "condition", "MINOR_FAULT", "estimatedResaleValue", 200,
                "repairCost", 25, "recyclingValue", 5, "hazardous", false, "weightKg", 2)));
        check("decision report endpoint returns structured recommendation and completeness",
                "REPAIR_AND_REUSE".equals(decisionReport.get("recommendedAction"))
                        && decisionReport.containsKey("financialComparison")
                        && decisionReport.containsKey("environmentalImpactEstimateKgCo2")
                        && ((Number) decisionReport.get("inputCompletenessPercent")).intValue() == 100);
        check("user cannot reach admin api -> 403", statusOf(() -> alice.get("/api/admin/stats")) == 403);
        check("password not leaked", !alice.getText("/api/me").toLowerCase().contains("hash"));

        System.out.println("Admin support inbox");
        int aliceId = ((Number) obj(alice.get("/api/me")).get("id")).intValue();
        int supportBobId = ((Number) obj(bob.get("/api/me")).get("id")).intValue();
        int adminUnreadBeforeSupport = ((Number) obj(admin.get("/api/me")).get("unread")).intValue();
        check("support inbox is private to admins", statusOf(() -> alice.get("/api/admin/support")) == 403);
        check("user can start a private support conversation",
                "I need help with my item.".equals(obj(alice.post("/api/me/support",
                        map("text", "  I need help with my item.  "))).get("text")));
        check("new support messages notify admins",
                ((Number) obj(admin.get("/api/me")).get("unread")).intValue() == adminUnreadBeforeSupport + 1);
        check("other users cannot see the conversation", list(bob.get("/api/me/support")).isEmpty());
        List<Map<String, Object>> supportInbox = list(admin.get("/api/admin/support"));
        Map<String, Object> aliceSupport = null;
        for (Map<String, Object> row : supportInbox)
            if (((Number) row.get("userId")).intValue() == aliceId) aliceSupport = row;
        check("admin inbox shows sender and unread count", aliceSupport != null
                && ((Number) aliceSupport.get("unread")).intValue() == 1
                && Boolean.TRUE.equals(aliceSupport.get("hasMessages")));
        List<Map<String, Object>> aliceMessages = list(admin.get("/api/admin/support/" + aliceId));
        check("admin can read the user's private thread", aliceMessages.size() == 1
                && "I need help with my item.".equals(aliceMessages.get(0).get("text"))
                && !Boolean.TRUE.equals(aliceMessages.get(0).get("fromAdmin")));
        List<Map<String, Object>> refreshedInbox = list(admin.get("/api/admin/support"));
        for (Map<String, Object> row : refreshedInbox) {
            if (((Number) row.get("userId")).intValue() == aliceId)
                check("reading a thread clears its admin unread count", ((Number) row.get("unread")).intValue() == 0);
        }
        int aliceUnreadBeforeReply = ((Number) obj(alice.get("/api/me")).get("unread")).intValue();
        admin.post("/api/admin/support/" + aliceId, map("text", "We can help with that submission."));
        aliceMessages = list(alice.get("/api/me/support"));
        check("admin can reply and users see support replies",
                aliceMessages.size() == 2 && Boolean.TRUE.equals(aliceMessages.get(1).get("fromAdmin"))
                        && ((Number) obj(alice.get("/api/me")).get("unread")).intValue() == aliceUnreadBeforeReply + 1);
        admin.post("/api/admin/support/" + supportBobId, map("text", "Welcome to support."));
        List<Map<String, Object>> bobMessages = list(bob.get("/api/me/support"));
        check("admin can start a conversation and threads remain isolated",
                bobMessages.size() == 1 && Boolean.TRUE.equals(bobMessages.get(0).get("fromAdmin"))
                        && list(bob.get("/api/me/support")).size() == 1);
        check("admin cannot start a conversation with another admin",
                statusOf(() -> admin.post("/api/admin/support/" + obj(admin.get("/api/me")).get("id"),
                        map("text", "Not allowed"))) == 400);
        check("empty support messages are rejected",
                statusOf(() -> alice.post("/api/me/support", map("text", "  "))) == 400);
        StringBuilder overlongSupportMessage = new StringBuilder();
        for (int i = 0; i < 4001; i++) overlongSupportMessage.append('x');
        check("support messages over 4000 characters are rejected",
                statusOf(() -> alice.post("/api/me/support", map("text", overlongSupportMessage.toString()))) == 400);
        check("users cannot use administrator thread routes",
                statusOf(() -> alice.get("/api/admin/support/" + supportBobId)) == 403);

        System.out.println("Reuse flow");
        Map<String, Object> phone = obj(alice.post("/api/items", map("name", "Pixel 4", "brand", "Google", "category", "MOBILE",
                "ageYears", 2, "weightKg", 0.2, "condition", "WORKING", "description", "ok")));
        int phoneId = ((Number) phone.get("id")).intValue();
        check("submit returns suggestion", "REUSE".equals(phone.get("suggestedDecision")) && num(phone.get("estimatedValue")) > 100);
        check("item response adds an explainable decision report",
                "REUSE".equals(obj(phone.get("decisionReport")).get("recommendedAction"))
                        && obj(phone.get("decisionReport")).containsKey("reasons"));
        check("invalid enum -> 400", statusOf(() -> alice.post("/api/items", map("name", "x", "category", "TOASTER",
                "ageYears", 1, "weightKg", 1, "condition", "WORKING"))) == 400);
        check("negative weight -> 400", statusOf(() -> alice.post("/api/items", map("name", "x", "category", "MOBILE",
                "ageYears", 1, "weightKg", -1, "condition", "WORKING"))) == 400);
        check("bob cannot see alice's item", statusOf(() -> bob.get("/api/items/" + phoneId)) == 403);
        check("admin sees pending item", list(admin.get("/api/admin/items?status=SUBMITTED")).size() == 1);
        check("search by owner works", list(admin.get("/api/admin/items?q=" + enc("alice"))).size() == 1);
        check("cannot collect before approval", statusOf(() -> admin.post("/api/admin/items/" + phoneId + "/collect", null)) == 400);
        check("bad date -> 400", statusOf(() -> admin.post("/api/admin/items/" + phoneId + "/approve",
                map("decision", "REUSE", "pickupDate", "tomorrow"))) == 400);
        check("past date -> 400", statusOf(() -> admin.post("/api/admin/items/" + phoneId + "/approve",
                map("decision", "REUSE", "pickupDate", LocalDate.now().minusDays(1).toString()))) == 400);
        admin.post("/api/admin/items/" + phoneId + "/approve", map("decision", "REUSE", "pickupDate", LocalDate.now().plusDays(1).toString(), "note", "bring charger"));
        Map<String, Object> col = obj(admin.post("/api/admin/items/" + phoneId + "/collect", null));
        check("collection awards points", ((Number) col.get("pointsAwarded")).intValue() == 6);
        check("alice has points", ((Number) obj(alice.get("/api/me")).get("points")).intValue() == 6);
        check("alice has 5 unread including her support reply", ((Number) obj(alice.get("/api/me")).get("unread")).intValue() == 5);
        Map<String, Object> listing = obj(admin.post("/api/admin/listings", map("itemId", phoneId, "price", 100)));
        int lid = ((Number) listing.get("id")).intValue();
        check("market shows listing", list(bob.get("/api/market?q=pixel")).size() == 1);
        Map<String, Object> passport = obj(anon.get("/api/passport/" + phone.get("trackingCode")));
        check("public passport contains an explainable score and privacy-safe journey",
                ((Number) passport.get("circularityScore")).intValue() >= 0
                        && list(passport.get("scoreFactors")).size() == 4 && !passport.containsKey("ownerName")
                        && !passport.containsKey("email") && list(passport.get("journey")).size() >= 4);
        check("public passport exposes modeled carbon and material estimates",
                num(passport.get("co2PotentialKg")) > 0 && obj(passport.get("materialsKg")).containsKey("metalKg"));
        check("market filter by category", list(bob.get("/api/market?category=LAPTOP")).isEmpty());
        check("market filter by max price", list(bob.get("/api/market?maxPrice=50")).isEmpty());
        check("bob notified of new listing", list(bob.get("/api/notifications")).get(0).get("message").toString().contains("New in the market"));
        byte[] png;
        try {
            BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            png = out.toByteArray();
        } catch (java.io.IOException e) { throw new RuntimeException(e); }
        Map<String, Object> photo = map("attachmentName", "device.png", "attachmentType", "image/png",
                "attachmentBase64", Base64.getEncoder().encodeToString(png));
        alice.post("/api/market/" + lid + "/photo", photo);
        check("seller can upload listing photo and buyer can download it",
                bob.getBytes("/api/market/" + lid + "/photo").length == png.length);
        check("non-owner cannot replace listing photo",
                statusOf(() -> bob.post("/api/market/" + lid + "/photo", photo)) == 403);
        Map<String, Object> invalidPhoto = map("attachmentName", "wrong.png", "attachmentType", "image/png",
                "attachmentBase64", Base64.getEncoder().encodeToString("%PDF-1.4".getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
        check("server validates uploaded file content", statusOf(() -> alice.post("/api/market/" + lid + "/photo", invalidPhoto)) == 400);
        Map<String, Object> chatFile = map("attachmentName", "../../quote.pdf", "attachmentType", "application/pdf",
                "attachmentBase64", Base64.getEncoder().encodeToString("%PDF-1.4".getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
        Map<String, Object> chatBody = map("text", "Is this device available?");
        chatBody.putAll(chatFile);
        Map<String, Object> firstMessage = obj(bob.post("/api/market/" + lid + "/messages", chatBody));
        String attachmentUrl = String.valueOf(firstMessage.get("downloadUrl"));
        check("uploaded filename is reduced to a safe basename", "quote.pdf".equals(firstMessage.get("fileName")));
        check("buyer can contact seller with a file attachment",
                !attachmentUrl.isEmpty() && new String(bob.getBytes(attachmentUrl), java.nio.charset.StandardCharsets.US_ASCII).startsWith("%PDF-"));
        check("buyer cannot message a different recipient",
                statusOf(() -> bob.post("/api/market/" + lid + "/messages", map("recipientId", 1, "text", "Hello"))) == 400);
        check("seller cannot read another buyer conversation",
                statusOf(() -> alice.get("/api/market/" + lid + "/messages?with=999")) == 400);
        check("seller sees only the buyer conversation", list(alice.get("/api/market/" + lid + "/messages?with=" + obj(bob.get("/api/me")).get("id"))).size() == 1);
        alice.post("/api/market/" + lid + "/messages", map("recipientId", obj(bob.get("/api/me")).get("id"), "text", "Yes, it is ready."));
        check("seller replies and conversation appears in inbox", list(bob.get("/api/messages")).size() == 1);
        check("buyer and seller dashboard endpoints", obj(obj(alice.get("/api/me/market/dashboard")).get("seller")).get("activeListings") != null
                && obj(obj(bob.get("/api/me/market/dashboard")).get("buyer")).get("orders") != null);
        check("owner cannot buy own item", statusOf(() -> alice.post("/api/market/" + lid + "/buy", map("points", 0))) == 400);
        check("cannot redeem more points than owned", statusOf(() -> bob.post("/api/market/" + lid + "/buy", map("points", 5))) == 400);
        admin.post("/api/admin/users/" + obj(bob.get("/api/me")).get("id") + "/points", map("points", 500));
        check("points cap enforced (30%)", statusOf(() -> bob.post("/api/market/" + lid + "/buy", map("points", 301))) == 400);
        Map<String, Object> order = obj(bob.post("/api/market/" + lid + "/buy", map("points", 300)));
        check("discount applied", num(order.get("amountPaid")) == 70.0 && num(order.get("discount")) == 30.0);
        check("bob points deducted", ((Number) obj(bob.get("/api/me")).get("points")).intValue() == 200);
        check("seller bonus paid", ((Number) obj(alice.get("/api/me")).get("points")).intValue() == 106);
        check("sold listing unavailable", statusOf(() -> bob.post("/api/market/" + lid + "/buy", map("points", 0))) == 400);
        check("orders listed", list(bob.get("/api/orders/mine")).size() == 1);
        check("buyer dashboard records the order", ((Number) obj(obj(bob.get("/api/me/market/dashboard")).get("buyer")).get("orders")).intValue() == 1);
        List<Object> hist = (List<Object>) obj(alice.get("/api/items/" + phoneId)).get("history");
        check("item timeline recorded", hist.size() >= 5);

        System.out.println("Repair flow");
        Map<String, Object> lap = obj(bob.post("/api/items", map("name", "ThinkPad", "brand", "Lenovo", "category", "LAPTOP",
                "ageYears", 3, "weightKg", 2, "condition", "MINOR_FAULT")));
        int lapId = ((Number) lap.get("id")).intValue();
        check("minor fault -> repair suggestion", "REPAIR_REUSE".equals(lap.get("suggestedDecision")));
        admin.post("/api/admin/items/" + lapId + "/approve", map("decision", "REPAIR_REUSE", "pickupDate", LocalDate.now().toString()));
        admin.post("/api/admin/items/" + lapId + "/collect", null);
        check("cannot publish before repair", statusOf(() -> admin.post("/api/admin/listings", map("itemId", lapId, "price", 50))) == 400);
        admin.post("/api/admin/items/" + lapId + "/refurbish/start", map("repairCost", 40));
        Map<String, Object> done = obj(admin.post("/api/admin/items/" + lapId + "/refurbish/complete", null));
        check("refurbished item is reusable", "REUSE".equals(done.get("decision")) && "WORKING".equals(done.get("condition")));
        Map<String, Object> l2 = obj(admin.post("/api/admin/listings", map("itemId", lapId, "price", 200)));
        int l2id = ((Number) l2.get("id")).intValue();
        admin.put("/api/admin/listings/" + l2id, map("price", 150));
        check("price drop notifies users", alice.get("/api/notifications").toString().contains("Price drop"));
        admin.delete("/api/admin/listings/" + l2id);
        check("delist returns to inventory", "COLLECTED".equals(obj(admin.get("/api/items/" + lapId)).get("status")));

        System.out.println("Recycling flow");
        Map<String, Object> tv = obj(alice.post("/api/items", map("name", "Old CRT", "category", "TELEVISION", "ageYears", 15, "weightKg", 20, "condition", "DEAD")));
        Map<String, Object> bat = obj(alice.post("/api/items", map("name", "Swollen battery", "category", "BATTERY", "ageYears", 4, "weightKg", 0.5, "condition", "DEAD")));
        check("hazardous suggestion", "HAZARDOUS_DISPOSAL".equals(bat.get("suggestedDecision")));
        for (Map<String, Object> it : List.of(tv, bat)) {
            int id = ((Number) it.get("id")).intValue();
            admin.post("/api/admin/items/" + id + "/approve", map("decision", it.get("suggestedDecision"), "pickupDate", LocalDate.now().toString()));
            admin.post("/api/admin/items/" + id + "/collect", null);
        }
        check("queue all", ((Number) obj(admin.post("/api/admin/recycling/queue-all", null)).get("queued")).intValue() == 2);
        Map<String, Object> info = obj(admin.get("/api/admin/recycling"));
        check("queues separated", ((Number) info.get("queuedRegular")).intValue() == 1 && ((Number) info.get("queuedHazardous")).intValue() == 1);
        Map<String, Object> b1 = obj(admin.post("/api/admin/recycling/batches", map("partner", "GreenCycle", "hazardous", false)));
        check("regular batch: only the TV", ((Number) b1.get("itemCount")).intValue() == 1 && Math.abs(num(b1.get("metalKg")) - 5.0) < 1e-9);
        admin.post("/api/admin/recycling/batches", map("partner", "SafeBatt Certified", "hazardous", true));
        check("empty queue -> 400", statusOf(() -> admin.post("/api/admin/recycling/batches", map("partner", "x", "hazardous", true))) == 400);
        check("owner notified of recycling", alice.get("/api/notifications").toString().contains("responsibly recycled"));

        System.out.println("Reject, users, reports");
        Map<String, Object> junk = obj(bob.post("/api/items", map("name", "Mystery", "category", "OTHER", "ageYears", 1, "weightKg", 1, "condition", "WORKING")));
        int junkId = ((Number) junk.get("id")).intValue();
        check("reject needs reason", statusOf(() -> admin.post("/api/admin/items/" + junkId + "/reject", map("reason", " "))) == 400);
        admin.post("/api/admin/items/" + junkId + "/reject", map("reason", "Not e-waste"));
        check("rejected status", "REJECTED".equals(obj(bob.get("/api/items/" + junkId)).get("status")));
        Map<String, Object> stats = obj(admin.get("/api/admin/stats"));
        check("stats: users (alice, bob, mallory) and batches", ((Number) stats.get("users")).intValue() == 3 && ((Number) stats.get("batches")).intValue() == 2);
        check("stats: revenue and CO2", num(stats.get("revenue")) == 70.0 && num(stats.get("co2Saved")) > 0);
        check("leaderboard ranks alice first", "alice".equals(list(alice.get("/api/leaderboard")).get(0).get("username")));
        check("impact endpoint", num(obj(alice.get("/api/me/impact")).get("kgDonated")) > 20);
        String csv = admin.getText("/api/admin/export");
        check("csv export has all items", csv.split("\n").length == 1 + 5 && csv.startsWith("id,owner"));
        check("announcement reaches users", ((Number) obj(admin.post("/api/admin/announce", map("message", "Drive on Saturday"))).get("sent")).intValue() == 3);
        alice.post("/api/notifications/read", null);
        check("mark read clears unread", ((Number) obj(alice.get("/api/me")).get("unread")).intValue() == 0);
        int bobId = ((Number) obj(bob.get("/api/me")).get("id")).intValue();
        admin.post("/api/admin/users/" + bobId + "/active", map("active", false));
        check("deactivated user is locked out", statusOf(() -> bob.get("/api/me")) == 401 && statusOf(() -> new ApiClient(url).login("bob", "secret2")) == 400);
        alice.post("/api/me/password", map("oldPassword", "secret1", "newPassword", "newsecret"));
        check("password change works", "alice".equals(new ApiClient(url).login("alice", "newsecret").get("username")));
        check("profile update", "Alice Z".equals(obj(alice.put("/api/me", map("fullName", "Alice Z"))).get("fullName")));
        int pointsBeforeEco = ((Number) obj(alice.get("/api/me")).get("points")).intValue();
        Map<String, Object> ecoChallenge = obj(admin.post("/api/admin/eco/challenges", map("title", "Verified weekly donation",
                "description", "A test challenge", "type", "DONATE_KG", "period", "WEEKLY",
                "targetValue", 0.01, "rewardPoints", 9)));
        int ecoChallengeId = ((Number) ecoChallenge.get("id")).intValue();
        check("only admins can create eco challenges", statusOf(() -> alice.post("/api/admin/eco/challenges",
                map("title", "Unauthorized", "type", "DONATE_KG", "period", "WEEKLY",
                        "targetValue", 1, "rewardPoints", 50))) == 403);
        Map<String, Object> ecoEnrollment = obj(alice.post("/api/eco/challenges/" + ecoChallengeId + "/enroll", map()));
        check("verified existing activity completes enrollment and awards configured bonus",
                "COMPLETED".equals(ecoEnrollment.get("status"))
                        && ((Number) obj(alice.get("/api/me")).get("points")).intValue() == pointsBeforeEco + 9);
        check("duplicate enrollment does not duplicate bonus points",
                "COMPLETED".equals(obj(alice.post("/api/eco/challenges/" + ecoChallengeId + "/enroll", map())).get("status"))
                        && ((Number) obj(alice.get("/api/me")).get("points")).intValue() == pointsBeforeEco + 9);
        check("users cannot modify challenge progress or reward records",
                statusOf(() -> alice.post("/api/eco/challenges/" + ecoChallengeId + "/progress", map("progress", 999))) == 404
                        && statusOf(() -> alice.post("/api/eco/challenges/" + ecoChallengeId + "/rewards", map("points", 999))) == 404);
        Map<String, Object> ecoView = obj(alice.get("/api/eco/challenges"));
        check("eco page API provides achievements, progress and both leaderboards",
                ecoView.containsKey("achievements") && ecoView.containsKey("weeklyLeaderboard")
                        && ecoView.containsKey("monthlyLeaderboard")
                        && list(ecoView.get("challenges")).stream().anyMatch(challenge ->
                            ((Number) obj(challenge).get("id")).intValue() == ecoChallengeId
                                    && "COMPLETED".equals(obj(challenge).get("status"))));
        check("challenge leaderboard ranks verified bonus points",
                !list(alice.get("/api/eco/leaderboard?period=WEEKLY")).isEmpty()
                        && "Alice Z".equals(list(alice.get("/api/eco/leaderboard?period=WEEKLY")).get(0).get("name")));
        check("malformed JSON -> 400", statusOf(() -> alice.post("/api/items", "{bad")) == 400);
        alice.logout();
        check("logout invalidates token", statusOf(() -> alice.get("/api/me")) == 401);
    }
}

package com.ewaste;

import com.ewaste.client.ApiClient;
import com.ewaste.client.ApiClient.ApiException;
import com.ewaste.server.ServerMain;
import com.ewaste.server.repo.MemoryRepository;
import com.ewaste.server.repo.Repository;
import com.sun.net.httpserver.HttpServer;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import static com.ewaste.client.ApiClient.*;

/** End-to-end tests for recycler auction authorization, bidding, closure and award behavior. */
public class AuctionTest {
    private static int passed, failed;

    private static void check(String name, boolean ok) {
        if (ok) { passed++; System.out.println("  PASS " + name); }
        else { failed++; System.out.println("  FAIL " + name); }
    }

    private static int statusOf(Callable<?> c) {
        try { c.call(); return 200; }
        catch (ApiException e) { return e.status; }
        catch (Exception e) { return -1; }
    }

    private static int id(Map<String, Object> row) { return ((Number) row.get("id")).intValue(); }

    public static void main(String[] args) throws Exception {
        if (com.ewaste.server.repo.SqliteRepository.driverAvailable()) legacyDatabaseMigration();
        Repository repo;
        if (args.length > 0 && args[0].equals("sqlite")) {
            repo = new com.ewaste.server.repo.SqliteRepository(Files.createTempFile("ewaste-auctions", ".db").toString());
        } else if (args.length > 0 && args[0].equals("postgres")) {
            String jdbcUrl = System.getenv("EWASTE_TEST_POSTGRES_AUCTION_URL");
            if (jdbcUrl == null || jdbcUrl.trim().isEmpty())
                throw new IllegalArgumentException("EWASTE_TEST_POSTGRES_AUCTION_URL must point to a disposable PostgreSQL test database.");
            repo = new com.ewaste.server.repo.PostgresRepository(jdbcUrl,
                    System.getenv("EWASTE_TEST_POSTGRES_USER"), System.getenv("EWASTE_TEST_POSTGRES_PASSWORD"));
        } else {
            repo = new MemoryRepository(null);
        }
        HttpServer server = ServerMain.start(repo, "127.0.0.1", 0);
        String url = "http://127.0.0.1:" + server.getAddress().getPort();
        try { run(url); }
        finally { server.stop(0); repo.close(); }
        System.out.println("\n" + passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }

    private static void legacyDatabaseMigration() throws Exception {
        Path path = Files.createTempFile("ewaste-legacy", ".db");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + path.toString());
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE users (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT NOT NULL UNIQUE COLLATE NOCASE, "
                    + "salt TEXT NOT NULL, password_hash TEXT NOT NULL, full_name TEXT NOT NULL, email TEXT, phone TEXT, address TEXT, "
                    + "role TEXT NOT NULL, points INTEGER NOT NULL DEFAULT 0, active INTEGER NOT NULL DEFAULT 1)");
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO users (username,salt,password_hash,full_name,email,phone,address,role,points,active) VALUES (?,?,?,?,?,?,?,?,?,?)")) {
                insert.setString(1, "legacyuser");
                insert.setString(2, "oldsalt");
                insert.setString(3, "oldhash");
                insert.setString(4, "Legacy User");
                insert.setString(5, "legacy@example.test");
                insert.setString(6, "");
                insert.setString(7, "");
                insert.setString(8, "USER");
                insert.setInt(9, 17);
                insert.setInt(10, 1);
                insert.executeUpdate();
            }
        }
        Repository migrated = new com.ewaste.server.repo.SqliteRepository(path.toString());
        try {
            com.ewaste.model.User user = migrated.userByName("legacyuser");
            check("legacy SQLite user data survives additive auction migration",
                    user != null && "Legacy User".equals(user.fullName) && user.points == 17 && !user.emailOptOut);
        } finally { migrated.close(); }
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + path.toString());
             PreparedStatement query = connection.prepareStatement(
                     "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name IN "
                             + "('auctions','auction_bids','auction_history','eco_challenges','challenge_enrollments',"
                             + "'user_achievements','challenge_reward_history')");
             ResultSet result = query.executeQuery()) {
            check("legacy SQLite database receives additive auction and eco challenge tables", result.next() && result.getInt(1) == 7);
        }
    }

    private static void run(String url) throws Exception {
        ApiClient admin = new ApiClient(url), anon = new ApiClient(url);
        admin.login("admin", "admin123");
        anon.post("/api/auth/register", map("username", "auctionowner", "password", "secret1",
                "fullName", "Auction Owner", "email", "owner@example.test"));
        anon.post("/api/auth/register", map("username", "recyclerone", "password", "secret1",
                "fullName", "Recycler One", "email", "one@example.test"));
        anon.post("/api/auth/register", map("username", "recyclertwo", "password", "secret1",
                "fullName", "Recycler Two", "email", "two@example.test"));
        anon.post("/api/auth/register", map("username", "ordinaryuser", "password", "secret1",
                "fullName", "Ordinary User", "email", "user@example.test"));

        ApiClient owner = new ApiClient(url), one = new ApiClient(url), two = new ApiClient(url), ordinary = new ApiClient(url);
        Map<String, Object> ownerUser = owner.login("auctionowner", "secret1");
        Map<String, Object> oneUser = one.login("recyclerone", "secret1");
        Map<String, Object> twoUser = two.login("recyclertwo", "secret1");
        Map<String, Object> ordinaryUser = ordinary.login("ordinaryuser", "secret1");
        for (Map<String, Object> user : List.of(ownerUser, oneUser, twoUser))
            admin.post("/api/admin/users/" + user.get("id") + "/role", map("role", "RECYCLER"));
        check("admin can grant recycler role", "RECYCLER".equals(ApiClient.list(admin.get("/api/admin/users")).stream()
                .filter(u -> "recyclerone".equals(u.get("username"))).findFirst().get().get("role")));
        check("ordinary user cannot grant recycler role", statusOf(() -> ordinary.post("/api/admin/users/"
                + ordinaryUser.get("id") + "/role", map("role", "RECYCLER"))) == 403);
        check("ordinary user cannot view recycler auctions", statusOf(() -> ordinary.get("/api/auctions")) == 403);
        check("recycler cannot access admin auction endpoints", statusOf(() -> one.get("/api/admin/auctions")) == 403);

        int firstItem = collectedBattery(owner, admin, "Auction battery");
        check("invalid minimum bid is rejected", statusOf(() -> admin.post("/api/admin/auctions", map("itemId", firstItem,
                "minimumBid", 0, "closesAt", futureClose()))) == 400);
        Map<String, Object> auction = obj(admin.post("/api/admin/auctions", map("itemId", firstItem,
                "minimumBid", 25.0, "closesAt", futureClose())));
        int auctionId = id(auction);
        check("admin publishes eligible recycling item", "OPEN".equals(auction.get("status"))
                && ((Number) auction.get("minimumBid")).doubleValue() == 25.0);
        check("non-recycler cannot submit a bid", statusOf(() -> ordinary.post("/api/auctions/" + auctionId + "/bids",
                bidBody(30, "Standard recovery"))) == 403);
        check("item with open auction is excluded from recycling queue", ((Number) obj(admin.get("/api/admin/recycling")).get("eligible")).intValue() == 0);
        check("recycler cannot bid on own item", statusOf(() -> owner.post("/api/auctions/" + auctionId + "/bids",
                bidBody(30, "Standard recovery"))) == 400);
        check("bid below minimum is rejected", statusOf(() -> one.post("/api/auctions/" + auctionId + "/bids",
                bidBody(24.99, "Standard recovery"))) == 400);
        check("missing recycling method is rejected", statusOf(() -> one.post("/api/auctions/" + auctionId + "/bids",
                bidBody(30, " "))) == 400);
        Map<String, Object> firstBid = obj(one.post("/api/auctions/" + auctionId + "/bids", bidBody(30, "Material recovery")));
        check("bid records required fields and pending status", firstBid.containsKey("id") && firstBid.containsKey("recyclerId")
                && firstBid.containsKey("itemId") && firstBid.containsKey("amount")
                && firstBid.containsKey("proposedCollectionDate") && firstBid.containsKey("recyclingMethod")
                && firstBid.containsKey("bidTimestamp") && "PENDING".equals(firstBid.get("status")));
        check("duplicate bid by same recycler is rejected", statusOf(() -> one.post("/api/auctions/" + auctionId + "/bids",
                bidBody(31, "Material recovery"))) == 400);
        Map<String, Object> secondBid = obj(two.post("/api/auctions/" + auctionId + "/bids", bidBody(28, "Certified recovery")));
        check("admin can compare bids", list(admin.get("/api/admin/auctions/" + auctionId + "/bids")).size() == 2);

        admin.post("/api/admin/auctions/" + auctionId + "/award", map("bidId", firstBid.get("id")));
        List<Map<String, Object>> awardedBids = list(admin.get("/api/admin/auctions/" + auctionId + "/bids"));
        check("admin selection marks winner and rejects competing bid",
                "WINNER".equals(find(awardedBids, id(firstBid)).get("status"))
                        && "REJECTED".equals(find(awardedBids, id(secondBid)).get("status")));
        Map<String, Object> awardedAuction = find(list(admin.get("/api/admin/auctions")), auctionId);
        check("auction reports awarded status and winning amount", "AWARDED".equals(awardedAuction.get("status"))
                && ((Number) obj(awardedAuction.get("winningBid")).get("amount")).doubleValue() == 30.0);
        check("winner and unsuccessful recycler receive notifications",
                list(one.get("/api/notifications")).toString().contains("Congratulations")
                        && list(two.get("/api/notifications")).toString().contains("not selected"));
        check("auction activity is audited", list(admin.get("/api/admin/auctions/" + auctionId + "/history")).size() >= 4);
        check("awarded item remains unavailable to recycle batches",
                ((Number) obj(admin.get("/api/admin/recycling")).get("eligible")).intValue() == 0);

        int secondItem = collectedBattery(owner, admin, "Closing battery");
        LocalDateTime closesSoon = LocalDateTime.now().plusSeconds(3).withNano(0);
        Map<String, Object> closingAuction = obj(admin.post("/api/admin/auctions", map("itemId", secondItem,
                "minimumBid", 10.0, "closesAt", closesSoon.toString())));
        int closingId = id(closingAuction);
        check("bid accepted before close", statusOf(() -> two.post("/api/auctions/" + closingId + "/bids",
                bidBody(10, "Certified recovery"))) == 200);
        Thread.sleep(3500);
        check("expired auction is automatically closed on refresh", "CLOSED".equals(
                find(list(admin.get("/api/admin/auctions")), closingId).get("status")));
        check("closed auction rejects new bids", statusOf(() -> one.post("/api/auctions/" + closingId + "/bids",
                bidBody(12, "Material recovery"))) == 400);
        admin.post("/api/admin/auctions/" + closingId + "/cancel", null);
        check("cancelled auction releases its item back to recycling", ((Number) obj(admin.post(
                "/api/admin/recycling/queue-all", null)).get("queued")).intValue() == 1);
    }

    private static int collectedBattery(ApiClient owner, ApiClient admin, String name) {
        Map<String, Object> item = obj(owner.post("/api/items", map("name", name, "category", "BATTERY",
                "ageYears", 2, "weightKg", 1.0, "condition", "DEAD")));
        int itemId = id(item);
        admin.post("/api/admin/items/" + itemId + "/approve",
                map("decision", "HAZARDOUS_DISPOSAL", "pickupDate", LocalDate.now().toString()));
        admin.post("/api/admin/items/" + itemId + "/collect", null);
        return itemId;
    }

    private static String futureClose() {
        return LocalDateTime.now().plusDays(1).withSecond(0).withNano(0).toString();
    }

    private static Map<String, Object> bidBody(double amount, String method) {
        return map("amount", amount, "proposedCollectionDate", LocalDate.now().plusDays(1).toString(),
                "recyclingMethod", method);
    }

    private static Map<String, Object> find(List<Map<String, Object>> rows, int id) {
        for (Map<String, Object> row : rows) if (id(row) == id) return row;
        throw new AssertionError("No row with id " + id);
    }

}

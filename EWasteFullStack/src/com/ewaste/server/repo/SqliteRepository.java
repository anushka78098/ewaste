package com.ewaste.server.repo;

import com.ewaste.model.*;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/** JDBC repository for SQLite. Requires the sqlite-jdbc driver jar on the classpath (see scripts/setup). */
public class SqliteRepository implements Repository {
    @FunctionalInterface
    private interface RowMapper<T> { T map(ResultSet rs) throws SQLException; }

    private final Connection c;
    private final String path;
    private int depth;

    public SqliteRepository(String path) {
        this.path = path;
        try {
            Class.forName("org.sqlite.JDBC");
            c = DriverManager.getConnection("jdbc:sqlite:" + path);
            try (Statement s = c.createStatement()) {
                s.execute("PRAGMA foreign_keys = ON");
                s.execute("PRAGMA journal_mode = WAL");
                for (String ddl : SCHEMA) s.execute(ddl);
                for (String m : MIGRATIONS) {
                    try { s.execute(m); }
                    catch (SQLException e) { if (e.getMessage() == null || !e.getMessage().toLowerCase().contains("duplicate column")) throw e; }
                }
            }
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("SQLite JDBC driver not found on the classpath", e);
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot open database " + path + ": " + e.getMessage(), e);
        }
    }

    public static boolean driverAvailable() {
        try { Class.forName("org.sqlite.JDBC"); return true; } catch (ClassNotFoundException e) { return false; }
    }

    static final String[] SCHEMA = {
        "CREATE TABLE IF NOT EXISTS users (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT NOT NULL UNIQUE COLLATE NOCASE, "
            + "salt TEXT NOT NULL, password_hash TEXT NOT NULL, full_name TEXT NOT NULL, email TEXT, phone TEXT, address TEXT, "
            + "role TEXT NOT NULL, points INTEGER NOT NULL DEFAULT 0, active INTEGER NOT NULL DEFAULT 1, email_opt_out INTEGER NOT NULL DEFAULT 0)",
        "CREATE TABLE IF NOT EXISTS items (id INTEGER PRIMARY KEY AUTOINCREMENT, owner_id INTEGER NOT NULL REFERENCES users(id), "
            + "name TEXT NOT NULL, brand TEXT, category TEXT NOT NULL, age_years INTEGER NOT NULL, weight_kg REAL NOT NULL, "
            + "condition TEXT NOT NULL, description TEXT, status TEXT NOT NULL, suggested_decision TEXT, decision TEXT, "
            + "estimated_value REAL NOT NULL DEFAULT 0, repair_cost REAL NOT NULL DEFAULT 0, pickup_date TEXT, admin_note TEXT, "
            + "points_awarded INTEGER NOT NULL DEFAULT 0, created_at TEXT NOT NULL)",
        "CREATE TABLE IF NOT EXISTS item_history (id INTEGER PRIMARY KEY AUTOINCREMENT, item_id INTEGER NOT NULL REFERENCES items(id), line TEXT NOT NULL)",
        "CREATE TABLE IF NOT EXISTS listings (id INTEGER PRIMARY KEY AUTOINCREMENT, item_id INTEGER NOT NULL REFERENCES items(id), "
            + "price REAL NOT NULL, active INTEGER NOT NULL DEFAULT 1, photo_file_name TEXT, photo_media_type TEXT, photo_data BLOB)",
        "CREATE TABLE IF NOT EXISTS orders (id INTEGER PRIMARY KEY AUTOINCREMENT, listing_id INTEGER NOT NULL, item_id INTEGER NOT NULL, "
            + "buyer_id INTEGER NOT NULL, list_price REAL NOT NULL, points_used INTEGER NOT NULL, discount REAL NOT NULL, "
            + "amount_paid REAL NOT NULL, created_at TEXT NOT NULL)",
        "CREATE TABLE IF NOT EXISTS market_messages (id INTEGER PRIMARY KEY AUTOINCREMENT, listing_id INTEGER NOT NULL REFERENCES listings(id), "
            + "sender_id INTEGER NOT NULL REFERENCES users(id), recipient_id INTEGER NOT NULL REFERENCES users(id), message TEXT NOT NULL, "
            + "file_name TEXT, media_type TEXT, file_data BLOB, created_at TEXT NOT NULL)",
        "CREATE TABLE IF NOT EXISTS notifications (id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER NOT NULL, message TEXT NOT NULL, "
            + "created_at TEXT NOT NULL, is_read INTEGER NOT NULL DEFAULT 0)",
        "CREATE TABLE IF NOT EXISTS batches (id INTEGER PRIMARY KEY AUTOINCREMENT, partner TEXT NOT NULL, certified INTEGER NOT NULL, "
            + "created_at TEXT NOT NULL, total_kg REAL, metal_kg REAL, plastic_kg REAL, glass_kg REAL, hazard_kg REAL, other_kg REAL, co2_saved_kg REAL)",
        "CREATE TABLE IF NOT EXISTS batch_items (batch_id INTEGER NOT NULL REFERENCES batches(id), item_id INTEGER NOT NULL REFERENCES items(id))",
        "CREATE TABLE IF NOT EXISTS campaigns (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, description TEXT, goal_kg REAL NOT NULL, "
            + "start_date TEXT NOT NULL, end_date TEXT NOT NULL, active INTEGER NOT NULL DEFAULT 1)",
        "CREATE TABLE IF NOT EXISTS auctions (id INTEGER PRIMARY KEY AUTOINCREMENT, item_id INTEGER NOT NULL REFERENCES items(id), "
            + "created_by INTEGER NOT NULL REFERENCES users(id), minimum_bid REAL NOT NULL CHECK (minimum_bid > 0), closes_at TEXT NOT NULL, "
            + "created_at TEXT NOT NULL, status TEXT NOT NULL CHECK (status IN ('OPEN','CLOSED','AWARDED','CANCELLED')), UNIQUE(id, item_id))",
        "CREATE TABLE IF NOT EXISTS auction_bids (id INTEGER PRIMARY KEY AUTOINCREMENT, auction_id INTEGER NOT NULL, recycler_id INTEGER NOT NULL REFERENCES users(id), "
            + "item_id INTEGER NOT NULL, amount REAL NOT NULL CHECK (amount > 0), proposed_collection_date TEXT NOT NULL, recycling_method TEXT NOT NULL, "
            + "created_at TEXT NOT NULL, status TEXT NOT NULL CHECK (status IN ('PENDING','WINNER','REJECTED')), "
            + "FOREIGN KEY (auction_id, item_id) REFERENCES auctions(id, item_id), UNIQUE(auction_id, recycler_id))",
        "CREATE TABLE IF NOT EXISTS auction_history (id INTEGER PRIMARY KEY AUTOINCREMENT, auction_id INTEGER NOT NULL REFERENCES auctions(id), "
            + "actor_id INTEGER REFERENCES users(id), action TEXT NOT NULL, details TEXT NOT NULL, created_at TEXT NOT NULL)",
        "CREATE INDEX IF NOT EXISTS idx_items_owner ON items(owner_id)",
        "CREATE INDEX IF NOT EXISTS idx_items_status ON items(status)",
        "CREATE INDEX IF NOT EXISTS idx_notifications_user ON notifications(user_id)",
        "CREATE INDEX IF NOT EXISTS idx_auctions_status_close ON auctions(status, closes_at)",
        "CREATE UNIQUE INDEX IF NOT EXISTS idx_auctions_reserved_item ON auctions(item_id) WHERE status IN ('OPEN','CLOSED','AWARDED')",
        "CREATE INDEX IF NOT EXISTS idx_auction_bids_auction_amount ON auction_bids(auction_id, amount)",
        "CREATE INDEX IF NOT EXISTS idx_auction_history_auction ON auction_history(auction_id, id)"
    };

    /** Upgrades databases created by earlier versions. Each statement may fail harmlessly if already applied. */
    static final String[] MIGRATIONS = {
        "ALTER TABLE users ADD COLUMN email_opt_out INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE listings ADD COLUMN photo_file_name TEXT",
        "ALTER TABLE listings ADD COLUMN photo_media_type TEXT",
        "ALTER TABLE listings ADD COLUMN photo_data BLOB"
    };

    // ------------------------------------------------------------------ JDBC helpers
    private PreparedStatement prep(String sql, boolean keys, Object... args) throws SQLException {
        PreparedStatement ps = keys ? c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS) : c.prepareStatement(sql);
        for (int i = 0; i < args.length; i++) {
            Object a = args[i];
            if (a instanceof Enum) a = ((Enum<?>) a).name();
            else if (a instanceof LocalDate || a instanceof LocalDateTime) a = a.toString();
            else if (a instanceof Boolean) a = ((Boolean) a) ? 1 : 0;
            ps.setObject(i + 1, a);
        }
        return ps;
    }

    private <T> List<T> query(String sql, RowMapper<T> m, Object... args) {
        try (PreparedStatement ps = prep(sql, false, args); ResultSet rs = ps.executeQuery()) {
            List<T> out = new ArrayList<>();
            while (rs.next()) out.add(m.map(rs));
            return out;
        } catch (SQLException e) {
            throw new IllegalStateException("DB query failed: " + e.getMessage(), e);
        }
    }

    private <T> T one(String sql, RowMapper<T> m, Object... args) {
        List<T> l = query(sql, m, args);
        return l.isEmpty() ? null : l.get(0);
    }

    private int update(String sql, Object... args) {
        try (PreparedStatement ps = prep(sql, false, args)) {
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("DB update failed: " + e.getMessage(), e);
        }
    }

    private int insert(String sql, Object... args) {
        try (PreparedStatement ps = prep(sql, true, args)) {
            ps.executeUpdate();
            try (ResultSet k = ps.getGeneratedKeys()) {
                if (!k.next()) throw new SQLException("no generated key");
                return k.getInt(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("DB insert failed: " + e.getMessage(), e);
        }
    }

    private static <E extends Enum<E>> E en(Class<E> t, String v) { return v == null ? null : Enum.valueOf(t, v); }

    // ------------------------------------------------------------------ mappers
    private static final RowMapper<User> USER = rs -> {
        User u = new User();
        u.id = rs.getInt("id"); u.username = rs.getString("username"); u.salt = rs.getString("salt");
        u.passwordHash = rs.getString("password_hash"); u.fullName = rs.getString("full_name");
        u.email = rs.getString("email"); u.phone = rs.getString("phone"); u.address = rs.getString("address");
        u.role = en(Role.class, rs.getString("role")); u.points = rs.getInt("points"); u.active = rs.getInt("active") == 1;
        u.emailOptOut = rs.getInt("email_opt_out") == 1;
        return u;
    };

    private static final RowMapper<EWasteItem> ITEM = rs -> {
        EWasteItem i = new EWasteItem();
        i.id = rs.getInt("id"); i.ownerId = rs.getInt("owner_id"); i.name = rs.getString("name");
        i.brand = nz(rs.getString("brand")); i.category = en(Category.class, rs.getString("category"));
        i.ageYears = rs.getInt("age_years"); i.weightKg = rs.getDouble("weight_kg");
        i.condition = en(Condition.class, rs.getString("condition")); i.description = nz(rs.getString("description"));
        i.status = en(ItemStatus.class, rs.getString("status"));
        i.suggestedDecision = en(Decision.class, rs.getString("suggested_decision"));
        i.decision = en(Decision.class, rs.getString("decision"));
        i.estimatedValue = rs.getDouble("estimated_value"); i.repairCost = rs.getDouble("repair_cost");
        String pd = rs.getString("pickup_date"); i.pickupDate = pd == null ? null : LocalDate.parse(pd);
        i.adminNote = nz(rs.getString("admin_note")); i.pointsAwarded = rs.getInt("points_awarded");
        i.createdAt = LocalDateTime.parse(rs.getString("created_at"));
        return i;
    };

    private static final RowMapper<Listing> LISTING = rs -> {
        Listing l = new Listing();
        l.id = rs.getInt("id"); l.itemId = rs.getInt("item_id"); l.price = rs.getDouble("price"); l.active = rs.getInt("active") == 1;
        l.photoFileName = rs.getString("photo_file_name"); l.photoMediaType = rs.getString("photo_media_type"); l.photoData = rs.getBytes("photo_data");
        return l;
    };

    private static final RowMapper<MarketMessage> MARKET_MESSAGE = rs -> {
        MarketMessage m = new MarketMessage();
        m.id = rs.getInt("id"); m.listingId = rs.getInt("listing_id"); m.senderId = rs.getInt("sender_id");
        m.recipientId = rs.getInt("recipient_id"); m.text = rs.getString("message");
        m.fileName = nz(rs.getString("file_name")); m.mediaType = nz(rs.getString("media_type"));
        m.fileData = rs.getBytes("file_data"); m.time = LocalDateTime.parse(rs.getString("created_at"));
        return m;
    };

    private static final RowMapper<Order> ORDER = rs -> {
        Order o = new Order();
        o.id = rs.getInt("id"); o.listingId = rs.getInt("listing_id"); o.itemId = rs.getInt("item_id");
        o.buyerId = rs.getInt("buyer_id"); o.listPrice = rs.getDouble("list_price"); o.pointsUsed = rs.getInt("points_used");
        o.discount = rs.getDouble("discount"); o.amountPaid = rs.getDouble("amount_paid");
        o.time = LocalDateTime.parse(rs.getString("created_at"));
        return o;
    };

    private static final RowMapper<Notification> NOTE = rs -> {
        Notification n = new Notification();
        n.id = rs.getInt("id"); n.userId = rs.getInt("user_id"); n.message = rs.getString("message");
        n.time = LocalDateTime.parse(rs.getString("created_at")); n.read = rs.getInt("is_read") == 1;
        return n;
    };

    private static final RowMapper<Auction> AUCTION = rs -> {
        Auction a = new Auction();
        a.id = rs.getInt("id"); a.itemId = rs.getInt("item_id"); a.createdBy = rs.getInt("created_by");
        a.minimumBid = rs.getDouble("minimum_bid"); a.closesAt = LocalDateTime.parse(rs.getString("closes_at"));
        a.createdAt = LocalDateTime.parse(rs.getString("created_at")); a.status = en(AuctionStatus.class, rs.getString("status"));
        return a;
    };

    private static final RowMapper<AuctionBid> AUCTION_BID = rs -> {
        AuctionBid b = new AuctionBid();
        b.id = rs.getInt("id"); b.auctionId = rs.getInt("auction_id"); b.recyclerId = rs.getInt("recycler_id");
        b.itemId = rs.getInt("item_id"); b.amount = rs.getDouble("amount");
        b.proposedCollectionDate = LocalDate.parse(rs.getString("proposed_collection_date"));
        b.recyclingMethod = rs.getString("recycling_method"); b.time = LocalDateTime.parse(rs.getString("created_at"));
        b.status = en(BidStatus.class, rs.getString("status"));
        return b;
    };

    private static final RowMapper<AuctionHistoryEntry> AUCTION_HISTORY = rs -> {
        AuctionHistoryEntry h = new AuctionHistoryEntry();
        h.id = rs.getInt("id"); h.auctionId = rs.getInt("auction_id");
        int actor = rs.getInt("actor_id"); h.actorId = rs.wasNull() ? null : actor;
        h.action = rs.getString("action"); h.details = rs.getString("details");
        h.time = LocalDateTime.parse(rs.getString("created_at"));
        return h;
    };

    private static String nz(String s) { return s == null ? "" : s; }

    // ------------------------------------------------------------------ users
    @Override public User addUser(User u) {
        u.id = insert("INSERT INTO users (username, salt, password_hash, full_name, email, phone, address, role, points, active, email_opt_out) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                u.username, u.salt, u.passwordHash, u.fullName, u.email, u.phone, u.address, u.role, u.points, u.active, u.emailOptOut);
        return u;
    }
    @Override public User user(int id) { return one("SELECT * FROM users WHERE id = ?", USER, id); }
    @Override public User userByName(String name) { return one("SELECT * FROM users WHERE username = ? COLLATE NOCASE", USER, name); }
    @Override public List<User> users() { return query("SELECT * FROM users ORDER BY id", USER); }
    @Override public void saveUser(User u) {
        update("UPDATE users SET salt=?, password_hash=?, full_name=?, email=?, phone=?, address=?, role=?, points=?, active=?, email_opt_out=? WHERE id=?",
                u.salt, u.passwordHash, u.fullName, u.email, u.phone, u.address, u.role, u.points, u.active, u.emailOptOut, u.id);
    }

    // ------------------------------------------------------------------ items
    @Override public EWasteItem addItem(EWasteItem i) {
        i.id = insert("INSERT INTO items (owner_id, name, brand, category, age_years, weight_kg, condition, description, status, suggested_decision, decision, "
                        + "estimated_value, repair_cost, pickup_date, admin_note, points_awarded, created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                i.ownerId, i.name, i.brand, i.category, i.ageYears, i.weightKg, i.condition, i.description, i.status, i.suggestedDecision,
                i.decision, i.estimatedValue, i.repairCost, i.pickupDate, i.adminNote, i.pointsAwarded, i.createdAt);
        return i;
    }
    @Override public EWasteItem item(int id) {
        EWasteItem i = one("SELECT * FROM items WHERE id = ?", ITEM, id);
        if (i != null) i.history = query("SELECT line FROM item_history WHERE item_id = ? ORDER BY id", rs -> rs.getString("line"), id);
        return i;
    }
    @Override public List<EWasteItem> items() { return query("SELECT * FROM items ORDER BY id", ITEM); }
    @Override public void saveItem(EWasteItem i) {
        update("UPDATE items SET name=?, brand=?, category=?, age_years=?, weight_kg=?, condition=?, description=?, status=?, suggested_decision=?, "
                        + "decision=?, estimated_value=?, repair_cost=?, pickup_date=?, admin_note=?, points_awarded=? WHERE id=?",
                i.name, i.brand, i.category, i.ageYears, i.weightKg, i.condition, i.description, i.status, i.suggestedDecision,
                i.decision, i.estimatedValue, i.repairCost, i.pickupDate, i.adminNote, i.pointsAwarded, i.id);
    }
    @Override public void addHistory(int itemId, String line) {
        update("INSERT INTO item_history (item_id, line) VALUES (?, ?)", itemId, line);
    }

    // ------------------------------------------------------------------ market
    @Override public Listing addListing(Listing l) {
        l.id = insert("INSERT INTO listings (item_id, price, active, photo_file_name, photo_media_type, photo_data) VALUES (?,?,?,?,?,?)",
                l.itemId, l.price, l.active, l.photoFileName, l.photoMediaType, l.photoData);
        return l;
    }
    @Override public Listing listing(int id) { return one("SELECT * FROM listings WHERE id = ?", LISTING, id); }
    @Override public List<Listing> listings() { return query("SELECT * FROM listings ORDER BY id", LISTING); }
    @Override public void saveListing(Listing l) {
        update("UPDATE listings SET price=?, active=?, photo_file_name=?, photo_media_type=?, photo_data=? WHERE id=?",
                l.price, l.active, l.photoFileName, l.photoMediaType, l.photoData, l.id);
    }
    @Override public Order addOrder(Order o) {
        o.id = insert("INSERT INTO orders (listing_id, item_id, buyer_id, list_price, points_used, discount, amount_paid, created_at) VALUES (?,?,?,?,?,?,?,?)",
                o.listingId, o.itemId, o.buyerId, o.listPrice, o.pointsUsed, o.discount, o.amountPaid, o.time);
        return o;
    }
    @Override public List<Order> orders() { return query("SELECT * FROM orders ORDER BY id", ORDER); }
    @Override public MarketMessage addMarketMessage(MarketMessage m) {
        m.id = insert("INSERT INTO market_messages (listing_id, sender_id, recipient_id, message, file_name, media_type, file_data, created_at) "
                        + "VALUES (?,?,?,?,?,?,?,?)",
                m.listingId, m.senderId, m.recipientId, m.text, m.fileName, m.mediaType, m.fileData, m.time);
        return m;
    }
    @Override public List<MarketMessage> marketMessages() {
        return query("SELECT * FROM market_messages ORDER BY id", MARKET_MESSAGE);
    }

    // ------------------------------------------------------------------ notifications
    @Override public Notification addNotification(Notification n) {
        n.id = insert("INSERT INTO notifications (user_id, message, created_at, is_read) VALUES (?,?,?,?)", n.userId, n.message, n.time, n.read);
        return n;
    }
    @Override public List<Notification> notifications() { return query("SELECT * FROM notifications ORDER BY id", NOTE); }
    @Override public void saveNotification(Notification n) { update("UPDATE notifications SET is_read=? WHERE id=?", n.read, n.id); }

    // ------------------------------------------------------------------ recycling
    @Override public RecyclingBatch addBatch(RecyclingBatch b) {
        b.id = insert("INSERT INTO batches (partner, certified, created_at, total_kg, metal_kg, plastic_kg, glass_kg, hazard_kg, other_kg, co2_saved_kg) VALUES (?,?,?,?,?,?,?,?,?,?)",
                b.partner, b.certifiedHazardous, b.time, b.totalKg, b.metalKg, b.plasticKg, b.glassKg, b.hazardKg, b.otherKg, b.co2SavedKg);
        for (int itemId : b.itemIds) update("INSERT INTO batch_items (batch_id, item_id) VALUES (?, ?)", b.id, itemId);
        return b;
    }
    @Override public List<RecyclingBatch> batches() {
        List<RecyclingBatch> list = query("SELECT * FROM batches ORDER BY id", rs -> {
            RecyclingBatch b = new RecyclingBatch();
            b.id = rs.getInt("id"); b.partner = rs.getString("partner"); b.certifiedHazardous = rs.getInt("certified") == 1;
            b.time = LocalDateTime.parse(rs.getString("created_at")); b.totalKg = rs.getDouble("total_kg");
            b.metalKg = rs.getDouble("metal_kg"); b.plasticKg = rs.getDouble("plastic_kg"); b.glassKg = rs.getDouble("glass_kg");
            b.hazardKg = rs.getDouble("hazard_kg"); b.otherKg = rs.getDouble("other_kg"); b.co2SavedKg = rs.getDouble("co2_saved_kg");
            return b;
        });
        for (RecyclingBatch b : list) {
            b.itemIds = query("SELECT item_id FROM batch_items WHERE batch_id = ? ORDER BY item_id", rs -> rs.getInt("item_id"), b.id);
        }
        return list;
    }

    // ------------------------------------------------------------------ recycler auctions
    @Override public Auction addAuction(Auction a) {
        a.id = insert("INSERT INTO auctions (item_id, created_by, minimum_bid, closes_at, created_at, status) VALUES (?,?,?,?,?,?)",
                a.itemId, a.createdBy, a.minimumBid, a.closesAt, a.createdAt, a.status);
        return a;
    }
    @Override public Auction auction(int id) { return one("SELECT * FROM auctions WHERE id = ?", AUCTION, id); }
    @Override public List<Auction> auctions() { return query("SELECT * FROM auctions ORDER BY id", AUCTION); }
    @Override public void saveAuction(Auction a) {
        update("UPDATE auctions SET status=? WHERE id=?", a.status, a.id);
    }
    @Override public AuctionBid addAuctionBid(AuctionBid b) {
        b.id = insert("INSERT INTO auction_bids (auction_id, recycler_id, item_id, amount, proposed_collection_date, recycling_method, created_at, status) "
                        + "VALUES (?,?,?,?,?,?,?,?)",
                b.auctionId, b.recyclerId, b.itemId, b.amount, b.proposedCollectionDate, b.recyclingMethod, b.time, b.status);
        return b;
    }
    @Override public void saveAuctionBid(AuctionBid b) {
        update("UPDATE auction_bids SET status=? WHERE id=?", b.status, b.id);
    }
    @Override public List<AuctionBid> auctionBids(int auctionId) {
        return query("SELECT * FROM auction_bids WHERE auction_id = ? ORDER BY amount, id", AUCTION_BID, auctionId);
    }
    @Override public List<AuctionBid> auctionBids() { return query("SELECT * FROM auction_bids ORDER BY id", AUCTION_BID); }
    @Override public AuctionHistoryEntry addAuctionHistory(AuctionHistoryEntry h) {
        h.id = insert("INSERT INTO auction_history (auction_id, actor_id, action, details, created_at) VALUES (?,?,?,?,?)",
                h.auctionId, h.actorId, h.action, h.details, h.time);
        return h;
    }
    @Override public List<AuctionHistoryEntry> auctionHistory(int auctionId) {
        return query("SELECT * FROM auction_history WHERE auction_id = ? ORDER BY id", AUCTION_HISTORY, auctionId);
    }

    // ------------------------------------------------------------------ campaigns
    private static final RowMapper<Campaign> CAMPAIGN = rs -> {
        Campaign c = new Campaign();
        c.id = rs.getInt("id"); c.name = rs.getString("name"); c.description = nz(rs.getString("description"));
        c.goalKg = rs.getDouble("goal_kg"); c.startDate = LocalDate.parse(rs.getString("start_date"));
        c.endDate = LocalDate.parse(rs.getString("end_date")); c.active = rs.getInt("active") == 1;
        return c;
    };
    @Override public Campaign addCampaign(Campaign c) {
        c.id = insert("INSERT INTO campaigns (name, description, goal_kg, start_date, end_date, active) VALUES (?,?,?,?,?,?)",
                c.name, c.description, c.goalKg, c.startDate, c.endDate, c.active);
        return c;
    }
    @Override public Campaign campaign(int id) { return one("SELECT * FROM campaigns WHERE id = ?", CAMPAIGN, id); }
    @Override public List<Campaign> campaigns() { return query("SELECT * FROM campaigns ORDER BY id", CAMPAIGN); }
    @Override public void saveCampaign(Campaign c) {
        update("UPDATE campaigns SET name=?, description=?, goal_kg=?, end_date=?, active=? WHERE id=?",
                c.name, c.description, c.goalKg, c.endDate, c.active, c.id);
    }

    // ------------------------------------------------------------------ transactions
    @Override public <T> T atomic(Callable<T> work) {
        try {
            if (depth > 0) return work.call();
            depth++;
            c.setAutoCommit(false);
            try {
                T r = work.call();
                c.commit();
                return r;
            } catch (Exception e) {
                try { c.rollback(); } catch (SQLException ignored) { }
                throw e;
            } finally {
                depth--;
                c.setAutoCommit(true);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override public String describe() { return "SQLite database " + path; }
    @Override public void close() { try { c.close(); } catch (SQLException ignored) { } }
}

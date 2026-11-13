package com.ewaste.server.repo;

/** PostgreSQL-backed repository using the shared JDBC operations and PostgreSQL schema. */
public final class PostgresRepository extends JdbcRepository {
    public PostgresRepository(String jdbcUrl, String username, String password) {
        super("PostgreSQL database", "org.postgresql.Driver", jdbcUrl,
                username, password, SCHEMA, MIGRATIONS, false);
    }

    public static boolean driverAvailable() {
        return JdbcRepository.driverAvailable("org.postgresql.Driver");
    }

    private static final String[] SCHEMA = {
        "CREATE TABLE IF NOT EXISTS users (id SERIAL PRIMARY KEY, username TEXT NOT NULL, "
            + "salt TEXT NOT NULL, password_hash TEXT NOT NULL, full_name TEXT NOT NULL, email TEXT, phone TEXT, address TEXT, "
            + "role TEXT NOT NULL, points INTEGER NOT NULL DEFAULT 0, active SMALLINT NOT NULL DEFAULT 1, "
            + "email_opt_out SMALLINT NOT NULL DEFAULT 0)",
        "CREATE UNIQUE INDEX IF NOT EXISTS idx_users_username_lower ON users (LOWER(username))",
        "CREATE TABLE IF NOT EXISTS items (id SERIAL PRIMARY KEY, owner_id INTEGER NOT NULL REFERENCES users(id), "
            + "name TEXT NOT NULL, brand TEXT, category TEXT NOT NULL, age_years INTEGER NOT NULL, weight_kg DOUBLE PRECISION NOT NULL, "
            + "condition TEXT NOT NULL, description TEXT, status TEXT NOT NULL, suggested_decision TEXT, decision TEXT, "
            + "estimated_value DOUBLE PRECISION NOT NULL DEFAULT 0, repair_cost DOUBLE PRECISION NOT NULL DEFAULT 0, "
            + "pickup_date TEXT, admin_note TEXT, points_awarded INTEGER NOT NULL DEFAULT 0, created_at TEXT NOT NULL)",
        "CREATE TABLE IF NOT EXISTS item_history (id SERIAL PRIMARY KEY, item_id INTEGER NOT NULL REFERENCES items(id), line TEXT NOT NULL)",
        "CREATE TABLE IF NOT EXISTS listings (id SERIAL PRIMARY KEY, item_id INTEGER NOT NULL REFERENCES items(id), "
            + "price DOUBLE PRECISION NOT NULL, active SMALLINT NOT NULL DEFAULT 1, photo_file_name TEXT, photo_media_type TEXT, photo_data BYTEA)",
        "CREATE TABLE IF NOT EXISTS orders (id SERIAL PRIMARY KEY, listing_id INTEGER NOT NULL, item_id INTEGER NOT NULL, "
            + "buyer_id INTEGER NOT NULL, list_price DOUBLE PRECISION NOT NULL, points_used INTEGER NOT NULL, "
            + "discount DOUBLE PRECISION NOT NULL, amount_paid DOUBLE PRECISION NOT NULL, created_at TEXT NOT NULL)",
        "CREATE TABLE IF NOT EXISTS market_messages (id SERIAL PRIMARY KEY, listing_id INTEGER NOT NULL REFERENCES listings(id), "
            + "sender_id INTEGER NOT NULL REFERENCES users(id), recipient_id INTEGER NOT NULL REFERENCES users(id), message TEXT NOT NULL, "
            + "file_name TEXT, media_type TEXT, file_data BYTEA, created_at TEXT NOT NULL)",
        "CREATE TABLE IF NOT EXISTS support_messages (id SERIAL PRIMARY KEY, user_id INTEGER NOT NULL REFERENCES users(id), "
            + "sender_id INTEGER NOT NULL REFERENCES users(id), message TEXT NOT NULL CHECK(length(trim(message)) BETWEEN 1 AND 4000), "
            + "created_at TEXT NOT NULL, read_by_admin SMALLINT NOT NULL DEFAULT 0 CHECK(read_by_admin IN (0,1)), "
            + "read_by_user SMALLINT NOT NULL DEFAULT 0 CHECK(read_by_user IN (0,1)))",
        "CREATE INDEX IF NOT EXISTS idx_support_messages_user ON support_messages(user_id, id)",
        "CREATE TABLE IF NOT EXISTS notifications (id SERIAL PRIMARY KEY, user_id INTEGER NOT NULL, message TEXT NOT NULL, "
            + "created_at TEXT NOT NULL, is_read SMALLINT NOT NULL DEFAULT 0)",
        "CREATE TABLE IF NOT EXISTS batches (id SERIAL PRIMARY KEY, partner TEXT NOT NULL, certified SMALLINT NOT NULL, "
            + "created_at TEXT NOT NULL, total_kg DOUBLE PRECISION, metal_kg DOUBLE PRECISION, plastic_kg DOUBLE PRECISION, "
            + "glass_kg DOUBLE PRECISION, hazard_kg DOUBLE PRECISION, other_kg DOUBLE PRECISION, co2_saved_kg DOUBLE PRECISION)",
        "CREATE TABLE IF NOT EXISTS batch_items (batch_id INTEGER NOT NULL REFERENCES batches(id), item_id INTEGER NOT NULL REFERENCES items(id))",
        "CREATE TABLE IF NOT EXISTS campaigns (id SERIAL PRIMARY KEY, name TEXT NOT NULL, description TEXT, "
            + "goal_kg DOUBLE PRECISION NOT NULL, start_date TEXT NOT NULL, end_date TEXT NOT NULL, active SMALLINT NOT NULL DEFAULT 1)",
        "CREATE TABLE IF NOT EXISTS eco_challenges (id SERIAL PRIMARY KEY, title TEXT NOT NULL, description TEXT NOT NULL DEFAULT '', "
            + "type TEXT NOT NULL CHECK (type IN ('DONATE_KG','DONATE_REUSABLE','VERIFIED_RECYCLING','COLLECTION_DRIVE')), "
            + "period TEXT NOT NULL CHECK (period IN ('WEEKLY','MONTHLY')), target_value DOUBLE PRECISION NOT NULL CHECK (target_value > 0), "
            + "reward_points INTEGER NOT NULL CHECK (reward_points > 0), start_date TEXT NOT NULL, end_date TEXT NOT NULL, "
            + "active SMALLINT NOT NULL DEFAULT 1, created_at TEXT NOT NULL)",
        "CREATE TABLE IF NOT EXISTS challenge_enrollments (id SERIAL PRIMARY KEY, "
            + "challenge_id INTEGER NOT NULL REFERENCES eco_challenges(id), user_id INTEGER NOT NULL REFERENCES users(id), "
            + "enrolled_at TEXT NOT NULL, progress DOUBLE PRECISION NOT NULL DEFAULT 0 CHECK (progress >= 0), "
            + "status TEXT NOT NULL CHECK (status IN ('ACTIVE','COMPLETED','EXPIRED')), completed_at TEXT, "
            + "halfway_notified SMALLINT NOT NULL DEFAULT 0, UNIQUE(challenge_id, user_id), UNIQUE(id, challenge_id, user_id))",
        "CREATE TABLE IF NOT EXISTS user_achievements (id SERIAL PRIMARY KEY, user_id INTEGER NOT NULL REFERENCES users(id), "
            + "achievement_key TEXT NOT NULL, label TEXT NOT NULL, description TEXT NOT NULL, earned_at TEXT NOT NULL, "
            + "UNIQUE(user_id, achievement_key))",
        "CREATE TABLE IF NOT EXISTS challenge_reward_history (id SERIAL PRIMARY KEY, "
            + "challenge_id INTEGER NOT NULL, user_id INTEGER NOT NULL, points INTEGER NOT NULL CHECK (points > 0), "
            + "awarded_at TEXT NOT NULL, FOREIGN KEY (challenge_id, user_id) REFERENCES challenge_enrollments(challenge_id, user_id), "
            + "UNIQUE(challenge_id, user_id))",
        "CREATE INDEX IF NOT EXISTS idx_eco_challenges_dates ON eco_challenges(active, start_date, end_date)",
        "CREATE INDEX IF NOT EXISTS idx_challenge_enrollments_user ON challenge_enrollments(user_id, status)",
        "CREATE INDEX IF NOT EXISTS idx_challenge_rewards_awarded ON challenge_reward_history(awarded_at, user_id)",
        "CREATE TABLE IF NOT EXISTS auctions (id SERIAL PRIMARY KEY, item_id INTEGER NOT NULL REFERENCES items(id), "
            + "created_by INTEGER NOT NULL REFERENCES users(id), minimum_bid DOUBLE PRECISION NOT NULL CHECK (minimum_bid > 0), "
            + "closes_at TEXT NOT NULL, created_at TEXT NOT NULL, status TEXT NOT NULL "
            + "CHECK (status IN ('OPEN','CLOSED','AWARDED','CANCELLED')), UNIQUE(id, item_id))",
        "CREATE TABLE IF NOT EXISTS auction_bids (id SERIAL PRIMARY KEY, auction_id INTEGER NOT NULL, "
            + "recycler_id INTEGER NOT NULL REFERENCES users(id), item_id INTEGER NOT NULL, amount DOUBLE PRECISION NOT NULL CHECK (amount > 0), "
            + "proposed_collection_date TEXT NOT NULL, recycling_method TEXT NOT NULL, created_at TEXT NOT NULL, "
            + "status TEXT NOT NULL CHECK (status IN ('PENDING','WINNER','REJECTED')), "
            + "FOREIGN KEY (auction_id, item_id) REFERENCES auctions(id, item_id), UNIQUE(auction_id, recycler_id))",
        "CREATE TABLE IF NOT EXISTS auction_history (id SERIAL PRIMARY KEY, auction_id INTEGER NOT NULL REFERENCES auctions(id), "
            + "actor_id INTEGER REFERENCES users(id), action TEXT NOT NULL, details TEXT NOT NULL, created_at TEXT NOT NULL)",
        "CREATE INDEX IF NOT EXISTS idx_items_owner ON items(owner_id)",
        "CREATE INDEX IF NOT EXISTS idx_items_status ON items(status)",
        "CREATE INDEX IF NOT EXISTS idx_notifications_user ON notifications(user_id)",
        "CREATE INDEX IF NOT EXISTS idx_auctions_status_close ON auctions(status, closes_at)",
        "CREATE UNIQUE INDEX IF NOT EXISTS idx_auctions_reserved_item ON auctions(item_id) "
            + "WHERE status IN ('OPEN','CLOSED','AWARDED')",
        "CREATE INDEX IF NOT EXISTS idx_auction_bids_auction_amount ON auction_bids(auction_id, amount)",
        "CREATE INDEX IF NOT EXISTS idx_auction_history_auction ON auction_history(auction_id, id)"
    };

    private static final String[] MIGRATIONS = {
        "ALTER TABLE users ADD COLUMN IF NOT EXISTS email_opt_out SMALLINT NOT NULL DEFAULT 0",
        "ALTER TABLE listings ADD COLUMN IF NOT EXISTS photo_file_name TEXT",
        "ALTER TABLE listings ADD COLUMN IF NOT EXISTS photo_media_type TEXT",
        "ALTER TABLE listings ADD COLUMN IF NOT EXISTS photo_data BYTEA"
    };
}

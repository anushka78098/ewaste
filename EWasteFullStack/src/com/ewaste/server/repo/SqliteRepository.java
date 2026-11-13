package com.ewaste.server.repo;

/** SQLite-backed repository using the JDBC implementation and SQLite schema. */
public final class SqliteRepository extends JdbcRepository {
    public SqliteRepository(String path) {
        super("SQLite database " + path, "org.sqlite.JDBC", "jdbc:sqlite:" + path,
                null, null, JdbcRepository.SCHEMA, JdbcRepository.MIGRATIONS, true);
    }

    public static boolean driverAvailable() {
        return JdbcRepository.driverAvailable("org.sqlite.JDBC");
    }
}

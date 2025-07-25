package org.example.dao;

import org.sqlite.SQLiteConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class AuthDB implements AutoCloseable {
    private final Connection conn;

    public AuthDB(String path) throws SQLException {
        SQLiteConfig cfg = new SQLiteConfig();
        cfg.setBusyTimeout(5_000);
        cfg.setJournalMode(SQLiteConfig.JournalMode.WAL);
        cfg.enforceForeignKeys(true);
        conn = DriverManager.getConnection("jdbc:sqlite:" + path, cfg.toProperties());
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS users(
                    id        INTEGER PRIMARY KEY,
                    username  TEXT UNIQUE NOT NULL COLLATE NOCASE,
                    pwd_hash  TEXT NOT NULL,
                    kdf_salt  BLOB NOT NULL,
                    kdf_iters INTEGER NOT NULL,
                    created   TEXT DEFAULT CURRENT_TIMESTAMP
                )
            """);
        }
    }

    public Connection c() {
        return conn;
    }

    @Override
    public void close() {
        try {
            conn.close();
        } catch (Exception ignore) {
        }
    }
}

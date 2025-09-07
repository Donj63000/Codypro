package org.example.dao;

import org.sqlite.SQLiteConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class AuthDB implements AutoCloseable {
    private final Connection conn;

    public static Path defaultFile() {
        Path dir = Path.of(System.getProperty("user.home"), ".prestataires");
        return dir.resolve("auth.db");
    }

    public AuthDB() throws SQLException { this(defaultFile().toString()); }

    public AuthDB(String path) throws SQLException {
        Path p = Path.of(path);
        try { Files.createDirectories(p.getParent()); } catch (Exception ignore) {}

        SQLiteConfig cfg = new SQLiteConfig();
        cfg.setBusyTimeout(5_000);
        cfg.setJournalMode(SQLiteConfig.JournalMode.WAL);
        cfg.enforceForeignKeys(true);

        Connection c;
        try {
            c = DriverManager.getConnection("jdbc:sqlite:" + p.toAbsolutePath(), cfg.toProperties());
        } catch (SQLException e) {
            if (!isNotADB(e)) throw e;
            // Fichier corrompu/non-SQLite: on l'isole puis on recrée une DB vierge
            try {
                Path bad = p.resolveSibling(p.getFileName() + ".corrupt." + System.currentTimeMillis());
                Files.move(p, bad);
            } catch (Exception ignore) {}
            c = DriverManager.getConnection("jdbc:sqlite:" + p.toAbsolutePath(), cfg.toProperties());
        }
        this.conn = c;

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

    private static boolean isNotADB(SQLException e) {
        String m = e.getMessage();
        return m != null && (m.contains("NOTADB") || m.contains("file is not a database"));
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

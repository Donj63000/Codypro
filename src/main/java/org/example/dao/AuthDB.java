package org.example.dao;

import org.example.util.AppPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sqlite.SQLiteConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class AuthDB implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(AuthDB.class);
    private final Connection conn;

    public static Path defaultFile() {
        return AppPaths.authDb();
    }

    public AuthDB() throws SQLException { this(defaultFile().toString()); }

    public AuthDB(String path) throws SQLException {
        Path p = Path.of(path);
        try { Files.createDirectories(p.getParent()); } catch (Exception ignore) {}

        log.debug("[AuthDB] Init path={}", p.toAbsolutePath());

        SQLiteConfig cfg = new SQLiteConfig();
        cfg.setBusyTimeout(5_000);
        cfg.setJournalMode(SQLiteConfig.JournalMode.WAL);
        cfg.enforceForeignKeys(true);

        Connection c;
        try {
            c = DriverManager.getConnection("jdbc:sqlite:" + p.toAbsolutePath(), cfg.toProperties());
            // Initialize schema; if this fails with NOTADB, handle in catch branch.
            try (Statement st = c.createStatement()) {
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
        } catch (SQLException e) {
            if (!isNotADB(e)) throw e;
            // Fichier corrompu/non-SQLite: isole le fichier puis recrée une DB vierge
            log.warn("[AuthDB] NOTADB for {} -> recreating. cause={}", p.getFileName(), oneLine(e));
            try {
                Path bad = p.resolveSibling(p.getFileName() + ".corrupt." + System.currentTimeMillis());
                Files.move(p, bad);
                log.info("[AuthDB] Moved invalid auth DB to {}", bad.getFileName());
            } catch (Exception ignore) {}
            c = DriverManager.getConnection("jdbc:sqlite:" + p.toAbsolutePath(), cfg.toProperties());
            try (Statement st = c.createStatement()) {
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
        log.debug("[AuthDB] Opened OK at {} (WAL, FK ON)", p.toAbsolutePath());
        this.conn = c;
    }

    private static boolean isNotADB(SQLException e) {
        // Parcourt la chaîne de causes et accepte plusieurs variantes
        for (Throwable t = e; t != null; t = t.getCause()) {
            String m = t.getMessage();
            if (m == null) continue;
            String s = m.toLowerCase(java.util.Locale.ROOT);
            if (s.contains("notadb")) return true; // code d'erreur sqlite
            if (s.contains("file is not a database")) return true; // variante courante
            if (s.contains("not a database file")) return true; // autre formulation
            if (s.contains("file opened that is not a database")) return true; // formulation driver
            if (s.contains("file is encrypted or is not a database")) return true; // sqlcipher
            if (s.contains("database disk image is malformed")) return true; // corruption disque
        }
        return false;
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

    private static String oneLine(Throwable t) {
        String m = t == null ? null : t.getMessage();
        return m == null ? "<no-message>" : m.replace('\n',' ').replace('\r',' ');
    }
}


package org.example.dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.*;
import java.util.HexFormat;

public final class UserDB implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(UserDB.class);
    private Connection conn;
    private final Path dbPath;

    public UserDB(String filePath) {
        this.dbPath = Path.of(filePath);
        try { Files.createDirectories(this.dbPath.getParent()); } catch (Exception ignore) {}
        log.debug("[UserDB] path={}", this.dbPath.toAbsolutePath());
    }

    // Ouvre une connexion unique avec la cle appliquee des la creation
    public synchronized void openPool(byte[] keyBytes) throws SQLException {
        close();
        String hexKey = HexFormat.of().formatHex(keyBytes);
        boolean needsInit = false;
        try { needsInit = Files.notExists(dbPath) || Files.size(dbPath) == 0L; } catch (Exception ignore) {}
        if (needsInit) log.debug("[UserDB] creating new encrypted DB at {}", dbPath.toAbsolutePath());
        SQLException sseFailure = null;
        try {
            conn = openWithMode(hexKey, org.sqlite.SQLiteConfig.HexKeyMode.SSE, needsInit);
            log.debug("[UserDB] openPool OK (WAL, FK ON, mode=SSE)");
            return;
        } catch (SQLException ex) {
            if (!isNotADB(ex)) {
                throw ex;
            }
            sseFailure = ex;
            log.warn("[UserDB] SSE key rejected for {}: {}", dbPath.getFileName(), oneLine(ex));
        }
        try {
            conn = openWithMode(hexKey, org.sqlite.SQLiteConfig.HexKeyMode.SQLCIPHER, needsInit);
            log.info("[UserDB] openPool OK (WAL, FK ON, mode=SQLCIPHER - legacy)");
        } catch (SQLException ex) {
            if (sseFailure != null) ex.addSuppressed(sseFailure);
            throw ex;
        }
    }

    private Connection openWithMode(String hexKey, org.sqlite.SQLiteConfig.HexKeyMode mode, boolean needsInit) throws SQLException {
        org.sqlite.SQLiteConfig sc = new org.sqlite.SQLiteConfig();
        sc.setBusyTimeout(5000);
        sc.setSharedCache(true);
        sc.setSynchronous(org.sqlite.SQLiteConfig.SynchronousMode.FULL);
        sc.setJournalMode(org.sqlite.SQLiteConfig.JournalMode.WAL);
        sc.enforceForeignKeys(true);
        sc.setOpenMode(org.sqlite.SQLiteOpenMode.FULLMUTEX);
        sc.setHexKeyMode(mode);
        sc.setPragma(org.sqlite.SQLiteConfig.Pragma.HEXKEY_MODE, mode.name());
        sc.setPragma(org.sqlite.SQLiteConfig.Pragma.KEY, hexKey);
        String url = "jdbc:sqlite:" + dbPath.toAbsolutePath();
        Connection c = DriverManager.getConnection(url, sc.toProperties());
        try {
            try (Statement st = c.createStatement()) {
                st.execute("PRAGMA foreign_keys=ON");
                st.execute("PRAGMA busy_timeout=5000");
                try { st.execute("PRAGMA cipher_compatibility=4"); } catch (SQLException ignore) {}
                if (needsInit) {
                    try { st.execute("CREATE TABLE IF NOT EXISTS __init__(x INTEGER)"); } catch (SQLException ignore) {}
                    try { st.execute("DROP TABLE IF EXISTS __init__"); } catch (SQLException ignore) {}
                }
                try (ResultSet rs = st.executeQuery("SELECT count(*) FROM sqlite_master")) {
                    if (!rs.next()) throw new SQLException("sqlite_master not readable");
                }
            }
            return c;
        } catch (SQLException | RuntimeException ex) {
            try { c.close(); } catch (Exception ignore) {}
            throw ex;
        }
    }

    // Essaie d'ouvrir; si NOTADB -> traite le cas clair/corrompu et recrée au besoin
    public synchronized void openOrRepair(byte[] keyBytes) throws SQLException {
        try {
            openPool(keyBytes);
            return;
        } catch (SQLException e) {
            if (!isNotADB(e)) {
                throw e;
            }
            log.warn("[UserDB] NOTADB at {} -> attempting repair. cause={}", dbPath.getFileName(), oneLine(e));
            try {
                long ts = System.currentTimeMillis();
                Path wal = Path.of(dbPath.toString() + "-wal");
                Path shm = Path.of(dbPath.toString() + "-shm");
                if (Files.exists(wal)) {
                    Files.move(wal, wal.resolveSibling(wal.getFileName() + ".corrupt." + ts));
                    log.info("[UserDB] isolated WAL file {}", wal.getFileName());
                }
                if (Files.exists(shm)) {
                    Files.move(shm, shm.resolveSibling(shm.getFileName() + ".corrupt." + ts));
                    log.info("[UserDB] isolated SHM file {}", shm.getFileName());
                }
            } catch (Exception ignore) {}

            boolean plain = looksPlainSQLite(dbPath);
            if (plain) {
                try (Connection cPlain = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
                     Statement st = cPlain.createStatement()) {
                    st.execute("PRAGMA foreign_keys=ON");
                    st.execute("PRAGMA busy_timeout=5000");
                    st.executeQuery("SELECT 1");
                    migratePlainToEncrypted(cPlain, keyBytes);
                    openPool(keyBytes);
                    return;
                } catch (SQLException ex2) {
                    log.warn("[UserDB] Migration plain -> encrypted failed: {}", oneLine(ex2));
                    try {
                        Path bad = dbPath.resolveSibling(dbPath.getFileName() + ".corrupt." + System.currentTimeMillis());
                        Files.move(dbPath, bad);
                    } catch (Exception ignore) {}
                }
            }

            throw new SQLException("Base chiffrée ou illisible. Mot de passe incorrect ? Fichier: " + dbPath);
        }
    }

    private void migratePlainToEncrypted(Connection plainConn, byte[] keyBytes) throws SQLException {
        Path tmp = dbPath.resolveSibling(dbPath.getFileName() + ".tmp.enc");
        try {
            try { Files.deleteIfExists(tmp); } catch (Exception ignore) {}
            String hexKey = HexFormat.of().formatHex(keyBytes);
            String tmpPath = tmp.toAbsolutePath().toString().replace("'", "''");
            try (Statement st = plainConn.createStatement()) {
                st.execute("PRAGMA foreign_keys=ON");
                st.execute("PRAGMA busy_timeout=5000");
                st.executeQuery("SELECT 1");
                st.execute("ATTACH DATABASE '" + tmpPath + "' AS encrypted KEY \"x'" + hexKey + "'\"");
                try { st.execute("PRAGMA cipher_compatibility=4"); } catch (SQLException ignore) {}
                st.execute("SELECT sqlcipher_export('encrypted')");
                st.execute("DETACH DATABASE encrypted");
            }
            Path bak = dbPath.resolveSibling(dbPath.getFileName() + ".plain.bak." + System.currentTimeMillis());
            Files.move(dbPath, bak, StandardCopyOption.REPLACE_EXISTING);
            Files.move(tmp, dbPath, StandardCopyOption.REPLACE_EXISTING);
            log.info("[UserDB] Migrated plain SQLite to encrypted DB (backup: {})", bak.getFileName());
        } catch (SQLException e) {
            try { Files.deleteIfExists(tmp); } catch (Exception ignore) {}
            throw e;
        } catch (Exception e) {
            try { Files.deleteIfExists(tmp); } catch (Exception ignore) {}
            throw new SQLException("Migration plain->encrypted failed: " + e.getMessage(), e);
        }
    }

    private void createFreshEncrypted(byte[] keyBytes) throws SQLException {
        String hex = HexFormat.of().formatHex(keyBytes);
        try (Connection cNew = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
             Statement stNew = cNew.createStatement()) {
            try { stNew.execute("PRAGMA cipher_compatibility=4"); } catch (SQLException ignore) {}
            stNew.execute("PRAGMA key = \"x'" + hex + "'\"");
            try { stNew.execute("CREATE TABLE IF NOT EXISTS __init__(x INTEGER)"); } catch (SQLException ignore) {}
            try { stNew.execute("DROP TABLE IF EXISTS __init__"); } catch (SQLException ignore) {}
        }
        log.debug("[UserDB] Fresh encrypted DB created at {}", dbPath.toAbsolutePath());
    }

    private static boolean isNotADB(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            String m = t.getMessage();
            if (m == null) continue;
            m = m.toLowerCase(java.util.Locale.ROOT);
            if (m.contains("notadb")) return true;
            if (m.contains("file is not a database")) return true;
            if (m.contains("not a database file")) return true;
            if (m.contains("file opened that is not a database")) return true;
            if (m.contains("file is encrypted or is not a database")) return true;
            if (m.contains("database disk image is malformed")) return true;
        }
        return false;
    }

    private static boolean looksPlainSQLite(Path p) {
        try (var in = java.nio.file.Files.newInputStream(p)) {
            byte[] hdr = in.readNBytes(16);
            return new String(hdr, java.nio.charset.StandardCharsets.US_ASCII).startsWith("SQLite format 3");
        } catch (Exception e) { return false; }
    }

    public synchronized void rekeyDatabase(byte[] oldKey, byte[] newKey, int newIterations) throws SQLException {
        String oldHex = HexFormat.of().formatHex(oldKey);
        String newHex = HexFormat.of().formatHex(newKey);
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
             Statement st = c.createStatement()) {
            st.execute("PRAGMA key = \"x'" + oldHex + "'\"");
            st.execute("PRAGMA kdf_iter = " + newIterations);
            st.execute("PRAGMA rekey = \"x'" + newHex + "'\"");
        }
    }

    public Connection getConnection() throws SQLException {
        if (conn == null) throw new IllegalStateException("openPool must be called first");
        return conn;
    }

    // Convenience alias
    public Connection connection() throws SQLException { return getConnection(); }

    @Override
    public void close() {
        if (conn != null) {
            try { conn.close(); } catch (Exception ignore) {}
            conn = null;
        }
    }

    private static String oneLine(Throwable t) {
        String m = t == null ? null : t.getMessage();
        return m == null ? "<no-message>" : m.replace('\n',' ').replace('\r',' ');
    }
}

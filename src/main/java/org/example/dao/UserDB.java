package org.example.dao;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HexFormat;

import static org.example.dao.SqlcipherUtil.disableWalForRekey;
import static org.example.dao.SqlcipherUtil.enableWal;
import static org.example.dao.SqlcipherUtil.rekey;

public final class UserDB implements AutoCloseable {
    private final Path dbPath;
    private HikariDataSource ds;
    private String currentHexKey;

    public UserDB(String filePath) {
        this.dbPath = Path.of(filePath);
    }

    private String url() { return "jdbc:sqlite:" + dbPath; }
    private String urlWithHexKey(String hex) { return url() + "?cipher=sqlcipher&hexkey=" + hex; }

    public synchronized void openPool(byte[] rawKey) throws SQLException {
        String hexKey = HexFormat.of().formatHex(rawKey);
        // Idempotent: if already open with the same key, nothing to do
        if (ds != null && currentHexKey != null && currentHexKey.equalsIgnoreCase(hexKey)) {
            return;
        }
        // Close any previous pool
        closeQuietly();

        // 1) Verify access with key; if it fails, try to encrypt a clear DB; else surface error
        if (!tryOpenWithKey(url(), hexKey)) {
            if (!tryEncryptClearDb(url(), rawKey) && !tryMigratePlainToEncrypted(url(), rawKey)) {
                throw new SQLException("SQLITE_NOTADB: file not recognized as SQLite DB (bad key or corrupted file)");
            }
            if (!tryOpenWithKey(url(), hexKey)) {
                throw new SQLException("Open check after rekey failed: invalid key or driver without SQLCipher");
            }
        }

        // 2) Create a Hikari pool (size 1) and provide the key both via URL and Properties
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(url());
        hc.addDataSourceProperty("cipher", "sqlcipher");
        hc.addDataSourceProperty("hexkey", hexKey);
        hc.setMaximumPoolSize(1);
        hc.setMinimumIdle(1);
        hc.setPoolName("userdb-" + dbPath.getFileName());
        // Apply useful PRAGMAs to each new connection (key is in the JDBC URL)
        hc.setConnectionInitSql("PRAGMA foreign_keys=ON; PRAGMA busy_timeout=5000;");
        this.ds = new HikariDataSource(hc);

        // 3) Verify the pool is operational and enable WAL
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            try (var rs = st.executeQuery("SELECT count(*) FROM sqlite_master")) {
                if (!rs.next()) throw new SQLException("Impossible de lire sqlite_master");
            }
            enableWal(c);
        }

        this.currentHexKey = hexKey;
    }

    private static void configureCommonPragmas(Statement st) throws SQLException {
        st.execute("PRAGMA busy_timeout=5000");
        st.execute("PRAGMA foreign_keys=ON");
    }

    private static boolean tryOpenWithKey(String url, String hexKey) {
        // First try: provide key at connection time (Willena driver)
        try (Connection c = DriverManager.getConnection(url + "?cipher=sqlcipher&hexkey=" + hexKey);
             Statement st = c.createStatement()) {
            configureCommonPragmas(st);
            try (var rs = st.executeQuery("SELECT count(*) FROM sqlite_master")) {
                if (!rs.next()) return false;
            }
            enableWal(c);
            return true;
        } catch (SQLException ignore) {
        }

        // Second try: provide key via connection properties
        try {
            java.util.Properties p = new java.util.Properties();
            p.setProperty("cipher", "sqlcipher");
            p.setProperty("hexkey", hexKey);
            try (Connection c = DriverManager.getConnection(url, p); Statement st = c.createStatement()) {
                configureCommonPragmas(st);
                try (var rs = st.executeQuery("SELECT count(*) FROM sqlite_master")) {
                    if (!rs.next()) return false;
                }
                enableWal(c);
                return true;
            }
        } catch (SQLException ignore) {
        }

        // Fallback: open then apply PRAGMA key (covers drivers requiring PRAGMA form)
        try (Connection c = DriverManager.getConnection(url); Statement st = c.createStatement()) {
            configureCommonPragmas(st);
            st.execute("PRAGMA key = \"x'" + hexKey + "'\"");
            try (var rs = st.executeQuery("SELECT count(*) FROM sqlite_master")) {
                if (!rs.next()) return false;
            }
            enableWal(c);
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    private static boolean tryEncryptClearDb(String url, byte[] rawKey) {
        try (Connection c = DriverManager.getConnection(url); Statement st = c.createStatement()) {
            configureCommonPragmas(st);
            // Verify we can read sqlite_master in clear (file = non-encrypted DB)
            try (var rs = st.executeQuery("SELECT count(*) FROM sqlite_master")) {
                if (!rs.next()) return false;
            }
            disableWalForRekey(c);
            rekey(c, rawKey, null);
            enableWal(c);
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    // Fallback migration for plain DBs using sqlcipher_export to a new file
    private boolean tryMigratePlainToEncrypted(String url, byte[] rawKey) {
        String hex = HexFormat.of().formatHex(rawKey);
        Path tmpEnc = dbPath.resolveSibling(dbPath.getFileName().toString() + ".enc.tmp");
        try (Connection c = DriverManager.getConnection(url); Statement st = c.createStatement()) {
            configureCommonPragmas(st);
            // Check readable (plain DB)
            try (var rs = st.executeQuery("SELECT 1")) { if (!rs.next()) return false; }
            // Export to encrypted DB
            String escaped = tmpEnc.toAbsolutePath().toString().replace("'", "''");
            st.execute("ATTACH DATABASE '" + escaped + "' AS encrypted KEY \"x'" + hex + "'\"");
            st.execute("SELECT sqlcipher_export('encrypted')");
            st.execute("DETACH DATABASE encrypted");
        } catch (SQLException e) {
            try { Files.deleteIfExists(tmpEnc); } catch (Exception ignore) {}
            return false;
        }
        try {
            Path bak = dbPath.resolveSibling(dbPath.getFileName() + ".plain.bak");
            Files.deleteIfExists(bak);
            Files.move(dbPath, bak);
            Files.move(tmpEnc, dbPath);
            return true;
        } catch (Exception ioe) {
            try { Files.deleteIfExists(tmpEnc); } catch (Exception ignore) {}
            return false;
        }
    }

    public Connection connection() throws SQLException {
        if (ds == null) throw new IllegalStateException("openPool() must be called before requesting connections");
        return ds.getConnection();
    }

    @Override
    public void close() {
        closeQuietly();
    }

    private void closeQuietly() {
        try {
            if (ds != null) ds.close();
        } catch (Exception ignore) {
        } finally {
            ds = null;
            currentHexKey = null;
        }
    }

    /**
     * Open the pool or repair the database file if needed.
     * - If the DB is clear (not encrypted): encrypt it and reopen
     * - If the file is corrupted or not a DB: rename it aside and reopen on a fresh encrypted DB
     */
    public synchronized void openOrRepair(byte[] rawKey) throws SQLException {
        try {
            openPool(rawKey);
            return;
        } catch (SQLException e) {
            if (!isNotADB(e)) throw e;
        }

        boolean migrated = tryEncryptClearDb(url(), rawKey);
        if (!migrated) {
            try {
                Path bad = dbPath.resolveSibling(dbPath.getFileName() + ".corrupt." + System.currentTimeMillis());
                if (Files.exists(dbPath)) {
                    Files.move(dbPath, bad);
                }
            } catch (Exception ignore) {
            }
        }

        openPool(rawKey);
    }

    private static boolean isNotADB(SQLException e) {
        String m = e.getMessage();
        if (m == null) return false;
        m = m.toLowerCase();
        return m.contains("notadb") || m.contains("file is not a database");
    }

    // Convenience alias
    public Connection getConnection() throws SQLException { return connection(); }
}

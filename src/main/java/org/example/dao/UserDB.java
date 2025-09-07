package org.example.dao;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Path;
import java.nio.file.Files;
import java.sql.*;
import java.util.HexFormat;

public final class UserDB implements AutoCloseable {
    private HikariDataSource ds;
    private final Path dbPath;

    public UserDB(String filePath) {
        this.dbPath = Path.of(filePath);
    }

    // Applique PRAGMA key à CHAQUE connexion et vérifie l'accès
    public synchronized void openPool(byte[] keyBytes) throws SQLException {
        close();
        String hexKey = HexFormat.of().formatHex(keyBytes);

        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:sqlite:" + dbPath.toAbsolutePath());
        cfg.setMaximumPoolSize(1); // SQLite
        cfg.setConnectionInitSql("PRAGMA key = \"x'" + hexKey + "'\"");

        ds = new HikariDataSource(cfg);

        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("PRAGMA foreign_keys=ON");
            st.execute("PRAGMA busy_timeout=5000");
            try (ResultSet rs = st.executeQuery("SELECT count(*) FROM sqlite_master")) {
                if (!rs.next()) throw new SQLException("sqlite_master not readable");
            }
        }
    }

    // Tente avec clé -> si NOTADB, teste sans clé -> migre clair→chiffré ou isole fichier corrompu
    public synchronized void openOrRepair(byte[] keyBytes) throws SQLException {
        try { openPool(keyBytes); return; }
        catch (SQLException e) { if (!isNotADB(e)) throw e; }

        try (Connection cPlain = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
             Statement st = cPlain.createStatement()) {
            st.execute("PRAGMA foreign_keys=ON");
            st.execute("PRAGMA busy_timeout=5000");
            try (ResultSet rs = st.executeQuery("SELECT 1")) { /* OK en clair */ }
            migratePlainToEncrypted(cPlain, keyBytes);
        } catch (SQLException ex2) {
            try {
                Path bad = dbPath.resolveSibling(dbPath.getFileName() + ".corrupt." + System.currentTimeMillis());
                Files.move(dbPath, bad);
            } catch (Exception ignore) {}
        }
        openPool(keyBytes);
    }

    private static boolean isNotADB(SQLException e) {
        String m = e.getMessage();
        return m != null && (m.contains("NOTADB") || m.contains("file is not a database"));
    }

    private void migratePlainToEncrypted(Connection plainConn, byte[] keyBytes) throws SQLException {
        String hex = java.util.HexFormat.of().formatHex(keyBytes);
        Path tmp = dbPath.resolveSibling(dbPath.getFileName() + ".enc.tmp");
        try (Statement st = plainConn.createStatement()) {
            st.execute("ATTACH DATABASE '" + tmp.toAbsolutePath().toString().replace("'", "''")
                    + "' AS encrypted KEY \"x'" + hex + "'\"");
            st.execute("SELECT sqlcipher_export('encrypted')");
            st.execute("DETACH DATABASE encrypted");
        } catch (SQLException e) {
            try { Files.deleteIfExists(tmp); } catch (Exception ignore) {}
            throw e;
        }
        Path bak = dbPath.resolveSibling(dbPath.getFileName() + ".plain.bak");
        try {
            Files.deleteIfExists(bak);
            Files.move(dbPath, bak);
            Files.move(tmp, dbPath);
        } catch (Exception ioe) {
            try { Files.deleteIfExists(tmp); } catch (Exception ignore) {}
            throw new SQLException("Migration clair→chiffré échouée : " + ioe.getMessage(), ioe);
        }
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
        if (ds == null) throw new IllegalStateException("openPool must be called first");
        return ds.getConnection();
    }

    // Convenience alias
    public Connection connection() throws SQLException { return getConnection(); }

    @Override
    public void close() {
        if (ds != null) {
            try { ds.close(); } catch (Exception ignore) {}
            ds = null;
        }
    }
}

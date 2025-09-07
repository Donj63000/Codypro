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
        try { Files.createDirectories(this.dbPath.getParent()); } catch (Exception ignore) {}
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

        // Helper de détection d'en-tête SQLite clair
        // (ajouté plus bas dans cette classe)

        if (looksPlainSQLite(dbPath)) {
            try (Connection cPlain = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
                 Statement st = cPlain.createStatement()) {
                st.execute("PRAGMA foreign_keys=ON");
                st.execute("PRAGMA busy_timeout=5000");
                st.executeQuery("SELECT 1");
                migratePlainToEncrypted(cPlain, keyBytes);
            } catch (SQLException ex2) {
                try {
                    Path bad = dbPath.resolveSibling(dbPath.getFileName() + ".corrupt." + System.currentTimeMillis());
                    Files.move(dbPath, bad);
                } catch (Exception ignore) {}
            }
            openPool(keyBytes);
            return;
        }
        throw new SQLException("Base chiffrée ou illisible. Mot de passe incorrect ? Fichier: " + dbPath);
    }

    private static boolean isNotADB(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            String m = t.getMessage();
            if (m == null) continue;
            m = m.toLowerCase(java.util.Locale.ROOT);
            if (m.contains("notadb") || m.contains("file is not a database")
                    || m.contains("file is encrypted or is not a database")) return true;
        }
        return false;
    }

    // Détection d'en-tête SQLite clair
    private static boolean looksPlainSQLite(Path p) {
        try (var in = java.nio.file.Files.newInputStream(p)) {
            byte[] hdr = in.readNBytes(16);
            return new String(hdr, java.nio.charset.StandardCharsets.US_ASCII)
                    .startsWith("SQLite format 3");
        } catch (Exception e) { return false; }
    }

    private void migratePlainToEncrypted(Connection plainConn, byte[] keyBytes) throws SQLException {
        String hex = java.util.HexFormat.of().formatHex(keyBytes);
        Path tmp = dbPath.resolveSibling(dbPath.getFileName() + ".enc.tmp");
        try { Files.deleteIfExists(tmp); } catch (Exception ignore) {}
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

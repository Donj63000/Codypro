package org.example.dao;

import javax.crypto.SecretKey;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.SQLException;
import java.util.HexFormat;
import java.util.Properties;

public final class UserDB implements AutoCloseable {
    private final Connection conn;

    public UserDB(String filePath, SecretKey key) throws SQLException {
        String url = "jdbc:sqlite:" + filePath;
        String hexKey = HexFormat.of().formatHex(key.getEncoded());
        Properties props = new Properties();
        props.setProperty("key", hexKey);

        Connection c;
        try {
            // Essaye d'abord avec la clé (DB déjà chiffrée)
            c = DriverManager.getConnection(url, props);
        } catch (SQLException first) {
            // Fallback: DB non chiffrée existante -> ouvre en clair puis chiffre
            c = DriverManager.getConnection(url);
            try (Statement st = c.createStatement()) {
                st.execute("PRAGMA rekey = '" + hexKey + "'");
            } catch (SQLException rekeyEx) {
                try { c.close(); } catch (Exception ignore) {}
                throw rekeyEx;
            }
        }

        this.conn = c;
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA journal_mode = WAL");
            st.execute("PRAGMA busy_timeout = 5000");
            st.execute("PRAGMA foreign_keys = 1");
        }
    }

    public Connection connection() { return conn; }

    @Override
    public void close() {
        try { conn.close(); } catch (Exception ignored) {}
    }
}

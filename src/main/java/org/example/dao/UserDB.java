package org.example.dao;

import javax.crypto.SecretKey;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HexFormat;
import java.util.Properties;

import static org.example.dao.SqlcipherUtil.disableWalForRekey;
import static org.example.dao.SqlcipherUtil.enableWal;
import static org.example.dao.SqlcipherUtil.rekey;

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
            try {
                disableWalForRekey(c);
                rekey(c, key.getEncoded(), null);
            } catch (SQLException rekeyEx) {
                try { c.close(); } catch (Exception ignore) {}
                throw rekeyEx;
            }
        }

        this.conn = c;
        enableWal(conn);
    }

    public Connection connection() { return conn; }

    @Override
    public void close() {
        try { conn.close(); } catch (Exception ignored) {}
    }
}

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
        Properties props = new Properties();
        props.setProperty("key", HexFormat.of().formatHex(key.getEncoded()));
        conn = DriverManager.getConnection("jdbc:sqlite:" + filePath, props);
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA journal_mode = WAL");
            st.execute("PRAGMA busy_timeout = 5000");
            st.execute("PRAGMA foreign_keys = 1");
        }
    }

    public Connection connection() {
        return conn;
    }

    @Override
    public void close() {
        try {
            conn.close();
        } catch (Exception ignored) {
        }
    }
}

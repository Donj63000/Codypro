package org.example.dao;

import javax.crypto.SecretKey;
import java.sql.*;
import java.util.Properties;

public final class UserDB implements AutoCloseable {
    private final Connection c;

    public UserDB(String filePath, SecretKey key) throws SQLException {
        // SQLCipher attend la clé sous forme HEX ASCII
        String hex = javax.xml.bind.DatatypeConverter.printHexBinary(key.getEncoded());
        Properties p = new Properties();
        p.setProperty("key", hex);
        c = DriverManager.getConnection("jdbc:sqlite:" + filePath, p);
        // vous pouvez exécuter ici un CREATE TABLE IF NOT EXISTS...
    }
    public Connection connection() { return c; }
    @Override public void close() { try { c.close(); } catch (Exception ignore) {} }
}

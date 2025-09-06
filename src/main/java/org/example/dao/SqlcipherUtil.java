package org.example.dao;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class SqlcipherUtil {

    private static String hex(byte[] key) {
        return java.util.HexFormat.of().formatHex(key);
    }

    public static void disableWalForRekey(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute("PRAGMA wal_checkpoint(TRUNCATE)");
            st.execute("PRAGMA journal_mode=DELETE");
            st.execute("PRAGMA locking_mode=EXCLUSIVE");
            st.execute("PRAGMA busy_timeout=5000");
        }
    }

    public static void enableWal(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute("PRAGMA locking_mode=NORMAL");
            st.execute("PRAGMA journal_mode=WAL");
            st.execute("PRAGMA busy_timeout=5000");
            st.execute("PRAGMA foreign_keys=ON");
        }
    }

    public static void rekey(Connection c, byte[] newKey, Integer newKdfItersOrNull) throws SQLException {
        try (Statement st = c.createStatement()) {
            if (newKdfItersOrNull != null) {
                st.execute("PRAGMA kdf_iter = " + newKdfItersOrNull);
            }
            st.execute("PRAGMA rekey = \"x'" + hex(newKey) + "'\"");
        }
    }
}


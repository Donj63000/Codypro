package org.example.dao;

import org.example.security.CryptoUtils;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class UserDBTest {
    @Test
    void sqlCipherFileCannotBeOpenedWithoutKey() throws Exception {
        SecretKey key = CryptoUtils.deriveKey("pwd".toCharArray(), new byte[16], 100_000);
        Path db = Files.createTempFile("test", ".db");
        try (UserDB u1 = new UserDB(db.toString(), key)) {
            Statement st = u1.connection().createStatement();
            st.execute("CREATE TABLE t(id INTEGER)");
        }
        assertThrows(SQLException.class, () -> {
            DriverManager.getConnection("jdbc:sqlite:" + db);
        });
    }
}

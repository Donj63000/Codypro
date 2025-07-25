package org.example.security;

import org.example.dao.AuthDB;
import org.example.dao.UserDB;

import javax.crypto.SecretKey;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HexFormat;

public final class AuthService {
    private static final int    KDF_ITER_REG  = 150_000;
    private static final int    KDF_ITER_NEW  = 180_000;
    private static final int    SALT_BYTES    = 16;
    private static final SecureRandom RNG     = new SecureRandom();

    private final AuthDB store;

    public AuthService(AuthDB store) { this.store = store; }

    public void register(String username, char[] pwd) throws Exception {
        byte[] salt = RNG.generateSeed(SALT_BYTES);
        String hash = CryptoUtils.hashPwd(pwd);
        Arrays.fill(pwd, '\0');
        try (PreparedStatement ps = store.c().prepareStatement(
                "INSERT INTO users(username,pwd_hash,kdf_salt,kdf_iters) VALUES(?,?,?,?)")) {
            ps.setString(1, username);
            ps.setString(2, hash);
            ps.setBytes (3, salt);
            ps.setInt   (4, KDF_ITER_REG);
            ps.executeUpdate();
        }
    }

    public Session login(String username, char[] pwd) throws Exception {
        try (PreparedStatement ps = store.c().prepareStatement(
                "SELECT id,pwd_hash,kdf_salt,kdf_iters FROM users WHERE username=?")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                String hash = rs.getString("pwd_hash");
                if (!CryptoUtils.verifyPwd(pwd, hash)) return null;
                byte[] salt = rs.getBytes("kdf_salt");
                int    it   = rs.getInt("kdf_iters");
                SecretKey key = CryptoUtils.deriveKey(pwd, salt, it);
                Arrays.fill(pwd, '\0');
                return new Session(rs.getInt("id"), key, username);
            }
        }
    }

    public String getUsername(int userId) throws Exception {
        try (PreparedStatement ps = store.c().prepareStatement(
                "SELECT username FROM users WHERE id=?")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    public void changePassword(int userId, char[] oldPwd, char[] newPwd) throws Exception {
        Session sess = login(getUsername(userId), oldPwd);
        if (sess == null) throw new IllegalArgumentException("Mot de passe incorrect");

        byte[] newSalt = RNG.generateSeed(SALT_BYTES);
        String newHash = CryptoUtils.hashPwd(newPwd);
        SecretKey newKey = CryptoUtils.deriveKey(newPwd, newSalt, KDF_ITER_NEW);
        Arrays.fill(oldPwd, '\0');
        Arrays.fill(newPwd, '\0');

        Path db = Path.of(System.getProperty("user.home"), ".prestataires", sess.username() + ".db");
        try (UserDB udb = new UserDB(db.toString(), sess.key());
             Statement st = udb.connection().createStatement()) {
            st.execute("PRAGMA rekey = '" + HexFormat.of().formatHex(newKey.getEncoded()) + "'");
        }

        try (PreparedStatement ps = store.c().prepareStatement(
                "UPDATE users SET pwd_hash=?,kdf_salt=?,kdf_iters=? WHERE id=?")) {
            ps.setString(1, newHash);
            ps.setBytes (2, newSalt);
            ps.setInt   (3, KDF_ITER_NEW);
            ps.setInt   (4, userId);
            ps.executeUpdate();
        }
    }

    public record Session(int userId, SecretKey key, String username) {}
}

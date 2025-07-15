package org.example.security;

import org.example.dao.AuthDB;

import javax.crypto.SecretKey;
import java.sql.*;
import java.security.SecureRandom;
import java.util.Arrays;

public final class AuthService {
    private final AuthDB store;

    public AuthService(AuthDB store) { this.store = store; }

    private static final SecureRandom RNG = new SecureRandom();

    /* -------- inscription -------- */
    public void register(String username, char[] pwd) throws Exception {
        byte[] kdfSalt = RNG.generateSeed(16);
        int iterations = 150_000;
        String hash = CryptoUtils.hashPwd(pwd);

        try (PreparedStatement ps = store.c().prepareStatement("""
             INSERT INTO users(username,pwd_hash,kdf_salt,kdf_iters)
             VALUES(?,?,?,?)""")) {
            ps.setString(1, username);
            ps.setString(2, hash);
            ps.setBytes (3, kdfSalt);
            ps.setInt   (4, iterations);
            ps.executeUpdate();
        }
        Arrays.fill(pwd, '\0'); // effacement en mémoire
    }

    /* -------- connexion -------- */
    public Session login(String username, char[] pwd) throws Exception {
        try (PreparedStatement ps = store.c().prepareStatement("""
                SELECT id,pwd_hash,kdf_salt,kdf_iters
                FROM users WHERE username=?""")) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) return null;            // compte inexistant

            String hash = rs.getString("pwd_hash");
            if (!CryptoUtils.verifyPwd(pwd, hash)) return null;

            byte[] salt = rs.getBytes("kdf_salt");
            int iters   = rs.getInt  ("kdf_iters");
            SecretKey k = CryptoUtils.deriveKey(pwd, salt, iters);
            Arrays.fill(pwd, '\0');

            int uid = rs.getInt("id");
            return new Session(uid, k, username);
        }
    }

    /* -------- objet session ---------- */
    public record Session(int userId, SecretKey key, String username) {}
}

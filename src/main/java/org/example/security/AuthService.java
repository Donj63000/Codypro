package org.example.security;

import org.example.dao.AuthDB;
import org.example.dao.UserDB;

import javax.crypto.SecretKey;
import java.sql.*;
import java.security.SecureRandom;
import java.util.Arrays;
import java.nio.file.Path;
import java.util.HexFormat;

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

    /** Obtenir le nom d'utilisateur à partir de l'identifiant. */
    public String getUsername(int userId) throws SQLException {
        try (PreparedStatement ps = store.c().prepareStatement(
                "SELECT username FROM users WHERE id=?")) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getString("username") : null;
        }
    }

    /** Changer le mot de passe et rekey la base chiffrée. */
    public void changePassword(int userId,
                               char[] oldPwd, char[] newPwd) throws Exception {

        /* 1) vérifier l'ancien */
        Session sess = login(getUsername(userId), oldPwd);
        if (sess == null) throw new IllegalArgumentException("Mot de passe incorrect");

        /* 2) dériver nouvelle clé */
        byte[] newSalt = RNG.generateSeed(16);
        int newIter    = 180_000;
        String newHash = CryptoUtils.hashPwd(newPwd);
        SecretKey newKey = CryptoUtils.deriveKey(newPwd, newSalt, newIter);

        /* 3) rekey SQLCipher */
        Path dbFile = Path.of(System.getProperty("user.home"), ".prestataires",
                              sess.username() + ".db");
        try (UserDB udb = new UserDB(dbFile.toString(), sess.key())) {
            Statement st = udb.connection().createStatement();
            String hex = HexFormat.of().formatHex(newKey.getEncoded());
            st.execute("PRAGMA rekey = '" + hex + "'");
        }

        /* 4) maj table users */
        try (PreparedStatement ps = store.c().prepareStatement("""
               UPDATE users SET pwd_hash=?,kdf_salt=?,kdf_iters=?
               WHERE id=?""")) {
            ps.setString(1, newHash);
            ps.setBytes (2, newSalt);
            ps.setInt   (3, newIter);
            ps.setInt   (4, userId);
            ps.executeUpdate();
        }
    }

    /* -------- objet session ---------- */
    public record Session(int userId, SecretKey key, String username) {}
}

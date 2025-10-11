package org.example.security;

import org.example.dao.AuthDB;
import org.example.dao.UserDB;
import org.example.dao.SqlcipherUtil;
import org.example.util.AppPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

public final class AuthService {
    private static final int    KDF_ITER_REG  = 150_000;
    private static final int    KDF_ITER_NEW  = 180_000;
    private static final int    SALT_BYTES    = 16;
    private static final int    MIN_PWD_LEN   = 8;
    private static final SecureRandom RNG     = new SecureRandom();
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AuthDB store;

    public AuthService(AuthDB store) { this.store = store; }

    public static int minimumPasswordLength() {
        return MIN_PWD_LEN;
    }

    public void register(String username, char[] pwd) throws Exception {
        String user = normalizeUsername(username);
        ensurePasswordStrength(pwd);
        byte[] salt = RNG.generateSeed(SALT_BYTES);
        String hash;
        try {
            hash = CryptoUtils.hashPwd(pwd);
        } finally {
            Arrays.fill(pwd, '\0');
        }
        try (PreparedStatement ps = store.c().prepareStatement(
                "INSERT INTO users(username,pwd_hash,kdf_salt,kdf_iters) VALUES(?,?,?,?)")) {
            ps.setString(1, user);
            ps.setString(2, hash);
            ps.setBytes (3, salt);
            ps.setInt   (4, KDF_ITER_REG);
            ps.executeUpdate();
        } catch (SQLException e) {
            if (isUniqueConstraint(e)) {
                throw new IllegalArgumentException("Ce nom d'utilisateur existe déjà.", e);
            }
            throw e;
        }
        log.debug("[Auth] user registered: {}", user);
    }

    public Session login(String username, char[] pwd) throws Exception {
        String user = username == null ? "" : username.trim();
        if (user.isEmpty()) {
            Arrays.fill(pwd, '\0');
            return null;
        }
        try {
            try (PreparedStatement ps = store.c().prepareStatement(
                    "SELECT id,pwd_hash,kdf_salt,kdf_iters FROM users WHERE username=?")) {
                ps.setString(1, user);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        log.debug("[Auth] login failed (unknown user): {}", user);
                        return null;
                    }
                    String hash = rs.getString("pwd_hash");
                    if (!CryptoUtils.verifyPwd(pwd, hash)) {
                        log.debug("[Auth] login failed (bad password): {}", user);
                        return null;
                    }
                    byte[] salt = rs.getBytes("kdf_salt");
                    int    it   = rs.getInt("kdf_iters");
                    SecretKey key = CryptoUtils.deriveKey(pwd, salt, it);
                    int uid = rs.getInt("id");
                    log.debug("[Auth] login OK: {} (id={})", user, uid);
                    return new Session(uid, key, user);
                }
            }
        } finally {
            Arrays.fill(pwd, '\0');
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
        String username = getUsername(userId);
        if (username == null) throw new IllegalArgumentException("Utilisateur introuvable.");
        ensurePasswordStrength(newPwd);
        Session sess = login(username, oldPwd);
        if (sess == null) throw new IllegalArgumentException("Mot de passe incorrect");

        byte[] newSalt = RNG.generateSeed(SALT_BYTES);
        String newHash;
        SecretKey newKey;
        try {
            newHash = CryptoUtils.hashPwd(newPwd);
            newKey = CryptoUtils.deriveKey(newPwd, newSalt, KDF_ITER_NEW);
        } finally {
            Arrays.fill(newPwd, '\0');
        }

        try {
            try (UserDB udb = new UserDB(AppPaths.userDb(sess.username()).toString())) {
                udb.openPool(sess.key().getEncoded());
                Connection c = udb.connection();
                SqlcipherUtil.disableWalForRekey(c);
                SqlcipherUtil.rekey(c, newKey.getEncoded(), KDF_ITER_NEW);
                SqlcipherUtil.enableWal(c);
            }

            try (PreparedStatement ps = store.c().prepareStatement(
                    "UPDATE users SET pwd_hash=?,kdf_salt=?,kdf_iters=? WHERE id=?")) {
                ps.setString(1, newHash);
                ps.setBytes (2, newSalt);
                ps.setInt   (3, KDF_ITER_NEW);
                ps.setInt   (4, userId);
                ps.executeUpdate();
            }
            log.debug("[Auth] password changed for user id={} ({})", userId, sess.username());
        } finally {
            Arrays.fill(oldPwd, '\0');
        }
    }

    public record Session(int userId, SecretKey key, String username) {}

    public record UserSummary(int id, String username, String createdAt) {}

    public List<UserSummary> listUsers() throws Exception {
        try (PreparedStatement ps = store.c().prepareStatement(
                "SELECT id, username, COALESCE(created,'') AS created FROM users ORDER BY username");
             ResultSet rs = ps.executeQuery()) {
            List<UserSummary> users = new ArrayList<>();
            while (rs.next()) {
                users.add(new UserSummary(
                        rs.getInt("id"),
                        rs.getString("username"),
                        rs.getString("created")));
            }
            return users;
        }
    }

    public void updateUsername(int userId, String newUsername) throws Exception {
        String updated = normalizeUsername(newUsername);
        String current = getUsername(userId);
        if (current == null) throw new IllegalArgumentException("Utilisateur introuvable.");
        if (current.equalsIgnoreCase(updated)) return;

        Path oldDb = AppPaths.userDb(current);
        Path newDb = AppPaths.userDb(updated);
        Files.createDirectories(newDb.getParent());

        try (PreparedStatement ps = store.c().prepareStatement(
                "UPDATE users SET username=? WHERE id=?")) {
            ps.setString(1, updated);
            ps.setInt(2, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            if (isUniqueConstraint(e)) {
                throw new IllegalArgumentException("Ce nom d'utilisateur est déjà utilisé.");
            }
            throw e;
        }

        java.util.List<Path[]> moved = new ArrayList<>();
        try {
            moveIfExists(oldDb, newDb, moved);
            moveIfExists(Path.of(oldDb.toString() + "-wal"), Path.of(newDb.toString() + "-wal"), moved);
            moveIfExists(Path.of(oldDb.toString() + "-shm"), Path.of(newDb.toString() + "-shm"), moved);
        } catch (RuntimeException ex) {
            rollbackMoves(moved);
            revertUsername(userId, current);
            throw new IllegalStateException("Impossible de renommer les fichiers chiffrés.", ex);
        }

        log.debug("[Auth] username changed from {} to {}", current, updated);
    }

    public void deleteUser(int userId, boolean forbidLast) throws Exception {
        String username = getUsername(userId);
        if (username == null) throw new IllegalArgumentException("Utilisateur introuvable.");

        if (forbidLast) {
            try (PreparedStatement ps = store.c().prepareStatement("SELECT COUNT(*) FROM users");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getInt(1) <= 1) {
                    throw new IllegalStateException("Impossible de supprimer le dernier compte.");
                }
            }
        }

        try (PreparedStatement ps = store.c().prepareStatement("DELETE FROM users WHERE id=?")) {
            ps.setInt(1, userId);
            ps.executeUpdate();
        }

        deleteIfExists(AppPaths.userDb(username));
        deleteIfExists(Path.of(AppPaths.userDb(username).toString() + "-wal"));
        deleteIfExists(Path.of(AppPaths.userDb(username).toString() + "-shm"));

        log.debug("[Auth] user deleted: {}", username);
    }

    private static void moveIfExists(Path from, Path to, List<Path[]> moved) {
        try {
            if (Files.exists(from)) {
                Files.createDirectories(to.getParent());
                Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
                moved.add(new Path[]{to, from});
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private static void rollbackMoves(List<Path[]> moved) {
        for (int i = moved.size() - 1; i >= 0; i--) {
            Path[] pair = moved.get(i);
            try {
                Files.createDirectories(pair[1].getParent());
                Files.move(pair[0], pair[1], StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception ignore) {
            }
        }
    }

    private void revertUsername(int userId, String username) {
        try (PreparedStatement ps = store.c().prepareStatement(
                "UPDATE users SET username=? WHERE id=?")) {
            ps.setString(1, username);
            ps.setInt(2, userId);
            ps.executeUpdate();
        } catch (Exception ex) {
            log.error("[Auth] revert username failed for {} -> {}: {}", userId, username, ex.getMessage());
        }
    }

    private static void deleteIfExists(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (Exception ignore) {
        }
    }

    private static boolean isUniqueConstraint(SQLException e) {
        String msg = e.getMessage();
        if (msg == null) return false;
        String lower = msg.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("unique");
    }

    private static String normalizeUsername(String username) {
        if (username == null) throw new IllegalArgumentException("Nom d'utilisateur manquant.");
        String trimmed = username.trim();
        if (trimmed.isEmpty()) throw new IllegalArgumentException("Le nom d'utilisateur ne peut pas être vide.");
        return trimmed;
    }

    private static void ensurePasswordStrength(char[] pwd) {
        if (pwd == null || pwd.length < MIN_PWD_LEN) {
            throw new IllegalArgumentException("Le mot de passe doit contenir au moins " + MIN_PWD_LEN + " caractères.");
        }
        boolean hasNonWhitespace = false;
        for (char c : pwd) {
            if (!Character.isWhitespace(c)) {
                hasNonWhitespace = true;
                break;
            }
        }
        if (!hasNonWhitespace) {
            throw new IllegalArgumentException("Le mot de passe ne peut pas être composé uniquement d'espaces.");
        }
    }
}

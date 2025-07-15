package org.example.security;

import de.mkammerer.argon2.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.security.*;

public final class CryptoUtils {

    /* ---------------- mot de passe ---------------- */
    private static final Argon2 ARGON = Argon2Factory.create(
            Argon2Factory.Argon2Types.ARGON2id, 32, 64);

    public static String hashPwd(char[] pwd) {
        // cost : 1 GiB/sec ≃ ~0.5 s sur CPU 2025
        return ARGON.hash(3, 1 << 16, 2, pwd);
    }

    public static boolean verifyPwd(char[] pwd, String hash) {
        return ARGON.verify(hash, pwd);
    }

    /* ---------------- dérivation de clé AES 256 ---------------- */
    public static SecretKey deriveKey(char[] pwd, byte[] salt, int iterations)
            throws GeneralSecurityException {

        PBEKeySpec spec = new PBEKeySpec(pwd, salt, iterations, 256);
        SecretKeyFactory kf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] raw = kf.generateSecret(spec).getEncoded();
        return new SecretKeySpec(raw, "AES");
    }

    /* ---------------- AES‑GCM ---------------- */
    public record CipherBlob(byte[] iv, byte[] ciphertext) {}

    public static CipherBlob encrypt(byte[] plain, SecretKey key)
            throws GeneralSecurityException {

        byte[] iv = SecureRandom.getInstanceStrong().generateSeed(12);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
        byte[] ct = c.doFinal(plain);
        return new CipherBlob(iv, ct);
    }

    public static byte[] decrypt(CipherBlob blob, SecretKey key)
            throws GeneralSecurityException {

        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, blob.iv));
        return c.doFinal(blob.ciphertext);
    }

    /* ---------------- helpers BLOB/byte[]<->String ---------------- */
    public static String blobToBase64(CipherBlob b) {
        ByteBuffer buf = ByteBuffer.allocate(12 + b.ciphertext.length)
                                   .put(b.iv).put(b.ciphertext);
        return java.util.Base64.getEncoder().encodeToString(buf.array());
    }
    public static CipherBlob base64ToBlob(String s) {
        byte[] all = java.util.Base64.getDecoder().decode(s);
        byte[] iv = java.util.Arrays.copyOfRange(all, 0, 12);
        byte[] ct = java.util.Arrays.copyOfRange(all, 12, all.length);
        return new CipherBlob(iv, ct);
    }
    private CryptoUtils() {}
}

package org.example.security;

import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

public final class CryptoUtils {

    private static final Argon2 ARGON = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id, 32, 64);
    private static final SecureRandom RNG = new SecureRandom();
    private static final int GCM_TAG_BITS = 128;
    private static final int GCM_IV_BYTES = 12;

    public static String hashPwd(char[] pwd) {
        return ARGON.hash(3, 1 << 16, 2, pwd);
    }

    public static boolean verifyPwd(char[] pwd, String hash) {
        return ARGON.verify(hash, pwd);
    }

    public static SecretKey deriveKey(char[] pwd, byte[] salt, int iterations) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(pwd, salt, iterations, 256);
        SecretKeyFactory kf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        return new SecretKeySpec(kf.generateSecret(spec).getEncoded(), "AES");
    }

    public record CipherBlob(byte[] iv, byte[] ciphertext) {}

    public static CipherBlob encrypt(byte[] plain, SecretKey key) throws GeneralSecurityException {
        byte[] iv = new byte[GCM_IV_BYTES];
        RNG.nextBytes(iv);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
        return new CipherBlob(iv, c.doFinal(plain));
    }

    public static byte[] decrypt(CipherBlob blob, SecretKey key) throws GeneralSecurityException {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, blob.iv()));
        return c.doFinal(blob.ciphertext());
    }

    public static String blobToBase64(CipherBlob b) {
        ByteBuffer buf = ByteBuffer.allocate(GCM_IV_BYTES + b.ciphertext().length)
                .put(b.iv())
                .put(b.ciphertext());
        return Base64.getEncoder().encodeToString(buf.array());
    }

    public static CipherBlob base64ToBlob(String s) {
        byte[] all = Base64.getDecoder().decode(s);
        byte[] iv = new byte[GCM_IV_BYTES];
        byte[] ct = new byte[all.length - GCM_IV_BYTES];
        System.arraycopy(all, 0, iv, 0, GCM_IV_BYTES);
        System.arraycopy(all, GCM_IV_BYTES, ct, 0, ct.length);
        return new CipherBlob(iv, ct);
    }

    private CryptoUtils() {}
}

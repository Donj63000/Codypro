package org.example.util;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

public class TokenCrypto {
    private static final byte[] KEY;
    private static final String ALGO = "AES/GCM/NoPadding";

    static {
        String env = System.getenv("TOKEN_KEY");
        byte[] k = env == null ? null : env.getBytes(StandardCharsets.UTF_8);
        if (k == null || k.length == 0) {
            k = "ChangeThisKey123".getBytes(StandardCharsets.UTF_8);
        }
        if (k.length < 16) {
            byte[] t = new byte[16];
            System.arraycopy(k, 0, t, 0, Math.min(k.length, 16));
            k = t;
        } else if (k.length > 16) {
            byte[] t = new byte[16];
            System.arraycopy(k, 0, t, 0, 16);
            k = t;
        }
        KEY = k;
    }

    public static String encrypt(String plain) {
        if (plain == null || plain.isEmpty()) return plain;
        try {
            Cipher c = Cipher.getInstance(ALGO);
            byte[] iv = new byte[12];
            new SecureRandom().nextBytes(iv);
            GCMParameterSpec spec = new GCMParameterSpec(128, iv);
            SecretKey key = new SecretKeySpec(KEY, "AES");
            c.init(Cipher.ENCRYPT_MODE, key, spec);
            byte[] enc = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            ByteBuffer bb = ByteBuffer.allocate(iv.length + enc.length);
            bb.put(iv);
            bb.put(enc);
            return Base64.getEncoder().encodeToString(bb.array());
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    public static String decrypt(String encrypted) {
        if (encrypted == null || encrypted.isEmpty()) return encrypted;
        try {
            byte[] all = Base64.getDecoder().decode(encrypted);
            byte[] iv = new byte[12];
            byte[] data = new byte[all.length - 12];
            System.arraycopy(all, 0, iv, 0, 12);
            System.arraycopy(all, 12, data, 0, data.length);
            Cipher c = Cipher.getInstance(ALGO);
            GCMParameterSpec spec = new GCMParameterSpec(128, iv);
            SecretKey key = new SecretKeySpec(KEY, "AES");
            c.init(Cipher.DECRYPT_MODE, key, spec);
            byte[] dec = c.doFinal(data);
            return new String(dec, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }
}

package org.example.util;

import org.example.security.CryptoUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

public final class TokenCrypto {

    private TokenCrypto() {}

    public static String encrypt(String plain, SecretKey key) {
        if (plain == null || plain.isBlank()) return "";
        try {
            return CryptoUtils.blobToBase64(
                    CryptoUtils.encrypt(plain.getBytes(StandardCharsets.UTF_8), key));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String decrypt(String enc, SecretKey key) {
        if (enc == null || enc.isBlank()) return "";
        try {
            return new String(
                    CryptoUtils.decrypt(CryptoUtils.base64ToBlob(enc), key),
                    StandardCharsets.UTF_8);
        } catch (Exception e) {
            // Fallback: value might be stored in plain text; return as-is
            return enc;
        }
    }
}

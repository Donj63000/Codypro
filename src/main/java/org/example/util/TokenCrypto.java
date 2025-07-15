package org.example.util;

import org.example.security.CryptoUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

public class TokenCrypto {
    private TokenCrypto() {}

    public static String encrypt(String plain, SecretKey key) {
        if (plain == null || plain.isBlank()) return "";
        try {
            var blob = CryptoUtils.encrypt(plain.getBytes(StandardCharsets.UTF_8), key);
            return CryptoUtils.blobToBase64(blob);
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    public static String decrypt(String enc, SecretKey key) {
        if (enc == null || enc.isBlank()) return "";
        try {
            var blob = CryptoUtils.base64ToBlob(enc);
            return new String(CryptoUtils.decrypt(blob, key), StandardCharsets.UTF_8);
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}

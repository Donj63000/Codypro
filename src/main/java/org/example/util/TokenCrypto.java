package org.example.util;

import org.example.security.CryptoUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class TokenCrypto {

    private static final Logger log = LoggerFactory.getLogger(TokenCrypto.class);

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
            if (looksLikeBase64(enc)) {
                log.warn("[TokenCrypto] Unable to decrypt value that appears to be encrypted. Returning placeholder.", e);
                return "";
            }
            log.warn("[TokenCrypto] Returning legacy clear-text token; will be migrated on next save. Cause: {}", e.getMessage());
            return enc;
        }
    }

    private static boolean looksLikeBase64(String value) {
        if (value.length() % 4 != 0) {
            return false;
        }
        try {
            Base64.getDecoder().decode(value);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}

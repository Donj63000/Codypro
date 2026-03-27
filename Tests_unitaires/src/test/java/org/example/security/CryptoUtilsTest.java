package org.example.security;

import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.security.GeneralSecurityException;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class CryptoUtilsTest {

    @Test
    void hashAndVerifyAcceptsCorrectPassword() {
        char[] password = "StrongPass123".toCharArray();
        String hash = CryptoUtils.hashPwd(password);

        assertTrue(CryptoUtils.verifyPwd("StrongPass123".toCharArray(), hash));
    }

    @Test
    void hashAndVerifyRejectsWrongPassword() {
        String hash = CryptoUtils.hashPwd("StrongPass123".toCharArray());

        assertFalse(CryptoUtils.verifyPwd("WrongPass456".toCharArray(), hash));
    }

    @Test
    void deriveKeyIsDeterministicForSameInputs() throws Exception {
        byte[] salt = new byte[16];
        Arrays.fill(salt, (byte) 3);

        SecretKey first = CryptoUtils.deriveKey("StrongPass123".toCharArray(), salt, 12_000);
        SecretKey second = CryptoUtils.deriveKey("StrongPass123".toCharArray(), salt, 12_000);

        assertArrayEquals(first.getEncoded(), second.getEncoded());
    }

    @Test
    void deriveKeyChangesWhenSaltChanges() throws Exception {
        byte[] saltA = new byte[16];
        byte[] saltB = new byte[16];
        Arrays.fill(saltA, (byte) 3);
        Arrays.fill(saltB, (byte) 9);

        SecretKey first = CryptoUtils.deriveKey("StrongPass123".toCharArray(), saltA, 12_000);
        SecretKey second = CryptoUtils.deriveKey("StrongPass123".toCharArray(), saltB, 12_000);

        assertFalse(Arrays.equals(first.getEncoded(), second.getEncoded()));
    }

    @Test
    void encryptThenDecryptReturnsOriginalPayload() throws Exception {
        SecretKey key = deriveTestKey((byte) 1);
        byte[] payload = "payload-secret".getBytes();

        CryptoUtils.CipherBlob blob = CryptoUtils.encrypt(payload, key);
        byte[] plain = CryptoUtils.decrypt(blob, key);

        assertArrayEquals(payload, plain);
    }

    @Test
    void blobBase64RoundTripKeepsIvAndCiphertext() throws Exception {
        SecretKey key = deriveTestKey((byte) 2);
        byte[] payload = "another-payload".getBytes();

        CryptoUtils.CipherBlob blob = CryptoUtils.encrypt(payload, key);
        String encoded = CryptoUtils.blobToBase64(blob);
        CryptoUtils.CipherBlob decoded = CryptoUtils.base64ToBlob(encoded);

        assertArrayEquals(blob.iv(), decoded.iv());
        assertArrayEquals(blob.ciphertext(), decoded.ciphertext());
    }

    @Test
    void decryptFailsWithWrongKey() throws Exception {
        SecretKey keyA = deriveTestKey((byte) 3);
        SecretKey keyB = deriveTestKey((byte) 4);
        CryptoUtils.CipherBlob blob = CryptoUtils.encrypt("secret".getBytes(), keyA);

        assertThrows(GeneralSecurityException.class, () -> CryptoUtils.decrypt(blob, keyB));
    }

    @Test
    void decryptFailsWhenCiphertextIsTampered() throws Exception {
        SecretKey key = deriveTestKey((byte) 5);
        CryptoUtils.CipherBlob blob = CryptoUtils.encrypt("secret".getBytes(), key);
        byte[] tampered = blob.ciphertext().clone();
        tampered[0] ^= 0x01;

        CryptoUtils.CipherBlob broken = new CryptoUtils.CipherBlob(blob.iv(), tampered);
        assertThrows(GeneralSecurityException.class, () -> CryptoUtils.decrypt(broken, key));
    }

    @Test
    void base64ToBlobRejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> CryptoUtils.base64ToBlob("@@not-base64@@"));
    }

    @Test
    void encryptUsesRandomIvAcrossCalls() throws Exception {
        SecretKey key = deriveTestKey((byte) 6);
        byte[] payload = "same-message".getBytes();

        CryptoUtils.CipherBlob first = CryptoUtils.encrypt(payload, key);
        CryptoUtils.CipherBlob second = CryptoUtils.encrypt(payload, key);

        assertFalse(Arrays.equals(first.iv(), second.iv()));
    }

    private static SecretKey deriveTestKey(byte marker) throws Exception {
        byte[] salt = new byte[16];
        Arrays.fill(salt, marker);
        return CryptoUtils.deriveKey("StrongPass123".toCharArray(), salt, 10_000);
    }
}

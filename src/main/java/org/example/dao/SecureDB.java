package org.example.dao;

import org.example.model.ServiceRow;
import org.example.security.CryptoUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class SecureDB extends DB {
    private final int userId;
    private final SecretKey key;

    public SecureDB(ConnectionProvider provider, int userId, SecretKey key) {
        super(provider);
        this.userId = userId;
        this.key = key;
    }

    @Override
    public void addService(int prestataireId, String plainDescription) {
        try {
            var blob = CryptoUtils.encrypt(plainDescription.getBytes(StandardCharsets.UTF_8), key);
            super.addService(prestataireId, CryptoUtils.blobToBase64(blob));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<ServiceRow> services(int prestataireId) {
        List<ServiceRow> encrypted = super.services(prestataireId);
        List<ServiceRow> decrypted = new ArrayList<>(encrypted.size());
        for (ServiceRow row : encrypted) {
            try {
                var blob = CryptoUtils.base64ToBlob(row.desc());
                String plain = new String(CryptoUtils.decrypt(blob, key), StandardCharsets.UTF_8);
                decrypted.add(new ServiceRow(plain, row.date()));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return decrypted;
    }

    public int userId() {
        return userId;
    }
}

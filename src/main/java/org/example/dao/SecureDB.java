package org.example.dao;

import org.example.model.ServiceRow;
import org.example.security.CryptoUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
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
            super.addService(prestataireId, encryptDescription(plainDescription));
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int insertService(int prestataireId, ServiceRow service) {
        try {
            return super.insertService(prestataireId, encryptRow(service));
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void updateService(ServiceRow service) {
        try {
            super.updateService(encryptRow(service));
        } catch (GeneralSecurityException e) {
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
                decrypted.add(new ServiceRow(row.id(), plain, row.date(), row.status()));
            } catch (IllegalArgumentException ex) {
                String plain = row.desc();
                decrypted.add(new ServiceRow(row.id(), plain, row.date(), row.status()));
                upgradeLegacyRow(row, plain);
            } catch (GeneralSecurityException e) {
                throw new RuntimeException(e);
            }
        }
        return decrypted;
    }

    private void upgradeLegacyRow(ServiceRow row, String plain) {
        if (row.id() == null) {
            return;
        }
        try {
            ServiceRow reencrypted = encryptRow(new ServiceRow(row.id(), plain, row.date(), row.status()));
            super.updateService(reencrypted);
        } catch (GeneralSecurityException | RuntimeException ignore) {
            // best-effort upgrade; ignore failures so the caller still sees the plaintext value
        }
    }

    private String encryptDescription(String plainDescription) throws GeneralSecurityException {
        var blob = CryptoUtils.encrypt(plainDescription.getBytes(StandardCharsets.UTF_8), key);
        return CryptoUtils.blobToBase64(blob);
    }

    private ServiceRow encryptRow(ServiceRow service) throws GeneralSecurityException {
        return new ServiceRow(service.id(), encryptDescription(service.desc()), service.date(), service.status());
    }

    public int userId() {
        return userId;
    }
}

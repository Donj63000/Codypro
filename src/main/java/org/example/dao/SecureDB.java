package org.example.dao;

import org.example.security.CryptoUtils;
import org.example.model.ServiceRow;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class SecureDB extends DB {
    private final int userId;
    private final SecretKey key;

    public SecureDB(ConnectionProvider cp, int userId, SecretKey key) {
        super(cp);            // réutilise toutes vos méthodes
        this.userId = userId;
        this.key = key;
    }

    /* Exemple : surcharge addService → chiffrer ‘description’ */
    @Override
    public void addService(int pid, String descPlain) {
        try {
            var blob = CryptoUtils.encrypt(descPlain.getBytes(StandardCharsets.UTF_8), key);
            String base64 = CryptoUtils.blobToBase64(blob);
            super.addService(pid, base64); // appelez ensuite la méthode d'origine avec base64
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    /* inverse au moment de la lecture (services()) */
    @Override
    public List<ServiceRow> services(int pid) {
        List<ServiceRow> enc = super.services(pid);
        List<ServiceRow> out = new ArrayList<>();
        for (ServiceRow sr : enc) {
            try {
                var blob = CryptoUtils.base64ToBlob(sr.desc());
                String plain = new String(CryptoUtils.decrypt(blob, key), StandardCharsets.UTF_8);
                out.add(new ServiceRow(plain, sr.date()));
            } catch (Exception e) { throw new RuntimeException(e); }
        }
        return out;
    }
}

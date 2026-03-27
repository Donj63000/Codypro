package org.example.dao;

import org.example.model.Prestataire;
import org.example.model.ServiceRow;
import org.example.model.ServiceStatus;
import org.example.security.CryptoUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.crypto.SecretKey;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SecureDBEncryptionTest {

    @TempDir
    Path tempDir;

    private Path dbFile;
    private SecureDB secureDb;

    @BeforeEach
    void setUp() throws Exception {
        dbFile = tempDir.resolve("secure.sqlite");
        secureDb = new SecureDB(() -> DB.newConnection(dbFile.toString()), 7, testKey());
    }

    @AfterEach
    void tearDown() {
        if (secureDb != null) {
            secureDb.close();
        }
    }

    @Test
    void insertServiceStoresCiphertextInDatabase() throws Exception {
        int prestataireId = secureDb.insertPrestataire(samplePrestataire("Alpha"));

        int serviceId = secureDb.insertService(prestataireId,
                new ServiceRow("Top secret", "01/01/2025", ServiceStatus.EN_ATTENTE));

        String stored = rawServiceDescription(serviceId);
        assertNotNull(stored);
        assertNotEquals("Top secret", stored);
        assertTrue(stored.length() > "Top secret".length());
        assertDoesNotThrow(() -> CryptoUtils.base64ToBlob(stored));
    }

    @Test
    void servicesReturnsDecryptedDescription() {
        int prestataireId = secureDb.insertPrestataire(samplePrestataire("Alpha"));
        secureDb.insertService(prestataireId,
                new ServiceRow("Audit confidentiel", "01/01/2025", ServiceStatus.EN_ATTENTE));

        List<ServiceRow> rows = secureDb.services(prestataireId);

        assertEquals(1, rows.size());
        assertEquals("Audit confidentiel", rows.get(0).desc());
    }

    @Test
    void updateServiceStoresEncryptedPayloadAndReturnsPlainValue() throws Exception {
        int prestataireId = secureDb.insertPrestataire(samplePrestataire("Alpha"));
        int serviceId = secureDb.insertService(prestataireId,
                new ServiceRow("Avant", "01/01/2025", ServiceStatus.EN_ATTENTE));

        secureDb.updateService(new ServiceRow(serviceId, "Apres", "02/01/2025", ServiceStatus.TERMINE));

        String stored = rawServiceDescription(serviceId);
        assertNotEquals("Apres", stored);

        ServiceRow loaded = secureDb.services(prestataireId).get(0);
        assertEquals("Apres", loaded.desc());
        assertEquals(ServiceStatus.TERMINE, loaded.status());
    }

    @Test
    void servicesMigratesLegacyPlaintextRowsToEncryptedStorage() throws Exception {
        int prestataireId = secureDb.insertPrestataire(samplePrestataire("Alpha"));
        int serviceId;
        String legacy = "Legacy plaintext";

        try (Connection c = DB.newConnection(dbFile.toString());
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO services(prestataire_id, description, date, date_ts, status) VALUES(?,?,?,?,?)",
                     PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, prestataireId);
            ps.setString(2, legacy);
            ps.setString(3, "01/01/2025");
            ps.setLong(4, LocalDate.of(2025, 1, 1).atStartOfDay().toEpochSecond(ZoneOffset.UTC));
            ps.setString(5, ServiceStatus.EN_ATTENTE.name());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                assertTrue(rs.next());
                serviceId = rs.getInt(1);
            }
        }

        ServiceRow row = secureDb.services(prestataireId).stream()
                .filter(r -> r.id() == serviceId)
                .findFirst()
                .orElseThrow();
        assertEquals(legacy, row.desc());

        String storedAfterMigration = rawServiceDescription(serviceId);
        assertNotEquals(legacy, storedAfterMigration);
        assertDoesNotThrow(() -> CryptoUtils.base64ToBlob(storedAfterMigration));
    }

    private String rawServiceDescription(int serviceId) throws Exception {
        try (Connection c = DB.newConnection(dbFile.toString());
             PreparedStatement ps = c.prepareStatement("SELECT description FROM services WHERE id=?")) {
            ps.setInt(1, serviceId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getString(1);
            }
        }
    }

    private static Prestataire samplePrestataire(String name) {
        return new Prestataire(0, name, "Societe", "0102030405", "mail@example.com", 60, "Conseil", "Notes", "01/01/2024");
    }

    private static SecretKey testKey() throws Exception {
        byte[] salt = new byte[16];
        Arrays.fill(salt, (byte) 7);
        return CryptoUtils.deriveKey("StrongPass123".toCharArray(), salt, 10_000);
    }
}

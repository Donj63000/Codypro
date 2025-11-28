package org.example.dao;

import org.example.model.Prestataire;
import org.example.model.ServiceRow;
import org.example.model.ServiceStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DBServiceStatusTest {

    private Path dbFile;
    private DB dao;

    @BeforeEach
    void setUp() throws Exception {
        dbFile = Files.createTempFile("prestataires-tests", ".db");
        dao = new DB(dbFile.toString());
    }

    @AfterEach
    void tearDown() throws Exception {
        if (dao != null) {
            dao.close();
        }
        if (dbFile != null) {
            Files.deleteIfExists(dbFile);
        }
    }

    @Test
    @DisplayName("Services persist and propagate their status")
    void servicesPersistStatus() {
        int prestataireId = insertSamplePrestataire();

        int serviceId = dao.insertService(prestataireId,
                new ServiceRow(null, "Audit", "01/04/2024", ServiceStatus.EN_COURS));

        List<ServiceRow> rows = dao.services(prestataireId);
        assertEquals(1, rows.size());
        ServiceRow row = rows.get(0);
        assertEquals(ServiceStatus.EN_COURS, row.status());
        assertEquals(serviceId, row.id());

        dao.updateServiceStatus(serviceId, ServiceStatus.TERMINE);
        ServiceRow updated = dao.services(prestataireId).get(0);
        assertEquals(ServiceStatus.TERMINE, updated.status());

        dao.updateService(new ServiceRow(serviceId, "Audit annuel", "02/04/2024", ServiceStatus.EN_ATTENTE));
        ServiceRow edited = dao.services(prestataireId).get(0);
        assertEquals("Audit annuel", edited.desc());
        assertEquals(ServiceStatus.EN_ATTENTE, edited.status());
    }

    @Test
    @DisplayName("Unknown or legacy textual statuses fallback to EN_ATTENTE")
    void legacyStatusFallback() throws Exception {
        int prestataireId = insertSamplePrestataire();
        int serviceId = dao.insertService(prestataireId,
                new ServiceRow(null, "Migration", "05/04/2024", ServiceStatus.EN_ATTENTE));

        try (Connection c = dao.getConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE services SET status=? WHERE id=?")) {
            ps.setString(1, "Termin\u00E9");
            ps.setInt(2, serviceId);
            ps.executeUpdate();
        }

        ServiceRow row = dao.services(prestataireId).get(0);
        assertEquals(ServiceStatus.TERMINE, row.status());

        try (Connection c = dao.getConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE services SET status=? WHERE id=?")) {
            ps.setString(1, "obsolete");
            ps.setInt(2, serviceId);
            ps.executeUpdate();
        }

        ServiceRow fallbackRow = dao.services(prestataireId).get(0);
        assertEquals(ServiceStatus.EN_ATTENTE, fallbackRow.status());
    }

    private int insertSamplePrestataire() {
        Prestataire p = new Prestataire(0,
                "Test", "Societe", "0000", "test@example.com", 50,
                "Consulting", "Notes", "01/01/2024");
        int id = dao.insertPrestataire(p);
        p.idProperty().set(id);
        return id;
    }
}
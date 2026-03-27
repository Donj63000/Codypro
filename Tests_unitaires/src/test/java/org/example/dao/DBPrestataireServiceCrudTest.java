package org.example.dao;

import org.example.model.Facture;
import org.example.model.Prestataire;
import org.example.model.ServiceRow;
import org.example.model.ServiceStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DBPrestataireServiceCrudTest {

    @TempDir
    Path tempDir;

    private DB db;

    @BeforeEach
    void setUp() {
        db = new DB(tempDir.resolve("prestataires.sqlite").toString());
    }

    @AfterEach
    void tearDown() {
        if (db != null) {
            db.close();
        }
    }

    @Test
    void insertPrestataireAndFindById() {
        Prestataire input = prestataire("Alpha", "Alpha Corp", "0102030405", "alpha@example.com");

        int id = db.insertPrestataire(input);
        Prestataire found = db.findPrestataire(id);

        assertNotNull(found);
        assertEquals("Alpha", found.getNom());
        assertEquals("Alpha Corp", found.getSociete());
        assertEquals("0102030405", found.getTelephone());
        assertEquals("alpha@example.com", found.getEmail());
    }

    @Test
    void updatePrestatairePersistsEditedFields() {
        int id = db.insertPrestataire(prestataire("Alpha", "Alpha Corp", "0102030405", "alpha@example.com"));

        Prestataire updated = new Prestataire(id,
                "Alpha Updated",
                "Updated Corp",
                "0607080910",
                "updated@example.com",
                95,
                "Maintenance",
                "Notes mises a jour",
                "01/02/2025");

        db.updatePrestataire(updated);

        Prestataire found = db.findPrestataire(id);
        assertEquals("Alpha Updated", found.getNom());
        assertEquals("Updated Corp", found.getSociete());
        assertEquals("0607080910", found.getTelephone());
        assertEquals("updated@example.com", found.getEmail());
        assertEquals(95, found.getNote());
    }

    @Test
    void insertPrestataireRejectsDuplicateName() {
        db.insertPrestataire(prestataire("Alpha", "A", "0102030405", "a@example.com"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> db.insertPrestataire(prestataire("alpha", "B", "0607080910", "b@example.com")));

        assertTrue(ex.getMessage().contains("existe"));
    }

    @Test
    void listFilterMatchesName() {
        db.insertPrestataire(prestataire("Alpha", "A", "0102030405", "a@example.com"));
        db.insertPrestataire(prestataire("Beta", "B", "0607080910", "b@example.com"));

        List<Prestataire> filtered = db.list("alp");

        assertEquals(1, filtered.size());
        assertEquals("Alpha", filtered.get(0).getNom());
    }

    @Test
    void listFilterMatchesSociete() {
        db.insertPrestataire(prestataire("Alpha", "Cabinet Atlas", "0102030405", "a@example.com"));
        db.insertPrestataire(prestataire("Beta", "Studio Beta", "0607080910", "b@example.com"));

        List<Prestataire> filtered = db.list("atlas");

        assertEquals(1, filtered.size());
        assertEquals("Alpha", filtered.get(0).getNom());
    }

    @Test
    void listFilterMatchesEmail() {
        db.insertPrestataire(prestataire("Alpha", "A", "0102030405", "contact.alpha@example.com"));
        db.insertPrestataire(prestataire("Beta", "B", "0607080910", "contact.beta@example.com"));

        List<Prestataire> filtered = db.list("beta@");

        assertEquals(1, filtered.size());
        assertEquals("Beta", filtered.get(0).getNom());
    }

    @Test
    void listFilterMatchesTelephone() {
        db.insertPrestataire(prestataire("Alpha", "A", "0102030405", "a@example.com"));
        db.insertPrestataire(prestataire("Beta", "B", "0607080910", "b@example.com"));

        List<Prestataire> filtered = db.list("0607");

        assertEquals(1, filtered.size());
        assertEquals("Beta", filtered.get(0).getNom());
    }

    @Test
    void deletePrestataireCascadesServices() {
        int prestataireId = db.insertPrestataire(prestataire("Alpha", "A", "0102030405", "a@example.com"));
        db.insertService(prestataireId, new ServiceRow("Audit", "01/01/2025", ServiceStatus.EN_ATTENTE));

        db.deletePrestataire(prestataireId);

        assertTrue(db.services(prestataireId).isEmpty());
    }

    @Test
    void deletePrestataireCascadesFactures() {
        int prestataireId = db.insertPrestataire(prestataire("Alpha", "A", "0102030405", "a@example.com"));
        db.insertFacture(prestataireId, facture(prestataireId, "Janvier", LocalDate.now().plusDays(3), false));

        db.deletePrestataire(prestataireId);

        assertTrue(db.factures(prestataireId, null).isEmpty());
    }

    @Test
    void serviceCrudAndStatusWorkflow() {
        int prestataireId = db.insertPrestataire(prestataire("Alpha", "A", "0102030405", "a@example.com"));
        int serviceId = db.insertService(prestataireId,
                new ServiceRow("Audit initial", "01/01/2025", ServiceStatus.EN_ATTENTE));

        db.updateServiceStatus(serviceId, ServiceStatus.EN_COURS);
        ServiceRow afterStatus = db.services(prestataireId).get(0);
        assertEquals(ServiceStatus.EN_COURS, afterStatus.status());

        db.updateService(new ServiceRow(serviceId, "Audit final", "02/01/2025", ServiceStatus.TERMINE));
        ServiceRow updated = db.services(prestataireId).get(0);
        assertEquals("Audit final", updated.desc());
        assertEquals(ServiceStatus.TERMINE, updated.status());

        db.deleteService(serviceId);
        assertTrue(db.services(prestataireId).isEmpty());
    }

    private static Prestataire prestataire(String nom, String societe, String telephone, String email) {
        return new Prestataire(0, nom, societe, telephone, email, 80, "Conseil", "Notes", "01/01/2024");
    }

    private static Facture facture(int prestataireId, String description, LocalDate dueDate, boolean payed) {
        return new Facture(
                0,
                prestataireId,
                description,
                dueDate,
                new BigDecimal("100.00"),
                new BigDecimal("20.0"),
                null,
                null,
                payed,
                payed ? dueDate : null,
                false
        );
    }
}

package org.example.dao;

import org.example.model.Prestataire;
import org.example.model.ServiceRow;
import org.example.model.Facture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DBTest {
    private static final DateTimeFormatter DATE_FR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private DB db;

    @BeforeEach
    void setUp() {
        db = new DB("file:memdb1?mode=memory&cache=shared");
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    private Prestataire createSample() {
        String date = LocalDate.now().format(DATE_FR);
        return new Prestataire(0, "Nom", "Societe", "0123456789", "a@b.com", 60, "Fact", date);
    }

    @Test
    void testCRUD() {
        Prestataire p = createSample();
        db.add(p);

        List<Prestataire> all = db.list("");
        assertEquals(1, all.size());
        Prestataire stored = all.get(0);
        assertEquals(p.getNom(), stored.getNom());
        int id = stored.getId();

        stored.setNom("Updated");
        db.update(stored);

        all = db.list("");
        assertEquals("Updated", all.get(0).getNom());

        db.addService(id, "Service");
        List<ServiceRow> services = db.services(id);
        assertEquals(1, services.size());
        assertEquals("Service", services.get(0).desc());

        db.delete(id);
        assertTrue(db.list("").isEmpty());
        assertTrue(db.services(id).isEmpty());
    }

    @Test
    void testAncienFormatCompatible() {
        Prestataire p = new Prestataire(0, "Nom", "S", "0", "a@b.com", 50, "F", "23/09/2025");
        db.add(p);
        assertEquals("23/09/2025", db.list("").get(0).getDateContrat());
    }

    @Test
    void testFactures() {
        Prestataire p = createSample();
        db.add(p);
        int pid = db.list("").get(0).getId();

        Facture f = new Facture(0, pid, "Test", LocalDate.now(), 100.0, false, null, false);
        db.addFacture(f);

        List<Facture> all = db.factures(pid, null);
        assertEquals(1, all.size());
        Facture stored = all.get(0);
        assertFalse(stored.isPaye());

        db.setFacturePayee(stored.getId(), true);
        all = db.factures(pid, true);
        assertEquals(1, all.size());
        assertTrue(all.get(0).isPaye());
        assertNotNull(all.get(0).getDatePaiement());
    }
}

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
import java.util.ArrayList;
import java.sql.Connection;
import java.sql.Statement;
import java.sql.ResultSet;
import java.math.BigDecimal;

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

        BigDecimal ht = new BigDecimal("100");
        BigDecimal pct = new BigDecimal("20");
        BigDecimal mtva = ht.multiply(pct).divide(BigDecimal.valueOf(100));
        BigDecimal ttc = ht.add(mtva);
        Facture f = new Facture(0, pid, "Test", LocalDate.now(), ht, pct, mtva, ttc, false, null, false);
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

    @Test
    void testAddWithNullDateContrat() {
        Prestataire p = new Prestataire(0, "Nom", "S", "0", "a@b.com", 50, "F", null);
        db.add(p);
        Prestataire stored = db.list("").get(0);
        assertEquals("", stored.getDateContrat());
    }

    @Test
    void testFactureAmountsPersist() {
        Prestataire p = createSample();
        db.add(p);
        int pid = db.list("").get(0).getId();

        BigDecimal ht = new BigDecimal("123.45");
        BigDecimal pct = new BigDecimal("17.5");
        BigDecimal mtva = Facture.calcTva(ht, pct);
        BigDecimal ttc = Facture.calcTtc(ht, pct);
        Facture f = new Facture(0, pid, "Amounts", LocalDate.now(), ht, pct, mtva, ttc, false, null, false);
        db.addFacture(f);

        List<Facture> all = db.factures(pid, null);
        assertEquals(1, all.size());
        Facture stored = all.get(0);
        assertEquals(ht, stored.getMontantHt());
        assertEquals(pct, stored.getTvaPct());
        assertEquals(mtva, stored.getMontantTva());
        assertEquals(ttc, stored.getMontantTtc());
    }

    @Test
    void testMigrationAddsMoneyColumns() throws Exception {
        try (Connection c = DB.newConnection("old.db"); Statement st = c.createStatement()) {
            st.executeUpdate("""
                CREATE TABLE factures(
                    id INTEGER PRIMARY KEY,
                    prestataire_id INTEGER NOT NULL,
                    description TEXT,
                    echeance TEXT NOT NULL,
                    echeance_ts INTEGER NOT NULL,
                    montant_ht REAL NOT NULL,
                    paye INTEGER NOT NULL DEFAULT 0
                );
            """);
        }

        DB migrated = new DB("old.db");
        migrated.close();

        try (Connection c = DB.newConnection("old.db"); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(factures)")) {
            List<String> cols = new ArrayList<>();
            while (rs.next()) cols.add(rs.getString("name"));
            assertTrue(cols.containsAll(List.of("tva_pct","montant_tva","montant_ttc","devise")));
        }
    }
}

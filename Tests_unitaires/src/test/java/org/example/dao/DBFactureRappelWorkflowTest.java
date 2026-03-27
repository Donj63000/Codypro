package org.example.dao;

import org.example.model.Facture;
import org.example.model.NotificationSettings;
import org.example.model.Prestataire;
import org.example.model.Rappel;
import org.example.model.SmtpSecurity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class DBFactureRappelWorkflowTest {

    @TempDir
    Path tempDir;

    private DB db;

    @BeforeEach
    void setUp() {
        db = new DB(tempDir.resolve("factures.sqlite").toString());
    }

    @AfterEach
    void tearDown() {
        if (db != null) {
            db.close();
        }
    }

    @Test
    void insertFactureComputesMoneyAndDefaults() {
        int prestataireId = db.insertPrestataire(samplePrestataire("Alpha"));

        int factureId = db.insertFacture(prestataireId, new Facture(
                0,
                prestataireId,
                "Janvier",
                LocalDate.now().plusDays(2),
                new BigDecimal("100.00"),
                new BigDecimal("20.0"),
                null,
                null,
                false,
                null,
                false
        ));

        Facture loaded = byId(db.factures(prestataireId, null), factureId);
        assertNotNull(loaded);
        assertFalse(loaded.isPaye());
        assertFalse(loaded.isPreavisEnvoye());
        assertEquals(0, loaded.getMontantTva().compareTo(new BigDecimal("20.00")));
        assertEquals(0, loaded.getMontantTtc().compareTo(new BigDecimal("120.00")));
    }

    @Test
    void facturesFilterByPayeeFlag() {
        int prestataireId = db.insertPrestataire(samplePrestataire("Alpha"));
        db.insertFacture(prestataireId, sampleFacture(prestataireId, "Unpaid", LocalDate.now().plusDays(1), false));
        db.insertFacture(prestataireId, sampleFacture(prestataireId, "Paid", LocalDate.now(), true));

        List<Facture> paid = db.factures(prestataireId, true);
        List<Facture> unpaid = db.factures(prestataireId, false);

        assertEquals(1, paid.size());
        assertEquals("Paid", paid.get(0).getDescription());
        assertEquals(1, unpaid.size());
        assertEquals("Unpaid", unpaid.get(0).getDescription());
    }

    @Test
    void setFacturePayeeSetsPaymentDate() {
        int prestataireId = db.insertPrestataire(samplePrestataire("Alpha"));
        int factureId = db.insertFacture(prestataireId, sampleFacture(prestataireId, "Unpaid", LocalDate.now().plusDays(1), false));

        db.setFacturePayee(factureId, true);

        Facture loaded = byId(db.factures(prestataireId, null), factureId);
        assertNotNull(loaded);
        assertTrue(loaded.isPaye());
        assertNotNull(loaded.getDatePaiement());
    }

    @Test
    void setFacturePayeeFalseClearsPaymentDate() {
        int prestataireId = db.insertPrestataire(samplePrestataire("Alpha"));
        int factureId = db.insertFacture(prestataireId, sampleFacture(prestataireId, "Paid", LocalDate.now(), true));

        db.setFacturePayee(factureId, false);

        Facture loaded = byId(db.factures(prestataireId, null), factureId);
        assertNotNull(loaded);
        assertFalse(loaded.isPaye());
        assertNull(loaded.getDatePaiement());
    }

    @Test
    void toggleFacturePayeeSetsProvidedDate() {
        int prestataireId = db.insertPrestataire(samplePrestataire("Alpha"));
        int factureId = db.insertFacture(prestataireId, sampleFacture(prestataireId, "Toggle", LocalDate.now().plusDays(5), false));

        LocalDate paymentDate = LocalDate.of(2025, 1, 10);
        long paymentTs = paymentDate.atStartOfDay().toEpochSecond(ZoneOffset.UTC);
        db.toggleFacturePayee(factureId, true, paymentTs, "10/01/2025");

        Facture loaded = byId(db.factures(prestataireId, null), factureId);
        assertNotNull(loaded);
        assertTrue(loaded.isPaye());
        assertEquals(paymentDate, loaded.getDatePaiement());
    }

    @Test
    void toggleFacturePayeeFalseClearsProvidedDate() {
        int prestataireId = db.insertPrestataire(samplePrestataire("Alpha"));
        int factureId = db.insertFacture(prestataireId, sampleFacture(prestataireId, "Toggle", LocalDate.now().plusDays(5), false));

        db.toggleFacturePayee(factureId, true, LocalDate.of(2025, 1, 10).atStartOfDay().toEpochSecond(ZoneOffset.UTC), "10/01/2025");
        db.toggleFacturePayee(factureId, false, null, null);

        Facture loaded = byId(db.factures(prestataireId, null), factureId);
        assertNotNull(loaded);
        assertFalse(loaded.isPaye());
        assertNull(loaded.getDatePaiement());
    }

    @Test
    void updateFacturePersistsDescriptionAndAmounts() {
        int prestataireId = db.insertPrestataire(samplePrestataire("Alpha"));
        int factureId = db.insertFacture(prestataireId, sampleFacture(prestataireId, "Initial", LocalDate.now().plusDays(2), false));

        Facture updated = new Facture(
                factureId,
                prestataireId,
                "Updated",
                LocalDate.now().plusDays(7),
                new BigDecimal("250.00"),
                new BigDecimal("10"),
                null,
                null,
                false,
                null,
                false
        );
        db.updateFacture(updated);

        Facture loaded = byId(db.factures(prestataireId, null), factureId);
        assertNotNull(loaded);
        assertEquals("Updated", loaded.getDescription());
        assertEquals(0, loaded.getMontantHt().compareTo(new BigDecimal("250.00")));
        assertEquals(0, loaded.getMontantTva().compareTo(new BigDecimal("25.00")));
        assertEquals(0, loaded.getMontantTtc().compareTo(new BigDecimal("275.00")));
    }

    @Test
    void deleteFactureRemovesOnlyTarget() {
        int prestataireId = db.insertPrestataire(samplePrestataire("Alpha"));
        int first = db.insertFacture(prestataireId, sampleFacture(prestataireId, "A", LocalDate.now().plusDays(1), false));
        int second = db.insertFacture(prestataireId, sampleFacture(prestataireId, "B", LocalDate.now().plusDays(2), false));

        db.deleteFacture(first);

        List<Facture> remaining = db.factures(prestataireId, null);
        assertEquals(1, remaining.size());
        assertEquals(second, remaining.get(0).getId());
    }

    @Test
    void facturesImpayeesAvantRespectsLimit() {
        int prestataireId = db.insertPrestataire(samplePrestataire("Alpha"));
        LocalDate today = LocalDate.now();

        db.insertFacture(prestataireId, sampleFacture(prestataireId, "Soon", today.plusDays(1), false));
        db.insertFacture(prestataireId, sampleFacture(prestataireId, "Far", today.plusDays(20), false));
        db.insertFacture(prestataireId, sampleFacture(prestataireId, "Paid", today.minusDays(1), true));

        Set<String> descriptions = db.facturesImpayeesAvant(today.plusDays(2).atStartOfDay()).stream()
                .map(Facture::getDescription)
                .collect(Collectors.toSet());

        assertEquals(Set.of("Soon"), descriptions);
    }

    @Test
    void marquerPreavisEnvoyeMovesFactureToNotifiedLists() {
        int prestataireId = db.insertPrestataire(samplePrestataire("Alpha"));
        int factureId = db.insertFacture(prestataireId, sampleFacture(prestataireId, "Soon", LocalDate.now().plusDays(1), false));

        db.marquerPreavisEnvoye(factureId);

        Set<Integer> notifiedIds = db.factureIdsNonPayesAvecPreavis();
        List<Facture> notifiedFactures = db.facturesNonPayeesAvecPreavis();
        List<Facture> upcoming = db.facturesImpayeesAvant(LocalDate.now().plusDays(3).atStartOfDay());

        assertTrue(notifiedIds.contains(factureId));
        assertTrue(notifiedFactures.stream().anyMatch(f -> f.getId() == factureId));
        assertTrue(upcoming.stream().noneMatch(f -> f.getId() == factureId));
    }

    @Test
    void addRappelOutboxAndMarkSent() {
        int prestataireId = db.insertPrestataire(samplePrestataire("Alpha"));
        int factureId = db.insertFacture(prestataireId, sampleFacture(prestataireId, "Soon", LocalDate.now().plusDays(1), false));

        db.addRappel(new Rappel(0, factureId, "owner@example.com", "Sujet", "Corps", LocalDateTime.now().minusMinutes(1), false));

        List<Rappel> pending = db.rappelsAEnvoyer();
        assertEquals(1, pending.size());

        db.markRappelEnvoye(pending.get(0).id());

        assertTrue(db.rappelsAEnvoyer().isEmpty());
    }

    @Test
    void notificationSettingsLoadDefaultsAndSaveRoundTrip() {
        NotificationSettings defaults = db.loadNotificationSettings();
        assertEquals(NotificationSettings.defaults().normalized(), defaults);

        NotificationSettings updated = new NotificationSettings(
                5,
                8,
                15,
                6,
                true,
                true,
                25,
                true,
                "owner@example.com",
                "alerts@example.com",
                "smtp.example.com",
                465,
                "smtp-user",
                "smtp-pass",
                SmtpSecurity.SSL,
                "Sujet {{facture}}",
                "Corps {{prestataire}}"
        );

        db.saveNotificationSettings(updated);

        assertEquals(updated.normalized(), db.loadNotificationSettings());
    }

    private static Facture byId(List<Facture> factures, int id) {
        return factures.stream().filter(f -> f.getId() == id).findFirst().orElse(null);
    }

    private static Prestataire samplePrestataire(String name) {
        return new Prestataire(0, name, "Societe", "0102030405", "mail@example.com", 70, "Conseil", "Notes", "01/01/2024");
    }

    private static Facture sampleFacture(int prestataireId, String description, LocalDate dueDate, boolean payed) {
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

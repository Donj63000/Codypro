package org.example.dao;

import org.example.model.Facture;
import org.example.model.Prestataire;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DBFactureAlertsTest {

    @TempDir
    Path tempDir;

    private DB db;

    @BeforeEach
    void setUp() {
        Path dbFile = tempDir.resolve("alerts.sqlite");
        db = new DB(dbFile.toString());
    }

    @AfterEach
    void tearDown() {
        if (db != null) {
            db.close();
        }
    }

    @Test
    void facturesImpayeesAvantIgnoresPaidFutureAndNotifiedInvoices() {
        int prestataireId = db.insertPrestataire(samplePrestataire("Alpha"));
        LocalDate today = LocalDate.now();

        db.insertFacture(prestataireId, sampleFacture(prestataireId, "future", today.plusDays(7), false));
        db.insertFacture(prestataireId, sampleFacture(prestataireId, "overdue", today.minusDays(2), false));
        db.insertFacture(prestataireId, sampleFacture(prestataireId, "due-soon", today.plusDays(1), false));
        int notifiedId = db.insertFacture(prestataireId, sampleFacture(prestataireId, "notified", today.plusDays(2), false));
        db.marquerPreavisEnvoye(notifiedId);
        db.insertFacture(prestataireId, sampleFacture(prestataireId, "paid", today.plusDays(1), true));

        LocalDateTime limit = today.plusDays(5).atStartOfDay();
        Set<String> descriptions = db.facturesImpayeesAvant(limit).stream()
                .map(Facture::getDescription)
                .collect(Collectors.toSet());

        assertEquals(Set.of("overdue", "due-soon"), descriptions);
        assertFalse(descriptions.contains("notified"));
    }

    @Test
    void marquerPreavisEnvoyeRemovesInvoiceFromNextRun() {
        int prestataireId = db.insertPrestataire(samplePrestataire("Beta"));
        LocalDate today = LocalDate.now();
        int factureId = db.insertFacture(prestataireId, sampleFacture(prestataireId, "ping", today.plusDays(1), false));

        LocalDateTime limit = today.plusDays(3).atStartOfDay();
        assertTrue(db.facturesImpayeesAvant(limit).stream().anyMatch(f -> f.getId() == factureId));

        db.marquerPreavisEnvoye(factureId);

        assertFalse(db.facturesImpayeesAvant(limit).stream().anyMatch(f -> f.getId() == factureId));
    }

    private static Prestataire samplePrestataire(String name) {
        return new Prestataire(0, name, "", "", "", 0, "", "", "");
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

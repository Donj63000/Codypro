package org.example.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class FactureModelTest {

    @Test
    void constructorComputesAmountsWhenNull() {
        Facture facture = new Facture(
                1,
                2,
                "Audit",
                LocalDate.of(2025, 1, 10),
                new BigDecimal("100"),
                new BigDecimal("20"),
                null,
                null,
                false,
                null,
                false
        );

        assertEquals(0, facture.getMontantTva().compareTo(new BigDecimal("20")));
        assertEquals(0, facture.getMontantTtc().compareTo(new BigDecimal("120")));
    }

    @Test
    void constructorKeepsProvidedAmounts() {
        Facture facture = new Facture(
                1,
                2,
                "Audit",
                LocalDate.of(2025, 1, 10),
                new BigDecimal("100"),
                new BigDecimal("20"),
                new BigDecimal("15"),
                new BigDecimal("115"),
                false,
                null,
                false
        );

        assertEquals(0, facture.getMontantTva().compareTo(new BigDecimal("15")));
        assertEquals(0, facture.getMontantTtc().compareTo(new BigDecimal("115")));
    }

    @Test
    void constructorTurnsNullDescriptionIntoEmptyString() {
        Facture facture = new Facture(
                1,
                2,
                null,
                LocalDate.of(2025, 1, 10),
                new BigDecimal("10"),
                new BigDecimal("20"),
                null,
                null,
                false,
                null,
                false
        );

        assertEquals("", facture.getDescription());
    }

    @Test
    void echeanceFrUsesFrenchDateFormat() {
        Facture facture = new Facture(
                1,
                2,
                "Audit",
                LocalDate.of(2025, 1, 10),
                new BigDecimal("10"),
                new BigDecimal("20"),
                null,
                null,
                false,
                null,
                false
        );

        assertEquals("10/01/2025", facture.getEcheanceFr());
    }

    @Test
    void datePaiementFrUsesFrenchDateFormat() {
        Facture facture = new Facture(
                1,
                2,
                "Audit",
                LocalDate.of(2025, 1, 10),
                new BigDecimal("10"),
                new BigDecimal("20"),
                null,
                null,
                true,
                LocalDate.of(2025, 1, 11),
                false
        );

        assertEquals("11/01/2025", facture.getDatePaiementFr());
    }

    @Test
    void datePaiementFrIsEmptyWhenMissing() {
        Facture facture = new Facture(
                1,
                2,
                "Audit",
                LocalDate.of(2025, 1, 10),
                new BigDecimal("10"),
                new BigDecimal("20"),
                null,
                null,
                false,
                null,
                false
        );

        assertEquals("", facture.getDatePaiementFr());
    }

    @Test
    void calcTvaReturnsZeroWhenHtIsNull() {
        assertEquals(0, Facture.calcTva(null, new BigDecimal("20")).compareTo(BigDecimal.ZERO));
    }

    @Test
    void calcTvaReturnsZeroWhenPctIsNull() {
        assertEquals(0, Facture.calcTva(new BigDecimal("100"), null).compareTo(BigDecimal.ZERO));
    }

    @Test
    void calcTtcReturnsZeroWhenHtIsNull() {
        assertEquals(0, Facture.calcTtc(null, new BigDecimal("20")).compareTo(BigDecimal.ZERO));
    }

    @Test
    void calcTtcAddsVatToBaseAmount() {
        BigDecimal ttc = Facture.calcTtc(new BigDecimal("200"), new BigDecimal("10"));

        assertEquals(0, ttc.compareTo(new BigDecimal("220")));
    }
}

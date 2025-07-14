package org.example.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

public class FactureTest {
    @Test
    void testCalculeTtc() {
        BigDecimal ht = new BigDecimal("150.00");
        BigDecimal pct = new BigDecimal("20");
        BigDecimal expectedTva = new BigDecimal("30.00");
        BigDecimal expectedTtc = new BigDecimal("180.00");

        assertEquals(expectedTva, Facture.calcTva(ht, pct));
        assertEquals(expectedTtc, Facture.calcTtc(ht, pct));
    }
}

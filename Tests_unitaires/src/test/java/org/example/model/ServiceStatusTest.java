package org.example.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ServiceStatusTest {

    @Test
    @DisplayName("label() exposes accent for TERMINE")
    void labelUsesAccentForTermine() {
        assertEquals("Termin\u00E9", ServiceStatus.TERMINE.label());
        assertEquals("En attente", ServiceStatus.EN_ATTENTE.label());
    }

    @Test
    @DisplayName("from() accepts raw enum name, labels and accented values")
    void fromAcceptsVariousRepresentations() {
        assertEquals(ServiceStatus.EN_COURS, ServiceStatus.from("EN_COURS"));
        assertEquals(ServiceStatus.EN_COURS, ServiceStatus.from("En cours"));
        assertEquals(ServiceStatus.TERMINE, ServiceStatus.from("Termin\u00E9"));
        assertEquals(ServiceStatus.TERMINE, ServiceStatus.from("termine"));
    }

    @Test
    @DisplayName("from() falls back to EN_ATTENTE for unknown values")
    void fromFallsBack() {
        assertEquals(ServiceStatus.EN_ATTENTE, ServiceStatus.from(null));
        assertEquals(ServiceStatus.EN_ATTENTE, ServiceStatus.from(""));
        assertEquals(ServiceStatus.EN_ATTENTE, ServiceStatus.from("inconnu"));
    }
}
package org.example.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ServiceRowTest {

    @Test
    @DisplayName("Constructors sanitize values and default status")
    void constructorSanitizesValues() {
        ServiceRow row = new ServiceRow("  description  ", " 01/01/2024 ");
        assertNull(row.id());
        assertEquals("description", row.desc());
        assertEquals("01/01/2024", row.date());
        assertEquals(ServiceStatus.EN_ATTENTE, row.status());
    }

    @Test
    @DisplayName("Supports custom status and immutable update helpers")
    void supportsStatusUpdates() {
        ServiceRow row = new ServiceRow(5, "desc", "02/02/2024", ServiceStatus.EN_COURS);
        ServiceRow updatedStatus = row.withStatus(ServiceStatus.TERMINE);
        ServiceRow updatedId = row.withId(10);

        assertEquals(ServiceStatus.TERMINE, updatedStatus.status());
        assertEquals(ServiceStatus.EN_COURS, row.status());
        assertEquals(10, updatedId.id());
        assertEquals(5, row.id());
    }

    @Test
    @DisplayName("Rejects empty description")
    void rejectsEmptyDescription() {
        assertThrows(IllegalArgumentException.class, () -> new ServiceRow("   ", "01/03/2024"));
    }
}
package org.example.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class NotificationTemplateEngineTest {

    @Test
    void renderReturnsEmptyWhenTemplateIsNull() {
        String rendered = NotificationTemplateEngine.render(null, sampleContext(false));

        assertEquals("", rendered);
    }

    @Test
    void renderReturnsEmptyWhenTemplateIsBlank() {
        String rendered = NotificationTemplateEngine.render("   ", sampleContext(false));

        assertEquals("", rendered);
    }

    @Test
    void renderReplacesKnownPlaceholders() {
        String template = "{{prestataire}}|{{facture}}|{{echeance}}|{{montant}}|{{delai}}|{{delai_jours}}|{{statut}}";

        String rendered = NotificationTemplateEngine.render(template, sampleContext(false));

        assertEquals("Alpha Corp|F-001|10/01/2025|120,00 €|dans 2 jours|2|À venir", rendered);
    }

    @Test
    void renderUsesOverdueStatusWhenContextIsLate() {
        String rendered = NotificationTemplateEngine.render("Etat: {{statut}}", sampleContext(true));

        assertEquals("Etat: En retard", rendered);
    }

    @Test
    void renderUnknownPlaceholderAsEmptyString() {
        String rendered = NotificationTemplateEngine.render("X={{inconnu}}", sampleContext(false));

        assertEquals("X=", rendered);
    }

    @Test
    void renderSupportsCaseInsensitivePlaceholders() {
        String rendered = NotificationTemplateEngine.render("{{PrEsTaTaIrE}}", sampleContext(false));

        assertEquals("Alpha Corp", rendered);
    }

    @Test
    void renderSupportsWhitespaceAroundPlaceholderName() {
        String rendered = NotificationTemplateEngine.render("{{   facture   }}", sampleContext(false));

        assertEquals("F-001", rendered);
    }

    @Test
    void renderWithNullContextFallsBackToEmptyValues() {
        String rendered = NotificationTemplateEngine.render("{{prestataire}}-{{facture}}", null);

        assertEquals("-", rendered);
    }

    @Test
    void sampleContextContainsUsefulDefaults() {
        NotificationTemplateEngine.Context sample = NotificationTemplateEngine.sampleContext();

        assertNotNull(sample.prestataire());
        assertFalse(sample.prestataire().isBlank());
        assertNotNull(sample.dueDate());
    }

    @Test
    void contextWithRelativeOverridesDelayOnly() {
        NotificationTemplateEngine.Context base = sampleContext(false);

        NotificationTemplateEngine.Context updated = base.withRelative("dans 5 jours");

        assertEquals(base.prestataire(), updated.prestataire());
        assertEquals("dans 5 jours", updated.relativeDelay());
    }

    private static NotificationTemplateEngine.Context sampleContext(boolean overdue) {
        return new NotificationTemplateEngine.Context(
                "Alpha Corp",
                "F-001",
                LocalDate.of(2025, 1, 10),
                "120,00 €",
                overdue ? "depuis 1 jour" : "dans 2 jours",
                overdue ? 1 : 2,
                overdue
        );
    }
}

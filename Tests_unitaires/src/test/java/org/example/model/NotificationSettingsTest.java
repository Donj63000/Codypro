package org.example.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationSettingsTest {

    @Nested
    class Normalization {

        @Test
        @DisplayName("Clamp numeric values and reuse default templates when needed")
        void clampAndFallback() {
            NotificationSettings defaults = NotificationSettings.defaults();
            NotificationSettings raw = new NotificationSettings(
                    0,              // below minimum
                    28,             // beyond 24h range
                    58,             // should be rounded to nearest 5
                    120,            // beyond repeat bounds
                    true,
                    false,
                    1,              // below snooze minimum
                    "   ",          // should fallback to default
                    "Chantier {{facture}}   \r\n  Prévoyez {{delai}}  "
            );

            NotificationSettings normalized = raw.normalized();

            assertEquals(1, normalized.leadDays());
            assertEquals(23, normalized.reminderHour());
            assertEquals(55, normalized.reminderMinute());
            assertEquals(72, normalized.repeatEveryHours());
            assertTrue(normalized.highlightOverdue());
            assertFalse(normalized.desktopPopup());
            assertEquals(5, normalized.snoozeMinutes());
            assertEquals(defaults.subjectTemplate(), normalized.subjectTemplate());
            assertEquals("Chantier {{facture}}\n  Prévoyez {{delai}}", normalized.bodyTemplate());
        }
    }

    @Test
    @DisplayName("Generate localized summary with readable formatting")
    void summaryReadableInFrench() {
        NotificationSettings settings = new NotificationSettings(
                4,
                7,
                5,
                2,
                false,
                true,
                45,
                "Sujet",
                "Corps"
        );

        String summary = settings.summary(Locale.FRENCH);

        assertEquals("4 jours de préavis · rappel à 07 h 05 · rappel toutes les 2 h · 45 min de report", summary);
    }

    @Test
    @DisplayName("Fallback to French locale when summary is invoked with null")
    void summaryDefaultsToFrenchLocale() {
        NotificationSettings settings = NotificationSettings.defaults();

        String summary = settings.summary(null);

        assertEquals("3 jours de préavis · rappel à 09 h 00 · rappel toutes les 4 h · 30 min de report", summary);
    }
}

package org.example.model;

import java.text.Normalizer;
import java.util.Locale;

public enum ServiceStatus {
    EN_ATTENTE("En attente"),
    EN_COURS("En cours"),
    TERMINE("Termine");

    private final String rawLabel;

    ServiceStatus(String rawLabel) {
        this.rawLabel = rawLabel;
    }

    public String label() {
        if (this == TERMINE) {
            return "Termin" + "\u00E9";
        }
        return rawLabel;
    }

    public String cssClassSuffix() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static ServiceStatus from(String raw) {
        if (raw == null || raw.isBlank()) return EN_ATTENTE;
        String normalized = raw.trim();
        for (ServiceStatus status : values()) {
            if (equalsIgnoreCase(status.label(), normalized) || equalsIgnoreCase(status.rawLabel, normalized)) {
                return status;
            }
        }
        String enumKey = normalized.toUpperCase(Locale.ROOT).replace(' ', '_');
        try {
            return ServiceStatus.valueOf(enumKey);
        } catch (IllegalArgumentException ex) {
            return EN_ATTENTE;
        }
    }

    private static boolean equalsIgnoreCase(String a, String b) {
        return normalize(a).equalsIgnoreCase(normalize(b));
    }

    private static String normalize(String input) {
        return Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}\u005D+", "");
    }
}

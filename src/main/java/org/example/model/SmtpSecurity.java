package org.example.model;

import java.util.Locale;

public enum SmtpSecurity {
    NONE("Aucun"),
    STARTTLS("STARTTLS"),
    SSL("SSL/TLS");

    private final String label;

    SmtpSecurity(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static SmtpSecurity from(String raw) {
        if (raw == null || raw.isBlank()) {
            return STARTTLS;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "NONE", "AUCUN" -> NONE;
            case "SSL", "SSL_TLS", "SSL/TLS" -> SSL;
            case "TLS", "STARTTLS" -> STARTTLS;
            default -> STARTTLS;
        };
    }
}

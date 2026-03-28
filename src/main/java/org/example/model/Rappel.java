package org.example.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

public record Rappel(
        int id,
        String jobKey,
        String type,
        int factureId,
        Integer prestataireId,
        String dest,
        String sujet,
        String corps,
        LocalDateTime dateEnvoi,
        boolean envoye,
        String statut,
        int attemptCount,
        String lastError,
        LocalDateTime sentAt
) implements Serializable {

    public static final String TYPE_MANAGER_PRE = "MANAGER_PRE";
    public static final String TYPE_MANAGER_DUE = "MANAGER_DUE";
    public static final String TYPE_MANAGER_OVERDUE = "MANAGER_OVERDUE";
    public static final String TYPE_SUPPLIER_PRE = "SUPPLIER_PRE";
    public static final String TYPE_SUPPLIER_DUE = "SUPPLIER_DUE";
    public static final String TYPE_SUPPLIER_OVERDUE = "SUPPLIER_OVERDUE";

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SENT = "SENT";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_SKIPPED = "SKIPPED";

    public Rappel(
            int id,
            int factureId,
            String dest,
            String sujet,
            String corps,
            LocalDateTime dateEnvoi,
            boolean envoye
    ) {
        this(
                id,
                "",
                TYPE_MANAGER_PRE,
                factureId,
                null,
                dest,
                sujet,
                corps,
                dateEnvoi,
                envoye,
                envoye ? STATUS_SENT : STATUS_PENDING,
                0,
                "",
                envoye ? dateEnvoi : null
        );
    }

    public Rappel {
        jobKey = jobKey == null ? "" : jobKey.trim();
        type = validateType(type);
        dest = validate(dest);
        sujet = validate(sujet);
        corps = validate(corps);
        dateEnvoi = Objects.requireNonNull(dateEnvoi, "dateEnvoi");
        statut = validateStatus(statut);
        attemptCount = Math.max(attemptCount, 0);
        lastError = lastError == null ? "" : lastError.trim();
    }

    private static String validate(String s) {
        if (s == null || s.isBlank()) {
            throw new IllegalArgumentException("Champ obligatoire manquant");
        }
        return s.trim();
    }

    private static String validateType(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            return TYPE_MANAGER_PRE;
        }
        return normalized;
    }

    public boolean isManagerFlow() {
        return type.startsWith("MANAGER_");
    }

    public boolean isSupplierFlow() {
        return type.startsWith("SUPPLIER_");
    }

    public String flowLabel() {
        return switch (type) {
            case TYPE_MANAGER_PRE -> "Gestionnaire · préavis";
            case TYPE_MANAGER_DUE -> "Gestionnaire · échéance";
            case TYPE_MANAGER_OVERDUE -> "Gestionnaire · retard";
            case TYPE_SUPPLIER_PRE -> "Prestataire · préavis";
            case TYPE_SUPPLIER_DUE -> "Prestataire · échéance";
            case TYPE_SUPPLIER_OVERDUE -> "Prestataire · retard";
            default -> type;
        };
    }

    public String statusLabel() {
        return switch (statut) {
            case STATUS_PENDING -> "En attente";
            case STATUS_SENT -> "Envoyé";
            case STATUS_FAILED -> "Échec";
            case STATUS_SKIPPED -> "Ignoré";
            default -> statut;
        };
    }

    private static String validateStatus(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            return STATUS_PENDING;
        }
        return normalized;
    }
}

package org.example.model;

import java.io.Serializable;
import java.util.Objects;

public record ServiceRow(Integer id, String desc, String date, ServiceStatus status) implements Serializable {

    public ServiceRow {
        desc = sanitize(desc);
        date = sanitize(date);
        status = status == null ? ServiceStatus.EN_ATTENTE : status;
    }

    public ServiceRow(String desc, String date) {
        this(null, desc, date, ServiceStatus.EN_ATTENTE);
    }

    public ServiceRow(String desc, String date, ServiceStatus status) {
        this(null, desc, date, status);
    }

    public ServiceRow(Integer id, String desc, String date) {
        this(id, desc, date, ServiceStatus.EN_ATTENTE);
    }

    public ServiceRow withStatus(ServiceStatus newStatus) {
        return new ServiceRow(id, desc, date, newStatus);
    }

    public ServiceRow withId(Integer newId) {
        return new ServiceRow(newId, desc, date, status);
    }

    private static String sanitize(String v) {
        v = Objects.requireNonNull(v, "value").trim();
        if (v.isEmpty()) throw new IllegalArgumentException("Champ vide");
        return v;
    }
}

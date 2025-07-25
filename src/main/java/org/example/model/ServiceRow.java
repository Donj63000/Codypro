package org.example.model;

import java.io.Serializable;
import java.util.Objects;

public record ServiceRow(String desc, String date) implements Serializable {

    public ServiceRow {
        desc = sanitize(desc);
        date = sanitize(date);
    }

    private static String sanitize(String v) {
        v = Objects.requireNonNull(v, "value").trim();
        if (v.isEmpty()) throw new IllegalArgumentException("Champ vide");
        return v;
    }
}

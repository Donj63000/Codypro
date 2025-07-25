package org.example.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

public record Rappel(
        int            id,
        int            factureId,
        String         dest,
        String         sujet,
        String         corps,
        LocalDateTime  dateEnvoi,
        boolean        envoye) implements Serializable {

    public Rappel {
        dest      = validate(dest);
        sujet     = validate(sujet);
        corps     = validate(corps);
        dateEnvoi = Objects.requireNonNull(dateEnvoi, "dateEnvoi");
    }

    private static String validate(String s) {
        if (s == null || s.isBlank())
            throw new IllegalArgumentException("Champ obligatoire manquant");
        return s.trim();
    }
}

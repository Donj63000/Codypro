package org.example.model;

import java.time.LocalDateTime;

public record Rappel(int id, int factureId, String dest, String sujet,
                     String corps, LocalDateTime dateEnvoi, boolean envoye) {}

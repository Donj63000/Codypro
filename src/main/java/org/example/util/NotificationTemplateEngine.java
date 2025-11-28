package org.example.util;

import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class to render notification templates with lightweight moustache-like placeholders.
 */
public final class NotificationTemplateEngine {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_]+)\\s*}}");
    private static final DateTimeFormatter DATE_FR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private NotificationTemplateEngine() {}

    public static Context sampleContext() {
        return new Context(
                "Studio Atlas",
                "Maintenance serveur T2",
                java.time.LocalDate.now().plusDays(3),
                "2 450,00 €",
                "dans 3 jours",
                3,
                false
        );
    }

    public static String render(String template, Context context) {
        if (template == null || template.isBlank()) {
            return "";
        }
        Context ctx = context == null ? Context.empty() : context;
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        StringBuffer sb = new StringBuffer(template.length());
        while (matcher.find()) {
            String key = matcher.group(1).toLowerCase(Locale.ROOT);
            String replacement = valueFor(key, ctx);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String valueFor(String key, Context ctx) {
        return switch (key) {
            case "prestataire" -> safe(ctx.prestataire());
            case "facture" -> safe(ctx.facture());
            case "echeance" -> formatDate(ctx.dueDate());
            case "montant" -> safe(ctx.montant());
            case "delai" -> safe(ctx.relativeDelay());
            case "delai_jours" -> Long.toString(ctx.deltaDays());
            case "statut" -> ctx.overdue() ? "En retard" : "À venir";
            default -> "";
        };
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String formatDate(java.time.LocalDate date) {
        return date == null ? "" : DATE_FR.format(date);
    }

    public record Context(
            String prestataire,
            String facture,
            java.time.LocalDate dueDate,
            String montant,
            String relativeDelay,
            long deltaDays,
            boolean overdue
    ) {
        public static Context empty() {
            return new Context("", "", null, "", "", 0, false);
        }

        public Context withRelative(String relative) {
            return new Context(prestataire, facture, dueDate, montant, Objects.requireNonNullElse(relative, ""), deltaDays, overdue);
        }
    }
}

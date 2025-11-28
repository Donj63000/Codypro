package org.example.model;

import java.util.Locale;

/**
 * Holds user-configurable settings for upcoming payment notifications.
 * The record is immutable; use {@link #normalized()} to obtain a sanitized copy.
 */
public record NotificationSettings(
        int leadDays,
        int reminderHour,
        int reminderMinute,
        int repeatEveryHours,
        boolean highlightOverdue,
        boolean desktopPopup,
        int snoozeMinutes,
        String subjectTemplate,
        String bodyTemplate
) {

    private static final int MIN_LEAD_DAYS = 1;
    private static final int MAX_LEAD_DAYS = 60;
    private static final int MIN_REPEAT_HOURS = 0;
    private static final int MAX_REPEAT_HOURS = 72;
    private static final int MIN_SNOOZE_MINUTES = 5;
    private static final int MAX_SNOOZE_MINUTES = 720;

    public static NotificationSettings defaults() {
        return new NotificationSettings(
                3,
                9,
                0,
                4,
                true,
                true,
                30,
                "Facture {{prestataire}} : échéance le {{echeance}}",
                """
                        La facture {{facture}} d'un montant de {{montant}} pour {{prestataire}} arrive {{delai}}.
                        Statut : {{statut}}.
                        """
        );
    }

    public NotificationSettings normalized() {
        int safeLead = clamp(leadDays, MIN_LEAD_DAYS, MAX_LEAD_DAYS);
        int safeHour = clamp(reminderHour, 0, 23);
        int safeMinute = clamp(reminderMinute, 0, 59);
        // Round minutes to 5-minute steps for consistency
        safeMinute -= safeMinute % 5;
        int safeRepeat = clamp(repeatEveryHours, MIN_REPEAT_HOURS, MAX_REPEAT_HOURS);
        int safeSnooze = clamp(snoozeMinutes, MIN_SNOOZE_MINUTES, MAX_SNOOZE_MINUTES);
        String subject = sanitizeTemplate(subjectTemplate, defaults().subjectTemplate());
        String body = sanitizeTemplate(bodyTemplate, defaults().bodyTemplate());
        return new NotificationSettings(
                safeLead,
                safeHour,
                safeMinute,
                safeRepeat,
                highlightOverdue,
                desktopPopup,
                safeSnooze,
                subject,
                body
        );
    }

    private static int clamp(int value, int min, int max) {
        return Math.min(Math.max(value, min), max);
    }

    private static String sanitizeTemplate(String template, String fallback) {
        if (template == null) {
            return fallback;
        }
        String trimmed = template.strip();
        if (trimmed.isEmpty()) {
            return fallback;
        }
        // Normalize line endings and remove trailing spaces for deterministic rendering.
        trimmed = trimmed.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = trimmed.split("\n", -1);
        StringBuilder sanitized = new StringBuilder(trimmed.length());
        for (int i = 0; i < lines.length; i++) {
            sanitized.append(lines[i].stripTrailing());
            if (i < lines.length - 1) {
                sanitized.append('\n');
            }
        }
        return sanitized.toString();
    }

    public String summary(Locale locale) {
        Locale effective = locale == null ? Locale.FRENCH : locale;
        String leadPart = leadDays == 1
                ? "1 jour de préavis"
                : leadDays + " jours de préavis";
        String repeatPart = repeatEveryHours <= 0
                ? "sans rappel automatique"
                : (repeatEveryHours == 1 ? "rappel toutes les heures" : "rappel toutes les " + repeatEveryHours + " h");
        String snoozePart = snoozeMinutes + " min de report";
        return "%s · rappel à %02d h %02d · %s · %s".formatted(
                leadPart,
                reminderHour,
                reminderMinute,
                repeatPart,
                snoozePart
        );
    }
}

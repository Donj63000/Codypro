package org.example.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public record NotificationSettings(
        int leadDays,
        int reminderHour,
        int reminderMinute,
        int repeatEveryHours,
        boolean highlightOverdue,
        boolean desktopPopup,
        int snoozeMinutes,
        boolean emailEnabled,
        String emailRecipient,
        String emailFrom,
        String emailFromName,
        String emailReplyTo,
        String emailSignature,
        String smtpHost,
        int smtpPort,
        String smtpUsername,
        String smtpPassword,
        SmtpSecurity smtpSecurity,
        String subjectTemplate,
        String bodyTemplate,
        boolean supplierEmailEnabled,
        boolean supplierSendOnDueDate,
        String supplierSubjectTemplate,
        String supplierBodyTemplate
) {

    private static final int MIN_LEAD_DAYS = 1;
    private static final int MAX_LEAD_DAYS = 60;
    private static final int MIN_REPEAT_HOURS = 0;
    private static final int MAX_REPEAT_HOURS = 72;
    private static final int MIN_SNOOZE_MINUTES = 5;
    private static final int MAX_SNOOZE_MINUTES = 720;
    private static final int MIN_SMTP_PORT = 1;
    private static final int MAX_SMTP_PORT = 65535;
    private static final Pattern SIMPLE_EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public NotificationSettings(
            int leadDays,
            int reminderHour,
            int reminderMinute,
            int repeatEveryHours,
            boolean highlightOverdue,
            boolean desktopPopup,
            int snoozeMinutes,
            boolean emailEnabled,
            String emailRecipient,
            String emailFrom,
            String smtpHost,
            int smtpPort,
            String smtpUsername,
            String smtpPassword,
            SmtpSecurity smtpSecurity,
            String subjectTemplate,
            String bodyTemplate
    ) {
        this(
                leadDays,
                reminderHour,
                reminderMinute,
                repeatEveryHours,
                highlightOverdue,
                desktopPopup,
                snoozeMinutes,
                emailEnabled,
                emailRecipient,
                emailFrom,
                defaults().emailFromName(),
                "",
                defaults().emailSignature(),
                smtpHost,
                smtpPort,
                smtpUsername,
                smtpPassword,
                smtpSecurity,
                subjectTemplate,
                bodyTemplate,
                defaults().supplierEmailEnabled(),
                defaults().supplierSendOnDueDate(),
                defaults().supplierSubjectTemplate(),
                defaults().supplierBodyTemplate()
        );
    }

    public static NotificationSettings defaults() {
        return new NotificationSettings(
                3,
                9,
                0,
                24,
                true,
                true,
                30,
                false,
                "",
                "",
                "Prestataires Manager",
                "",
                "Cordialement,\nPrestataires Manager",
                "",
                587,
                "",
                "",
                SmtpSecurity.STARTTLS,
                "Alerte échéance - {{prestataire}} - {{facture}}",
                "Le prestataire {{prestataire}} a une facture {{facture}} de {{montant}} qui arrive {{delai}}.\n"
                        + "Échéance : {{echeance}}.\n"
                        + "Statut : {{statut}}.",
                false,
                true,
                "Rappel de paiement - {{facture}} - échéance {{echeance}}",
                "Bonjour {{prestataire}},\n\n"
                        + "Nous vous rappelons que la facture {{facture}} d'un montant de {{montant}} arrive {{delai}}.\n"
                        + "Date d'échéance : {{echeance}}.\n\n"
                        + "Merci de procéder au règlement dans les délais."
        );
    }

    public NotificationSettings normalized() {
        NotificationSettings defaults = defaults();
        int safeLead = clamp(leadDays, MIN_LEAD_DAYS, MAX_LEAD_DAYS);
        int safeHour = clamp(reminderHour, 0, 23);
        int safeMinute = clamp(reminderMinute, 0, 59);
        safeMinute -= safeMinute % 5;
        int safeRepeat = clamp(repeatEveryHours, MIN_REPEAT_HOURS, MAX_REPEAT_HOURS);
        int safeSnooze = clamp(snoozeMinutes, MIN_SNOOZE_MINUTES, MAX_SNOOZE_MINUTES);
        int safePort = clamp(smtpPort, MIN_SMTP_PORT, MAX_SMTP_PORT);
        String safeRecipient = sanitizeValue(emailRecipient);
        String safeFrom = sanitizeValue(emailFrom);
        String safeFromName = sanitizeValue(emailFromName);
        String safeReplyTo = sanitizeValue(emailReplyTo);
        String safeSignature = sanitizeMultiline(emailSignature, defaults.emailSignature());
        String safeHost = sanitizeValue(smtpHost);
        String safeUser = sanitizeValue(smtpUsername);
        String safePassword = smtpPassword == null ? "" : smtpPassword;
        SmtpSecurity safeSecurity = smtpSecurity == null ? SmtpSecurity.STARTTLS : smtpSecurity;
        String subject = sanitizeTemplate(subjectTemplate, defaults.subjectTemplate());
        String body = sanitizeTemplate(bodyTemplate, defaults.bodyTemplate());
        String supplierSubject = sanitizeTemplate(supplierSubjectTemplate, defaults.supplierSubjectTemplate());
        String supplierBody = sanitizeTemplate(supplierBodyTemplate, defaults.supplierBodyTemplate());
        return new NotificationSettings(
                safeLead,
                safeHour,
                safeMinute,
                safeRepeat,
                highlightOverdue,
                desktopPopup,
                safeSnooze,
                emailEnabled,
                safeRecipient,
                safeFrom,
                safeFromName,
                safeReplyTo,
                safeSignature,
                safeHost,
                safePort,
                safeUser,
                safePassword,
                safeSecurity,
                subject,
                body,
                supplierEmailEnabled,
                supplierSendOnDueDate,
                supplierSubject,
                supplierBody
        );
    }

    public boolean hasAnyEmailFlow() {
        return emailEnabled || supplierEmailEnabled;
    }

    public String resolvedSenderAddress() {
        return sanitizeValue(firstNonBlank(emailFrom, smtpUsername, emailRecipient));
    }

    public String resolvedSenderName() {
        String explicit = sanitizeValue(emailFromName);
        return explicit.isBlank() ? "Prestataires Manager" : explicit;
    }

    public String resolvedReplyTo() {
        return sanitizeValue(emailReplyTo);
    }

    public boolean smtpReady() {
        if (smtpHost == null || smtpHost.isBlank()) {
            return false;
        }
        if (smtpPort < MIN_SMTP_PORT || smtpPort > MAX_SMTP_PORT) {
            return false;
        }
        return looksLikeEmail(resolvedSenderAddress());
    }

    public boolean emailReady() {
        if (!emailEnabled) {
            return false;
        }
        return looksLikeEmail(emailRecipient) && smtpReady();
    }

    public boolean supplierEmailReady() {
        return supplierEmailEnabled && smtpReady();
    }

    public String applySignature(String body) {
        String base = body == null ? "" : body.replace("\r\n", "\n").replace('\r', '\n').stripTrailing();
        String signature = sanitizeMultiline(emailSignature, "");
        if (signature.isBlank()) {
            return base;
        }
        if (base.isBlank()) {
            return signature;
        }
        return base + "\n\n" + signature;
    }

    public List<String> validationErrors() {
        NotificationSettings draft = normalized();
        List<String> errors = new ArrayList<>();
        if (draft.leadDays() < MIN_LEAD_DAYS || draft.leadDays() > MAX_LEAD_DAYS) {
            errors.add("Le nombre de jours de préavis est invalide.");
        }
        if (draft.reminderMinute() % 5 != 0) {
            errors.add("Les minutes doivent être alignées sur un pas de 5.");
        }
        if (!draft.hasAnyEmailFlow()) {
            return errors;
        }
        if (draft.smtpHost() == null || draft.smtpHost().isBlank()) {
            errors.add("L'hôte SMTP est obligatoire.");
        }
        if (draft.smtpPort() < MIN_SMTP_PORT || draft.smtpPort() > MAX_SMTP_PORT) {
            errors.add("Le port SMTP est invalide.");
        }
        if (!looksLikeEmail(draft.resolvedSenderAddress())) {
            errors.add("L'adresse d'expédition est invalide ou incomplète.");
        }
        if (!draft.resolvedReplyTo().isBlank() && !looksLikeEmail(draft.resolvedReplyTo())) {
            errors.add("L'adresse de réponse est invalide.");
        }
        if (draft.emailEnabled() && !looksLikeEmail(draft.emailRecipient())) {
            errors.add("L'adresse e-mail du gestionnaire est obligatoire.");
        }
        return errors;
    }

    public String summary(Locale locale) {
        Locale effective = locale == null ? Locale.FRENCH : locale;
        boolean french = effective.getLanguage().equals(Locale.FRENCH.getLanguage());
        String leadPart = french
                ? (leadDays == 1 ? "1 jour de préavis" : leadDays + " jours de préavis")
                : (leadDays == 1 ? "1 day notice" : leadDays + " days notice");
        String repeatPart = french
                ? (repeatEveryHours <= 0
                ? "sans relance répétée"
                : (repeatEveryHours == 1 ? "rappel toutes les heures" : "rappel toutes les " + repeatEveryHours + " h"))
                : (repeatEveryHours <= 0
                ? "no repeated reminder"
                : (repeatEveryHours == 1 ? "hourly reminder" : "reminder every " + repeatEveryHours + " h"));
        String managerPart = french
                ? (emailEnabled ? "emails internes actifs" : "emails internes désactivés")
                : (emailEnabled ? "manager emails on" : "manager emails off");
        String supplierPart = french
                ? (supplierEmailEnabled ? "emails prestataires actifs" : "emails prestataires désactivés")
                : (supplierEmailEnabled ? "supplier emails on" : "supplier emails off");
        if (french) {
            return "%s · déclenchement à %02d h %02d · %s · %s · %s".formatted(
                    leadPart,
                    reminderHour,
                    reminderMinute,
                    repeatPart,
                    managerPart,
                    supplierPart
            );
        }
        return "%s · %02d:%02d · %s · %s · %s".formatted(
                leadPart,
                reminderHour,
                reminderMinute,
                repeatPart,
                managerPart,
                supplierPart
        );
    }

    public String senderSummary() {
        String sender = resolvedSenderAddress();
        if (sender.isBlank()) {
            return "Aucune adresse d'expédition";
        }
        String name = resolvedSenderName();
        return name.isBlank() ? sender : name + " <" + sender + ">";
    }

    public static boolean looksLikeEmail(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.strip();
        if (trimmed.isEmpty()) {
            return false;
        }
        return SIMPLE_EMAIL.matcher(trimmed).matches();
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
        return sanitizeMultiline(trimmed, fallback);
    }

    private static String sanitizeMultiline(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.strip();
        if (trimmed.isEmpty()) {
            return fallback == null ? "" : fallback;
        }
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

    private static String sanitizeValue(String value) {
        return value == null ? "" : value.strip();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}

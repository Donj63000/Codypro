package org.example.mail;

/** Simple data holder for SMTP provider presets. */
public record SmtpPreset(
        String provider,
        String label,
        String host,
        int port,
        boolean ssl,
        boolean oauth
) {
    /** Display label in combo boxes. */
    @Override public String toString() { return label; }

    /** Known presets. Index 0 is a generic custom configuration. */
    public static final SmtpPreset[] PRESETS = {
            new SmtpPreset("custom", "Personnalisé",
                    "smtp.example.com", 465, true, false),
            new SmtpPreset("gmail", "Gmail (OAuth)",
                    "smtp.gmail.com", 587, false, true),
            new SmtpPreset("outlook", "Outlook / Office365",
                    "smtp.office365.com", 587, false, false)
    };

    /** Find a preset by provider id, ignoring case. */
    public static SmtpPreset byProvider(String provider) {
        if (provider == null) return null;
        for (SmtpPreset p : PRESETS) {
            if (p.provider.equalsIgnoreCase(provider)) {
                return p;
            }
        }
        return null;
    }
}

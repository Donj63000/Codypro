package org.example.mail;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record SmtpPreset(
        String provider,
        String label,
        String host,
        int     port,
        boolean ssl,
        boolean oauth) {

    @Override
    public String toString() { return label; }

    public static final SmtpPreset[] PRESETS = {
            new SmtpPreset("local",   "Local Relay (localhost:2525)", "localhost",        2525, false, false),
            new SmtpPreset("custom",  "Personnalisé",                "smtp.example.com", 465,  true,  false),
            new SmtpPreset("gmail",   "Gmail (OAuth)",               "smtp.gmail.com",   587,  false, true ),
            new SmtpPreset("outlook", "Outlook / Office365 (OAuth)", "smtp.office365.com",587,  false, true )
    };

    private static final Map<String, SmtpPreset> INDEX =
            Stream.of(PRESETS).collect(Collectors.toUnmodifiableMap(p -> p.provider().toLowerCase(), p -> p));

    public static SmtpPreset byProvider(String provider) {
        if (provider == null) return null;
        return INDEX.get(provider.trim().toLowerCase());
    }
}

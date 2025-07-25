package org.example.mail;

public final class OAuthServiceFactory {

    private OAuthServiceFactory() {}

    public static OAuthService create(MailPrefs prefs) {
        if (prefs == null) return null;
        return switch (normalize(prefs.provider())) {
            case "gmail"   -> new GoogleAuthService(prefs);
            case "outlook" -> new MicrosoftAuthService(prefs);
            default        -> null;
        };
    }

    private static String normalize(String v) {
        return v == null ? "" : v.trim().toLowerCase();
    }
}

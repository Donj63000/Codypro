package org.example.mail;

/** Factory creating {@link OAuthService} implementations based on provider. */
public final class OAuthServiceFactory {
    private OAuthServiceFactory() {}

    /** Create the appropriate service for the given preferences. */
    public static OAuthService create(MailPrefs prefs) {
        String p = prefs.provider();
        if (p == null) return null;
        if (p.equalsIgnoreCase("gmail")) {
            return new GoogleAuthService(prefs);
        }
        if (p.equalsIgnoreCase("outlook")) {
            return new MicrosoftAuthService(prefs);
        }
        return null;
    }
}

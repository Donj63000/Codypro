package org.example.mail.autodetect;

/** Interface to discover SMTP configuration for a sender address. */
public interface AutoConfigProvider {
    /**
     * Discover SMTP settings for the given e-mail address.
     *
     * @param email address to discover settings for
     * @return result or {@code null} if none could be found
     */
    AutoConfigResult discover(String email) throws Exception;
}

package org.example.mail;

/** Basic operations for OAuth-based mail providers. */
public interface OAuthService {
    /**
     * Launch an interactive flow to obtain user consent.
     *
     * @return port used by the local loopback server
     */
    int interactiveAuth();

    /** Return a valid access token, refreshing if needed. */
    String getAccessToken();

    /** Refresh the access token using the stored refresh token. */
    void refreshAccessToken();
}

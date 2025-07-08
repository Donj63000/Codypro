package org.example.mail;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponseException;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleRefreshTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import jakarta.mail.Authenticator;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Properties;

/**
 * Utility class that handles the complete OAuth 2 flow for Gmail and returns
 * a ready‑to‑use {@link Session} configured for the XOAUTH2 mechanism.
 *
 * <p>Dependencies required in the {@code pom.xml}:</p>
 * <ul>
 *     <li>{@code com.google.oauth-client:google-oauth-client-jetty}</li>
 *     <li>its transitive dependencies (google‑oauth‑client, google‑http‑client, …)</li>
 * </ul>
 */
public final class GmailOAuth2Service {

    /* ==============================================================
       ==  🔧  UPDATE THESE TWO CONSTANTS WITH YOUR OWN CREDENTIALS  ==
       ============================================================== */
    private static final String CLIENT_ID     = "xxxxxxxxxxxxxxxxxxxxxxxxxxxx.apps.googleusercontent.com";
    private static final String CLIENT_SECRET = "xxxxxxxxxxxxxxxxxxxxxx";
    /* ============================================================== */

    private static final List<String> SCOPES = List.of("https://mail.google.com/");

    private static final NetHttpTransport HTTP_TRANSPORT = new NetHttpTransport();
    private static final JsonFactory     JSON_FACTORY    = JacksonFactory.getDefaultInstance();

    /** Refresh token obtained after the interactive consent flow. */
    private String refreshToken;

    /* ------------------------------------------------------------------
       Interactive authorisation
       ------------------------------------------------------------------ */

    /**
     * Launches the Google consent screen in the user’s browser, starts a local
     * Jetty server to capture the redirect, and stores the resulting refresh
     * token in memory. Persist the token yourself if you need it later.
     *
     * @throws IOException              transport‑level errors
     * @throws GeneralSecurityException SSL initialisation errors
     */
    public void authorizeInteractive() throws IOException, GeneralSecurityException {

        GoogleClientSecrets secrets = new GoogleClientSecrets()
                .setInstalled(new GoogleClientSecrets.Details()
                        .setClientId(CLIENT_ID)
                        .setClientSecret(CLIENT_SECRET));

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, secrets, SCOPES)
                .setAccessType("offline")      // mandatory to obtain a refresh token
                .build();

        LocalServerReceiver receiver = new LocalServerReceiver.Builder()
                .setPort(8888)                 // ⇢ http://localhost:8888/Callback
                .build();

        Credential cred = new AuthorizationCodeInstalledApp(flow, receiver)
                .authorize("user");

        this.refreshToken = cred.getRefreshToken();
    }

    /* ------------------------------------------------------------------
       Token management
       ------------------------------------------------------------------ */

    /**
     * Exchanges the stored refresh token for a short‑lived access token.
     *
     * @return raw OAuth2 access token
     */
    private String fetchAccessToken() throws IOException {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalStateException(
                    "No refresh token available – call authorizeInteractive() first.");
        }

        GoogleRefreshTokenRequest req = new GoogleRefreshTokenRequest(
                HTTP_TRANSPORT, JSON_FACTORY,
                refreshToken, CLIENT_ID, CLIENT_SECRET);

        try {
            GoogleTokenResponse resp = req.execute();
            return resp.getAccessToken();
        } catch (TokenResponseException ex) {
            throw new IOException("Unable to refresh OAuth2 token: " + ex.getDetails(), ex);
        }
    }

    /* ------------------------------------------------------------------
       Jakarta Mail session
       ------------------------------------------------------------------ */

    /**
     * Creates a {@link Session} pre‑configured for SMTP authentication using
     * XOAUTH2.  The session will transparently retrieve a fresh access token
     * whenever Jakarta Mail asks the {@link Authenticator} for credentials.
     *
     * @param gmailAddress user’s full Gmail address (e.g. {@code user@gmail.com})
     */
    public Session createSession(String gmailAddress) {

        Properties props = new Properties();
        props.put("mail.smtp.auth",            "true");
        props.put("mail.smtp.auth.mechanisms", "XOAUTH2");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host",            "smtp.gmail.com");
        props.put("mail.smtp.port",            "587");

        Authenticator auth = new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                try {
                    return new PasswordAuthentication(gmailAddress, fetchAccessToken());
                } catch (Exception ex) {
                    throw new RuntimeException("Failed to obtain OAuth2 access token", ex);
                }
            }
        };

        return Session.getInstance(props, auth);
    }
}

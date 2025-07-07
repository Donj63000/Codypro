package org.example.mail;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleRefreshTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import jakarta.mail.Authenticator;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Properties;

/**
 * Helper for authenticating to Gmail and creating a Jakarta Mail {@link Session}
 * that uses XOAUTH2.
 */
public final class GmailOAuth2Service {

    private static final String CLIENT_ID     = "xxxxxxxxxxxxxxxxxxxxxxxxxxxx.apps.googleusercontent.com";
    private static final String CLIENT_SECRET = "xxxxxxxxxxxxxxxxxxxxxx";
    private static final List<String> SCOPES  = List.of("https://mail.google.com/");

    private final NetHttpTransport http      = new NetHttpTransport();
    private final JacksonFactory   json      = JacksonFactory.getDefaultInstance();
    private       String           refreshToken;

    /**
     * Runs the OAuth2 consent flow in the user’s browser and stores the
     * resulting refresh token in memory (persist it yourself if necessary).
     */
    public void authorizeInteractive() throws IOException, GeneralSecurityException {
        GoogleClientSecrets secrets = new GoogleClientSecrets()
                .setInstalled(new GoogleClientSecrets.Details()
                        .setClientId(CLIENT_ID)
                        .setClientSecret(CLIENT_SECRET));

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                http, json, secrets, SCOPES)
                .setAccessType("offline")
                .build();

        LocalServerReceiver receiver = new LocalServerReceiver.Builder()
                .setPort(8888)
                .build();

        Credential cred = new AuthorizationCodeInstalledApp(flow, receiver)
                .authorize("user");

        this.refreshToken = cred.getRefreshToken();
    }

    /**
     * Exchanges the stored refresh token for a short‑lived access token.
     */
    private String fetchAccessToken() throws IOException, GeneralSecurityException {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalStateException("No refresh token available – run authorizeInteractive() first");
        }
        GoogleTokenResponse resp = new GoogleRefreshTokenRequest(
                http, json, refreshToken, CLIENT_ID, CLIENT_SECRET)
                .execute();
        return resp.getAccessToken();
    }

    /**
     * Builds a {@link Session} configured for SMTP with XOAUTH2, using the
     * current access token derived from the stored refresh token.
     *
     * @param gmailAddress the user’s full Gmail address (user@gmail.com)
     */
    public Session createSession(String gmailAddress)
            throws IOException, GeneralSecurityException {

        Properties props = new Properties();
        props.put("mail.smtp.auth",               "true");
        props.put("mail.smtp.auth.mechanisms",    "XOAUTH2");
        props.put("mail.smtp.starttls.enable",    "true");
        props.put("mail.smtp.host",               "smtp.gmail.com");
        props.put("mail.smtp.port",               "587");

        Authenticator auth = new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                try {
                    return new PasswordAuthentication(gmailAddress, fetchAccessToken());
                } catch (Exception ex) {
                    throw new RuntimeException("Unable to retrieve OAuth2 access token", ex);
                }
            }
        };
        return Session.getInstance(props, auth);
    }
}

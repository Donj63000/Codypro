package org.example.mail;

import com.google.api.client.googleapis.auth.oauth2.*;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import jakarta.mail.*;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Properties;

/**
 * Gmail OAuth2 helper using Google's official libraries.
 */
public final class GmailOAuth2Service {
    private static final String CLIENT_ID     = "xxxxxxxxxxxxxxxxxxxxxxxxxxxx.apps.googleusercontent.com";
    private static final String CLIENT_SECRET = "xxxxxxxxxxxxxxxxxxxxxx";
    private static final List<String> SCOPES  = List.of("https://mail.google.com/");

    private String refreshToken;

    /** Launch the consent screen and extract the refresh token. */
    public void authorizeInteractive() throws IOException, GeneralSecurityException {
        GoogleClientSecrets secrets = new GoogleClientSecrets()
                .setInstalled(new GoogleClientSecrets.Details()
                        .setClientId(CLIENT_ID)
                        .setClientSecret(CLIENT_SECRET));

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                new NetHttpTransport(), JacksonFactory.getDefaultInstance(),
                secrets, SCOPES)
                .setAccessType("offline")
                .build();

        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
        Credential credential = new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
        this.refreshToken = credential.getRefreshToken();
        // TODO: persist refreshToken securely
    }

    /** Exchange the refresh token for a short-lived access token. */
    private String fetchAccessToken() throws IOException, GeneralSecurityException {
        GoogleTokenResponse response = new GoogleRefreshTokenRequest(
                new NetHttpTransport(), JacksonFactory.getDefaultInstance(),
                refreshToken, CLIENT_ID, CLIENT_SECRET).execute();
        return response.getAccessToken();
    }

    /** Create a Jakarta Mail {@link Session} pre-configured for XOAUTH2. */
    public Session createSession(String gmailAddress)
            throws IOException, GeneralSecurityException {
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.auth.mechanisms", "XOAUTH2");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.port", "587");

        Authenticator auth = new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                try {
                    return new PasswordAuthentication(gmailAddress, fetchAccessToken());
                } catch (Exception e) {
                    throw new RuntimeException("OAuth2 token retrieval failed", e);
                }
            }
        };
        return Session.getInstance(props, auth);
    }
}

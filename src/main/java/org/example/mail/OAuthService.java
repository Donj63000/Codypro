package org.example.mail;

public sealed interface OAuthService
        permits GoogleAuthService, MicrosoftAuthService {

    int interactiveAuth();

    String getAccessToken();

    void refreshAccessToken();
}

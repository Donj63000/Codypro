package org.example.mail;

public interface OAuthService {
    int interactiveAuth();
    String getAccessToken();
    void refreshAccessToken();
}

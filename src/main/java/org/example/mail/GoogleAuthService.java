package org.example.mail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.example.dao.MailPrefsDAO;

import java.awt.Desktop;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Helper for Google OAuth authentication using Jackson for JSON parsing.
 */
public class GoogleAuthService implements OAuthService {
    private final MailPrefsDAO dao;
    private MailPrefs prefs;
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private String accessToken;

    public GoogleAuthService(MailPrefsDAO dao) {
        this.dao = dao;
        this.prefs = dao.load();
    }

    /** Construct using existing preferences without persistence. */
    public GoogleAuthService(MailPrefs prefs) {
        this.dao = null;
        this.prefs = prefs;
    }

    /**
     * Internal helper used when a previously created service lacked DAO
     * support but already fetched tokens.
     */
    public GoogleAuthService(MailPrefsDAO dao, MailPrefs prefs) {
        this.dao = dao;
        this.prefs = prefs;
    }

    /** Accessor for current OAuth preferences. */
    public MailPrefs prefs() { return prefs; }

    /** Whether this service can persist updates. */
    public boolean hasDao() { return dao != null; }

    /** Launch interactive OAuth flow in the user's browser and store refresh token. */
    @Override
    public synchronized int interactiveAuth() {
        String[] client = parseClient(prefs.oauthClient());
        if (client[0].isEmpty()) throw new IllegalStateException("Missing client id");
        int fallback = fallbackPort();
        HttpServer server = null;
        int port;
        try {
            String state = UUID.randomUUID().toString();
            CompletableFuture<String> codeFuture = new CompletableFuture<>();
            try {
                server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            } catch (Exception ex) {
                server = HttpServer.create(new InetSocketAddress("localhost", fallback), 0);
            }
            server.createContext("/oauth", ex -> {
                String query = ex.getRequestURI().getRawQuery();
                String error = extractParam(query, "error");
                String resp = (error == null)
                        ? "<html><body>You may close this window.</body></html>"
                        : "<html><body>Authorization failed: " + error + "</body></html>";
                ex.sendResponseHeaders(200, resp.length());
                try (OutputStream os = ex.getResponseBody()) { os.write(resp.getBytes()); }
                if (error != null) {
                    if (!codeFuture.isDone()) {
                        codeFuture.completeExceptionally(new IllegalStateException(error));
                    }
                    return;
                }
                String code = extractParam(query, "code");
                String returnedState = extractParam(query, "state");
                if (code != null && state.equals(returnedState)) {
                    codeFuture.complete(code);
                } else if (!codeFuture.isDone()) {
                    codeFuture.completeExceptionally(new IllegalStateException("Invalid state"));
                }
            });
            server.start();
            port = server.getAddress().getPort();
            String redirect = "http://localhost:" + port + "/oauth";

            byte[] buf = new byte[32];
            new SecureRandom().nextBytes(buf);
            String verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(verifier.getBytes(StandardCharsets.US_ASCII));
            String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(digest);

            String url = "https://accounts.google.com/o/oauth2/v2/auth" +
                    "?response_type=code" +
                    "&client_id=" + enc(client[0]) +
                    "&redirect_uri=" + enc(redirect) +
                    "&scope=" + enc("https://mail.google.com/") +
                    "&access_type=offline" +
                    "&prompt=consent" +
                    "&state=" + enc(state) +
                    "&code_challenge=" + enc(challenge) +
                    "&code_challenge_method=S256";
            Desktop.getDesktop().browse(URI.create(url));
            String code = codeFuture.join();

            HttpRequest req = HttpRequest.newBuilder(URI.create("https://oauth2.googleapis.com/token"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            "code=" + enc(code) +
                            "&client_id=" + enc(client[0]) +
                            "&code_verifier=" + enc(verifier) +
                            "&redirect_uri=" + enc(redirect) +
                            "&grant_type=authorization_code"))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode json = mapper.readTree(resp.body());
            accessToken = json.path("access_token").asText(null);
            String refresh = json.path("refresh_token").asText("");
            if (refresh.isEmpty()) refresh = prefs.oauthRefresh();
            long exp = json.path("expires_in").asLong();
            long expiry = System.currentTimeMillis() / 1000 + exp;
            prefs = updatePrefs(refresh, expiry);
            if (dao != null) dao.save(prefs);
            return port;
        } catch (Exception e) {
            throw new RuntimeException("Failed to start loopback server on ports 0 and " + fallback, e);
        } finally {
            if (server != null) server.stop(0);
        }
    }

    /** Return a valid access token, refreshing it when necessary. */
    @Override
    public synchronized String getAccessToken() {
        long now = System.currentTimeMillis() / 1000;
        if (accessToken == null || now >= prefs.oauthExpiry() - 60) {
            refreshAccessToken();
        }
        return accessToken;
    }

    /** Refresh the access token using the stored refresh token. */
    @Override
    public synchronized void refreshAccessToken() {
        String refresh = prefs.oauthRefresh();
        if (refresh.isBlank()) throw new IllegalStateException("No refresh token");
        String[] client = parseClient(prefs.oauthClient());
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create("https://oauth2.googleapis.com/token"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            "client_id=" + enc(client[0]) +
                            "&refresh_token=" + enc(refresh) +
                            "&grant_type=refresh_token"))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode json = mapper.readTree(resp.body());
            accessToken = json.path("access_token").asText(null);
            String newRefresh = json.path("refresh_token").asText("");
            if (newRefresh.isEmpty()) newRefresh = prefs.oauthRefresh();
            long exp = json.path("expires_in").asLong();
            long expiry = System.currentTimeMillis() / 1000 + exp;
            prefs = updatePrefs(newRefresh, expiry);
            if (dao != null) dao.save(prefs);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String[] parseClient(String val) {
        if (val == null) throw new IllegalArgumentException("No OAuth client configured");
        String[] parts = val.split(":", 2);
        if (parts.length < 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new IllegalArgumentException("Client ID and secret must be provided");
        }
        return parts;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String extractParam(String query, String name) {
        if (query == null) return null;
        for (String p : query.split("&")) {
            int idx = p.indexOf('=');
            if (idx > 0 && name.equals(p.substring(0, idx))) {
                return URLDecoder.decode(p.substring(idx + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static int fallbackPort() {
        String prop = System.getProperty("oauth.port");
        if (prop == null || prop.isBlank()) prop = System.getenv("OAUTH_PORT");
        if (prop != null && !prop.isBlank()) {
            try { return Integer.parseInt(prop); } catch (Exception ignore) {}
        }
        return 53682;
    }

    private MailPrefs updatePrefs(String refresh, long expiry) {
        return new MailPrefs(
                prefs.host(), prefs.port(), prefs.ssl(),
                prefs.user(), prefs.pwd(),
                "gmail", prefs.oauthClient(),
                refresh, expiry,
                prefs.from(), prefs.copyToSelf(), prefs.delayHours(),
                prefs.style(),
                prefs.subjPresta(), prefs.bodyPresta(),
                prefs.subjSelf(), prefs.bodySelf()
        );
    }
}

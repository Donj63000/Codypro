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

    /** Launch interactive OAuth flow in the user's browser and store refresh token. */
    @Override
    public synchronized void interactiveAuth() {
        String[] client = parseClient(prefs.oauthClient());
        if (client[0].isEmpty()) throw new IllegalStateException("Missing client id");
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            CompletableFuture<String> codeFuture = new CompletableFuture<>();
            server.createContext("/oauth", ex -> {
                String query = ex.getRequestURI().getRawQuery();
                String resp = "<html><body>You may close this window.</body></html>";
                ex.sendResponseHeaders(200, resp.length());
                try (OutputStream os = ex.getResponseBody()) { os.write(resp.getBytes()); }
                String code = extractParam(query, "code");
                if (code != null) codeFuture.complete(code);
            });
            server.start();
            int port = server.getAddress().getPort();
            String redirect = "http://localhost:" + port + "/oauth";
            String url = "https://accounts.google.com/o/oauth2/v2/auth" +
                    "?response_type=code" +
                    "&client_id=" + enc(client[0]) +
                    "&redirect_uri=" + enc(redirect) +
                    "&scope=" + enc("https://mail.google.com/") +
                    "&access_type=offline" +
                    "&prompt=consent";
            Desktop.getDesktop().browse(URI.create(url));
            String code = codeFuture.join();
            server.stop(0);

            HttpRequest req = HttpRequest.newBuilder(URI.create("https://oauth2.googleapis.com/token"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            "code=" + enc(code) +
                            "&client_id=" + enc(client[0]) +
                            "&client_secret=" + enc(client[1]) +
                            "&redirect_uri=" + enc(redirect) +
                            "&grant_type=authorization_code"))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode json = mapper.readTree(resp.body());
            accessToken = json.path("access_token").asText(null);
            String refresh = json.path("refresh_token").asText("");
            long exp = json.path("expires_in").asLong();
            long expiry = System.currentTimeMillis() / 1000 + exp;
            prefs = updatePrefs(refresh, expiry);
            if (dao != null) dao.save(prefs);
        } catch (Exception e) {
            throw new RuntimeException(e);
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
                            "&client_secret=" + enc(client[1]) +
                            "&refresh_token=" + enc(refresh) +
                            "&grant_type=refresh_token"))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode json = mapper.readTree(resp.body());
            accessToken = json.path("access_token").asText(null);
            long exp = json.path("expires_in").asLong();
            long expiry = System.currentTimeMillis() / 1000 + exp;
            prefs = updatePrefs(refresh, expiry);
            if (dao != null) dao.save(prefs);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String[] parseClient(String val) {
        String[] c = val == null ? new String[]{"", ""} : val.split(":", 2);
        if (c.length < 2) {
            String[] tmp = new String[2];
            tmp[0] = c.length > 0 ? c[0] : "";
            tmp[1] = "";
            c = tmp;
        }
        return c;
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

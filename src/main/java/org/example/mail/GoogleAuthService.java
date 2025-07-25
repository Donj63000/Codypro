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
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class GoogleAuthService implements OAuthService {
    private static final String SCOPE = "https://mail.google.com/ https://www.googleapis.com/auth/userinfo.email";
    private static final int    EXPIRY_MARGIN = 60;

    private final MailPrefsDAO dao;
    private MailPrefs          prefs;
    private final HttpClient   http  = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private String accessToken;

    public GoogleAuthService(MailPrefsDAO dao)          { this.dao = dao;  this.prefs = dao.load(); }
    public GoogleAuthService(MailPrefs prefs)           { this.dao = null; this.prefs = prefs;      }
    public GoogleAuthService(MailPrefsDAO dao, MailPrefs prefs) { this.dao = dao; this.prefs = prefs; }

    public MailPrefs prefs()  { return prefs; }
    public boolean   hasDao() { return dao != null; }

    @Override
    public synchronized int interactiveAuth() {
        String[] client = splitClient(prefs.oauthClient());
        int fallback = fallbackPort();

        HttpServer server;
        try {
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (Exception e) {                                       // port 0 indisponible
            try { server = HttpServer.create(new InetSocketAddress("localhost", fallback), 0); }
            catch (Exception ex) { throw new RuntimeException(ex); }
        }

        CompletableFuture<String> codeFuture = new CompletableFuture<>();
        String state = UUID.randomUUID().toString();

        server.createContext("/oauth", ex -> {
            String q    = ex.getRequestURI().getRawQuery();
            String err  = param(q, "error");
            String html = (err == null)
                    ? "<html><body>You may close this window.</body></html>"
                    : "<html><body>Authorization failed: " + err + "</body></html>";
            ex.sendResponseHeaders(200, 0);
            try (OutputStream os = ex.getResponseBody()) { os.write(html.getBytes(StandardCharsets.UTF_8)); }

            if (err != null) {
                codeFuture.completeExceptionally(new IllegalStateException(err));
                return;
            }
            String code = param(q, "code");
            if (code != null && state.equals(param(q, "state"))) codeFuture.complete(code);
        });
        server.start();

        int    port     = server.getAddress().getPort();
        String redirect = "http://localhost:" + port + "/oauth";

        /* ---------- PKCE : challenge ----------- */
        byte[] verBytes = new byte[32];
        new SecureRandom().nextBytes(verBytes);
        String verifier  = Base64.getUrlEncoder().withoutPadding().encodeToString(verBytes);
        byte[] digest    = sha256(verifier.getBytes(StandardCharsets.US_ASCII));
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(digest);

        /* ---------- ouverture navigateur -------- */
        String authUrl = "https://accounts.google.com/o/oauth2/v2/auth"
                + "?response_type=code"
                + "&client_id="          + enc(client[0])
                + "&redirect_uri="       + enc(redirect)
                + "&scope="              + enc(SCOPE)
                + "&access_type=offline"
                + "&prompt=consent"
                + "&state="              + enc(state)
                + "&code_challenge="     + enc(challenge)
                + "&code_challenge_method=S256";

        try { if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI.create(authUrl)); }
        catch (Exception e) { throw new RuntimeException("Cannot open browser", e); }

        try {
            /* ---------- échange code ↔ token -------- */
            String code = codeFuture.join();
            HttpResponse<String> tokenResp = http.send(
                    HttpRequest.newBuilder(URI.create("https://oauth2.googleapis.com/token"))
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .POST(HttpRequest.BodyPublishers.ofString(
                                    "code="          + enc(code)      +
                                            "&client_id="    + enc(client[0]) +
                                            "&code_verifier="+ enc(verifier)  +
                                            "&redirect_uri=" + enc(redirect)  +
                                            "&grant_type=authorization_code"))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());

            JsonNode j = mapper.readTree(tokenResp.body());
            accessToken         = j.path("access_token").asText(null);
            String refreshToken = j.path("refresh_token").asText(prefs.oauthRefresh());
            long   expiry       = System.currentTimeMillis()/1000 + j.path("expires_in").asLong();

            /* ---------- e‑mail utilisateur --------- */
            String email = prefs.user();
            if (email == null || email.isBlank()) {
                try {
                    HttpResponse<String> who = http.send(
                            HttpRequest.newBuilder(URI.create("https://www.googleapis.com/oauth2/v3/userinfo"))
                                    .header("Authorization", "Bearer " + accessToken)
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
                    email = mapper.readTree(who.body()).path("email").asText("");
                } catch (Exception ignore) {}
            }

            prefs = updatePrefs(refreshToken, expiry, email);
            if (dao != null) dao.save(prefs);
            return port;
        } catch (Exception e) {
            throw new RuntimeException("Failed to complete Google OAuth flow", e);
        } finally {
            server.stop(0);
        }
    }

    @Override
    public synchronized String getAccessToken() {
        long now = System.currentTimeMillis() / 1000;
        if (accessToken == null || now >= prefs.oauthExpiry() - EXPIRY_MARGIN) refreshAccessToken();
        return accessToken;
    }

    @Override
    public synchronized void refreshAccessToken() {
        String refresh = prefs.oauthRefresh();
        if (refresh.isBlank()) throw new IllegalStateException("No refresh token");
        String[] client = splitClient(prefs.oauthClient());

        try {
            HttpResponse<String> resp = http.send(
                    HttpRequest.newBuilder(URI.create("https://oauth2.googleapis.com/token"))
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .POST(HttpRequest.BodyPublishers.ofString(
                                    "client_id="     + enc(client[0]) +
                                            "&refresh_token="+ enc(refresh)   +
                                            "&grant_type=refresh_token"))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());

            JsonNode j = mapper.readTree(resp.body());
            accessToken = j.path("access_token").asText(null);
            long expiry = System.currentTimeMillis()/1000 + j.path("expires_in").asLong();

            prefs = updatePrefs(refresh, expiry, prefs.user());
            if (dao != null) dao.save(prefs);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /* ---------- helpers internes ---------- */
    private static byte[] sha256(byte[] in) {
        try { return MessageDigest.getInstance("SHA-256").digest(in); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA‑256 unavailable", e); }
    }

    private static String[] splitClient(String val) {
        if (val == null) throw new IllegalArgumentException("No OAuth client configured");
        String[] p = val.split(":", 2);
        if (p.length != 2 || p[0].isBlank() || p[1].isBlank())
            throw new IllegalArgumentException("Client ID and secret must be provided");
        return p;
    }

    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }

    private static String param(String q, String name) {
        if (q == null) return null;
        for (String p : q.split("&")) {
            int idx = p.indexOf('=');
            if (idx > 0 && name.equals(p.substring(0, idx)))
                return URLDecoder.decode(p.substring(idx + 1), StandardCharsets.UTF_8);
        }
        return null;
    }

    private static int fallbackPort() {
        String v = System.getProperty("oauth.port", System.getenv("OAUTH_PORT"));
        try { return (v == null || v.isBlank()) ? 53682 : Integer.parseInt(v); }
        catch (Exception e) { return 53682; }
    }

    private MailPrefs updatePrefs(String refresh, long expiry, String user) {
        return new MailPrefs(
                prefs.host(), prefs.port(), prefs.ssl(),
                user == null ? "" : user,
                prefs.pwd(),
                "gmail",
                prefs.oauthClient(),
                refresh,
                expiry,
                prefs.from().isBlank() && user != null ? user : prefs.from(),
                prefs.copyToSelf(),
                prefs.delayHours(),
                prefs.style(),
                prefs.subjPresta(), prefs.bodyPresta(),
                prefs.subjSelf(),  prefs.bodySelf());
    }
}

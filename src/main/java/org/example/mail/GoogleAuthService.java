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
 * <p>
 * Nouveauté : après l’obtention du premier access‑token, on interroge l’API
 * <code>/oauth2/v3/userinfo</code> afin de récupérer l’adresse e‑mail et de la
 * stocker dans {@link MailPrefs#user()}. Cela évite l’erreur SMTP
 * « Invalid SASL username » lors du premier envoi.
 * </p>
 */
public class GoogleAuthService implements OAuthService {
    private final MailPrefsDAO dao;
    private MailPrefs prefs;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    /** Dernier access‑token valide (ré‑utilisé jusqu’à expiration). */
    private String accessToken;

    /* ------------------------------------------------------------------ */
    /*                         CONSTRUCTEURS                              */
    /* ------------------------------------------------------------------ */

    public GoogleAuthService(MailPrefsDAO dao) {
        this.dao = dao;
        this.prefs = dao.load();
    }

    /** Constructeur sans persistance (tests unitaires, etc.). */
    public GoogleAuthService(MailPrefs prefs) {
        this.dao = null;
        this.prefs = prefs;
    }

    /** Constructeur interne : DAO + prefs déjà chargés. */
    public GoogleAuthService(MailPrefsDAO dao, MailPrefs prefs) {
        this.dao = dao;
        this.prefs = prefs;
    }

    /* ------------------------------------------------------------------ */
    /*                        ACCESSEURS UTILES                           */
    /* ------------------------------------------------------------------ */

    public MailPrefs prefs()                { return prefs; }
    public boolean   hasDao()               { return dao != null; }

    /* ------------------------------------------------------------------ */
    /*                       FLUX D’AUTHENTIFICATION                      */
    /* ------------------------------------------------------------------ */

    /**
     * Lance le flow OAuth « loop‑back server ». À la fin :
     * <ul>
     *   <li>un refresh‑token durable est stocké ;</li>
     *   <li>l’adresse Gmail est renseignée dans <em>prefs.user()</em>.</li>
     * </ul>
     *
     * @return le port TCP utilisé par le serveur local.
     */
    @Override
    public synchronized int interactiveAuth() {
        String[] client = parseClient(prefs.oauthClient());
        if (client[0].isEmpty())
            throw new IllegalStateException("Missing client id");

        int fallbackPort = fallbackPort();
        HttpServer server = null;

        try {
            /* ------------ 1) serveur local pour capter le code --------- */
            CompletableFuture<String> codeFuture = new CompletableFuture<>();
            try {
                server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            } catch (Exception ex) {
                // port auto impossible → on tente le port fallback
                server = HttpServer.create(new InetSocketAddress("localhost", fallbackPort), 0);
            }

            String state = UUID.randomUUID().toString();
            server.createContext("/oauth", ex -> {
                String query = ex.getRequestURI().getRawQuery();
                String error = extractParam(query, "error");

                String html = (error == null)
                        ? "<html><body>You may close this window.</body></html>"
                        : "<html><body>Authorization failed: " + error + "</body></html>";

                ex.sendResponseHeaders(200, html.length());
                try (OutputStream os = ex.getResponseBody()) { os.write(html.getBytes()); }

                if (error != null) {
                    codeFuture.completeExceptionally(new IllegalStateException(error));
                    return;
                }
                String code  = extractParam(query, "code");
                String check = extractParam(query, "state");
                if (code != null && state.equals(check))
                    codeFuture.complete(code);
            });
            server.start();
            int port = server.getAddress().getPort();
            String redirect = "http://localhost:" + port + "/oauth";

            /* ------------ 2) PKCE (verifier + challenge) --------------- */
            byte[] buf = new byte[32];
            new SecureRandom().nextBytes(buf);
            String verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(verifier.getBytes(StandardCharsets.US_ASCII));
            String challenge = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(digest);

            /* ------------ 3) ouverture du navigateur ------------------- */
            String url = "https://accounts.google.com/o/oauth2/v2/auth" +
                    "?response_type=code" +
                    "&client_id=" + enc(client[0]) +
                    "&redirect_uri=" + enc(redirect) +
                    "&scope=" + enc("https://mail.google.com/ https://www.googleapis.com/auth/userinfo.email") +
                    "&access_type=offline" +
                    "&prompt=consent" +
                    "&state=" + enc(state) +
                    "&code_challenge=" + enc(challenge) +
                    "&code_challenge_method=S256";
            Desktop.getDesktop().browse(URI.create(url));

            /* ------------ 4) échange code ↔ tokens --------------------- */
            String code = codeFuture.join();
            HttpRequest tokenReq = HttpRequest.newBuilder(URI.create("https://oauth2.googleapis.com/token"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            "code=" + enc(code) +
                                    "&client_id=" + enc(client[0]) +
                                    "&code_verifier=" + enc(verifier) +
                                    "&redirect_uri=" + enc(redirect) +
                                    "&grant_type=authorization_code"))
                    .build();
            HttpResponse<String> tokenResp = http.send(tokenReq, HttpResponse.BodyHandlers.ofString());
            JsonNode json = mapper.readTree(tokenResp.body());

            this.accessToken = json.path("access_token").asText(null);
            String refresh   = json.path("refresh_token").asText("");
            if (refresh.isEmpty()) refresh = prefs.oauthRefresh();
            long expiry = (System.currentTimeMillis() / 1000)
                    + json.path("expires_in").asLong();

            /* ------------ 5) récupération de l’adresse e‑mail ---------- */
            String email = prefs.user();               // valeur existante
            if (email == null || email.isBlank()) {
                try {
                    HttpRequest whoReq = HttpRequest.newBuilder(
                                    URI.create("https://www.googleapis.com/oauth2/v3/userinfo"))
                            .header("Authorization", "Bearer " + accessToken)
                            .GET()
                            .build();
                    HttpResponse<String> whoResp = http.send(
                            whoReq, HttpResponse.BodyHandlers.ofString());
                    email = mapper.readTree(whoResp.body())
                            .path("email").asText("");
                } catch (Exception ignore) {
                    // en cas d’échec on laisse l’utilisateur vide,
                    // le champ pourra être saisi manuellement
                }
            }

            /* ------------ 6) persistance des prefs --------------------- */
            prefs = updatePrefs(refresh, expiry, email);
            if (dao != null) dao.save(prefs);

            return port;

        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to complete Google OAuth flow", e);

        } finally {
            if (server != null) server.stop(0);
        }
    }

    /* ------------------------------------------------------------------ */
    /*                    GESTION DES ACCESS‑TOKENS                       */
    /* ------------------------------------------------------------------ */

    @Override
    public synchronized String getAccessToken() {
        long now = System.currentTimeMillis() / 1000;
        if (accessToken == null || now >= prefs.oauthExpiry() - 60) {
            refreshAccessToken();
        }
        return accessToken;
    }

    @Override
    public synchronized void refreshAccessToken() {
        String refresh = prefs.oauthRefresh();
        if (refresh.isBlank())
            throw new IllegalStateException("No refresh token");

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

            this.accessToken = json.path("access_token").asText(null);
            long expiry = (System.currentTimeMillis() / 1000)
                    + json.path("expires_in").asLong();

            prefs = updatePrefs(refresh, expiry, prefs.user());
            if (dao != null) dao.save(prefs);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /* ------------------------------------------------------------------ */
    /*                         OUTILS INTERNES                            */
    /* ------------------------------------------------------------------ */

    private static String[] parseClient(String val) {
        if (val == null) throw new IllegalArgumentException("No OAuth client configured");
        String[] parts = val.split(":", 2);
        if (parts.length < 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new IllegalArgumentException("Client ID and secret must be provided");
        }
        return parts;
    }

    /** URL‑encode UTF‑8. */
    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    /** Extrait un paramètre de requête GET. */
    private static String extractParam(String query, String name) {
        if (query == null) return null;
        for (String p : query.split("&")) {
            int idx = p.indexOf('=');
            if (idx > 0 && name.equals(p.substring(0, idx)))
                return URLDecoder.decode(p.substring(idx + 1), StandardCharsets.UTF_8);
        }
        return null;
    }

    /** Port fallback lorsque le port 0 (auto) n’est pas disponible. */
    private static int fallbackPort() {
        String prop = System.getProperty("oauth.port");
        if (prop == null || prop.isBlank()) prop = System.getenv("OAUTH_PORT");
        if (prop != null && !prop.isBlank())
            try { return Integer.parseInt(prop); } catch (Exception ignore) {}
        return 53682;
    }

    /* ------------------------------------------------------------------ */
    /*                  MISE À JOUR DES PRÉFÉRENCES                       */
    /* ------------------------------------------------------------------ */

    /**
     * Construit un nouvel objet {@link MailPrefs} à partir des anciennes
     * préférences + des nouvelles données OAuth.
     */
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
                prefs.subjSelf(),  prefs.bodySelf()
        );
    }

    /* --- surcharge conservant l’API historique ---------------------- */
    private MailPrefs updatePrefs(String refresh, long expiry) {
        return updatePrefs(refresh, expiry, prefs.user());
    }
}

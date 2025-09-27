package org.example.mail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.example.dao.MailPrefsDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

public final class MicrosoftAuthService implements OAuthService {
    private static final Logger log = LoggerFactory.getLogger(MicrosoftAuthService.class);
    private static final String AUTH_URL   = "https://login.microsoftonline.com/common/oauth2/v2.0/authorize";
    private static final String TOKEN_URL  = "https://login.microsoftonline.com/common/oauth2/v2.0/token";
    private static final String SCOPE      = "offline_access https://outlook.office.com/SMTP.Send";
    private static final int    EXP_MARGIN = 60;

    private final MailPrefsDAO dao;
    private MailPrefs prefs;
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private String accessToken;

    public MicrosoftAuthService(MailPrefsDAO dao) {
        this(dao, dao.load());
    }

    public MicrosoftAuthService(MailPrefsDAO dao, MailPrefs prefs) {
        this.dao = dao;
        this.prefs = prefs;
    }

    public MicrosoftAuthService(MailPrefs prefs) {
        this.dao = null;
        this.prefs = prefs;
    }

    public MailPrefs prefs() {
        return prefs;
    }

    public boolean hasDao() {
        return dao != null;
    }

    @Override
    public synchronized int interactiveAuth() {
        String[] client = splitClient(prefs.oauthClient());
        HttpServer srv = null;
        try {
            try {
                srv = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            } catch (Exception e) {
                srv = HttpServer.create(new InetSocketAddress("localhost", fallbackPort()), 0);
            }

            CompletableFuture<String> codeFuture = new CompletableFuture<>();
            srv.createContext("/oauth", ex -> {
                String q = ex.getRequestURI().getRawQuery();
                String err = param(q, "error");
                String page = err == null
                        ? "You may close this window."
                        : "Authorization failed: " + err;
                ex.sendResponseHeaders(200, 0);
                try (OutputStream os = ex.getResponseBody()) {
                    os.write(("<html><body>" + page + "</body></html>").getBytes());
                }
                if (err == null) {
                    String code = param(q, "code");
                    if (code != null) codeFuture.complete(code);
                } else {
                    codeFuture.completeExceptionally(new IllegalStateException(err));
                }
            });
            srv.start();
            log.debug("[MS OAuth] local HTTP receiver started");

            int port = srv.getAddress().getPort();
            String redirect = "http://localhost:" + port + "/oauth";
            String open = AUTH_URL
                    + "?response_type=code"
                    + "&client_id=" + enc(client[0])
                    + "&redirect_uri=" + enc(redirect)
                    + "&scope=" + enc(SCOPE)
                    + "&prompt=consent";
            log.debug("[MS OAuth] opening browser for user consent (redirect={})", redirect);
            Desktop.getDesktop().browse(URI.create(open));

            String code = codeFuture.join();
            HttpRequest req = HttpRequest.newBuilder(URI.create(TOKEN_URL))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            "code=" + enc(code)
                                    + "&client_id=" + enc(client[0])
                                    + "&client_secret=" + enc(client[1])
                                    + "&redirect_uri=" + enc(redirect)
                                    + "&grant_type=authorization_code"))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode j = mapper.readTree(resp.body());
            accessToken = j.path("access_token").asText("");
            String refresh = j.path("refresh_token").asText("");
            long expiry = System.currentTimeMillis() / 1000 + j.path("expires_in").asLong();
            prefs = updatePrefs(refresh, expiry);
            if (dao != null) dao.save(prefs);
            log.debug("[MS OAuth] received tokens; expiry={}s", j.path("expires_in").asLong());
            return port;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (srv != null) srv.stop(0);
        }
    }

    @Override
    public synchronized String getAccessToken() {
        long now = System.currentTimeMillis() / 1000;
        if (accessToken == null || now >= prefs.oauthExpiry() - EXP_MARGIN) refreshAccessToken();
        return accessToken;
    }

    @Override
    public synchronized void refreshAccessToken() {
        String refresh = prefs.oauthRefresh();
        if (refresh.isBlank()) throw new IllegalStateException("No refresh token");
        String[] client = splitClient(prefs.oauthClient());
        try {
            log.debug("[MS OAuth] refreshing access token");
            HttpRequest req = HttpRequest.newBuilder(URI.create(TOKEN_URL))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            "client_id=" + enc(client[0])
                                    + "&client_secret=" + enc(client[1])
                                    + "&refresh_token=" + enc(refresh)
                                    + "&grant_type=refresh_token"))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode j = mapper.readTree(resp.body());
            accessToken = j.path("access_token").asText("");
            long expiry = System.currentTimeMillis() / 1000 + j.path("expires_in").asLong();
            prefs = updatePrefs(refresh, expiry);
            if (dao != null) dao.save(prefs);
            log.debug("[MS OAuth] refresh OK; expires in {}s", j.path("expires_in").asLong());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String[] splitClient(String v) {
        if (v == null) throw new IllegalArgumentException("No OAuth client configured");
        String[] p = v.split(":", 2);
        if (p.length != 2 || p[0].isBlank() || p[1].isBlank())
            throw new IllegalArgumentException("Client ID and secret must be provided");
        return p;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String param(String q, String n) {
        if (q == null) return null;
        for (String p : q.split("&")) {
            int i = p.indexOf('=');
            if (i > 0 && n.equals(p.substring(0, i)))
                return URLDecoder.decode(p.substring(i + 1), StandardCharsets.UTF_8);
        }
        return null;
    }

    private static int fallbackPort() {
        String v = System.getProperty("oauth.port", System.getenv("OAUTH_PORT"));
        try {
            return v == null || v.isBlank() ? 53682 : Integer.parseInt(v);
        } catch (Exception ignore) {
            return 53682;
        }
    }

    private MailPrefs updatePrefs(String refresh, long expiry) {
        return new MailPrefs(
                prefs.host(), prefs.port(), prefs.ssl(),
                prefs.user(), prefs.pwd(),
                "outlook", prefs.oauthClient(),
                refresh, expiry,
                prefs.from(), prefs.copyToSelf(), prefs.delayHours(),
                prefs.style(),
                prefs.subjPresta(), prefs.bodyPresta(),
                prefs.subjSelf(), prefs.bodySelf()
        );
    }
}

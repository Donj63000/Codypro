package org.example.mail;

import com.sun.net.httpserver.HttpServer;
import org.example.dao.MailPrefsDAO;

import java.awt.Desktop;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Helper for Microsoft Outlook/Office365 OAuth authentication. */
public class MicrosoftAuthService implements OAuthService {
    private final MailPrefsDAO dao;
    private MailPrefs prefs;
    private final HttpClient http = HttpClient.newHttpClient();
    private String accessToken;

    public MicrosoftAuthService(MailPrefsDAO dao) {
        this.dao = dao;
        this.prefs = dao.load();
    }

    /** Construct using existing preferences without persistence. */
    public MicrosoftAuthService(MailPrefs prefs) {
        this.dao = null;
        this.prefs = prefs;
    }

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
            String url = "https://login.microsoftonline.com/common/oauth2/v2.0/authorize" +
                    "?response_type=code" +
                    "&client_id=" + enc(client[0]) +
                    "&redirect_uri=" + enc(redirect) +
                    "&scope=" + enc("offline_access https://outlook.office.com/SMTP.Send") +
                    "&prompt=consent";
            Desktop.getDesktop().browse(URI.create(url));
            String code = codeFuture.join();
            server.stop(0);

            HttpRequest req = HttpRequest.newBuilder(URI.create("https://login.microsoftonline.com/common/oauth2/v2.0/token"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            "code=" + enc(code) +
                            "&client_id=" + enc(client[0]) +
                            "&client_secret=" + enc(client[1]) +
                            "&redirect_uri=" + enc(redirect) +
                            "&grant_type=authorization_code"))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            accessToken = jsonString(resp.body(), "access_token");
            String refresh = jsonString(resp.body(), "refresh_token");
            long exp = jsonLong(resp.body(), "expires_in");
            long expiry = System.currentTimeMillis() / 1000 + exp;
            prefs = updatePrefs(refresh, expiry);
            if (dao != null) dao.save(prefs);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

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
        if (refresh.isBlank()) throw new IllegalStateException("No refresh token");
        String[] client = parseClient(prefs.oauthClient());
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create("https://login.microsoftonline.com/common/oauth2/v2.0/token"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            "client_id=" + enc(client[0]) +
                            "&client_secret=" + enc(client[1]) +
                            "&refresh_token=" + enc(refresh) +
                            "&grant_type=refresh_token"))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            accessToken = jsonString(resp.body(), "access_token");
            long exp = jsonLong(resp.body(), "expires_in");
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

    private static String jsonString(String body, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]*)\"").matcher(body);
        return m.find() ? m.group(1) : null;
    }

    private static long jsonLong(String body, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*(\\d+)").matcher(body);
        return m.find() ? Long.parseLong(m.group(1)) : 0L;
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

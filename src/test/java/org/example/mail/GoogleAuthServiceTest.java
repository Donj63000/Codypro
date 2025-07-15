package org.example.mail;

import org.example.dao.MailPrefsDAO;
import org.example.dao.DB;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.*;

import java.lang.reflect.Field;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient.Version;
import java.sql.Connection;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.time.Duration;
import java.net.CookieHandler;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class GoogleAuthServiceTest {
    private String url;
    private MailPrefsDAO dao;
    private SecretKey key;

    @BeforeEach
    void setUp() throws Exception {
        url = "file:google?mode=memory&cache=shared";
        try (Connection conn = DB.newConnection(url); Statement st = conn.createStatement()) {
            st.executeUpdate("""
                CREATE TABLE mail_prefs (
                    id INTEGER PRIMARY KEY CHECK(id=1),
                    host TEXT NOT NULL,
                    port INTEGER NOT NULL,
                    ssl INTEGER NOT NULL DEFAULT 1,
                    user TEXT,
                    pwd TEXT,
                    provider TEXT,
                    oauth_client TEXT,
                    oauth_refresh TEXT,
                    oauth_expiry INTEGER,
                    from_addr TEXT NOT NULL,
                    copy_to_self TEXT NOT NULL DEFAULT '',
                    delay_hours INTEGER NOT NULL DEFAULT 48,
                    style TEXT,
                    subj_tpl_presta TEXT NOT NULL,
                    body_tpl_presta TEXT NOT NULL,
                    subj_tpl_self TEXT NOT NULL,
                    body_tpl_self TEXT NOT NULL
                )
            """);
        }
        key = new SecretKeySpec(new byte[16], "AES");
        dao = new MailPrefsDAO(() -> DB.newConnection(url), key);
        MailPrefs prefs = new MailPrefs(
                "smtp.gmail.com", 465, true,
                "user", "pwd",
                "gmail", "id:secret", "refresh", 0L,
                "from@test.com", "", 48,
                "fr",
                "s1", "b1", "s2", "b2");
        dao.save(prefs);
    }



    @Test
    void testTokenRefresh() throws Exception {
        GoogleAuthService gs = new GoogleAuthService(dao);
        // inject stub client
        StubHttpClient stub = new StubHttpClient("{\"access_token\":\"tok\",\"expires_in\":3600}");
        Field f = GoogleAuthService.class.getDeclaredField("http");
        f.setAccessible(true);
        f.set(gs, stub);

        String token = gs.getAccessToken();
        assertEquals("tok", token);
        assertNotNull(stub.lastRequest);
        assertTrue(stub.lastRequest.bodyPublisher().isPresent());

        MailPrefs stored = dao.load();
        assertEquals("refresh", stored.oauthRefresh());
        assertTrue(stored.oauthExpiry() > 0);
    }

    private static class StubHttpClient extends HttpClient {
        final String body;
        final HttpClient delegate = HttpClient.newHttpClient();
        HttpRequest lastRequest;
        StubHttpClient(String body) { this.body = body; }
        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
            this.lastRequest = request;
            return (HttpResponse<T>) new SimpleResponse(request, body);
        }
        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
            return CompletableFuture.completedFuture(send(request, handler));
        }
        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest req, HttpResponse.BodyHandler<T> h, HttpResponse.PushPromiseHandler<T> p) {
            return CompletableFuture.completedFuture(send(req, h));
        }
        private static class SimpleResponse implements HttpResponse<String> {
            private final HttpRequest req;
            private final String body;
            SimpleResponse(HttpRequest req, String body) { this.req = req; this.body = body; }
            public int statusCode() { return 200; }
            public HttpRequest request() { return req; }
            public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
            public HttpHeaders headers() { return HttpHeaders.of(Map.of(), (a,b)->true); }
            public String body() { return body; }
            public Optional<javax.net.ssl.SSLSession> sslSession() { return Optional.empty(); }
            public java.net.URI uri() { return req.uri(); }
            public Version version() { return Version.HTTP_1_1; }
        }
        public Optional<java.net.ProxySelector> proxy() { return delegate.proxy(); }
        public Optional<java.net.Authenticator> authenticator() { return delegate.authenticator(); }
        @Override                       // Java 17
        public Optional<Duration> connectTimeout() {
            return delegate.connectTimeout();        // on propage tel‑quel
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return delegate.cookieHandler();
        }
        public java.net.http.HttpClient.Redirect followRedirects() { return delegate.followRedirects(); }
        public javax.net.ssl.SSLContext sslContext() { return delegate.sslContext(); }
        public javax.net.ssl.SSLParameters sslParameters() { return delegate.sslParameters(); }
        public java.net.http.HttpClient.Version version() { return delegate.version(); }
        public java.util.Optional<java.util.concurrent.Executor> executor() { return delegate.executor(); }
        public void close() { delegate.close(); }
    }
}

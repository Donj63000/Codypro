package org.example.dao;

import org.example.mail.MailPrefs;
import org.example.mail.SmtpPreset;
import org.junit.jupiter.api.*;

import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;

public class MailPrefsDAOTest {
    private Connection conn;
    private MailPrefsDAO dao;

    @BeforeEach
    void setUp() throws Exception {
        conn = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement st = conn.createStatement()) {
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
                    copy_to_self TEXT,
                    delay_hours INTEGER NOT NULL DEFAULT 48,
                    style TEXT,
                    subj_tpl_presta TEXT NOT NULL,
                    body_tpl_presta TEXT NOT NULL,
                    subj_tpl_self TEXT NOT NULL,
                    body_tpl_self TEXT NOT NULL
                )
            """);
        }
        dao = new MailPrefsDAO(conn);
    }

    @AfterEach
    void tearDown() throws Exception {
        conn.close();
    }

    @Test
    void testLoadPresetWhenEmpty() {
        MailPrefs prefs = dao.load();
        MailPrefs expected = MailPrefs.fromPreset(SmtpPreset.PRESETS[0]);
        assertEquals(expected, prefs);
        assertEquals("custom", prefs.provider());
        assertEquals("", prefs.oauthClient());
        assertEquals("", prefs.oauthRefresh());
        assertEquals(0L, prefs.oauthExpiry());
    }

    @Test
    void testSaveAndLoad() {
        MailPrefs prefs = new MailPrefs(
                "smtp.test.com", 25, false,
                "user", "pwd",
                "gmail", "client:secret", "refresh", 123L,
                "from@test.com", "copy@test.com", 12,
                "fr",
                "s1", "b1", "s2", "b2");
        dao.save(prefs);
        MailPrefs loaded = dao.load();
        assertEquals(prefs, loaded);
    }

    @Test
    void testRefreshTokenPersists() {
        MailPrefs prefs = new MailPrefs(
                "smtp.test.com", 25, false,
                "user", "pwd",
                "gmail", "client:secret", "tok1", 100L,
                "from@test.com", null, 12,
                "fr",
                "s1", "b1", "s2", "b2");
        dao.save(prefs);

        MailPrefs updated = new MailPrefs(
                prefs.host(), prefs.port(), prefs.ssl(),
                prefs.user(), prefs.pwd(),
                prefs.provider(), prefs.oauthClient(), prefs.oauthRefresh(),
                200L,
                prefs.from(), prefs.copyToSelf(), prefs.delayHours(),
                prefs.style(),
                prefs.subjPresta(), prefs.bodyPresta(),
                prefs.subjSelf(), prefs.bodySelf());
        dao.save(updated);

        MailPrefs loaded = dao.load();
        assertEquals("tok1", loaded.oauthRefresh());
        assertEquals(200L, loaded.oauthExpiry());
    }

    @Test
    void testStylePersists() {
        String[] en = MailPrefs.TEMPLATE_SETS.get("en");
        MailPrefs prefs = new MailPrefs(
                "smtp.test.com", 25, false,
                "u", "p",
                "custom", "", "", 0L,
                "from@test.com", null, 12,
                "en",
                en[0], en[1], en[2], en[3]);
        dao.save(prefs);

        MailPrefs loaded = dao.load();
        assertEquals("en", loaded.style());
        assertEquals(en[0], loaded.subjPresta());
    }
}

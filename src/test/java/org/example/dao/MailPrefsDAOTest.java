package org.example.dao;

import org.example.mail.MailPrefs;
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
    void testLoadDefaultWhenEmpty() {
        MailPrefs prefs = dao.load();
        assertEquals(MailPrefs.defaultValues(), prefs);
    }

    @Test
    void testSaveAndLoad() {
        MailPrefs prefs = new MailPrefs(
                "smtp.test.com", 25, false,
                "user", "pwd",
                "google", "client", "refresh", 123L,
                "from@test.com", "copy@test.com", 12,
                "s1", "b1", "s2", "b2");
        dao.save(prefs);
        MailPrefs loaded = dao.load();
        assertEquals(prefs, loaded);
    }
}

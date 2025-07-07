package org.example.gui;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.control.ButtonType;
import org.example.dao.MailPrefsDAO;
import org.example.mail.MailPrefs;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class MailQuickSetupDialogTest {
    private Connection conn;
    private MailPrefsDAO dao;

    @BeforeAll
    static void initJfx() {
        new JFXPanel();
    }

    @BeforeEach
    void setup() throws Exception {
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
    void testDialogResultPersists() throws Exception {
        MailPrefs initial = MailPrefs.defaultValues();
        dao.save(initial);

        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            MailQuickSetupDialog d = new MailQuickSetupDialog(initial, dao);
            MailPrefs res = d.getResultConverter().call(ButtonType.OK);
            dao.save(res);
            latch.countDown();
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));

        MailPrefs stored = dao.load();
        assertEquals(initial.host(), stored.host());
    }

    @Test
    void testTemplatesSwitchWithStyle() throws Exception {
        MailPrefs initial = MailPrefs.defaultValues();
        dao.save(initial);

        CountDownLatch latch = new CountDownLatch(1);
        final MailPrefs[] result = new MailPrefs[1];
        Platform.runLater(() -> {
            MailQuickSetupDialog d = new MailQuickSetupDialog(initial, dao);
            d.styleCombo().getSelectionModel().select("en");
            result[0] = d.getResultConverter().call(ButtonType.OK);
            latch.countDown();
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));

        assertEquals("en", result[0].style());
        assertEquals(MailPrefs.TEMPLATE_SETS.get("en")[0], result[0].subjPresta());
    }
}

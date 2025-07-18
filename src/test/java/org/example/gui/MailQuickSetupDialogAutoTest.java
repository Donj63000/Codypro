package org.example.gui;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import org.example.dao.MailPrefsDAO;
import org.example.dao.DB;
import org.example.mail.MailPrefs;
import org.example.mail.autodetect.AutoConfigProvider;
import org.example.mail.autodetect.AutoConfigResult;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.Statement;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class MailQuickSetupDialogAutoTest {
    private String url;
    private MailPrefsDAO dao;
    private SecretKey key;

    @BeforeAll
    static void initJfx() { new JFXPanel(); }

    @AfterAll
    static void shutdown() { Platform.exit(); }

    @BeforeEach
    void setup() throws Exception {
        url = "file:prefsauto?mode=memory&cache=shared";
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
    }


    @Test
    void testAutoDiscoveryPopulatesFields() throws Exception {
        MailPrefs prefs = MailPrefs.defaultValues();
        dao.save(prefs);
        StubProvider stub = new StubProvider();
        stub.result = new AutoConfigResult("auto.smtp", 2525, false);
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            MailQuickSetupDialog d = new MailQuickSetupDialog(prefs, dao, stub);
            d.fromField().setText("user@test.com");
            new Thread(() -> {
                try { stub.called.await(); Thread.sleep(50); } catch(Exception ignore) {}
                Platform.runLater(() -> {
                    assertEquals("auto.smtp", d.hostField().getText());
                    assertEquals("2525", d.portField().getText());
                    assertFalse(d.sslBox().isSelected());
                    latch.countDown();
                });
            }).start();
        });
        assertTrue(latch.await(3, TimeUnit.SECONDS));
    }

    private static class StubProvider implements AutoConfigProvider {
        AutoConfigResult result;
        CountDownLatch called = new CountDownLatch(1);
        @Override
        public AutoConfigResult discover(String email) {
            called.countDown();
            return result;
        }
    }
}

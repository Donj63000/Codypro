package org.example.mail;

import jakarta.mail.Session;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

public class MailerTest {
    @Test
    public void outlookSessionUsesXoauth2() throws Exception {
        SmtpPreset preset = SmtpPreset.byProvider("outlook");
        MailPrefs cfg = MailPrefs.fromPreset(preset);
        // minimal user info
        cfg = new MailPrefs(
                cfg.host(), cfg.port(), cfg.ssl(),
                "user@example.com", "",
                "outlook", "", "", 0L,
                "from@example.com", "", cfg.delayHours(),
                cfg.style(),
                cfg.subjPresta(), cfg.bodyPresta(),
                cfg.subjSelf(), cfg.bodySelf()
        );

        Method m = Mailer.class.getDeclaredMethod("makeSessionOAuth", MailPrefs.class, String.class);
        m.setAccessible(true);
        Session session = (Session) m.invoke(null, cfg, "testToken");

        Properties p = session.getProperties();
        assertEquals("true", p.getProperty("mail.smtp.sasl.enable"));
        assertEquals("XOAUTH2", p.getProperty("mail.smtp.auth.mechanisms"));
    }
}

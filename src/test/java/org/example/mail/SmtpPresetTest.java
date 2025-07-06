package org.example.mail;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SmtpPresetTest {
    @Test
    void testByProvider() {
        SmtpPreset gmail = SmtpPreset.byProvider("gmail");
        assertNotNull(gmail);
        assertEquals("gmail", gmail.provider());
        assertEquals("smtp.gmail.com", gmail.host());
    }
}

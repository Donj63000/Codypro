package org.example.dao;

import org.example.model.NotificationSettings;
import org.example.model.SmtpSecurity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class DBNotificationSettingsEmailTest {

    @TempDir
    Path tempDir;

    private DB db;

    @BeforeEach
    void setUp() {
        Path dbFile = tempDir.resolve("notifications.sqlite");
        db = new DB(dbFile.toString());
    }

    @AfterEach
    void tearDown() {
        if (db != null) {
            db.close();
        }
    }

    @Test
    void saveAndLoadEmailSettings() {
        NotificationSettings defaults = NotificationSettings.defaults();
        NotificationSettings updated = new NotificationSettings(
                defaults.leadDays(),
                defaults.reminderHour(),
                defaults.reminderMinute(),
                defaults.repeatEveryHours(),
                defaults.highlightOverdue(),
                defaults.desktopPopup(),
                defaults.snoozeMinutes(),
                true,
                "owner@example.com",
                "alerts@example.com",
                "smtp.example.com",
                465,
                "smtp-user",
                "smtp-pass",
                SmtpSecurity.SSL,
                "Sujet test",
                "Corps test",
                true,
                false,
                "Sujet prestataire",
                "Corps prestataire"
        );

        db.saveNotificationSettings(updated);

        NotificationSettings loaded = db.loadNotificationSettings();

        assertEquals(updated.normalized(), loaded);
    }
}

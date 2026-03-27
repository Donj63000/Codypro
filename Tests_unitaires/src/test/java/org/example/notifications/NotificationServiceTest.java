package org.example.notifications;

import org.example.dao.DB;
import org.example.model.Facture;
import org.example.model.NotificationSettings;
import org.example.model.Prestataire;
import org.example.model.SmtpSecurity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class NotificationServiceTest {

    @TempDir
    Path tempDir;

    private DB db;
    private NotificationService service;

    @AfterEach
    void tearDown() {
        if (service != null) {
            service.stop();
        }
        if (db != null) {
            db.close();
        }
    }

    @Test
    void sendPreviewDispatchesDesktopNotificationWhenEnabled() throws Exception {
        RecordingNotifier notifier = new RecordingNotifier(1);
        service = newService(baseSettings(true, false), notifier, new RecordingEmailSender(0));

        service.sendPreview(service.currentSettings());

        assertTrue(notifier.await(2, TimeUnit.SECONDS));
        assertEquals(1, notifier.messages.size());
    }

    @Test
    void sendPreviewSkipsWhenDesktopPopupIsDisabled() throws Exception {
        RecordingNotifier notifier = new RecordingNotifier(1);
        service = newService(baseSettings(false, false), notifier, new RecordingEmailSender(0));

        service.sendPreview(service.currentSettings());

        Thread.sleep(250);
        assertEquals(0, notifier.messages.size());
    }

    @Test
    void sendEmailPreviewSkipsWhenEmailIsDisabled() throws Exception {
        RecordingNotifier notifier = new RecordingNotifier(0);
        RecordingEmailSender sender = new RecordingEmailSender(1);
        service = newService(baseSettings(true, false), notifier, sender);

        service.sendEmailPreview(service.currentSettings());

        Thread.sleep(250);
        assertEquals(0, sender.messages.size());
    }

    @Test
    void sendEmailPreviewSkipsWhenSmtpConfigurationIsIncomplete() throws Exception {
        RecordingNotifier notifier = new RecordingNotifier(0);
        RecordingEmailSender sender = new RecordingEmailSender(1);
        NotificationSettings incomplete = new NotificationSettings(
                3,
                9,
                0,
                4,
                true,
                false,
                30,
                true,
                "owner@example.com",
                "",
                "",
                587,
                "",
                "",
                SmtpSecurity.STARTTLS,
                "Sujet {{facture}}",
                "Corps {{prestataire}}"
        );
        service = newService(incomplete, notifier, sender);

        service.sendEmailPreview(service.currentSettings());

        Thread.sleep(250);
        assertEquals(0, sender.messages.size());
    }

    @Test
    void sendEmailPreviewDispatchesMessageWhenReady() throws Exception {
        RecordingNotifier notifier = new RecordingNotifier(0);
        RecordingEmailSender sender = new RecordingEmailSender(1);
        service = newService(baseSettings(false, true), notifier, sender);

        service.sendEmailPreview(service.currentSettings());

        assertTrue(sender.await(2, TimeUnit.SECONDS));
        assertEquals(1, sender.messages.size());
        assertEquals("owner@example.com", sender.messages.get(0).to());
    }

    @Test
    void tickEmitsDesktopReminderAndMarksPreavis() throws Exception {
        RecordingNotifier notifier = new RecordingNotifier(0);
        RecordingEmailSender sender = new RecordingEmailSender(0);
        service = newService(baseSettings(true, false), notifier, sender);

        int factureId = insertDueInvoice();

        invokeTick(service);

        assertEquals(1, notifier.messages.size());
        Facture facture = findFactureById(factureId);
        assertNotNull(facture);
        assertTrue(facture.isPreavisEnvoye());
    }

    @Test
    void tickQueuesAndFlushesEmailReminderWhenEmailEnabled() throws Exception {
        RecordingNotifier notifier = new RecordingNotifier(0);
        RecordingEmailSender sender = new RecordingEmailSender(0);
        service = newService(baseSettings(false, true), notifier, sender);

        int factureId = insertDueInvoice();

        invokeTick(service);

        assertEquals(1, sender.messages.size());
        assertTrue(db.rappelsAEnvoyer().isEmpty());
        Facture facture = findFactureById(factureId);
        assertNotNull(facture);
        assertTrue(facture.isPreavisEnvoye());
    }

    @Test
    void tickRespectsSnoozeWindow() throws Exception {
        RecordingNotifier notifier = new RecordingNotifier(0);
        RecordingEmailSender sender = new RecordingEmailSender(0);
        service = newService(baseSettings(true, false), notifier, sender);

        insertDueInvoice();
        service.snooze(Duration.ofMinutes(30));

        invokeTick(service);

        assertEquals(0, notifier.messages.size());
    }

    @Test
    void repeatReminderTriggersOnlyAfterConfiguredInterval() throws Exception {
        RecordingNotifier notifier = new RecordingNotifier(0);
        RecordingEmailSender sender = new RecordingEmailSender(0);
        service = newService(baseSettings(true, false), notifier, sender);

        int factureId = insertDueInvoice();

        invokeTick(service);
        assertEquals(1, notifier.messages.size());

        invokeTick(service);
        assertEquals(1, notifier.messages.size());

        setReminderTimestamp(service, factureId, Instant.now().minus(Duration.ofHours(5)));
        invokeTick(service);

        assertEquals(2, notifier.messages.size());
    }

    private NotificationService newService(NotificationSettings settings,
                                           RecordingNotifier notifier,
                                           RecordingEmailSender sender) {
        db = new DB(tempDir.resolve("notifications.sqlite").toString());
        return new NotificationService(db, notifier, sender, () -> settings);
    }

    private NotificationSettings baseSettings(boolean desktopPopup, boolean emailEnabled) {
        return new NotificationSettings(
                1,
                0,
                0,
                4,
                true,
                desktopPopup,
                30,
                emailEnabled,
                "owner@example.com",
                "alerts@example.com",
                "smtp.example.com",
                587,
                "smtp-user",
                "smtp-pass",
                SmtpSecurity.STARTTLS,
                "Sujet {{facture}}",
                "Corps {{prestataire}}"
        );
    }

    private int insertDueInvoice() {
        int prestataireId = db.insertPrestataire(new Prestataire(
                0,
                "Alpha",
                "Alpha Corp",
                "0102030405",
                "alpha@example.com",
                80,
                "Conseil",
                "Notes",
                "01/01/2024"
        ));
        return db.insertFacture(prestataireId, new Facture(
                0,
                prestataireId,
                "Facture test",
                LocalDate.now(),
                new BigDecimal("100.00"),
                new BigDecimal("20.0"),
                null,
                null,
                false,
                null,
                false
        ));
    }

    private Facture findFactureById(int factureId) {
        List<Facture> factures = db.facturesImpayeesAvant(LocalDate.now().plusDays(3).atStartOfDay());
        for (Facture facture : factures) {
            if (facture.getId() == factureId) {
                return facture;
            }
        }
        for (Facture facture : db.facturesNonPayeesAvecPreavis()) {
            if (facture.getId() == factureId) {
                return facture;
            }
        }
        return null;
    }

    private static void invokeTick(NotificationService service) throws Exception {
        Method tick = NotificationService.class.getDeclaredMethod("tick");
        tick.setAccessible(true);
        tick.invoke(service);
    }

    @SuppressWarnings("unchecked")
    private static void setReminderTimestamp(NotificationService service, int factureId, Instant value) throws Exception {
        Field historyField = NotificationService.class.getDeclaredField("reminderHistory");
        historyField.setAccessible(true);
        Map<Integer, Instant> history = (Map<Integer, Instant>) historyField.get(service);
        history.put(factureId, value);
    }

    private static final class RecordingNotifier implements DesktopNotifier {
        private final CountDownLatch latch;
        private final List<String> messages = new ArrayList<>();

        private RecordingNotifier(int expectedEvents) {
            this.latch = new CountDownLatch(expectedEvents);
        }

        @Override
        public synchronized void notify(String title, String message) {
            messages.add((title == null ? "" : title) + "|" + (message == null ? "" : message));
            if (latch.getCount() > 0) {
                latch.countDown();
            }
        }

        private boolean await(long timeout, TimeUnit unit) throws InterruptedException {
            return latch.await(timeout, unit);
        }
    }

    private static final class RecordingEmailSender implements EmailSender {
        private final CountDownLatch latch;
        private final List<EmailMessage> messages = new ArrayList<>();

        private RecordingEmailSender(int expectedEvents) {
            this.latch = new CountDownLatch(expectedEvents);
        }

        @Override
        public synchronized void send(NotificationSettings settings, EmailMessage message) {
            messages.add(message);
            if (latch.getCount() > 0) {
                latch.countDown();
            }
        }

        private boolean await(long timeout, TimeUnit unit) throws InterruptedException {
            return latch.await(timeout, unit);
        }
    }
}

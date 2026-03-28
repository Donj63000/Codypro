package org.example.notifications;

import org.example.dao.DB;
import org.example.model.Facture;
import org.example.model.NotificationSettings;
import org.example.model.Prestataire;
import org.example.model.Rappel;
import org.example.util.NotificationTemplateEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public final class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final int MAX_FAILURE_ATTEMPTS = 6;

    private final DB dao;
    private final DesktopNotifier notifier;
    private final EmailSender emailSender;
    private final ScheduledExecutorService executor;
    private final Map<Integer, Instant> reminderHistory = new ConcurrentHashMap<>();
    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(Locale.FRANCE);

    private volatile NotificationSettings settings;
    private volatile Instant snoozeUntil = Instant.EPOCH;

    public NotificationService(DB dao,
                               DesktopNotifier notifier,
                               Supplier<NotificationSettings> initialSettingsSupplier) {
        this(dao, notifier, new SmtpEmailSender(), initialSettingsSupplier);
    }

    public NotificationService(DB dao,
                               DesktopNotifier notifier,
                               EmailSender emailSender,
                               Supplier<NotificationSettings> initialSettingsSupplier) {
        this.dao = Objects.requireNonNull(dao, "dao");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.emailSender = Objects.requireNonNull(emailSender, "emailSender");
        this.settings = Objects.requireNonNull(initialSettingsSupplier.get(), "settings").normalized();
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "notifications-runner");
            t.setDaemon(true);
            return t;
        });
        seedReminderHistory();
    }

    public record DeliveryCheck(boolean success, String title, String message) {
    }

    public record ReminderSnapshot(
            int upcomingCount,
            int overdueCount,
            int missingSupplierEmailCount,
            int pendingCount,
            int failedCount,
            int sentCount,
            int skippedCount,
            String schedulerSummary,
            String senderSummary,
            String managerSummary,
            String supplierSummary
    ) {
    }

    private record SendContext(Facture facture, Prestataire prestataire, String recipient) {
    }

    public void start() {
        executor.scheduleAtFixedRate(this::safeTick, 0, 60, TimeUnit.SECONDS);
    }

    public void stop() {
        executor.shutdownNow();
    }

    public void runNow() {
        executor.execute(this::safeTick);
    }

    public void runNow(NotificationSettings previewSettings) {
        NotificationSettings candidate = previewSettings == null ? null : previewSettings.normalized();
        if (candidate == null) {
            runNow();
            return;
        }
        executor.execute(() -> safeTick(candidate));
    }

    public NotificationSettings currentSettings() {
        return settings;
    }

    public void updateSettings(NotificationSettings newSettings) {
        if (newSettings == null) {
            return;
        }
        this.settings = newSettings.normalized();
    }

    public void snooze(java.time.Duration duration) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            snoozeUntil = Instant.EPOCH;
        } else {
            snoozeUntil = Instant.now().plus(duration);
        }
    }

    public DeliveryCheck testDesktopNotification(NotificationSettings previewSettings) {
        NotificationSettings candidate = previewSettings == null ? settings : previewSettings.normalized();
        try {
            NotificationTemplateEngine.Context sample = NotificationTemplateEngine.sampleContext();
            NotificationTemplateEngine.Context ctx = new NotificationTemplateEngine.Context(
                    sample.prestataire(),
                    sample.facture(),
                    sample.dueDate(),
                    sample.montant(),
                    relativeLabel(candidate.leadDays()),
                    candidate.leadDays(),
                    false
            );
            String title = NotificationTemplateEngine.render(candidate.subjectTemplate(), ctx);
            String body = NotificationTemplateEngine.render(candidate.bodyTemplate(), ctx);
            notifier.notify(title, body);
            return new DeliveryCheck(true, "Notification de bureau envoyée", "Un aperçu local vient d'être généré.");
        } catch (Exception ex) {
            log.error("Unable to send desktop preview", ex);
            return new DeliveryCheck(false, "Échec du test bureau", failureMessage(ex));
        }
    }

    public DeliveryCheck testManagerEmail(NotificationSettings previewSettings) {
        NotificationSettings candidate = previewSettings == null ? settings : previewSettings.normalized();
        if (!candidate.smtpReady()) {
            return new DeliveryCheck(false, "SMTP incomplet", "Le transport SMTP n'est pas configuré correctement.");
        }
        if (!NotificationSettings.looksLikeEmail(candidate.emailRecipient())) {
            return new DeliveryCheck(false, "Destinataire manquant", "Renseignez l'adresse e-mail du gestionnaire.");
        }
        try {
            NotificationTemplateEngine.Context sample = NotificationTemplateEngine.sampleContext();
            NotificationTemplateEngine.Context ctx = new NotificationTemplateEngine.Context(
                    sample.prestataire(),
                    sample.facture(),
                    sample.dueDate(),
                    sample.montant(),
                    relativeLabel(candidate.leadDays()),
                    candidate.leadDays(),
                    false
            );
            String subject = NotificationTemplateEngine.render(candidate.subjectTemplate(), ctx);
            String body = NotificationTemplateEngine.render(candidate.bodyTemplate(), ctx);
            EmailMessage message = buildEmailMessage(candidate, candidate.emailRecipient(), subject, body);
            if (message == null) {
                return new DeliveryCheck(false, "Paramètres invalides", "Impossible de résoudre l'expéditeur ou le destinataire.");
            }
            emailSender.send(candidate, message);
            return new DeliveryCheck(true, "E-mail gestionnaire envoyé", "L'aperçu a été envoyé à " + candidate.emailRecipient() + ".");
        } catch (Exception ex) {
            log.error("Unable to send manager email preview", ex);
            return new DeliveryCheck(false, "Échec de l'e-mail gestionnaire", failureMessage(ex));
        }
    }

    public DeliveryCheck testSupplierEmail(NotificationSettings previewSettings) {
        NotificationSettings candidate = previewSettings == null ? settings : previewSettings.normalized();
        if (!candidate.smtpReady()) {
            return new DeliveryCheck(false, "SMTP incomplet", "Le transport SMTP n'est pas configuré correctement.");
        }
        String previewRecipient = firstNonBlank(candidate.emailRecipient(), candidate.resolvedSenderAddress());
        if (!NotificationSettings.looksLikeEmail(previewRecipient)) {
            return new DeliveryCheck(false, "Boîte de test manquante", "Renseignez l'adresse du gestionnaire ou l'adresse d'expédition.");
        }
        try {
            NotificationTemplateEngine.Context sample = NotificationTemplateEngine.sampleContext();
            NotificationTemplateEngine.Context ctx = new NotificationTemplateEngine.Context(
                    sample.prestataire(),
                    sample.facture(),
                    sample.dueDate(),
                    sample.montant(),
                    relativeLabel(candidate.leadDays()),
                    candidate.leadDays(),
                    false
            );
            String subject = NotificationTemplateEngine.render(candidate.supplierSubjectTemplate(), ctx);
            String body = NotificationTemplateEngine.render(candidate.supplierBodyTemplate(), ctx);
            EmailMessage message = buildEmailMessage(candidate, previewRecipient, subject, body);
            if (message == null) {
                return new DeliveryCheck(false, "Paramètres invalides", "Impossible de résoudre l'expéditeur ou la boîte de test.");
            }
            emailSender.send(candidate, message);
            return new DeliveryCheck(true, "Aperçu prestataire envoyé", "L'aperçu a été envoyé à " + previewRecipient + ".");
        } catch (Exception ex) {
            log.error("Unable to send supplier email preview", ex);
            return new DeliveryCheck(false, "Échec de l'aperçu prestataire", failureMessage(ex));
        }
    }

    public void sendPreview(NotificationSettings previewSettings) {
        DeliveryCheck result = testDesktopNotification(previewSettings);
        if (!result.success()) {
            log.warn("Desktop preview failed: {}", result.message());
        }
    }

    public void sendEmailPreview(NotificationSettings previewSettings) {
        DeliveryCheck result = testManagerEmail(previewSettings);
        if (!result.success()) {
            log.warn("Manager email preview failed: {}", result.message());
        }
    }

    public void sendSupplierEmailPreview(NotificationSettings previewSettings) {
        DeliveryCheck result = testSupplierEmail(previewSettings);
        if (!result.success()) {
            log.warn("Supplier email preview failed: {}", result.message());
        }
    }

    public ReminderSnapshot snapshot(NotificationSettings previewSettings) {
        NotificationSettings candidate = previewSettings == null ? settings : previewSettings.normalized();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime horizon = now.plusDays(Math.max(candidate.leadDays(), 1));
        List<Facture> factures = dao.facturesImpayeesPourDashboard(horizon);
        int overdue = 0;
        int upcoming = 0;
        int missingSupplierEmail = 0;
        for (Facture facture : factures) {
            if (facture == null || facture.isPaye() || facture.getEcheance() == null) {
                continue;
            }
            if (facture.getEcheance().isBefore(now.toLocalDate())) {
                overdue++;
            } else {
                upcoming++;
            }
            Prestataire prestataire = dao.findPrestataire(facture.getPrestataireId());
            if (prestataire == null || !NotificationSettings.looksLikeEmail(prestataire.getEmail())) {
                missingSupplierEmail++;
            }
        }
        int pending = dao.countRappelsByStatus(Rappel.STATUS_PENDING);
        int failed = dao.countRappelsByStatus(Rappel.STATUS_FAILED);
        int sent = dao.countRappelsByStatus(Rappel.STATUS_SENT);
        int skipped = dao.countRappelsByStatus(Rappel.STATUS_SKIPPED);

        String managerSummary = candidate.emailEnabled()
                ? (NotificationSettings.looksLikeEmail(candidate.emailRecipient())
                ? "Gestionnaire : " + candidate.emailRecipient()
                : "Gestionnaire : adresse à compléter")
                : "Gestionnaire : désactivé";
        String supplierSummary = candidate.supplierEmailEnabled()
                ? "Prestataires : relance J-" + candidate.leadDays()
                + (candidate.supplierSendOnDueDate() ? " + jour J" : "")
                : "Prestataires : désactivé";

        return new ReminderSnapshot(
                upcoming,
                overdue,
                missingSupplierEmail,
                pending,
                failed,
                sent,
                skipped,
                candidate.summary(Locale.FRENCH),
                candidate.senderSummary(),
                managerSummary,
                supplierSummary
        );
    }

    public List<Rappel> recentReminders(int limit) {
        try {
            return dao.rappelsHistorique(limit);
        } catch (Exception ex) {
            log.error("Unable to load reminder history", ex);
            return List.of();
        }
    }

    private void safeTick() {
        safeTick(settings);
    }

    private void safeTick(NotificationSettings previewSettings) {
        try {
            tick(previewSettings);
        } catch (Throwable t) {
            log.error("Unhandled exception during notification tick", t);
        }
    }

    private void tick() {
        tick(settings);
    }

    private void tick(NotificationSettings previewSettings) {
        NotificationSettings cfg = previewSettings == null ? NotificationSettings.defaults() : previewSettings.normalized();
        if (!cfg.desktopPopup() && !cfg.hasAnyEmailFlow()) {
            return;
        }
        Instant nowInstant = Instant.now();
        if (nowInstant.isBefore(snoozeUntil)) {
            return;
        }
        LocalDateTime now = LocalDateTime.ofInstant(nowInstant, ZoneId.systemDefault());
        Set<Integer> freshlyNotified = handleFirstReminders(now, nowInstant, cfg);
        handleDueDayEmailReminders(now, nowInstant, cfg, freshlyNotified);
        handleRepeatReminders(now, nowInstant, cfg, freshlyNotified);
        flushEmailOutbox(cfg);
        cleanupHistory();
    }

    private Set<Integer> handleFirstReminders(LocalDateTime now, Instant nowInstant, NotificationSettings cfg) {
        Set<Integer> freshlyNotified = new HashSet<>();
        LocalDateTime windowLimit = now.plusDays(cfg.leadDays());
        List<Facture> candidates;
        try {
            candidates = dao.facturesImpayeesAvant(windowLimit);
        } catch (Exception ex) {
            log.error("Unable to load unpaid invoices scheduled before {}", windowLimit, ex);
            return freshlyNotified;
        }
        for (Facture facture : candidates) {
            LocalDate due = facture.getEcheance();
            if (due == null) {
                continue;
            }
            LocalDateTime scheduled = due.atTime(cfg.reminderHour(), cfg.reminderMinute()).minusDays(cfg.leadDays());
            if (now.isBefore(scheduled)) {
                continue;
            }
            boolean desktopSent = emitDesktopNotification(facture, cfg);
            boolean managerQueued = queueManagerEmailReminder(
                    facture,
                    cfg,
                    Rappel.TYPE_MANAGER_PRE,
                    oneShotJobKey("manager-pre", facture)
            );
            boolean supplierQueued = queueSupplierEmailReminder(
                    facture,
                    cfg,
                    Rappel.TYPE_SUPPLIER_PRE,
                    oneShotJobKey("supplier-pre", facture)
            );
            if (desktopSent || managerQueued || supplierQueued) {
                try {
                    dao.marquerPreavisEnvoye(facture.getId());
                    reminderHistory.put(facture.getId(), nowInstant);
                    freshlyNotified.add(facture.getId());
                } catch (Exception ex) {
                    log.error("Unable to mark pre-notice sent for facture {}", facture.getId(), ex);
                }
            }
        }
        return freshlyNotified;
    }

    private void handleDueDayEmailReminders(LocalDateTime now,
                                            Instant nowInstant,
                                            NotificationSettings cfg,
                                            Set<Integer> freshlyNotified) {
        if (!cfg.hasAnyEmailFlow()) {
            return;
        }
        List<Facture> dueInvoices;
        try {
            dueInvoices = dao.facturesImpayeesPourDashboard(now);
        } catch (Exception ex) {
            log.error("Unable to load invoices for due-day reminders", ex);
            return;
        }
        LocalDate today = now.toLocalDate();
        for (Facture facture : dueInvoices) {
            if (freshlyNotified.contains(facture.getId())) {
                continue;
            }
            LocalDate due = facture.getEcheance();
            if (due == null || !due.isEqual(today)) {
                continue;
            }
            if (now.isBefore(due.atTime(cfg.reminderHour(), cfg.reminderMinute()))) {
                continue;
            }
            boolean managerQueued = queueManagerEmailReminder(
                    facture,
                    cfg,
                    Rappel.TYPE_MANAGER_DUE,
                    oneShotJobKey("manager-due", facture)
            );
            boolean supplierQueued = cfg.supplierSendOnDueDate() && queueSupplierEmailReminder(
                    facture,
                    cfg,
                    Rappel.TYPE_SUPPLIER_DUE,
                    oneShotJobKey("supplier-due", facture)
            );
            if (managerQueued || supplierQueued) {
                reminderHistory.put(facture.getId(), nowInstant);
                freshlyNotified.add(facture.getId());
            }
        }
    }

    private void handleRepeatReminders(LocalDateTime now,
                                       Instant nowInstant,
                                       NotificationSettings cfg,
                                       Set<Integer> freshlyNotified) {
        int repeatHours = cfg.repeatEveryHours();
        if (repeatHours <= 0) {
            return;
        }
        List<Facture> pending;
        try {
            pending = dao.facturesNonPayeesAvecPreavis();
        } catch (Exception ex) {
            log.error("Unable to load invoices awaiting repeat reminders", ex);
            return;
        }
        if (pending.isEmpty()) {
            return;
        }
        long repeatMinutes = repeatHours * 60L;
        LocalDate today = now.toLocalDate();
        for (Facture facture : pending) {
            if (freshlyNotified.contains(facture.getId())) {
                continue;
            }
            LocalDate due = facture.getEcheance();
            if (due == null || due.isAfter(today)) {
                continue;
            }
            Instant last = reminderHistory.get(facture.getId());
            if (last != null) {
                long elapsed = java.time.Duration.between(last, nowInstant).toMinutes();
                if (elapsed < repeatMinutes) {
                    continue;
                }
            }
            boolean desktopSent = emitDesktopNotification(facture, cfg);
            boolean managerQueued = false;
            boolean supplierQueued = false;

            if (due.isEqual(today)) {
                managerQueued = queueManagerEmailReminder(
                        facture,
                        cfg,
                        Rappel.TYPE_MANAGER_DUE,
                        repeatJobKey("manager-due", facture, now, repeatHours)
                );
                if (cfg.supplierSendOnDueDate()) {
                    supplierQueued = queueSupplierEmailReminder(
                            facture,
                            cfg,
                            Rappel.TYPE_SUPPLIER_DUE,
                            repeatJobKey("supplier-due", facture, now, repeatHours)
                    );
                }
            } else if (due.isBefore(today)) {
                managerQueued = queueManagerEmailReminder(
                        facture,
                        cfg,
                        Rappel.TYPE_MANAGER_OVERDUE,
                        repeatJobKey("manager-overdue", facture, now, repeatHours)
                );
                supplierQueued = queueSupplierEmailReminder(
                        facture,
                        cfg,
                        Rappel.TYPE_SUPPLIER_OVERDUE,
                        repeatJobKey("supplier-overdue", facture, now, repeatHours)
                );
            }

            if (desktopSent || managerQueued || supplierQueued) {
                reminderHistory.put(facture.getId(), nowInstant);
            }
        }
    }

    private boolean emitDesktopNotification(Facture facture, NotificationSettings cfg) {
        if (!cfg.desktopPopup()) {
            return false;
        }
        try {
            Prestataire prestataire = dao.findPrestataire(facture.getPrestataireId());
            NotificationTemplateEngine.Context context = buildContext(facture, prestataire);
            String title = NotificationTemplateEngine.render(cfg.subjectTemplate(), context);
            String body = NotificationTemplateEngine.render(cfg.bodyTemplate(), context);
            notifier.notify(title, body);
            return true;
        } catch (Exception ex) {
            log.error("Unable to emit notification for facture {}", facture.getId(), ex);
            return false;
        }
    }

    private boolean queueManagerEmailReminder(Facture facture,
                                              NotificationSettings cfg,
                                              String type,
                                              String jobKey) {
        if (!cfg.emailEnabled()) {
            return false;
        }
        String recipient = safe(cfg.emailRecipient());
        if (!NotificationSettings.looksLikeEmail(recipient)) {
            return false;
        }
        try {
            Prestataire prestataire = dao.findPrestataire(facture.getPrestataireId());
            NotificationTemplateEngine.Context context = buildContext(facture, prestataire);
            String subject = NotificationTemplateEngine.render(cfg.subjectTemplate(), context);
            String body = NotificationTemplateEngine.render(cfg.bodyTemplate(), context);
            Integer prestataireId = prestataire == null ? facture.getPrestataireId() : prestataire.getId();
            Rappel rappel = new Rappel(
                    0,
                    jobKey,
                    type,
                    facture.getId(),
                    prestataireId,
                    recipient,
                    subject,
                    body,
                    LocalDateTime.now(),
                    false,
                    Rappel.STATUS_PENDING,
                    0,
                    "",
                    null
            );
            return dao.enqueueRappelIfAbsent(rappel);
        } catch (Exception ex) {
            log.error("Unable to queue manager email reminder for facture {}", facture.getId(), ex);
            return false;
        }
    }

    private boolean queueSupplierEmailReminder(Facture facture,
                                               NotificationSettings cfg,
                                               String type,
                                               String jobKey) {
        if (!cfg.supplierEmailEnabled()) {
            return false;
        }
        try {
            Prestataire prestataire = dao.findPrestataire(facture.getPrestataireId());
            String recipient = prestataire == null ? "" : safe(prestataire.getEmail());
            if (!NotificationSettings.looksLikeEmail(recipient)) {
                return false;
            }
            NotificationTemplateEngine.Context context = buildContext(facture, prestataire);
            String subject = NotificationTemplateEngine.render(cfg.supplierSubjectTemplate(), context);
            String body = NotificationTemplateEngine.render(cfg.supplierBodyTemplate(), context);
            Rappel rappel = new Rappel(
                    0,
                    jobKey,
                    type,
                    facture.getId(),
                    prestataire == null ? facture.getPrestataireId() : prestataire.getId(),
                    recipient,
                    subject,
                    body,
                    LocalDateTime.now(),
                    false,
                    Rappel.STATUS_PENDING,
                    0,
                    "",
                    null
            );
            return dao.enqueueRappelIfAbsent(rappel);
        } catch (Exception ex) {
            log.error("Unable to queue supplier email reminder for facture {}", facture.getId(), ex);
            return false;
        }
    }

    private void flushEmailOutbox(NotificationSettings cfg) {
        if (!cfg.hasAnyEmailFlow()) {
            return;
        }
        List<Rappel> pending;
        try {
            pending = dao.rappelsAEnvoyer();
        } catch (Exception ex) {
            log.error("Unable to load pending email reminders", ex);
            return;
        }
        if (pending.isEmpty()) {
            return;
        }
        if (!cfg.smtpReady()) {
            log.warn("Email reminders pending but SMTP settings are incomplete.");
            return;
        }
        for (Rappel rappel : pending) {
            if (rappel.attemptCount() >= MAX_FAILURE_ATTEMPTS) {
                dao.markRappelSkipped(rappel.id(), "Nombre maximal d'essais atteint.");
                continue;
            }
            if (isManagerFlow(rappel) && !cfg.emailEnabled()) {
                continue;
            }
            if (isSupplierFlow(rappel) && !cfg.supplierEmailEnabled()) {
                continue;
            }
            SendContext context = resolveSendContext(rappel, cfg);
            if (context == null) {
                continue;
            }
            EmailMessage message = buildEmailMessage(cfg, context.recipient(), rappel.sujet(), rappel.corps());
            if (message == null) {
                dao.markRappelSkipped(rappel.id(), "Destinataire ou expéditeur invalide.");
                continue;
            }
            try {
                emailSender.send(cfg, message);
                dao.markRappelEnvoye(rappel.id());
                reminderHistory.put(rappel.factureId(), Instant.now());
            } catch (Exception ex) {
                int backoffMinutes = Math.min(60, Math.max(5, (rappel.attemptCount() + 1) * 5));
                LocalDateTime nextAttempt = LocalDateTime.now().plusMinutes(backoffMinutes);
                dao.markRappelFailed(rappel.id(), failureMessage(ex), nextAttempt);
                log.error("Unable to send email reminder {}", rappel.id(), ex);
            }
        }
    }

    private SendContext resolveSendContext(Rappel rappel, NotificationSettings cfg) {
        try {
            Facture facture = dao.findFacture(rappel.factureId());
            if (facture == null) {
                dao.markRappelSkipped(rappel.id(), "Facture introuvable.");
                return null;
            }
            if (facture.isPaye()) {
                dao.markRappelSkipped(rappel.id(), "Facture déjà réglée.");
                return null;
            }
            Prestataire prestataire = dao.findPrestataire(facture.getPrestataireId());
            String recipient;
            if (isManagerFlow(rappel)) {
                recipient = firstNonBlank(cfg.emailRecipient(), rappel.dest());
            } else {
                recipient = firstNonBlank(prestataire == null ? "" : prestataire.getEmail(), rappel.dest());
            }
            if (!NotificationSettings.looksLikeEmail(recipient)) {
                dao.markRappelSkipped(rappel.id(), "Adresse destinataire invalide.");
                return null;
            }
            return new SendContext(facture, prestataire, recipient);
        } catch (Exception ex) {
            log.error("Unable to prepare reminder {}", rappel.id(), ex);
            dao.markRappelFailed(rappel.id(), failureMessage(ex), LocalDateTime.now().plusMinutes(15));
            return null;
        }
    }

    private EmailMessage buildEmailMessage(NotificationSettings cfg, String to, String subject, String body) {
        String resolvedTo = safe(to);
        if (!NotificationSettings.looksLikeEmail(resolvedTo)) {
            return null;
        }
        String from = cfg.resolvedSenderAddress();
        if (!NotificationSettings.looksLikeEmail(from)) {
            return null;
        }
        String safeSubject = subject == null ? "" : subject;
        String safeBody = cfg.applySignature(body);
        return new EmailMessage(
                resolvedTo,
                from,
                cfg.resolvedSenderName(),
                cfg.resolvedReplyTo(),
                safeSubject,
                safeBody
        );
    }

    private NotificationTemplateEngine.Context buildContext(Facture facture, Prestataire prestataire) {
        String prestataireName = Optional.ofNullable(prestataire)
                .map(p -> {
                    String company = safe(p.getSociete());
                    return company.isEmpty() ? safe(p.getNom()) : company;
                })
                .filter(value -> !value.isEmpty())
                .orElseGet(() -> "Prestataire #" + facture.getPrestataireId());

        String factureLabel = safe(facture.getDescription());
        if (factureLabel.isBlank()) {
            factureLabel = "Facture #" + facture.getId();
        }

        BigDecimal amount = facture.getMontantTtc();
        if (amount == null || BigDecimal.ZERO.compareTo(amount) == 0) {
            amount = facture.getMontantHt();
        }
        String amountLabel = amount == null ? "" : currencyFormat.format(amount);

        LocalDate dueDate = facture.getEcheance();
        long deltaDays = dueDate == null ? 0 : ChronoUnit.DAYS.between(LocalDate.now(), dueDate);
        boolean overdue = dueDate != null && dueDate.isBefore(LocalDate.now());
        String relative = relativeLabel((int) deltaDays);

        return new NotificationTemplateEngine.Context(
                prestataireName,
                factureLabel,
                dueDate,
                amountLabel,
                relative,
                deltaDays,
                overdue
        );
    }

    private void cleanupHistory() {
        Set<Integer> validIds;
        try {
            validIds = dao.factureIdsNonPayesAvecPreavis();
        } catch (Exception ex) {
            log.error("Unable to refresh reminder history", ex);
            return;
        }
        reminderHistory.keySet().removeIf(id -> !validIds.contains(id));
    }

    private void seedReminderHistory() {
        try {
            reminderHistory.clear();
            reminderHistory.putAll(dao.latestReminderActivityByFacture());
            cleanupHistory();
        } catch (Exception ex) {
            log.error("Unable to seed reminder history", ex);
        }
    }

    private static boolean isManagerFlow(Rappel rappel) {
        String type = rappel == null ? "" : safe(rappel.type());
        return type.startsWith("MANAGER_");
    }

    private static boolean isSupplierFlow(Rappel rappel) {
        String type = rappel == null ? "" : safe(rappel.type());
        return type.startsWith("SUPPLIER_");
    }

    private static String oneShotJobKey(String prefix, Facture facture) {
        LocalDate due = facture == null ? null : facture.getEcheance();
        String dueKey = due == null ? "none" : due.toString();
        int factureId = facture == null ? 0 : facture.getId();
        String amountKey = facture == null || facture.getMontantTtc() == null
                ? "0"
                : facture.getMontantTtc().stripTrailingZeros().toPlainString();
        String description = facture == null || facture.getDescription() == null
                ? ""
                : facture.getDescription().trim().replaceAll("\\s+", " ");
        String descKey = description.isBlank()
                ? "na"
                : Integer.toUnsignedString(description.hashCode(), 36);
        return prefix + ":" + factureId + ":" + dueKey + ":" + amountKey + ":" + descKey;
    }

    private static String repeatJobKey(String prefix,
                                       Facture facture,
                                       LocalDateTime now,
                                       int repeatHours) {
        long bucketSizeSeconds = Math.max(1, repeatHours) * 3600L;
        long epochSeconds = now.toEpochSecond(ZoneOffset.UTC);
        long bucket = Math.floorDiv(epochSeconds, bucketSizeSeconds);
        int factureId = facture == null ? 0 : facture.getId();
        return prefix + ":" + factureId + ":" + bucket;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            String safe = safe(value);
            if (!safe.isBlank()) {
                return safe;
            }
        }
        return "";
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String failureMessage(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            String message = current.getMessage();
            if (message != null && !message.isBlank()) {
                return message.strip();
            }
        }
        return throwable == null ? "Erreur inconnue." : throwable.getClass().getSimpleName();
    }

    private static String relativeLabel(int deltaDays) {
        if (deltaDays > 1) {
            return "dans " + deltaDays + " jours";
        }
        if (deltaDays == 1) {
            return "dans 1 jour";
        }
        if (deltaDays == 0) {
            return "aujourd'hui";
        }
        if (deltaDays == -1) {
            return "depuis 1 jour";
        }
        if (deltaDays < -1) {
            return "depuis " + Math.abs(deltaDays) + " jours";
        }
        return "";
    }
}

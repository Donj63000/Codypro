package org.example.notifications;

import org.example.dao.DB;
import org.example.model.Facture;
import org.example.model.NotificationSettings;
import org.example.model.Prestataire;
import org.example.util.NotificationTemplateEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
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

/**
 * Background service responsible for triggering invoice reminders and surfacing them via desktop notifications.
 */
public final class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final DB dao;
    private final DesktopNotifier notifier;
    private final ScheduledExecutorService executor;
    private final Map<Integer, Instant> reminderHistory = new ConcurrentHashMap<>();
    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(Locale.FRANCE);

    private volatile NotificationSettings settings;
    private volatile Instant snoozeUntil = Instant.EPOCH;

    public NotificationService(DB dao,
                               DesktopNotifier notifier,
                               Supplier<NotificationSettings> initialSettingsSupplier) {
        this.dao = Objects.requireNonNull(dao, "dao");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.settings = Objects.requireNonNull(initialSettingsSupplier.get(), "settings").normalized();
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "notifications-runner");
            t.setDaemon(true);
            return t;
        });
        seedReminderHistory();
    }

    public void start() {
        executor.scheduleAtFixedRate(this::safeTick, 0, 60, TimeUnit.SECONDS);
    }

    public void stop() {
        executor.shutdownNow();
    }

    public NotificationSettings currentSettings() {
        return settings;
    }

    public void updateSettings(NotificationSettings newSettings) {
        if (newSettings == null) {
            return;
        }
        NotificationSettings normalized = newSettings.normalized();
        this.settings = normalized;
    }

    public void snooze(java.time.Duration duration) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            snoozeUntil = Instant.EPOCH;
        } else {
            snoozeUntil = Instant.now().plus(duration);
        }
    }

    /**
     * Emits a preview notification using the provided settings (if popups are enabled).
     */
    public void sendPreview(NotificationSettings previewSettings) {
        NotificationSettings candidate = previewSettings == null ? settings : previewSettings.normalized();
        if (!candidate.desktopPopup()) {
            return;
        }
        executor.execute(() -> {
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
        });
    }

    private void safeTick() {
        try {
            tick();
        } catch (Throwable t) {
            log.error("Unhandled exception during notification tick", t);
        }
    }

    private void tick() {
        NotificationSettings cfg = settings;
        if (!cfg.desktopPopup()) {
            return;
        }
        Instant nowInstant = Instant.now();
        if (nowInstant.isBefore(snoozeUntil)) {
            return;
        }
        LocalDateTime now = LocalDateTime.ofInstant(nowInstant, ZoneId.systemDefault());
        handleFirstReminders(now, cfg);
        handleRepeatReminders(nowInstant, cfg);
        cleanupHistory();
    }

    private void handleFirstReminders(LocalDateTime now, NotificationSettings cfg) {
        LocalDateTime windowLimit = now.plusDays(cfg.leadDays());
        List<Facture> candidates;
        try {
            candidates = dao.facturesImpayeesAvant(windowLimit);
        } catch (Exception ex) {
            log.error("Unable to load unpaid invoices scheduled before {}", windowLimit, ex);
            return;
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
                if (emitNotification(facture, cfg)) {
                    try {
                        dao.marquerPreavisEnvoye(facture.getId());
                        reminderHistory.put(facture.getId(), Instant.now());
                    } catch (Exception ex) {
                        log.error("Unable to mark pre-notice sent for facture {}", facture.getId(), ex);
                    }
                }
        }
    }

    private void handleRepeatReminders(Instant now, NotificationSettings cfg) {
        int repeatHours = cfg.repeatEveryHours();
        if (repeatHours <= 0) {
            return;
        }
        List<Facture> pending = new ArrayList<>();
        try {
            pending = dao.facturesNonPayeesAvecPreavis();
        } catch (Exception ex) {
            log.error("Unable to load invoices awaiting repeat reminders", ex);
        }
        if (pending.isEmpty()) {
            return;
        }
        long repeatMinutes = repeatHours * 60L;
        for (Facture facture : pending) {
            Instant last = reminderHistory.get(facture.getId());
            if (last != null) {
                long elapsed = java.time.Duration.between(last, now).toMinutes();
                if (elapsed < repeatMinutes) {
                    continue;
                }
            }
            if (emitNotification(facture, cfg)) {
                reminderHistory.put(facture.getId(), now);
            }
        }
    }

    private boolean emitNotification(Facture facture, NotificationSettings cfg) {
        try {
            Prestataire prestataire = dao.findPrestataire(facture.getPrestataireId());
            NotificationTemplateEngine.Context context = buildContext(facture, prestataire, cfg.leadDays());
            String title = NotificationTemplateEngine.render(cfg.subjectTemplate(), context);
            String body = NotificationTemplateEngine.render(cfg.bodyTemplate(), context);
            notifier.notify(title, body);
            return true;
        } catch (Exception ex) {
            log.error("Unable to emit notification for facture {}", facture.getId(), ex);
            return false;
        }
    }

    private NotificationTemplateEngine.Context buildContext(Facture facture,
                                                            Prestataire prestataire,
                                                            int leadDays) {
        String prestataireName = Optional.ofNullable(prestataire)
                .map(p -> {
                    String company = safe(p.getSociete());
                    return !company.isEmpty() ? company : safe(p.getNom());
                })
                .filter(s -> !s.isEmpty())
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
                Math.abs(deltaDays),
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
            for (Facture facture : dao.facturesNonPayeesAvecPreavis()) {
                reminderHistory.putIfAbsent(facture.getId(), Instant.now());
            }
        } catch (Exception ex) {
            log.error("Unable to seed reminder history", ex);
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
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

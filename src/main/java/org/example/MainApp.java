package org.example;

import com.google.api.client.auth.oauth2.TokenResponseException;
import jakarta.mail.AuthenticationFailedException;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;
import org.example.dao.AuthDB;
import org.example.dao.DB;
import org.example.dao.DbBootstrap;
import org.example.dao.MailPrefsDAO;
import org.example.dao.UserDB;
import org.example.dao.SecureDB;
import org.example.gui.LoginDialog;
import org.example.gui.MainView;
import org.example.gui.RegisterDialog;
import org.example.gui.ThemeManager;
import org.example.mail.LocalSmtpRelay;
import org.example.mail.Mailer;
import org.example.mail.MailPrefs;
import org.example.model.Facture;
import org.example.model.Prestataire;
import org.example.model.Rappel;
import org.example.security.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class MainApp extends Application {
    private static final Logger log = LoggerFactory.getLogger(MainApp.class);
    private DB dao;
    private MailPrefsDAO mailPrefsDao;
    private MainView view;
    private ScheduledExecutorService scheduler;
    private UserDB userDb;
    private final Set<String> prenotified = ConcurrentHashMap.newKeySet();
    private LocalSmtpRelay smtpRelay;

    @Override
    public void start(Stage stage) {
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            e.printStackTrace();
        });
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "debug");
        try (AuthDB auth = new AuthDB("auth.db")) {
            AuthService sec = new AuthService(auth);

            AuthService.Session session;
            try (Statement st = auth.c().createStatement();
                 ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM users")) {
                if (rs.next() && rs.getInt(1) == 0) {
                    session = new RegisterDialog(sec).showAndWait().orElse(null);
                } else {
                    session = promptLoginLoop(sec).orElse(null);
                }
            }

            if (session == null) {
                Platform.exit();
                return;
            }

            AuthService.Session sess = session;
            byte[] key = sess.key().getEncoded();

            Path dbFile = Path.of(System.getProperty("user.home"), ".prestataires", sess.username() + ".db");
            Files.createDirectories(dbFile.getParent());
            log.info("DB path = {}", dbFile);
            userDb = new UserDB(dbFile.toString());
            userDb.openOrRepair(key);

            DB dao = new SecureDB(userDb::getConnection, sess.userId(), sess.key());
            this.dao = dao;
            mailPrefsDao = new MailPrefsDAO(dao, sess.key());

            DbBootstrap.ensureSchema(dao, userDb);

            view = new MainView(stage, dao, mailPrefsDao);
            Scene sc = new Scene(view.getRoot(), 920, 600);
            if (Boolean.getBoolean("app.safeUi")) sc.getStylesheets().clear();
            else ThemeManager.apply(sc);
            stage.setScene(sc);
            stage.setTitle("Gestion des Prestataires");
            stage.show();

            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "rappel-mailer");
                t.setDaemon(true);
                return t;
            });
            scheduler.scheduleAtFixedRate(this::envoyerRappels, 1, 60, TimeUnit.MINUTES);

            try {
                smtpRelay = new LocalSmtpRelay(mailPrefsDao, 2525);
                smtpRelay.start();
                System.out.println("[SMTP-Relay] Démarré sur localhost:2525");
            } catch (Exception ex) {
                System.err.println("[SMTP-Relay] Impossible de démarrer: " + ex.getMessage());
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            showError("Erreur au démarrage: " + ex.getMessage());
            Platform.exit();
        }
    }

    private Optional<AuthService.Session> promptLoginLoop(AuthService sec) {
        while (true) {
            Optional<AuthService.Session> opt = new LoginDialog(sec).showAndWait();
            if (opt.isPresent()) return opt;
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "Voulez‑vous quitter l’application ?", ButtonType.YES, ButtonType.NO);
            ThemeManager.apply(confirm);
            if (confirm.showAndWait().orElse(ButtonType.NO) == ButtonType.YES) {
                return Optional.empty();
            }
        }
    }


    private void envoyerRappels() {
        try {
            MailPrefs cfg = mailPrefsDao.load();
            if (cfg == null) return;

            LocalDateTime now = LocalDateTime.now();

            for (Facture f : dao.facturesImpayeesAvant(now.plusHours(48))) {
                LocalDateTime due = f.getEcheance().atStartOfDay();
                long hoursUntil = Duration.between(now, due).toHours();
                int slot = (hoursUntil >= 47 && hoursUntil <= 48) ? 48 :
                        (hoursUntil >= 23 && hoursUntil <= 24) ? 24 :
                                (hoursUntil >= 11 && hoursUntil <= 12) ? 12 : -1;
                if (slot > 0) {
                    String key = f.getId() + ":" + slot;
                    if (prenotified.add(key)) {
                        try {
                            Prestataire pr = dao.findPrestataire(f.getPrestataireId());
                            if (pr == null) continue;
                            String dest = cfg.copyToSelf().isBlank() ? cfg.from() : cfg.copyToSelf();
                            String subject = "Échéance dans " + slot + " h – facture " + f.getId();
                            String body = String.format(java.util.Locale.FRANCE,
                                    "La facture %d (%.2f €) pour %s arrive à échéance le %s.",
                                    f.getId(), f.getMontantTtc(), pr.getNom(), f.getEcheanceFr());
                            Mailer.send(mailPrefsDao, cfg, dest, subject, body);
                        } catch (Exception ex) {
                            handleAuthException(ex);
                        }
                    }
                }
            }

            LocalDateTime limit = now.minusHours(cfg.delayHours());

            for (Facture f : dao.facturesImpayeesAvant(limit)) {
                try {
                    Prestataire pr = dao.findPrestataire(f.getPrestataireId());
                    if (pr == null || pr.getEmail().isBlank()) continue;
                    Map<String, String> vars = Mailer.vars(pr, f);
                    Mailer.send(mailPrefsDao, cfg, pr.getEmail(),
                            Mailer.subjToPresta(cfg, vars), Mailer.bodyToPresta(cfg, vars));
                    if (!cfg.copyToSelf().isBlank()) {
                        Mailer.send(mailPrefsDao, cfg, cfg.copyToSelf(),
                                Mailer.subjToSelf(cfg, vars), Mailer.bodyToSelf(cfg, vars));
                    }
                    dao.marquerPreavisEnvoye(f.getId());
                } catch (Exception ex) {
                    handleAuthException(ex);
                }
            }

            for (Rappel r : dao.rappelsAEnvoyer()) {
                try {
                    Mailer.send(mailPrefsDao, cfg, r.dest(), r.sujet(), r.corps());
                    dao.markRappelEnvoye(r.id());
                } catch (Exception ex) {
                    handleAuthException(ex);
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void handleAuthException(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof AuthenticationFailedException || t instanceof TokenResponseException) {
                mailPrefsDao.invalidateOAuth();
                Platform.runLater(() ->
                        showError("Authentification expirée; veuillez reconfigurer votre compte e‑mail."));
                return;
            }
        }
        ex.printStackTrace();
    }

    private static void showError(String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        ThemeManager.apply(a);
        a.showAndWait();
    }

    @Override
    public void stop() {
        if (scheduler != null) scheduler.shutdownNow();
        if (dao != null) dao.close();
        if (userDb != null) userDb.close();
        if (view != null) view.shutdownExecutor();
        if (smtpRelay != null) smtpRelay.stop();
    }
}

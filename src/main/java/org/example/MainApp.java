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
import org.example.util.AppPaths;
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
        try {
            assertSqliteEncryption();
        } catch (Exception ex) {
            ex.printStackTrace();
            showError("Erreur au demarrage: " + ex.getMessage());
            Platform.exit();
            return;
        }
        try (AuthDB auth = new AuthDB()) {
            AuthService sec = new AuthService(auth);

            AuthService.Session session;
            try (Statement st = auth.c().createStatement();
                 ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM users")) {
                if (rs.next() && rs.getInt(1) == 0) {
                    session = new RegisterDialog(sec).showAndWait().orElse(null);
                    if (session != null) {
                        byte[] key = session.key().getEncoded();
                        if (!openUserDatabase(session, key, false)) {
                            Platform.exit();
                            return;
                        }
                    }
                } else {
                    session = authenticateUntilDbOpen(sec).orElse(null);
                }
            }

            if (session == null) {
                Platform.exit();
                return;
            }

            AuthService.Session sess = session;
            byte[] key = sess.key().getEncoded();

            log.info("DB path = {}", AppPaths.userDb(sess.username()));

            DB dao = initSecureDbWithRepair(userDb, sess, key);
            this.dao = dao;
            mailPrefsDao = new MailPrefsDAO(dao, sess.key());

            try {
                DbBootstrap.ensureSchema(dao, userDb);
            } catch (Exception ex) {
                if (looksLikeNotADB(ex)) {
                    userDb.close();
                    userDb.openOrRepair(key);
                    DbBootstrap.ensureSchema(dao, userDb);
                } else throw ex;
            }

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
                System.out.println("[SMTP-Relay] Demarre sur localhost:2525");
            } catch (Exception ex) {
                System.err.println("[SMTP-Relay] Impossible de demarrer: " + ex.getMessage());
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            showError("Erreur au demarrage: " + ex.getMessage());
            Platform.exit();
        }
    }

    private Optional<AuthService.Session> promptLoginLoop(AuthService sec) {
        while (true) {
            Optional<AuthService.Session> opt = new LoginDialog(sec).showAndWait();
            if (opt.isPresent()) return opt;
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "Voulez-vous quitter l'application ?", ButtonType.YES, ButtonType.NO);
            ThemeManager.apply(confirm);
            if (confirm.showAndWait().orElse(ButtonType.NO) == ButtonType.YES) {
                return Optional.empty();
            }
        }
    }


    private DB initSecureDbWithRepair(UserDB userDb, AuthService.Session sess, byte[] keyBytes) throws Exception {
        RuntimeException last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                return new SecureDB(userDb::getConnection, sess.userId(), sess.key());
            } catch (RuntimeException ex) {
                last = ex;
                if (attempt == 0 && looksLikeNotADB(ex)) {
                    log.warn("[MainApp] NOTADB detected while opening user DB for {}. Attempting repair.", sess.username());
                    userDb.close();
                    userDb.openOrRepair(keyBytes);
                    continue;
                }
                throw ex;
            }
        }
        throw last;
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
                            String subject = "Echeance dans " + slot + " h - facture " + f.getId();
                            String body = String.format(java.util.Locale.FRANCE,
                                    "La facture %d (%.2f EUR) pour %s arrive a echeance le %s.",
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
                        showError("Authentification expiree; veuillez reconfigurer votre compte e-mail."));
                return;
            }
        }
        ex.printStackTrace();
    }

    private static boolean looksLikeNotADB(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            String m = t.getMessage();
            if (m == null) continue;
            String s = m.toLowerCase(java.util.Locale.ROOT);
            if (s.contains("notadb")) return true;
            if (s.contains("file is not a database")) return true;
            if (s.contains("not a database file")) return true;
            if (s.contains("file opened that is not a database")) return true;
            if (s.contains("file is encrypted or is not a database")) return true;
        }
        return false;
    }

    private static void showError(String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        ThemeManager.apply(a);
        a.showAndWait();
    }

    private Optional<AuthService.Session> authenticateUntilDbOpen(AuthService sec) throws Exception {
        while (true) {
            Optional<AuthService.Session> opt = promptLoginLoop(sec);
            if (opt.isEmpty()) {
                return Optional.empty();
            }
            AuthService.Session session = opt.get();
            byte[] key = session.key().getEncoded();
            if (openUserDatabase(session, key, true)) {
                return Optional.of(session);
            }
        }
    }

    private boolean openUserDatabase(AuthService.Session session, byte[] key, boolean allowRetry) throws Exception {
        Path dbFile = AppPaths.userDb(session.username());
        Files.createDirectories(dbFile.getParent());
        UserDB candidate = new UserDB(dbFile.toString());
        try {
            candidate.openOrRepair(key);
            if (userDb != null) {
                userDb.close();
            }
            userDb = candidate;
            return true;
        } catch (Exception ex) {
            candidate.close();
            if (allowRetry && isRecoverableDbOpenError(ex)) {
                log.warn("[MainApp] Unable to open user DB for {}: {}", session.username(), ex.getMessage());
                showError(ex.getMessage() != null ? ex.getMessage() :
                        "Base chiffrée ou illisible. Veuillez vérifier votre mot de passe.");
                return false;
            }
            throw ex;
        }
    }

    private static boolean isRecoverableDbOpenError(Throwable ex) {
        if (looksLikeNotADB(ex)) return true;
        for (Throwable t = ex; t != null; t = t.getCause()) {
            String msg = t.getMessage();
            if (msg != null && msg.contains("Base chiffrée ou illisible")) {
                return true;
            }
        }
        return false;
    }

    private static void assertSqliteEncryption() throws Exception {
        try (var c = java.sql.DriverManager.getConnection("jdbc:sqlite::memory:");
             var st = c.createStatement();
             var rs = st.executeQuery("pragma compile_options")) {

            boolean hasCodec = false;
            while (rs.next()) {
                var opt = rs.getString(1);
                System.out.println("compile_option: " + opt);
                if (opt.contains("HAS_CODEC") || opt.contains("SSEE") || opt.contains("SEE"))
                    hasCodec = true;
            }
            // Essayons aussi de lire la version du codec
            try (var rs2 = st.executeQuery("pragma cipher_version")) {
                if (rs2.next()) {
                    System.out.println("cipher_version=" + rs2.getString(1));
                    hasCodec = hasCodec || rs2.getString(1) != null;
                }
            } catch (Exception ignore) {}

            if (!hasCodec) {
                throw new IllegalStateException("Driver SQLite SANS chiffrement chargé. " +
                    "Assure-toi d'utiliser io.github.willena:sqlite-jdbc (SSE) et pas org.xerial.");
            }
        }
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




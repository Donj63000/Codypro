package org.example;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;
import org.example.dao.AuthDB;
import org.example.dao.DB;
import org.example.dao.DbBootstrap;
import org.example.dao.UserDB;
import org.example.dao.SecureDB;
import org.example.util.AppPaths;
import org.example.AppServices;
import org.example.model.NotificationSettings;
import org.example.notifications.DesktopNotifier;
import org.example.notifications.DialogDesktopNotifier;
import org.example.notifications.NotificationService;
import org.example.notifications.SystemTrayManager;
import org.example.notifications.SystemTrayNotifier;
import org.example.gui.LoginDialog;
import org.example.gui.MainView;
import org.example.gui.RegisterDialog;
import org.example.gui.ThemeManager;
import org.example.security.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Optional;

public final class MainApp extends Application {
    private static final Logger log = LoggerFactory.getLogger(MainApp.class);
    private DB dao;
    private MainView view;
    private UserDB userDb;
    private AuthDB authDb;
    private AuthService authService;
    private AuthService.Session session;
    private Instant loginStarted;
    private NotificationService notificationService;
    private SystemTrayNotifier trayNotifier;
    private SystemTrayManager trayManager;

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
        try {
            authDb = new AuthDB();
            authService = new AuthService(authDb);
        } catch (Exception ex) {
            ex.printStackTrace();
            showError("Erreur d'initialisation des comptes: " + ex.getMessage());
            Platform.exit();
            return;
        }
        try {
            AuthService.Session session;
            try (Statement st = authDb.c().createStatement();
                 ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM users")) {
                if (rs.next() && rs.getInt(1) == 0) {
                    session = new RegisterDialog(authService).showAndWait().orElse(null);
                    if (session != null) {
                        byte[] key = session.key().getEncoded();
                        if (!openUserDatabase(session, key, false)) {
                            Platform.exit();
                            return;
                        }
                    }
                } else {
                    session = authenticateUntilDbOpen(authService).orElse(null);
                }
            }

            if (session == null) {
                Platform.exit();
                return;
            }

            this.session = session;
            byte[] key = session.key().getEncoded();
            loginStarted = Instant.now();

            log.info("DB path = {}", AppPaths.userDb(session.username()));

            DB dao = initSecureDbWithRepair(userDb, session, key);
            this.dao = dao;
            try {
                DbBootstrap.ensureSchema(dao, userDb);
            } catch (Exception ex) {
                if (looksLikeNotADB(ex)) {
                    userDb.close();
                    userDb.openOrRepair(key);
                    DbBootstrap.ensureSchema(dao, userDb);
                } else throw ex;
            }

            view = new MainView(stage, dao, authService, session, loginStarted);
            Scene sc = new Scene(view.getRoot(), 920, 600);
            if (Boolean.getBoolean("app.safeUi")) sc.getStylesheets().clear();
            else ThemeManager.apply(sc);
            stage.setScene(sc);
            stage.setTitle("Gestion des Prestataires");
            initNotifications(stage);
            stage.show();

        } catch (Exception ex) {
            ex.printStackTrace();
            showError("Erreur au demarrage: " + ex.getMessage());
            Platform.exit();
        }
    }

    private void initNotifications(Stage stage) {
        try {
            NotificationSettings settings = loadNotificationSettingsSafe();
            DesktopNotifier notifier = initDesktopNotifier(stage, settings);
            notificationService = new NotificationService(dao, notifier, () -> settings);
            notificationService.start();
            AppServices.registerNotificationService(notificationService);
        } catch (Throwable ex) {
            log.warn("[MainApp] Notifications désactivées ({}).", ex.getMessage());
            log.debug("[MainApp] Stacktrace notifications", ex);
            notificationService = null;
            trayNotifier = null;
            trayManager = null;
            AppServices.clearNotificationService();
            AppServices.clearTrayManager();
        }
    }

    private NotificationSettings loadNotificationSettingsSafe() {
        try {
            return dao.loadNotificationSettings();
        } catch (Exception ex) {
            log.warn("[MainApp] Impossible de charger les paramètres de notification, utilisation des valeurs par défaut.", ex);
            return NotificationSettings.defaults();
        }
    }

    private DesktopNotifier initDesktopNotifier(Stage stage, NotificationSettings settings) {
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            throw new IllegalStateException("environnement sans interface graphique");
        }
        try {
            trayNotifier = new SystemTrayNotifier();
            trayManager = new SystemTrayManager(stage, trayNotifier, this::handleSnoozeRequest, this::shutdownFromTray);
            trayManager.install(settings.snoozeMinutes());
            AppServices.registerTrayManager(trayManager);
            stage.setOnCloseRequest(evt -> {
                evt.consume();
                stage.hide();
            });
            return trayNotifier;
        } catch (Throwable ex) {
            log.info("[MainApp] System tray indisponible, utilisation du fallback interne : {}", ex.getMessage());
            trayNotifier = null;
            trayManager = null;
            AppServices.clearTrayManager();
            stage.setOnCloseRequest(null);
            return new DialogDesktopNotifier();
        }
    }

    private void handleSnoozeRequest(java.time.Duration duration) {
        AppServices.notificationServiceOptional().ifPresent(service -> service.snooze(duration));
    }

    private void shutdownFromTray() {
        stopNotifications();
        Platform.exit();
    }

    private void stopNotifications() {
        if (notificationService != null) {
            notificationService.stop();
            notificationService = null;
        }
        if (trayNotifier != null) {
            trayNotifier.dispose();
            trayNotifier = null;
        }
        trayManager = null;
        AppServices.clearTrayManager();
        AppServices.clearNotificationService();
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
        java.nio.file.Path tmp = java.nio.file.Files.createTempFile("sqlite-enc-check", ".db");
        byte[] keyBytes = new byte[32];
        new java.security.SecureRandom().nextBytes(keyBytes);
        String hexKey = java.util.HexFormat.of().formatHex(keyBytes);
        org.sqlite.SQLiteConfig cfg = new org.sqlite.SQLiteConfig();
        cfg.setPragma(org.sqlite.SQLiteConfig.Pragma.HEXKEY_MODE, "SSE");
        cfg.setPragma(org.sqlite.SQLiteConfig.Pragma.KEY, hexKey);
        String jdbcUrl = "jdbc:sqlite:" + tmp.toAbsolutePath();
        java.util.Properties props = cfg.toProperties();
        try {
            try (var c = java.sql.DriverManager.getConnection(jdbcUrl, props);
                 var st = c.createStatement()) {
                try {
                    st.execute("PRAGMA cipher_compatibility=4");
                } catch (java.sql.SQLException ignore) {
                }
                st.execute("CREATE TABLE IF NOT EXISTS __enc_probe__(x INTEGER)");
                st.execute("INSERT INTO __enc_probe__ VALUES (1)");
            }

            try (var c = java.sql.DriverManager.getConnection(jdbcUrl, props);
                 var st = c.createStatement();
                 var rs = st.executeQuery("SELECT COUNT(*) FROM __enc_probe__")) {
                if (!rs.next() || rs.getInt(1) != 1) {
                    throw new IllegalStateException("Lecture de la base chiffree de test impossible (cle SSE rejetee).");
                }
            }

            boolean openedWithoutKey = false;
            try (var c = java.sql.DriverManager.getConnection(jdbcUrl);
                 var st = c.createStatement()) {
                st.executeQuery("SELECT COUNT(*) FROM __enc_probe__");
                openedWithoutKey = true;
            } catch (java.sql.SQLException expected) {
                openedWithoutKey = false;
            }

            if (openedWithoutKey) {
                throw new IllegalStateException("Le driver SQLite a ouvert une base chiffree sans cle; verifie l'utilisation de io.github.willena:sqlite-jdbc (SSE).");
            }
        } catch (java.sql.SQLException ex) {
            throw new IllegalStateException("Verification du chiffrement SQLite impossible: " + ex.getMessage(), ex);
        } finally {
            try {
                java.nio.file.Files.deleteIfExists(tmp);
            } catch (Exception ignore) {
            }
            try {
                java.nio.file.Files.deleteIfExists(java.nio.file.Path.of(tmp.toString() + "-wal"));
            } catch (Exception ignore) {
            }
            try {
                java.nio.file.Files.deleteIfExists(java.nio.file.Path.of(tmp.toString() + "-shm"));
            } catch (Exception ignore) {
            }
        }
    }


    @Override
    public void stop() {
        stopNotifications();
        if (dao != null) dao.close();
        if (userDb != null) userDb.close();
        if (view != null) view.shutdownExecutor();
        if (authDb != null) authDb.close();
    }
}




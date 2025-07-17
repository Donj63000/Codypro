package org.example;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;
import org.example.dao.AuthDB;
import org.example.dao.DB;
import org.example.dao.MailPrefsDAO;
import org.example.dao.UserDB;
import org.example.gui.LoginDialog;
import org.example.gui.MainView;
import org.example.gui.RegisterDialog;
import org.example.gui.ThemeManager;
import org.example.mail.Mailer;
import org.example.mail.MailPrefs;
import org.example.model.Facture;
import org.example.model.Prestataire;
import org.example.model.Rappel;
import org.example.security.AuthService;
import jakarta.mail.AuthenticationFailedException;
import com.google.api.client.auth.oauth2.TokenResponseException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MainApp extends Application {

    private DB dao;
    private MailPrefsDAO mailPrefsDao;
    private MainView view;
    private ScheduledExecutorService scheduler;
    private UserDB userDb;

    @Override
    public void start(Stage primaryStage) {
        try (AuthDB authDB = new AuthDB("auth.db")) {
            AuthService auth = new AuthService(authDB);
            try (Statement st = authDB.c().createStatement();
                 ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM users")) {
                if (rs.next() && rs.getInt(1) == 0) {
                    new RegisterDialog(auth).showAndWait();
                }
            }
            LoginDialog dlg = new LoginDialog(auth);
            dlg.showAndWait().ifPresent(sess -> {
                try {
                    Path dbPath = Path.of(System.getProperty("user.home"), ".prestataires", sess.username() + ".db");
                    Files.createDirectories(dbPath.getParent());
                    userDb = new UserDB(dbPath.toString(), sess.key());
                    dao = new DB(userDb::connection);
                    launchUI(primaryStage, dao, sess.key());
                } catch (Exception e) {
                    e.printStackTrace();
                    Alert a = new Alert(Alert.AlertType.ERROR, "Impossible d’ouvrir la base utilisateur :\n" + e.getMessage(), ButtonType.OK);
                    ThemeManager.apply(a);
                    a.showAndWait();
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void launchUI(Stage primaryStage, DB dao, javax.crypto.SecretKey key) {
        mailPrefsDao = new MailPrefsDAO(dao, key);
        view = new MainView(primaryStage, dao, mailPrefsDao);
        primaryStage.setTitle("Gestion des Prestataires");
        Scene sc = new Scene(view.getRoot(), 920, 600);
        ThemeManager.apply(sc);
        primaryStage.setScene(sc);
        primaryStage.show();
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::envoyerRappels, 1, 60, TimeUnit.MINUTES);
    }

    private void envoyerRappels() {
        try {
            MailPrefs cfg = mailPrefsDao.load();
            if (cfg == null) return;
            LocalDateTime lim = LocalDateTime.now().minusHours(cfg.delayHours());
            for (Facture f : dao.facturesImpayeesAvant(lim)) {
                try {
                    Prestataire pr = dao.findPrestataire(f.getPrestataireId());
                    if (pr == null) continue;
                    if (pr.getEmail() == null || pr.getEmail().isBlank()) continue;
                    Map<String, String> v = Mailer.vars(pr, f);
                    Mailer.send(mailPrefsDao, cfg, pr.getEmail(), Mailer.subjToPresta(cfg, v), Mailer.bodyToPresta(cfg, v));
                    if (!cfg.copyToSelf().isBlank()) {
                        Mailer.send(mailPrefsDao, cfg, cfg.copyToSelf(), Mailer.subjToSelf(cfg, v), Mailer.bodyToSelf(cfg, v));
                    }
                    dao.marquerPreavisEnvoye(f.getId());
                } catch (Exception ex) {
                    handleAuthException(ex);
                }
            }
            for (Rappel r : dao.rappelsÀEnvoyer()) {
                try {
                    Mailer.send(mailPrefsDao, cfg, r.dest(), r.sujet(), r.corps());
                    dao.markRappelEnvoyé(r.id());
                } catch (Exception ex) {
                    handleAuthException(ex);
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void handleAuthException(Throwable ex) {
        boolean authIssue = false;
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof AuthenticationFailedException || t instanceof TokenResponseException) {
                authIssue = true;
                break;
            }
        }
        if (authIssue) {
            mailPrefsDao.invalidateOAuth();
            Platform.runLater(() -> {
                Alert a = new Alert(Alert.AlertType.ERROR, "Authentification expirée ; veuillez reconfigurer votre compte e‑mail.", ButtonType.OK);
                ThemeManager.apply(a);
                a.showAndWait();
            });
        } else {
            ex.printStackTrace();
        }
    }

    @Override
    public void stop() {
        if (scheduler != null) scheduler.shutdownNow();
        if (dao != null) dao.close();
        if (userDb != null) userDb.close();
        if (view != null) view.shutdownExecutor();
    }
}

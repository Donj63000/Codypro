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

import javax.crypto.SecretKey;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class MainApp extends Application {

    private DB dao;
    private MailPrefsDAO mailPrefsDao;
    private MainView view;
    private ScheduledExecutorService scheduler;
    private UserDB userDb;
    private final Set<String> prenotified = ConcurrentHashMap.newKeySet();

    @Override
    public void start(Stage stage) {
        try (AuthDB auth = new AuthDB("auth.db")) {
            AuthService sec = new AuthService(auth);
            try (Statement st = auth.c().createStatement();
                 ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM users")) {
                if (rs.next() && rs.getInt(1) == 0) new RegisterDialog(sec).showAndWait();
            }
            LoginDialog dlg = new LoginDialog(sec);
            dlg.showAndWait().ifPresent(sess -> {
                try {
                    Path dbFile = Path.of(System.getProperty("user.home"), ".prestataires", sess.username() + ".db");
                    Files.createDirectories(dbFile.getParent());
                    userDb = new UserDB(dbFile.toString(), sess.key());
                    dao = new DB(userDb::connection);
                    launchUI(stage, sess.key());
                } catch (Exception ex) {
                    showError("Impossible d’ouvrir la base utilisateur :\n" + ex.getMessage());
                }
            });
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void launchUI(Stage stage, SecretKey key) {
        mailPrefsDao = new MailPrefsDAO(dao, key);
        view = new MainView(stage, dao, mailPrefsDao);
        stage.setTitle("Gestion des Prestataires");
        Scene sc = new Scene(view.getRoot(), 920, 600);
        ThemeManager.apply(sc);
        stage.setScene(sc);
        stage.show();

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "rappel-mailer");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::envoyerRappels, 1, 60, TimeUnit.MINUTES);
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
                            String subject = "Échéance dans " + slot + " h – facture " + f.getId();
                            String body = "La facture " + f.getId() + " (" +
                                    String.format("%.2f", f.getMontantTtc()) + " €) pour " +
                                    pr.getNom() + " arrive à échéance le " + f.getEcheanceFr() + ".";
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
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof AuthenticationFailedException || t instanceof TokenResponseException) {
                mailPrefsDao.invalidateOAuth();
                Platform.runLater(() ->
                        showError("Authentification expirée ; veuillez reconfigurer votre compte e‑mail."));
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
    }
}

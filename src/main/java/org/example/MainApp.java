package org.example;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.example.dao.DB;
import org.example.dao.MailPrefsDAO;
import org.example.gui.MainView;
import org.example.gui.ThemeManager;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import jakarta.mail.MessagingException;
import jakarta.mail.AuthenticationFailedException;
import com.google.api.client.auth.oauth2.TokenResponseException;
import org.example.mail.Mailer;
import org.example.mail.MailPrefs;
import org.example.model.Prestataire;

import java.time.LocalDateTime;
import java.util.Map;

import java.util.concurrent.*;

public class MainApp extends Application {
    private static final String DB_FILE = "prestataires.db";
    private DB dao;
    private MailPrefsDAO mailPrefsDao;
    private MainView view;
    private ScheduledExecutorService scheduler;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        dao = new DB(DB_FILE);
        mailPrefsDao = new MailPrefsDAO(dao);
        view = new MainView(primaryStage, dao, mailPrefsDao);
        primaryStage.setTitle("Gestion des Prestataires");
        Scene sc = new Scene(view.getRoot(), 920, 600);
        org.example.gui.ThemeManager.apply(sc);
        primaryStage.setScene(sc);
        primaryStage.show();

        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::envoyerRappels, 1, 60, TimeUnit.MINUTES);
    }

    private void envoyerRappels(){
        MailPrefs cfg = mailPrefsDao.load();          // ① config courante

        // ② rappels manuels (table rappels)
        dao.rappelsÀEnvoyer().forEach(r -> {
            try {
                Mailer.send(mailPrefsDao, cfg, r.dest(), r.sujet(), r.corps());
                dao.markRappelEnvoyé(r.id());
            } catch (MessagingException ex) {
                handleAuthException(ex);
                ex.printStackTrace();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        // ③ pré‑avis internes
        LocalDateTime lim = LocalDateTime.now().plusHours(cfg.delayHours());
        dao.facturesImpayeesAvant(lim).forEach(f -> {
            if(f.isPaye() || f.preavisEnvoye()) return;
            Prestataire pr = dao.findPrestataire(f.getPrestataireId());
            Map<String,String> v = Mailer.vars(pr,f);

            try {
                /* a) mail au prestataire */
                Mailer.send(mailPrefsDao, cfg, pr.getEmail(),
                        Mailer.subjToPresta(cfg,v),
                        Mailer.bodyToPresta(cfg,v));

                /* b) mail à nous‑même si renseigné */
                if (!cfg.copyToSelf().isBlank())
                    Mailer.send(mailPrefsDao, cfg, cfg.copyToSelf(),
                        Mailer.subjToSelf(cfg,v),
                        Mailer.bodyToSelf(cfg,v));

                dao.marquerPreavisEnvoye(f.getId());
                System.out.println("Pré‑avis envoyé pour facture "+f.getId());
            } catch (MessagingException ex) {
                handleAuthException(ex);
                ex.printStackTrace();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
    }

    private void handleAuthException(Throwable ex) {
        while (ex != null) {
            if (ex instanceof AuthenticationFailedException || ex instanceof TokenResponseException) {
                mailPrefsDao.invalidateOAuth();
                Platform.runLater(() -> {
                    Alert a = new Alert(Alert.AlertType.ERROR,
                            "Authentification expirée\u202f: merci de reconfigurer votre compte e-mail.",
                            ButtonType.OK);
                    ThemeManager.apply(a);
                    a.showAndWait();
                });
                break;
            }
            ex = ex.getCause();
        }
    }

    @Override
    public void stop() {
        if (scheduler != null) scheduler.shutdownNow();
        if (dao != null) dao.close();
        if (view != null) view.shutdownExecutor();
    }
}

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
import org.example.gui.RegisterDialog;
import org.example.gui.MainView;
import org.example.gui.ThemeManager;
import org.example.mail.Mailer;
import org.example.mail.MailPrefs;
import org.example.model.Prestataire;
import org.example.security.AuthService;

import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.MessagingException;
import com.google.api.client.auth.oauth2.TokenResponseException;

import java.nio.file.Path;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.*;
import java.sql.*;

public class MainApp extends Application {

    private DB dao;
    private MailPrefsDAO mailPrefsDao;
    private MainView view;
    private ScheduledExecutorService scheduler;

    /** Nouvel attribut : on garde UserDB ouvert jusqu’à la fin. */
    private UserDB userDb;

    @Override
    public void start(Stage primaryStage) {
        try (AuthDB authDB = new AuthDB("auth.db")) {

            AuthService auth = new AuthService(authDB);

            try (Statement st = authDB.c().createStatement();
                 ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM users")) {
                if (rs.next() && rs.getInt(1) == 0) {
                    RegisterDialog reg = new RegisterDialog(auth);
                    reg.showAndWait();
                }
            }

            LoginDialog dlg = new LoginDialog(auth);

            dlg.showAndWait().ifPresent(sess -> {
                try {
                    Path dbPath = Path.of(System.getProperty("user.home"),
                            ".prestataires",
                            sess.username() + ".db");

                    Files.createDirectories(dbPath.getParent());

                    /* --------- ouverture SQLCipher + DAO -------------- */
                    userDb = new UserDB(dbPath.toString(), sess.key());
                    dao    = new DB(userDb::connection);   // <-- CORRECTION
                    launchUI(primaryStage, dao, sess.key());

                } catch (Exception e) {
                    e.printStackTrace();
                    Alert a = new Alert(Alert.AlertType.ERROR,
                                        "Impossible d’ouvrir la base utilisateur :\n" + e.getMessage(),
                                        ButtonType.OK);
                    ThemeManager.apply(a);
                    a.showAndWait();
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /* ------------------------------------------------------------------ */
    /*                       Code existant inchangé                       */
    /* ------------------------------------------------------------------ */

    private void launchUI(Stage primaryStage, DB dao, javax.crypto.SecretKey key) {
        this.mailPrefsDao = new MailPrefsDAO(dao, key);
        this.view         = new MainView(primaryStage, dao, mailPrefsDao);

        primaryStage.setTitle("Gestion des Prestataires");
        Scene sc = new Scene(view.getRoot(), 920, 600);
        ThemeManager.apply(sc);
        primaryStage.setScene(sc);
        primaryStage.show();

        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::envoyerRappels, 1, 60, TimeUnit.MINUTES);
    }

    /* … (méthodes envoyerRappels, handleAuthException inchangées) … */

    @Override
    public void stop() {
        if (scheduler != null) scheduler.shutdownNow();
        if (dao       != null) dao.close();
        if (userDb    != null) userDb.close();   // <-- fermeture différée
        if (view      != null) view.shutdownExecutor();
    }
}

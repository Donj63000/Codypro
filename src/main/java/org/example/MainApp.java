package org.example;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.example.dao.DB;
import org.example.gui.MainView;
import org.example.mail.Mailer;
import org.example.mail.MailPrefs;

import java.util.concurrent.*;

public class MainApp extends Application {
    private static final String DB_FILE = "prestataires.db";
    private DB dao;
    private MainView view;
    private ScheduledExecutorService scheduler;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        dao = new DB(DB_FILE);
        view = new MainView(primaryStage, dao);
        primaryStage.setTitle("Gestion des Prestataires");
        Scene sc = new Scene(view.getRoot(), 920, 600);
        sc.getStylesheets().add(getClass().getResource("/css/dark.css").toExternalForm());
        primaryStage.setScene(sc);
        primaryStage.show();

        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::envoyerRappels, 1, 60, TimeUnit.MINUTES);
    }

    private void envoyerRappels() {
        dao.rappelsÀEnvoyer().forEach(r -> {
            try {
                Mailer.send(MailPrefs.defaultValues(), r.dest(), r.sujet(), r.corps());
                dao.markRappelEnvoyé(r.id());
                System.out.println("Rappel envoyé à " + r.dest());
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
    }

    @Override
    public void stop() {
        if (scheduler != null) scheduler.shutdownNow();
        if (dao != null) dao.close();
        if (view != null) view.shutdownExecutor();
    }
}

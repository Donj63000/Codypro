package org.example;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.example.dao.DB;
import org.example.gui.MainView;

public class MainApp extends Application {
    private static final String DB_FILE = "prestataires.db";
    private DB dao;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        dao = new DB(DB_FILE);
        MainView view = new MainView(primaryStage, dao);
        primaryStage.setTitle("Gestion des Prestataires — Rochias");
        primaryStage.setScene(new Scene(view.getRoot(), 920, 600));
        primaryStage.show();
    }

    @Override
    public void stop() {
        if (dao != null) {
            dao.close();
        }
    }
}

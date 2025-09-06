package org.example.gui;

import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.example.dao.DB;
import org.example.dao.MailPrefsDAO;

public final class MainView {
    private final Stage stage;
    private final DB dao;
    private final MailPrefsDAO mailDao;
    private final BorderPane root = new BorderPane();

    public MainView(Stage stage, DB dao, MailPrefsDAO mailDao) {
        this.stage = stage;
        this.dao = dao;
        this.mailDao = mailDao;

        MenuBar menuBar = new MenuBar();
        Menu menuFile = new Menu("Fichier");
        Menu menuTools = new Menu("Outils");
        MenuItem miMail = new MenuItem("Paramètres e‑mail…");
        miMail.setOnAction(e -> MailQuickSetupDialog.open(this.stage, this.mailDao));
        menuTools.getItems().addAll(miMail);
        menuBar.getMenus().addAll(menuFile, menuTools);

        HBox header = new HBox(new Label("Gestion des Prestataires"));
        header.setPadding(new Insets(8));
        header.setSpacing(8);
        Button bMail = new Button("Paramètres e‑mail");
        bMail.getStyleClass().add("accent");
        bMail.setOnAction(e -> MailQuickSetupDialog.open(this.stage, this.mailDao));
        header.getChildren().add(bMail);

        root.setTop(new BorderPane(header, menuBar, null, null, null));
    }

    public Parent getRoot() { return root; }

    public void shutdownExecutor() { /* no-op placeholder */ }
}


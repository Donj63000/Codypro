package org.example.gui;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.example.dao.DB;
import org.example.dao.MailPrefsDAO;
import org.example.model.Prestataire;

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

        try {
            Node center = buildCenter(this.dao);
            if (center == null) throw new IllegalStateException("center=null après buildCenter");
            root.setCenter(center);
        } catch (Exception ex) {
            ex.printStackTrace();
            Label err = new Label("Erreur d'initialisation de l'interface : " + ex.getMessage());
            err.setStyle("-fx-text-fill: -fx-text-background-color; -fx-font-size: 14px;");
            VBox fallback = new VBox(10, new Label("Gestion des Prestataires"), err);
            fallback.setPadding(new Insets(20));
            root.setCenter(fallback);
        }
    }

    public Parent getRoot() { return root; }

    public void shutdownExecutor() { /* no-op placeholder */ }

    private Node buildCenter(DB dao) {
        TableView<Prestataire> table = new TableView<>();
        table.setPlaceholder(new Label("Aucun prestataire pour le moment"));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        try {
            table.setItems(FXCollections.observableArrayList(dao.list("")));
        } catch (Exception ex) {
            ex.printStackTrace();
            table.setPlaceholder(new Label("Erreur de chargement : " + ex.getMessage()));
        }
        return new StackPane(table);
    }
}


package org.example.gui;

import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import java.util.Arrays;
import org.example.security.AuthService;

public class LoginDialog extends Dialog<AuthService.Session> {
    public LoginDialog(AuthService auth) {
        this(auth, "", "");
    }

    public LoginDialog(AuthService auth, String defaultUser, String defaultPwd) {
        setTitle("Connexion");

        DialogPane pane = getDialogPane();
        pane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextField tfUser = new TextField(defaultUser == null ? "" : defaultUser);
        tfUser.setPromptText("Nom d'utilisateur");
        PasswordField pfPwd = new PasswordField();
        if (defaultPwd != null) pfPwd.setText(defaultPwd);
        pfPwd.setPromptText("Mot de passe");

        GridPane grid = new GridPane();
        grid.setId("login-grid");
        grid.setHgap(10);
        grid.setVgap(12);
        grid.addRow(0, new Label("Utilisateur :"), tfUser);
        grid.addRow(1, new Label("Mot de passe :"), pfPwd);

        Label lblHeader = new Label("Veuillez vous connecter");
        lblHeader.getStyleClass().add("login-header");

        Label lblError = new Label();
        lblError.getStyleClass().add("error");

        VBox root = new VBox(lblHeader, grid, lblError);
        root.setPadding(new Insets(20, 32, 12, 32));
        root.setSpacing(10);
        pane.setContent(root);

        Button ok = (Button) pane.lookupButton(ButtonType.OK);
        ok.getStyleClass().add("accent");
        ok.setMaxWidth(Double.MAX_VALUE);
        ok.disableProperty().bind(tfUser.textProperty().isEmpty().or(pfPwd.textProperty().isEmpty()));

        tfUser.setOnAction(e -> ok.fire());
        pfPwd.setOnAction(e -> ok.fire());
        Platform.runLater(tfUser::requestFocus);

        final ObjectProperty<AuthService.Session> result = new SimpleObjectProperty<>();

        ok.addEventFilter(ActionEvent.ACTION, evt -> {
            try {
                String user = tfUser.getText().trim();
                char[] pwd  = pfPwd.getText().toCharArray();
                AuthService.Session sess = auth.login(user, pwd);
                Arrays.fill(pwd, '\0');
                if (sess == null) {
                    lblError.setText("Identifiants invalides");
                    shake(root);
                    evt.consume();
                } else {
                    result.set(sess);
                }
            } catch (Exception ex) {
                lblError.setText(ex.getMessage() != null ? ex.getMessage() : "Erreur d’authentification");
                shake(root);
                evt.consume();
            }
        });

        setResultConverter(bt -> bt == ButtonType.OK ? result.get() : null);

        ThemeManager.apply(this);
    }

    private static void shake(Node node) {
        TranslateTransition tt = new TranslateTransition(Duration.millis(60), node);
        tt.setByX(10);
        tt.setCycleCount(6);
        tt.setAutoReverse(true);
        tt.play();
    }
}

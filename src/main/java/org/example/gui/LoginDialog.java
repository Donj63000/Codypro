package org.example.gui;

import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;
import org.example.security.AuthService;

public class LoginDialog extends Dialog<AuthService.Session> {
    public LoginDialog(AuthService auth) {
        setTitle("Connexion");
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextField tfUser = new TextField();
        tfUser.setPromptText("Nom d'utilisateur");
        PasswordField pfPwd = new PasswordField();
        pfPwd.setPromptText("Mot de passe");

        GridPane grid = new GridPane();
        grid.setId("login-grid");
        grid.setHgap(10);
        grid.setVgap(12);
        grid.addRow(0, new Label("Utilisateur :"), tfUser);
        grid.addRow(1, new Label("Mot de passe :"), pfPwd);

        Label header = new Label("Veuillez vous connecter");
        header.getStyleClass().add("login-header");

        Label lblError = new Label();
        lblError.getStyleClass().add("error");

        VBox root = new VBox(header, grid, lblError);
        root.setPadding(new Insets(20, 32, 12, 32));
        root.setSpacing(10);
        getDialogPane().setContent(root);

        Button ok = (Button) getDialogPane().lookupButton(ButtonType.OK);
        ok.getStyleClass().add("accent");
        ok.setMaxWidth(Double.MAX_VALUE);

        tfUser.setOnAction(e -> ok.fire());
        pfPwd.setOnAction(e -> ok.fire());
        Platform.runLater(tfUser::requestFocus);

        setResultConverter(bt -> {
            if (bt == ButtonType.OK) {
                try {
                    AuthService.Session s = auth.login(tfUser.getText(), pfPwd.getText().toCharArray());
                    if (s == null) throw new IllegalArgumentException("Login invalide");
                    return s;
                } catch (Exception ex) {
                    lblError.setText(ex.getMessage());
                    shake(root);
                    return null;
                }
            }
            return null;
        });

        ThemeManager.apply(this);
    }

    private static void shake(Node node) {
        TranslateTransition tt = new TranslateTransition(Duration.millis(60), node);
        tt.setFromX(0);
        tt.setByX(10);
        tt.setCycleCount(6);
        tt.setAutoReverse(true);
        tt.play();
    }
}

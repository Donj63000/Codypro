package org.example.gui;

import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import org.example.security.AuthService;

public class LoginDialog extends Dialog<AuthService.Session> {
    public LoginDialog(AuthService auth) {
        setTitle("Connexion");
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        TextField tfUser = new TextField(); tfUser.setPromptText("Nom d'utilisateur");
        PasswordField pfPwd = new PasswordField(); pfPwd.setPromptText("Mot de passe");
        GridPane gp = new GridPane();
        gp.addRow(0, new Label("Utilisateur"), tfUser);
        gp.addRow(1, new Label("Mot de passe"), pfPwd);
        getDialogPane().setContent(gp);
        setResultConverter(bt -> {
            if (bt == ButtonType.OK) {
                try {
                    AuthService.Session s = auth.login(tfUser.getText(), pfPwd.getText().toCharArray());
                    if (s == null) throw new IllegalArgumentException("Login invalide");
                    return s;
                } catch (Exception ex) {
                    new Alert(Alert.AlertType.ERROR, ex.getMessage()).showAndWait();
                    return null;
                }
            }
            return null;
        });
    }
}

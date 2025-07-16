package org.example.gui;

import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import org.example.security.AuthService;

public class RegisterDialog extends Dialog<Boolean> {
    public RegisterDialog(AuthService auth) {
        setTitle("Création d'utilisateur");
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        TextField tfUser = new TextField();
        tfUser.setPromptText("Nom d'utilisateur");
        PasswordField pfPwd = new PasswordField();
        pfPwd.setPromptText("Mot de passe");
        GridPane gp = new GridPane();
        gp.addRow(0, new Label("Utilisateur"), tfUser);
        gp.addRow(1, new Label("Mot de passe"), pfPwd);
        getDialogPane().setContent(gp);
        setResultConverter(bt -> {
            if (bt == ButtonType.OK) {
                try {
                    auth.register(tfUser.getText(), pfPwd.getText().toCharArray());
                    return Boolean.TRUE;
                } catch (Exception ex) {
                    new Alert(Alert.AlertType.ERROR, ex.getMessage()).showAndWait();
                }
            }
            return null;
        });
    }
}

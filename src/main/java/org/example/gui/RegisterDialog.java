package org.example.gui;

import javafx.beans.binding.BooleanBinding;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import org.example.security.AuthService;

import java.util.Arrays;

public final class RegisterDialog extends Dialog<Boolean> {
    public RegisterDialog(AuthService auth) {
        setTitle("Création d'utilisateur");
        setResizable(false);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextField tfUser = new TextField();
        tfUser.setPromptText("Nom d'utilisateur");
        PasswordField pfPwd = new PasswordField();
        pfPwd.setPromptText("Mot de passe");

        GridPane gp = new GridPane();
        gp.setHgap(8);
        gp.setVgap(8);
        gp.setPadding(new Insets(12));
        gp.addRow(0, new Label("Utilisateur :"), tfUser);
        gp.addRow(1, new Label("Mot de passe :"), pfPwd);
        getDialogPane().setContent(gp);

        Button ok = (Button) getDialogPane().lookupButton(ButtonType.OK);
        BooleanBinding invalid = tfUser.textProperty().isEmpty().or(pfPwd.textProperty().isEmpty());
        ok.disableProperty().bind(invalid);

        setResultConverter(bt -> {
            if (bt == ButtonType.OK) {
                char[] pwd = pfPwd.getText().toCharArray();
                try {
                    auth.register(tfUser.getText().trim(), pwd);
                    return Boolean.TRUE;
                } catch (Exception ex) {
                    new Alert(Alert.AlertType.ERROR, ex.getMessage(), ButtonType.OK).showAndWait();
                } finally {
                    Arrays.fill(pwd, '\0');
                }
            }
            return null;
        });
        ThemeManager.apply(this);
    }
}

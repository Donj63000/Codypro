package org.example.gui;

import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import org.example.dao.MailPrefsDAO;
import org.example.mail.MailPrefs;
import org.example.mail.Mailer;

@Deprecated
public class MailSettingsDialog extends Dialog<MailPrefs> {
    private final MailPrefsDAO dao;

    public MailSettingsDialog(MailPrefs current, MailPrefsDAO dao) {
        this.dao = dao;

        setTitle("Paramètres e‑mail");
        setResizable(true);
        getDialogPane().setPrefSize(680, 520);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextField tfHost = new TextField(current.host());
        TextField tfPort = new TextField(String.valueOf(current.port()));
        CheckBox cbSSL   = new CheckBox("SSL");
        cbSSL.setSelected(current.ssl());
        TextField tfUser = new TextField(current.user());
        PasswordField tfPwd = new PasswordField();
        tfPwd.setText(current.pwd());
        TextField tfFrom = new TextField(current.from());
        TextField tfCopy = new TextField(current.copyToSelf());
        Spinner<Integer> spDelay = new Spinner<>(1, 240, current.delayHours());

        TextArea taSubjP = new TextArea(current.subjPresta());
        taSubjP.setPrefRowCount(2);
        TextArea taBodyP = new TextArea(current.bodyPresta());
        taBodyP.setPrefRowCount(3);
        TextArea taSubjS = new TextArea(current.subjSelf());
        taSubjS.setPrefRowCount(2);
        TextArea taBodyS = new TextArea(current.bodySelf());
        taBodyS.setPrefRowCount(3);

        Label vars = new Label("Variables : %NOM%, %EMAIL%, %MONTANT%, %ECHEANCE%, %ID%");
        vars.getStyleClass().add("caption");

        Button bTest = new Button("Tester l'envoi");
        bTest.getStyleClass().add("accent");

        GridPane gp = new GridPane();
        gp.setHgap(8);
        gp.setVgap(6);
        gp.setPadding(new Insets(12));
        int r = 0;
        gp.addRow(r++, new Label("SMTP :"), tfHost, new Label("Port"), tfPort, cbSSL);
        gp.addRow(r++, new Label("Utilisateur :"), tfUser);
        gp.addRow(r++, new Label("Mot de passe :"), tfPwd);
        gp.addRow(r++, new Label("Adresse expéditeur :"), tfFrom);
        gp.addRow(r++, new Label("Copie à (nous) :"), tfCopy);
        gp.addRow(r++, new Label("Délai pré‑avis (h) :"), spDelay);
        gp.add(bTest, 0, r++, 5, 1);
        gp.add(new Separator(), 0, r++, 5, 1);
        gp.addRow(r++, new Label("Sujet → prestataire"), taSubjP);
        gp.addRow(r++, new Label("Corps → prestataire"), taBodyP);
        gp.addRow(r++, new Label("Sujet → nous"), taSubjS);
        gp.addRow(r++, new Label("Corps → nous"), taBodyS);
        gp.add(vars, 0, r++, 5, 1);

        getDialogPane().setContent(gp);

        BooleanBinding portInvalid = Bindings.createBooleanBinding(() -> {
            try { Integer.parseInt(tfPort.getText()); return false; } catch (Exception e) { return true; }
        }, tfPort.textProperty());

        BooleanBinding invalid = tfHost.textProperty().isEmpty()
                .or(tfUser.textProperty().isEmpty())
                .or(tfFrom.textProperty().isEmpty())
                .or(portInvalid);

        Button ok = (Button) getDialogPane().lookupButton(ButtonType.OK);
        ok.disableProperty().bind(invalid);

        bTest.setOnAction(ev -> {
            MailPrefs tmp = new MailPrefs(
                    tfHost.getText(),
                    Integer.parseInt(tfPort.getText()),
                    cbSSL.isSelected(),
                    tfUser.getText(),
                    tfPwd.getText(),
                    current.provider(),
                    current.oauthClient(),
                    current.oauthRefresh(),
                    current.oauthExpiry(),
                    tfFrom.getText(),
                    tfCopy.getText(),
                    spDelay.getValue(),
                    current.style(),
                    taSubjP.getText(),
                    taBodyP.getText(),
                    taSubjS.getText(),
                    taBodyS.getText()
            );
            TextInputDialog td = new TextInputDialog(tmp.from());
            td.setTitle("Envoi de test");
            td.setHeaderText("Destinataire");
            ThemeManager.apply(td);
            td.showAndWait().ifPresent(addr -> {
                try {
                    Mailer.send(dao, tmp, addr, "Test", "Ceci est un message de test.");
                    alert(Alert.AlertType.INFORMATION, "E‑mail envoyé");
                } catch (Exception ex) {
                    alert(Alert.AlertType.ERROR, ex.getMessage());
                }
            });
        });

        setResultConverter(bt -> {
            if (bt != ButtonType.OK) return null;
            return new MailPrefs(
                    tfHost.getText(),
                    Integer.parseInt(tfPort.getText()),
                    cbSSL.isSelected(),
                    tfUser.getText(),
                    tfPwd.getText(),
                    current.provider(),
                    current.oauthClient(),
                    current.oauthRefresh(),
                    current.oauthExpiry(),
                    tfFrom.getText(),
                    tfCopy.getText(),
                    spDelay.getValue(),
                    current.style(),
                    taSubjP.getText(),
                    taBodyP.getText(),
                    taSubjS.getText(),
                    taBodyS.getText()
            );
        });

        ThemeManager.apply(this);
    }

    private void alert(Alert.AlertType type, String msg) {
        Alert a = new Alert(type, msg, ButtonType.OK);
        ThemeManager.apply(a);
        a.showAndWait();
    }

    public static void open(Stage owner, MailPrefsDAO dao) {
        MailSettingsDialog d = new MailSettingsDialog(dao.load(), dao);
        ThemeManager.apply(d);
        d.initOwner(owner);
        d.setHeaderText("Configurer le serveur SMTP, modèles et délai.");
        d.showAndWait().ifPresent(dao::save);
    }
}

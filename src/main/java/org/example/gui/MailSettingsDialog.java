package org.example.gui;

import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import org.example.dao.MailPrefsDAO;
import org.example.mail.Mailer;
import org.example.mail.MailPrefs;

/**
 * <p>Legacy dialog to edit mail server configuration and templates.</p>
 * <p>Replaced by {@link MailQuickSetupDialog} which offers additional
 * features and OAuth2 support.</p>
 */
@Deprecated
public class MailSettingsDialog extends Dialog<MailPrefs> {
    private final MailPrefsDAO dao;

    public MailSettingsDialog(MailPrefs current, MailPrefsDAO dao) {
        this.dao = dao;
        setTitle("Paramètres e-mail");
        setResizable(true);
        getDialogPane().setPrefSize(680, 520);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextField tfHost = new TextField(current.host());
        tfHost.setTooltip(new Tooltip("Serveur SMTP"));
        TextField tfPort = new TextField(String.valueOf(current.port()));
        tfPort.setTooltip(new Tooltip("Port SMTP"));
        CheckBox cbSSL = new CheckBox("SSL");
        cbSSL.setSelected(current.ssl());
        cbSSL.setTooltip(new Tooltip("Connexion sécurisée"));
        TextField tfUser = new TextField(current.user());
        tfUser.setTooltip(new Tooltip("Utilisateur SMTP"));
        PasswordField tfPwd = new PasswordField();
        tfPwd.setText(current.pwd());
        tfPwd.setTooltip(new Tooltip("Mot de passe SMTP"));
        TextField tfFrom = new TextField(current.from());
        tfFrom.setTooltip(new Tooltip("Adresse expéditeur"));
        TextField tfCopy = new TextField(current.copyToSelf());
        tfCopy.setTooltip(new Tooltip("Copie des préavis"));
        Spinner<Integer> spDelay = new Spinner<>(1, 240, current.delayHours());
        spDelay.setTooltip(new Tooltip("Délai pré-avis interne (heures)"));

        TextArea taSubjP = new TextArea(current.subjPresta());
        taSubjP.setPrefRowCount(2);
        taSubjP.setTooltip(new Tooltip("Sujet vers le prestataire"));
        TextArea taBodyP = new TextArea(current.bodyPresta());
        taBodyP.setPrefRowCount(3);
        taBodyP.setTooltip(new Tooltip("Corps vers le prestataire"));
        TextArea taSubjS = new TextArea(current.subjSelf());
        taSubjS.setPrefRowCount(2);
        taSubjS.setTooltip(new Tooltip("Sujet vers nous"));
        TextArea taBodyS = new TextArea(current.bodySelf());
        taBodyS.setPrefRowCount(3);
        taBodyS.setTooltip(new Tooltip("Corps vers nous"));

        Label vars = new Label("Variables : %NOM%, %EMAIL%, %MONTANT%, %ECHEANCE%, %ID%");
        vars.getStyleClass().add("caption");

        Button bTest = new Button("Tester l'envoi");
        bTest.getStyleClass().add("accent");

        GridPane gp = new GridPane();
        gp.setHgap(8);
        gp.setVgap(6);
        gp.setPadding(new Insets(12));
        int r = 0;
        gp.addRow(r++, new Label("SMTP :"), tfHost, new Label("Port"), tfPort, cbSSL);
        gp.addRow(r++, new Label("Utilisateur :"), tfUser);
        gp.addRow(r++, new Label("Mot de passe :"), tfPwd);
        gp.addRow(r++, new Label("Adresse expéditeur :"), tfFrom);
        gp.addRow(r++, new Label("Copie à (nous) :"), tfCopy);
        gp.addRow(r++, new Label("Délai pré-avis (h) :"), spDelay);
        gp.add(bTest, 0, r++, 5, 1);
        gp.add(new Separator(), 0, r++, 5, 1);
        gp.addRow(r++, new Label("Sujet → prestataire"), taSubjP);
        gp.addRow(r++, new Label("Corps → prestataire"), taBodyP);
        gp.addRow(r++, new Label("Sujet → nous"), taSubjS);
        gp.addRow(r++, new Label("Corps → nous"), taBodyS);
        gp.add(vars, 0, r++, 5, 1);

        getDialogPane().setContent(gp);

        // ---- validation bindings ----
        BooleanBinding portInvalid = Bindings.createBooleanBinding(() -> {
            try { Integer.parseInt(tfPort.getText()); return false; } catch(Exception e) { return true; }
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
                    Alert a = new Alert(Alert.AlertType.INFORMATION, "E-mail envoyé", ButtonType.OK);
                    ThemeManager.apply(a);
                    a.showAndWait();
                } catch (Exception ex) {
                    Alert a = new Alert(Alert.AlertType.ERROR, ex.getMessage(), ButtonType.OK);
                    ThemeManager.apply(a);
                    a.showAndWait();
                }
            });
        });

        setResultConverter(bt -> {
            if (bt == ButtonType.OK) {
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
            }
            return null;
        });
    }

    /** Utility entry point to open the dialog and persist changes. */
    public static void open(Stage owner, MailPrefsDAO dao) {
        MailSettingsDialog d = new MailSettingsDialog(dao.load(), dao);
        ThemeManager.apply(d);
        d.initOwner(owner);
        d.setHeaderText("Configurer le serveur SMTP, modèles et délai.");
        d.showAndWait().ifPresent(dao::save);
    }
}

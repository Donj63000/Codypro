package org.example.gui;

import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import org.example.dao.MailPrefsDAO;
import org.example.mail.MailPrefs;

public class MailPrefsDialog extends Dialog<MailPrefs> {
    public MailPrefsDialog(MailPrefs current){
        setTitle("Paramètres e‑mail");
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextField tfHost = new TextField(current.host());
        TextField tfPort = new TextField(String.valueOf(current.port()));
        CheckBox  cbSSL  = new CheckBox("SSL"); cbSSL.setSelected(current.ssl());
        TextField tfUser = new TextField(current.user());
        PasswordField tfPwd = new PasswordField(); tfPwd.setText(current.pwd());
        TextField tfFrom = new TextField(current.from());
        TextField tfCopy = new TextField(current.copyToSelf());
        Spinner<Integer> spDelay = new Spinner<>(1,240,current.delayHours());

        TextArea taSubjP = new TextArea(current.subjPresta());
        TextArea taBodyP = new TextArea(current.bodyPresta());
        TextArea taSubjS = new TextArea(current.subjSelf());
        TextArea taBodyS = new TextArea(current.bodySelf());

        GridPane gp = new GridPane(); gp.setHgap(8); gp.setVgap(6);
        int r=0;
        gp.addRow(r++, new Label("SMTP :"), tfHost, new Label("Port"), tfPort, cbSSL);
        gp.addRow(r++, new Label("Utilisateur :"), tfUser);
        gp.addRow(r++, new Label("Mot de passe :"), tfPwd);
        gp.addRow(r++, new Label("Adresse expéditeur :"), tfFrom);
        gp.addRow(r++, new Label("Copie à (nous) :"), tfCopy);
        gp.addRow(r++, new Label("Délai pré-avis (h) :"), spDelay);
        gp.add(new Separator(),0,r++,5,1);
        gp.addRow(r++, new Label("Sujet → prestataire"), taSubjP);
        gp.addRow(r++, new Label("Corps → prestataire"), taBodyP);
        gp.addRow(r++, new Label("Sujet → nous"), taSubjS);
        gp.addRow(r++, new Label("Corps → nous"), taBodyS);

        getDialogPane().setContent(gp);

        setResultConverter(bt -> {
            if(bt==ButtonType.OK){
                return new MailPrefs(tfHost.getText(),
                        Integer.parseInt(tfPort.getText()),
                        cbSSL.isSelected(),
                        tfUser.getText(),
                        tfPwd.getText(),
                        tfFrom.getText(),
                        tfCopy.getText(),
                        spDelay.getValue(),
                        taSubjP.getText(),
                        taBodyP.getText(),
                        taSubjS.getText(),
                        taBodyS.getText());
            }
            return null;
        });
    }

    /** sucré-salé : test d'envoi live */
    public static void open(Stage owner, MailPrefsDAO dao){
        MailPrefsDialog d = new MailPrefsDialog(dao.load());
        d.setHeaderText("Configurer le serveur SMTP, modèles et délai.");
        d.showAndWait().ifPresent(cfg -> dao.save(cfg));
    }
}

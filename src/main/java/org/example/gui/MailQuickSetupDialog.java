package org.example.gui;

import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.example.dao.MailPrefsDAO;
import org.example.mail.GoogleAuthService;
import org.example.mail.MailPrefs;
import org.example.mail.Mailer;
import org.example.mail.SmtpPreset;

/** Dialog providing simplified e‑mail configuration using provider presets. */
public class MailQuickSetupDialog extends Dialog<MailPrefs> {
    private final ComboBox<String> cbStyle;
    private final TextArea taSubjP;
    private final TextArea taBodyP;
    private final TextArea taSubjS;
    private final TextArea taBodyS;

    /** Accessor used in tests. */
    ComboBox<String> styleCombo() { return cbStyle; }

    public MailQuickSetupDialog(MailPrefs current, MailPrefsDAO dao) {
        setTitle("Paramètres e-mail");
        setResizable(true);
        getDialogPane().setPrefSize(680, 520);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        final MailPrefs[] prefsBox = { current };

        ComboBox<SmtpPreset> cbProv = new ComboBox<>(FXCollections.observableArrayList(SmtpPreset.PRESETS));
        SmtpPreset sel = SmtpPreset.byProvider(current.provider());
        if (sel == null) sel = SmtpPreset.PRESETS[0];
        cbProv.getSelectionModel().select(sel);

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

        Button bOAuth = new Button("Connexion Gmail...");
        bOAuth.getStyleClass().add("accent");
        bOAuth.setVisible(sel.oauth());
        bOAuth.setOnAction(ev -> {
            try {
                new GoogleAuthService(dao).interactiveAuth();
                prefsBox[0] = dao.load();
                Alert a = new Alert(Alert.AlertType.INFORMATION, "Authentification réussie", ButtonType.OK);
                ThemeManager.apply(a);
                a.showAndWait();
            } catch (Exception ex) {
                Alert a = new Alert(Alert.AlertType.ERROR, ex.getMessage(), ButtonType.OK);
                ThemeManager.apply(a);
                a.showAndWait();
            }
        });

        Button bTest = new Button("Tester l'envoi");
        bTest.getStyleClass().add("accent");

        // react to provider change
        cbProv.getSelectionModel().selectedItemProperty().addListener((o, p, n) -> {
            if (n != null) {
                tfHost.setText(n.host());
                tfPort.setText(String.valueOf(n.port()));
                cbSSL.setSelected(n.ssl());
                bOAuth.setVisible(n.oauth());
            }
        });

        cbStyle = new ComboBox<>(FXCollections.observableArrayList(MailPrefs.TEMPLATE_SETS.keySet()));
        cbStyle.getSelectionModel().select(current.style());

        GridPane gp = new GridPane();
        gp.setHgap(8); gp.setVgap(6); gp.setPadding(new Insets(12));
        int r = 0;
        gp.addRow(r++, new Label("Fournisseur :"), cbProv);
        gp.addRow(r++, new Label("Style :"), cbStyle);
        gp.addRow(r++, new Label("SMTP :"), tfHost, new Label("Port"), tfPort, cbSSL);
        gp.addRow(r++, new Label("Utilisateur :"), tfUser);
        gp.addRow(r++, new Label("Mot de passe :"), tfPwd);
        gp.addRow(r++, new Label("Adresse expéditeur :"), tfFrom);
        gp.addRow(r++, new Label("Copie à (nous) :"), tfCopy);
        gp.addRow(r++, new Label("Délai pré-avis (h) :"), spDelay);
        gp.add(bOAuth, 0, r++, 5, 1);
        gp.add(bTest, 0, r++, 5, 1);

        taSubjP = new TextArea(current.subjPresta());
        taSubjP.setPrefRowCount(2);
        taBodyP = new TextArea(current.bodyPresta());
        taBodyP.setPrefRowCount(3);
        taSubjS = new TextArea(current.subjSelf());
        taSubjS.setPrefRowCount(2);
        taBodyS = new TextArea(current.bodySelf());
        taBodyS.setPrefRowCount(3);
        cbStyle.valueProperty().addListener((o, p, n) -> {
            String[] tpl = MailPrefs.TEMPLATE_SETS.get(n);
            if (tpl != null) {
                taSubjP.setText(tpl[0]);
                taBodyP.setText(tpl[1]);
                taSubjS.setText(tpl[2]);
                taBodyS.setText(tpl[3]);
            }
        });
        Label vars = new Label("Variables : %NOM%, %EMAIL%, %MONTANT%, %ECHEANCE%, %ID%");
        vars.getStyleClass().add("caption");

        GridPane gpTpl = new GridPane();
        gpTpl.setHgap(8); gpTpl.setVgap(6); gpTpl.setPadding(new Insets(12));
        int t = 0;
        gpTpl.addRow(t++, new Label("Sujet → prestataire"), taSubjP);
        gpTpl.addRow(t++, new Label("Corps → prestataire"), taBodyP);
        gpTpl.addRow(t++, new Label("Sujet → nous"), taSubjS);
        gpTpl.addRow(t++, new Label("Corps → nous"), taBodyS);
        gpTpl.add(vars, 0, t++, 2, 1);
        TitledPane adv = new TitledPane("Options avancées", gpTpl);
        adv.setExpanded(false);

        VBox root = new VBox(gp, adv);
        getDialogPane().setContent(root);

        // validation bindings (same as MailSettingsDialog)
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
            MailPrefs base = MailPrefs.fromPreset(cbProv.getValue());
            MailPrefs tmp = new MailPrefs(
                    tfHost.getText(),
                    Integer.parseInt(tfPort.getText()),
                    cbSSL.isSelected(),
                    tfUser.getText(),
                    tfPwd.getText(),
                    base.provider(),
                    prefsBox[0].oauthClient(),
                    prefsBox[0].oauthRefresh(),
                    prefsBox[0].oauthExpiry(),
                    tfFrom.getText(),
                    tfCopy.getText(),
                    spDelay.getValue(),
                    cbStyle.getValue(),
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
                    Mailer.send(tmp, addr, "Test", "Ceci est un message de test.");
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
                MailPrefs base = MailPrefs.fromPreset(cbProv.getValue());
                return new MailPrefs(
                        tfHost.getText(),
                        Integer.parseInt(tfPort.getText()),
                        cbSSL.isSelected(),
                        tfUser.getText(),
                        tfPwd.getText(),
                        base.provider(),
                        prefsBox[0].oauthClient(),
                        prefsBox[0].oauthRefresh(),
                        prefsBox[0].oauthExpiry(),
                        tfFrom.getText(),
                        tfCopy.getText(),
                        spDelay.getValue(),
                        cbStyle.getValue(),
                        taSubjP.getText(),
                        taBodyP.getText(),
                        taSubjS.getText(),
                        taBodyS.getText()
                );
            }
            return null;
        });
    }

    /** Open the dialog and save the preferences if confirmed. */
    public static void open(Stage owner, MailPrefsDAO dao) {
        MailQuickSetupDialog d = new MailQuickSetupDialog(dao.load(), dao);
        ThemeManager.apply(d);
        d.initOwner(owner);
        d.setHeaderText("Configurer le serveur SMTP, modèles et délai.");
        d.showAndWait().ifPresent(dao::save);
    }
}

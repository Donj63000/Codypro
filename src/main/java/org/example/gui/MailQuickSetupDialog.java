package org.example.gui;

import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.example.gui.MailOAuthHelpDialog;
import org.example.dao.MailPrefsDAO;
import org.example.mail.GoogleAuthService;
import org.example.mail.MailPrefs;
import org.example.mail.Mailer;
import org.example.mail.SmtpPreset;
import org.example.mail.autodetect.AutoConfigProvider;
import org.example.mail.autodetect.AutoConfigResult;
import org.example.mail.autodetect.DefaultAutoConfigProvider;
import java.util.Properties;
import javafx.concurrent.Task;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

/** Dialog providing simplified e‑mail configuration using provider presets. */
public class MailQuickSetupDialog extends Dialog<MailPrefs> {
    private final MailPrefsDAO dao;
    private final ComboBox<String> cbStyle;
    private final TextArea taSubjP;
    private final TextArea taBodyP;
    private final TextArea taSubjS;
    private final TextArea taBodyS;
    private final AutoConfigProvider autoProv;
    private final CheckBox cbAuto;

    // fields exposed for tests
    private TextField tfHost;
    private TextField tfPort;
    private CheckBox cbSSL;
    private TextField tfFrom;

    /** Accessor used in tests. */
    ComboBox<String> styleCombo() { return cbStyle; }
    /** Accessor used in tests. */
    TextField hostField() { return tfHost; }
    /** Accessor used in tests. */
    TextField portField() { return tfPort; }
    /** Accessor used in tests. */
    CheckBox sslBox() { return cbSSL; }
    /** Accessor used in tests. */
    TextField fromField() { return tfFrom; }

    public MailQuickSetupDialog(MailPrefs current, MailPrefsDAO dao) {
        this(current, dao, new DefaultAutoConfigProvider());
    }

    public MailQuickSetupDialog(MailPrefs current, MailPrefsDAO dao, AutoConfigProvider provider) {
        this.dao = dao;
        this.autoProv = provider;
        setTitle("Paramètres e-mail");
        setResizable(true);
        getDialogPane().setPrefSize(680, 520);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        final MailPrefs[] prefsBox = { current };
        final String[] oauthBox = { current.oauthClient() };
        final GoogleAuthService[] gmailSvc = { null };

        // choose between classical SMTP and Gmail OAuth2
        ToggleGroup tgMode = new ToggleGroup();
        RadioButton rbClassic = new RadioButton("SMTP classique");
        RadioButton rbOauth2 = new RadioButton("Gmail OAuth2");
        Button bHelp = new Button("Aide");
        bHelp.setOnAction(ev -> new MailOAuthHelpDialog().showAndWait());
        rbClassic.setToggleGroup(tgMode);
        rbOauth2.setToggleGroup(tgMode);
        rbClassic.setSelected(true);

        ComboBox<SmtpPreset> cbProv = new ComboBox<>(FXCollections.observableArrayList(SmtpPreset.PRESETS));
        SmtpPreset sel = SmtpPreset.byProvider(current.provider());
        if (sel == null) sel = SmtpPreset.PRESETS[0];
        cbProv.getSelectionModel().select(sel);

        tfHost = new TextField(current.host());
        tfHost.setTooltip(new Tooltip("Serveur SMTP"));
        tfPort = new TextField(String.valueOf(current.port()));
        tfPort.setTooltip(new Tooltip("Port SMTP"));
        cbSSL = new CheckBox("SSL");
        cbSSL.setSelected(current.ssl());
        cbSSL.setTooltip(new Tooltip("Connexion sécurisée"));
        TextField tfUser = new TextField(current.user());
        tfUser.setTooltip(new Tooltip("Utilisateur SMTP"));
        TextField tfGmail = new TextField(current.user());
        tfGmail.setEditable(false);
        tfGmail.setVisible(false);
        TextField tfClient = new TextField(oauthBox[0]);
        tfClient.setVisible(false);
        tfClient.textProperty().addListener((o,p,n) -> oauthBox[0] = n);
        PasswordField tfPwd = new PasswordField();
        tfPwd.setText(current.pwd());
        tfPwd.setTooltip(new Tooltip("Mot de passe SMTP"));
        tfFrom = new TextField(current.from());
        tfFrom.setTooltip(new Tooltip("Adresse expéditeur"));
        TextField tfCopy = new TextField(current.copyToSelf());
        tfCopy.setTooltip(new Tooltip("Copie des préavis"));
        Spinner<Integer> spDelay = new Spinner<>(1, 240, current.delayHours());
        spDelay.setTooltip(new Tooltip("Délai pré-avis interne (heures)"));

        cbAuto = new CheckBox("Auto-découverte serveur");
        cbAuto.setSelected(true);

        Button bOAuth = new Button("Se connecter à Google");
        bOAuth.getStyleClass().add("accent");
        bOAuth.setVisible(false);
        bOAuth.setOnAction(ev -> {
            String[] parts = tfClient.getText().split(":", 2);
            if (parts.length < 2 || parts[0].isBlank() || parts[1].isBlank()) {
                Alert a = new Alert(Alert.AlertType.ERROR,
                        "Les champs clientId et clientSecret doivent être renseignés",
                        ButtonType.OK);
                ThemeManager.apply(a);
                a.showAndWait();
                return;
            }
            try {
                MailPrefs base = prefsBox[0];
                MailPrefs tmp = new MailPrefs(
                        base.host(), base.port(), base.ssl(),
                        base.user(), base.pwd(),
                        base.provider(), oauthBox[0],
                        base.oauthRefresh(), base.oauthExpiry(),
                        base.from(), base.copyToSelf(), base.delayHours(),
                        base.style(),
                        base.subjPresta(), base.bodyPresta(),
                        base.subjSelf(), base.bodySelf()
                );
                dao.save(tmp);
                GoogleAuthService svc = new GoogleAuthService(dao);
                svc.interactiveAuth();
                prefsBox[0] = dao.load();
                gmailSvc[0] = svc;
                tfGmail.setText(prefsBox[0].user());
                tfGmail.setVisible(true);
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

        // toggle between classic SMTP and Gmail OAuth2
        rbClassic.selectedProperty().addListener((o, p, n) -> {
            boolean classic = n;
            tfHost.setDisable(!classic);
            tfPort.setDisable(!classic);
            cbSSL.setDisable(!classic);
            tfUser.setDisable(!classic);
            tfPwd.setDisable(!classic);
            cbAuto.setDisable(!classic);
            bOAuth.setVisible(!classic);
            tfGmail.setVisible(!classic);
            tfClient.setVisible(!classic);
        });

        // auto discovery when sender address changes
        final String[] lastDomain = {""};
        tfFrom.textProperty().addListener((o, p, n) -> {
            int at = n.indexOf('@');
            if (!cbAuto.isSelected() || at < 0 || at == n.length() - 1) return;
            String dom = n.substring(at + 1);
            if (dom.equals(lastDomain[0])) return;
            lastDomain[0] = dom;
            Task<AutoConfigResult> task = new Task<>() {
                @Override protected AutoConfigResult call() throws Exception {
                    return autoProv.discover(n);
                }
            };
            task.setOnSucceeded(ev -> {
                AutoConfigResult res = task.getValue();
                if (res != null && cbAuto.isSelected()) {
                    tfHost.setText(res.host());
                    tfPort.setText(String.valueOf(res.port()));
                    cbSSL.setSelected(res.ssl());
                }
            });
            Thread th = new Thread(task, "smtp-auto");
            th.setDaemon(true);
            th.start();
        });

        // react to provider change
        cbProv.getSelectionModel().selectedItemProperty().addListener((o, p, n) -> {
            if (n != null) {
                tfHost.setText(n.host());
                tfPort.setText(String.valueOf(n.port()));
                cbSSL.setSelected(n.ssl());
                bOAuth.setVisible(n.oauth());
                tfClient.setVisible(n.oauth());
            }
        });

        cbStyle = new ComboBox<>(FXCollections.observableArrayList(MailPrefs.TEMPLATE_SETS.keySet()));
        cbStyle.getSelectionModel().select(current.style());

        GridPane gp = new GridPane();
        gp.setHgap(8); gp.setVgap(6); gp.setPadding(new Insets(12));
        int r = 0;
        gp.addRow(r++, new Label("Mode d'envoi :"), rbClassic, rbOauth2, bHelp);
        gp.addRow(r++, new Label("Fournisseur :"), cbProv);
        gp.addRow(r++, new Label("Client OAuth :"), tfClient);
        gp.addRow(r++, new Label("Adresse Gmail :"), tfGmail);
        gp.addRow(r++, new Label("Style :"), cbStyle);
        gp.addRow(r++, new Label("SMTP :"), tfHost, new Label("Port"), tfPort, cbSSL);
        gp.addRow(r++, new Label("Utilisateur :"), tfUser);
        gp.addRow(r++, new Label("Mot de passe :"), tfPwd);
        gp.addRow(r++, new Label("Adresse expéditeur :"), tfFrom);
        gp.addRow(r++, new Label("Copie à (nous) :"), tfCopy);
        gp.addRow(r++, new Label("Délai pré-avis (h) :"), spDelay);
        gp.addRow(r++, cbAuto);
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
        BooleanBinding classicInvalid = tfHost.textProperty().isEmpty()
                .or(tfUser.textProperty().isEmpty())
                .or(portInvalid);
        BooleanBinding oauthInvalid = Bindings.createBooleanBinding(
                () -> {
                    String[] p = tfClient.getText().split(":", 2);
                    return p.length < 2 || p[0].isBlank() || p[1].isBlank();
                }, tfClient.textProperty());
        BooleanBinding invalid = rbClassic.selectedProperty().and(classicInvalid)
                .or(tfFrom.textProperty().isEmpty())
                .or(rbOauth2.selectedProperty().and(oauthInvalid));
        Button ok = (Button) getDialogPane().lookupButton(ButtonType.OK);
        ok.disableProperty().bind(invalid);
        bOAuth.disableProperty().bind(oauthInvalid);

        bTest.setOnAction(ev -> {
            MailPrefs base = MailPrefs.fromPreset(cbProv.getValue());
            MailPrefs tmp = new MailPrefs(
                    tfHost.getText(),
                    Integer.parseInt(tfPort.getText()),
                    cbSSL.isSelected(),
                    tfUser.getText(),
                    tfPwd.getText(),
                    base.provider(),
                    oauthBox[0],
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
                    if (rbOauth2.isSelected() && gmailSvc[0] != null) {
                        String token = gmailSvc[0].getAccessToken();
                        Properties p = new Properties();
                        p.put("mail.smtp.auth", "true");
                        if (cbSSL.isSelected()) {
                            p.put("mail.smtp.ssl.enable", "true");
                        } else {
                            p.put("mail.smtp.starttls.enable", "true");
                        }
                        p.put("mail.smtp.host", tfHost.getText());
                        p.put("mail.smtp.port", tfPort.getText());
                        p.put("mail.smtp.sasl.enable", "true");
                        p.put("mail.smtp.sasl.mechanisms", "XOAUTH2");
                        p.put("mail.smtp.auth.mechanisms", "XOAUTH2");
                        p.put("mail.smtp.auth.login.disable", "true");
                        p.put("mail.smtp.auth.plain.disable", "true");
                        Session s = Session.getInstance(p, new Authenticator() {
                            @Override
                            protected PasswordAuthentication getPasswordAuthentication() {
                                return new PasswordAuthentication(tfGmail.getText(), token);
                            }
                        });
                        Message m = new MimeMessage(s);
                        m.setFrom(new InternetAddress(tfFrom.getText()));
                        m.setRecipients(Message.RecipientType.TO, InternetAddress.parse(addr, false));
                        m.setSubject("Test");
                        m.setText("Ceci est un message de test.");
                        Transport.send(m);
                    } else {
                        Mailer.send(dao, tmp, addr, "Test", "Ceci est un message de test.");
                    }
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
                        oauthBox[0],
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
        MailQuickSetupDialog d = new MailQuickSetupDialog(dao.load(), dao, new DefaultAutoConfigProvider());
        ThemeManager.apply(d);
        d.initOwner(owner);
        d.setHeaderText("Configurer le serveur SMTP, modèles et délai.");
        d.showAndWait().ifPresent(dao::save);
    }
}

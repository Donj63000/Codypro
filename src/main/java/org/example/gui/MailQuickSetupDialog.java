package org.example.gui;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.example.dao.MailPrefsDAO;
import org.example.mail.GoogleAuthService;
import org.example.mail.MailPrefs;
import org.example.mail.Mailer;
import org.example.mail.SmtpPreset;
import org.example.mail.autodetect.AutoConfigProvider;
import org.example.mail.autodetect.AutoConfigResult;
import org.example.mail.autodetect.DefaultAutoConfigProvider;

import java.util.Properties;

public class MailQuickSetupDialog extends Dialog<MailPrefs> {
    private final MailPrefsDAO dao;
    private final ComboBox<String> cbStyle;
    private final TextArea taSubjP;
    private final TextArea taBodyP;
    private final TextArea taSubjS;
    private final TextArea taBodyS;
    private final AutoConfigProvider autoProv;
    private final CheckBox cbAuto;
    private final TextField tfHost;
    private final TextField tfPort;
    private final CheckBox cbSSL;
    private final TextField tfFrom;

    ComboBox<String> styleCombo() { return cbStyle; }
    TextField hostField()         { return tfHost; }
    TextField portField()         { return tfPort; }
    CheckBox  sslBox()            { return cbSSL; }
    TextField fromField()         { return tfFrom; }

    public MailQuickSetupDialog(MailPrefs current, MailPrefsDAO dao) {
        this(current, dao, new DefaultAutoConfigProvider());
    }

    public MailQuickSetupDialog(MailPrefs current, MailPrefsDAO dao, AutoConfigProvider provider) {
        this.dao = dao;
        this.autoProv = provider;

        setTitle("Paramètres e‑mail");
        setResizable(true);
        getDialogPane().setPrefSize(680, 520);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        final MailPrefs[] prefsBox = {current};
        final String[] oauthBox    = {current.oauthClient()};
        final GoogleAuthService[] gmailSvc = {null};

        ToggleGroup tgMode = new ToggleGroup();
        RadioButton rbClassic = new RadioButton("SMTP classique");
        RadioButton rbOauth2  = new RadioButton("Gmail OAuth2");
        rbClassic.setToggleGroup(tgMode);
        rbOauth2.setToggleGroup(tgMode);
        rbClassic.setSelected(true);

        Button bOAuthHelp  = new Button("Aide");
        bOAuthHelp.setOnAction(e -> new MailOAuthHelpDialog().showAndWait());
        Button bSmtpHelp   = new Button("Aide SMTP");
        bSmtpHelp.setOnAction(e -> new SmtpHelpDialog().showAndWait());

        ComboBox<SmtpPreset> cbProv = new ComboBox<>(FXCollections.observableArrayList(SmtpPreset.PRESETS));
        SmtpPreset sel = SmtpPreset.byProvider(current.provider());
        cbProv.getSelectionModel().select(sel == null ? SmtpPreset.PRESETS[0] : sel);

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
        tfClient.textProperty().addListener((o, p, n) -> oauthBox[0] = n);
        PasswordField tfPwd = new PasswordField();
        tfPwd.setText(current.pwd());
        tfPwd.setTooltip(new Tooltip("Mot de passe SMTP"));
        tfFrom = new TextField(current.from());
        tfFrom.setTooltip(new Tooltip("Adresse expéditeur"));
        TextField tfCopy = new TextField(current.copyToSelf());
        tfCopy.setTooltip(new Tooltip("Copie des préavis"));
        Spinner<Integer> spDelay = new Spinner<>(1, 240, current.delayHours());
        spDelay.setTooltip(new Tooltip("Délai préavis interne (heures)"));

        cbAuto = new CheckBox("Auto‑découverte serveur");
        cbAuto.setSelected(true);

        Button bOAuth = new Button("Se connecter à Google");
        bOAuth.getStyleClass().add("accent");
        bOAuth.setVisible(false);

        Button bTest = new Button("Tester l'envoi");
        bTest.getStyleClass().add("accent");

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

        final String[] lastDomain = {""};
        tfFrom.textProperty().addListener((o, p, n) -> {
            int at = n.indexOf('@');
            if (!cbAuto.isSelected() || at < 0 || at == n.length() - 1) return;
            String dom = n.substring(at + 1);
            if (dom.equals(lastDomain[0])) return;
            lastDomain[0] = dom;
            Task<AutoConfigResult> task = new Task<>() {
                @Override protected AutoConfigResult call() throws Exception { return autoProv.discover(n); }
            };
            task.setOnSucceeded(ev -> {
                AutoConfigResult res = task.getValue();
                if (res != null && cbAuto.isSelected()) {
                    tfHost.setText(res.host());
                    tfPort.setText(String.valueOf(res.port()));
                    cbSSL.setSelected(res.ssl());
                }
            });
            Thread th = new Thread(task, "smtp-auto"); th.setDaemon(true); th.start();
        });

        cbProv.getSelectionModel().selectedItemProperty().addListener((o, p, n) -> {
            if (n == null) return;
            tfHost.setText(n.host());
            tfPort.setText(String.valueOf(n.port()));
            cbSSL.setSelected(n.ssl());
            bOAuth.setVisible(n.oauth());
            tfClient.setVisible(n.oauth());
        });

        cbStyle = new ComboBox<>(FXCollections.observableArrayList(MailPrefs.TEMPLATE_SETS.keySet()));
        cbStyle.getSelectionModel().select(current.style());

        GridPane gp = new GridPane();
        gp.setHgap(8); gp.setVgap(6); gp.setPadding(new Insets(12));
        int r = 0;
        gp.addRow(r++, new Label("Mode d'envoi :"), rbClassic, rbOauth2, bOAuthHelp, bSmtpHelp);
        gp.addRow(r++, new Label("Fournisseur :"), cbProv);
        gp.addRow(r++, new Label("Client OAuth (ID) :"), tfClient);
        gp.addRow(r++, new Label("Adresse Gmail :"), tfGmail);
        gp.addRow(r++, new Label("Style :"), cbStyle);
        gp.addRow(r++, new Label("SMTP :"), tfHost, new Label("Port"), tfPort, cbSSL);
        gp.addRow(r++, new Label("Utilisateur :"), tfUser);
        gp.addRow(r++, new Label("Mot de passe :"), tfPwd);
        gp.addRow(r++, new Label("Adresse expéditeur :"), tfFrom);
        gp.addRow(r++, new Label("Copie à (nous) :"), tfCopy);
        gp.addRow(r++, new Label("Délai préavis (h) :"), spDelay);
        gp.addRow(r++, cbAuto);
        gp.add(bOAuth, 0, r++, 5, 1);
        gp.add(bTest, 0, r++, 5, 1);

        taSubjP = new TextArea(current.subjPresta()); taSubjP.setPrefRowCount(2);
        taBodyP = new TextArea(current.bodyPresta()); taBodyP.setPrefRowCount(3);
        taSubjS = new TextArea(current.subjSelf());   taSubjS.setPrefRowCount(2);
        taBodyS = new TextArea(current.bodySelf());   taBodyS.setPrefRowCount(3);
        cbStyle.valueProperty().addListener((o, p, n) -> {
            String[] tpl = MailPrefs.TEMPLATE_SETS.get(n);
            if (tpl != null) {
                taSubjP.setText(tpl[0]);
                taBodyP.setText(tpl[1]);
                taSubjS.setText(tpl[2]);
                taBodyS.setText(tpl[3]);
            }
        });

        GridPane gpTpl = new GridPane();
        gpTpl.setHgap(8); gpTpl.setVgap(6); gpTpl.setPadding(new Insets(12));
        int t = 0;
        gpTpl.addRow(t++, new Label("Sujet → prestataire"), taSubjP);
        gpTpl.addRow(t++, new Label("Corps → prestataire"), taBodyP);
        gpTpl.addRow(t++, new Label("Sujet → nous"), taSubjS);
        gpTpl.addRow(t++, new Label("Corps → nous"), taBodyS);
        gpTpl.add(new Label("Variables : %NOM%, %EMAIL%, %MONTANT%, %ECHEANCE%, %ID%"), 0, t++, 2, 1);

        TitledPane adv = new TitledPane("Options avancées", gpTpl);
        adv.setExpanded(false);

        getDialogPane().setContent(new VBox(gp, adv));

        BooleanBinding portInvalid = Bindings.createBooleanBinding(() -> {
            try { Integer.parseInt(tfPort.getText()); return false; } catch (Exception e) { return true; }
        }, tfPort.textProperty());

        BooleanBinding classicInvalid = tfHost.textProperty().isEmpty()
                .or(tfUser.textProperty().isEmpty())
                .or(portInvalid);

        BooleanBinding oauthInvalid = Bindings.createBooleanBinding(() -> tfClient.getText().trim().isEmpty(), tfClient.textProperty());

        BooleanBinding invalid = rbClassic.selectedProperty().and(classicInvalid)
                .or(tfFrom.textProperty().isEmpty())
                .or(rbOauth2.selectedProperty().and(oauthInvalid));

        Button okBtn = (Button) getDialogPane().lookupButton(ButtonType.OK);
        okBtn.disableProperty().bind(invalid);
        bOAuth.disableProperty().bind(oauthInvalid);

        bOAuth.setOnAction(ev -> {
            if (tfClient.getText().trim().isEmpty()) {
                alert(Alert.AlertType.ERROR, "Le champ Client OAuth (ID) est requis");
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
                        base.style(), base.subjPresta(), base.bodyPresta(),
                        base.subjSelf(), base.bodySelf()
                );
                dao.save(tmp);
                GoogleAuthService svc = new GoogleAuthService(dao);
                int port = svc.interactiveAuth();
                prefsBox[0] = dao.load();
                gmailSvc[0] = svc;
                tfGmail.setText(prefsBox[0].user());
                tfGmail.setVisible(true);
                alert(Alert.AlertType.INFORMATION, "Authentification réussie (port " + port + ")");
            } catch (Exception ex) {
                alert(Alert.AlertType.ERROR, ex.getMessage());
            }
        });

        bTest.setOnAction(ev -> {
            MailPrefs base = MailPrefs.fromPreset(cbProv.getValue());
            MailPrefs tmp = new MailPrefs(
                    tfHost.getText(), Integer.parseInt(tfPort.getText()), cbSSL.isSelected(),
                    tfUser.getText(), tfPwd.getText(),
                    base.provider(), oauthBox[0],
                    prefsBox[0].oauthRefresh(), prefsBox[0].oauthExpiry(),
                    tfFrom.getText(), tfCopy.getText(), spDelay.getValue(),
                    cbStyle.getValue(),
                    taSubjP.getText(), taBodyP.getText(),
                    taSubjS.getText(), taBodyS.getText()
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
                        if (cbSSL.isSelected()) p.put("mail.smtp.ssl.enable", "true");
                        else p.put("mail.smtp.starttls.enable", "true");
                        p.put("mail.smtp.host", tfHost.getText());
                        p.put("mail.smtp.port", tfPort.getText());
                        p.put("mail.smtp.sasl.enable", "true");
                        p.put("mail.smtp.sasl.mechanisms", "XOAUTH2");
                        p.put("mail.smtp.auth.mechanisms", "XOAUTH2");
                        p.put("mail.smtp.auth.login.disable", "true");
                        p.put("mail.smtp.auth.plain.disable", "true");
                        Session s = Session.getInstance(p, new Authenticator() {
                            @Override protected PasswordAuthentication getPasswordAuthentication() {
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
                    alert(Alert.AlertType.INFORMATION, "E‑mail envoyé");
                } catch (Exception ex) {
                    alert(Alert.AlertType.ERROR, ex.getMessage());
                }
            });
        });

        setResultConverter(bt -> bt == ButtonType.OK ? buildPrefs(cbProv.getValue(), prefsBox[0], oauthBox[0],
                tfHost, tfPort, cbSSL, tfUser, tfPwd, tfFrom, tfCopy, spDelay, cbStyle,
                taSubjP, taBodyP, taSubjS, taBodyS) : null);
    }

    private static MailPrefs buildPrefs(SmtpPreset preset, MailPrefs base, String oauth,
                                        TextField host, TextField port, CheckBox ssl,
                                        TextField user, PasswordField pwd,
                                        TextField from, TextField copy,
                                        Spinner<Integer> delay,
                                        ComboBox<String> style,
                                        TextArea sp, TextArea bp, TextArea ss, TextArea bs) {
        return new MailPrefs(
                host.getText(),
                Integer.parseInt(port.getText()),
                ssl.isSelected(),
                user.getText(),
                pwd.getText(),
                preset.provider(),
                oauth,
                base.oauthRefresh(),
                base.oauthExpiry(),
                from.getText(),
                copy.getText(),
                delay.getValue(),
                style.getValue(),
                sp.getText(),
                bp.getText(),
                ss.getText(),
                bs.getText()
        );
    }

    private void alert(Alert.AlertType type, String txt) {
        Alert a = new Alert(type, txt, ButtonType.OK);
        ThemeManager.apply(a);
        a.showAndWait();
    }

    public static void open(Stage owner, MailPrefsDAO dao) {
        MailQuickSetupDialog d = new MailQuickSetupDialog(dao.load(), dao, new DefaultAutoConfigProvider());
        ThemeManager.apply(d);
        d.initOwner(owner);
        d.setHeaderText("Configurer le serveur SMTP, modèles et délai.");
        d.showAndWait().ifPresent(dao::save);
    }
}


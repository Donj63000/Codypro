package org.example.gui;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import org.example.dao.MailPrefsDAO;
import org.example.mail.GoogleAuthService;
import org.example.mail.MailPrefs;

/**
 * <p>Legacy dialog that configured e-mail templates and recipients.</p>
 * <p>
 * It has been superseded by {@link MailSettingsDialog} which provides the
 * same features with additional SMTP options and validation.
 * </p>
 *
 * @deprecated use {@link MailSettingsDialog} instead
 */
@Deprecated
public class MailWizardDialog extends Dialog<MailPrefs> {
    public MailWizardDialog(MailPrefs current, MailPrefsDAO dao) {
        setTitle("Assistant e-mail");
        setResizable(true);
        getDialogPane().setPrefSize(700, 500);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // keep prefs when Gmail login updates them
        final MailPrefs[] prefsBox = { current };

        Button bLogin = new Button("Connexion Gmail...");
        bLogin.getStyleClass().add("accent");
        bLogin.setOnAction(ev -> {
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

        Label vars = new Label("Variables : %NOM%, %EMAIL%, %MONTANT%, %ECHEANCE%, %ID%");
        vars.getStyleClass().add("caption");

        GridPane gp = new GridPane();
        gp.setHgap(8);
        gp.setVgap(6);
        gp.setPadding(new Insets(12));
        int r = 0;
        gp.add(bLogin, 0, r++, 2, 1);
        gp.addRow(r++, new Label("Adresse expéditeur :"), tfFrom);
        gp.addRow(r++, new Label("Copie à (nous) :"), tfCopy);
        gp.addRow(r++, new Label("Délai pré-avis (h) :"), spDelay);
        gp.add(new Separator(), 0, r++, 2, 1);
        gp.addRow(r++, new Label("Sujet → prestataire"), taSubjP);
        gp.addRow(r++, new Label("Corps → prestataire"), taBodyP);
        gp.addRow(r++, new Label("Sujet → nous"), taSubjS);
        gp.addRow(r++, new Label("Corps → nous"), taBodyS);
        gp.add(vars, 0, r++, 2, 1);

        getDialogPane().setContent(gp);

        setResultConverter(bt -> {
            if (bt == ButtonType.OK) {
                MailPrefs base = prefsBox[0];
                return new MailPrefs(
                        base.host(), base.port(), base.ssl(),
                        base.user(), base.pwd(),
                        base.provider(), base.oauthClient(),
                        base.oauthRefresh(), base.oauthExpiry(),
                        tfFrom.getText(), tfCopy.getText(),
                        spDelay.getValue(),
                        base.style(),
                        taSubjP.getText(), taBodyP.getText(),
                        taSubjS.getText(), taBodyS.getText()
                );
            }
            return null;
        });
    }

    /**
     * Opens the legacy dialog.
     *
     * @deprecated prefer {@link MailSettingsDialog#open(Stage, MailPrefsDAO)}
     */
    @Deprecated
    public static void open(Stage owner, MailPrefsDAO dao) {
        MailSettingsDialog.open(owner, dao);
    }
}

package org.example.gui;

import javafx.beans.value.ChangeListener;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.StringConverter;
import org.example.AppServices;
import org.example.model.NotificationSettings;
import org.example.model.Rappel;
import org.example.model.SmtpSecurity;
import org.example.util.NotificationTemplateEngine;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public final class NotificationSettingsDialog extends Dialog<NotificationSettings> {

    private final Spinner<Integer> leadDaysSpinner;
    private final Spinner<Integer> reminderHourSpinner;
    private final Spinner<Integer> reminderMinuteSpinner;
    private final Spinner<Integer> repeatHoursSpinner;
    private final Spinner<Integer> snoozeMinutesSpinner;
    private final CheckBox highlightOverdueCheck;
    private final CheckBox desktopPopupCheck;

    private final CheckBox managerEmailEnabledCheck;
    private final TextField managerRecipientField;

    private final TextField emailFromField;
    private final TextField smtpHostField;
    private final Spinner<Integer> smtpPortSpinner;
    private final TextField smtpUserField;
    private final PasswordField smtpPasswordField;
    private final ComboBox<SmtpSecurity> smtpSecurityBox;

    private final CheckBox supplierEmailEnabledCheck;
    private final CheckBox supplierSendOnDueDateCheck;

    private final TextField managerSubjectField;
    private final TextArea managerBodyArea;
    private final TextField supplierSubjectField;
    private final TextArea supplierBodyArea;

    private final Button managerTestButton = new Button("Tester l'email gestionnaire");
    private final Button supplierTestButton = new Button("Tester l'email prestataire");
    private final Button desktopPreviewButton = new Button("Tester la notification bureau");

    private final Label summaryLabel = new Label();
    private final Label managerPreviewTitle = new Label();
    private final Label managerPreviewBody = new Label();
    private final Label supplierPreviewTitle = new Label();
    private final Label supplierPreviewBody = new Label();
    private final TextArea historyArea = new TextArea();

    public NotificationSettingsDialog(Window owner, NotificationSettings baseSettings) {
        NotificationSettings settings = baseSettings == null ? NotificationSettings.defaults() : baseSettings.normalized();
        if (owner != null) {
            initOwner(owner);
        }

        setTitle("Centre de relances");
        ButtonType cancelType = ButtonType.CANCEL;
        ButtonType saveType = new ButtonType("Enregistrer", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(cancelType, saveType);
        getDialogPane().setPrefSize(860, 780);
        getDialogPane().setMinWidth(760);
        getDialogPane().getStyleClass().add("notification-settings-dialog");

        leadDaysSpinner = integerSpinner(1, 60, settings.leadDays(), 1);
        reminderHourSpinner = integerSpinner(0, 23, settings.reminderHour(), 1);
        reminderMinuteSpinner = integerSpinner(0, 55, settings.reminderMinute(), 5);
        repeatHoursSpinner = integerSpinner(0, 72, settings.repeatEveryHours(), 1);
        snoozeMinutesSpinner = integerSpinner(5, 720, settings.snoozeMinutes(), 5);

        highlightOverdueCheck = new CheckBox("Accentuer visuellement les factures en retard");
        highlightOverdueCheck.setSelected(settings.highlightOverdue());

        desktopPopupCheck = new CheckBox("Afficher une notification système");
        desktopPopupCheck.setSelected(settings.desktopPopup());

        managerEmailEnabledCheck = new CheckBox("Envoyer un email au gestionnaire");
        managerEmailEnabledCheck.setSelected(settings.emailEnabled());

        managerRecipientField = new TextField(settings.emailRecipient());
        managerRecipientField.setPromptText("gestionnaire@exemple.com");

        emailFromField = new TextField(settings.emailFrom());
        emailFromField.setPromptText("expediteur@exemple.com");

        smtpHostField = new TextField(settings.smtpHost());
        smtpHostField.setPromptText("smtp.exemple.com");

        smtpPortSpinner = integerSpinner(1, 65535, settings.smtpPort(), 1);
        smtpPortSpinner.setEditable(true);

        smtpUserField = new TextField(settings.smtpUsername());
        smtpUserField.setPromptText("identifiant SMTP");

        smtpPasswordField = new PasswordField();
        smtpPasswordField.setText(settings.smtpPassword());
        smtpPasswordField.setPromptText("mot de passe SMTP");

        smtpSecurityBox = new ComboBox<>();
        smtpSecurityBox.getItems().setAll(SmtpSecurity.values());
        smtpSecurityBox.setValue(settings.smtpSecurity() == null ? SmtpSecurity.STARTTLS : settings.smtpSecurity());
        smtpSecurityBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(SmtpSecurity value) {
                return value == null ? "" : value.label();
            }

            @Override
            public SmtpSecurity fromString(String value) {
                return SmtpSecurity.from(value);
            }
        });
        smtpSecurityBox.setMaxWidth(Double.MAX_VALUE);

        supplierEmailEnabledCheck = new CheckBox("Envoyer des emails aux prestataires");
        supplierEmailEnabledCheck.setSelected(settings.supplierEmailEnabled());

        supplierSendOnDueDateCheck = new CheckBox("Envoyer aussi un rappel le jour de l'échéance");
        supplierSendOnDueDateCheck.setSelected(settings.supplierSendOnDueDate());

        managerSubjectField = new TextField(settings.subjectTemplate());
        managerSubjectField.setPromptText("Alerte échéance - {{prestataire}} - {{facture}}");

        managerBodyArea = new TextArea(settings.bodyTemplate());
        managerBodyArea.setWrapText(true);
        managerBodyArea.setPrefRowCount(6);

        supplierSubjectField = new TextField(settings.supplierSubjectTemplate());
        supplierSubjectField.setPromptText("Rappel de paiement - {{facture}} - échéance {{echeance}}");

        supplierBodyArea = new TextArea(settings.supplierBodyTemplate());
        supplierBodyArea.setWrapText(true);
        supplierBodyArea.setPrefRowCount(7);

        summaryLabel.getStyleClass().add("dialog-subtitle");
        summaryLabel.setWrapText(true);

        managerPreviewTitle.getStyleClass().add("notification-preview-title");
        managerPreviewTitle.setWrapText(true);
        managerPreviewBody.getStyleClass().add("notification-preview-body");
        managerPreviewBody.setWrapText(true);
        supplierPreviewTitle.getStyleClass().add("notification-preview-title");
        supplierPreviewTitle.setWrapText(true);
        supplierPreviewBody.getStyleClass().add("notification-preview-body");
        supplierPreviewBody.setWrapText(true);

        historyArea.setEditable(false);
        historyArea.setWrapText(true);
        historyArea.setPrefRowCount(8);
        historyArea.setFocusTraversable(false);

        managerTestButton.getStyleClass().add("accent");
        supplierTestButton.getStyleClass().add("accent");
        desktopPreviewButton.getStyleClass().add("outline");

        managerTestButton.setMaxWidth(Double.MAX_VALUE);
        supplierTestButton.setMaxWidth(Double.MAX_VALUE);
        desktopPreviewButton.setMaxWidth(Double.MAX_VALUE);

        managerTestButton.setOnAction(event -> {
            String error = validateSettings();
            if (error != null) {
                Dialogs.error(ownerWindow(), error);
                return;
            }
            AppServices.notificationServiceOptional().ifPresent(service -> service.sendEmailPreview(draftSettings()));
        });

        supplierTestButton.setOnAction(event -> {
            String error = validateSettings();
            if (error != null) {
                Dialogs.error(ownerWindow(), error);
                return;
            }
            AppServices.notificationServiceOptional().ifPresent(service -> service.sendSupplierEmailPreview(draftSettings()));
        });

        desktopPreviewButton.setOnAction(event ->
                AppServices.notificationServiceOptional().ifPresent(service -> service.sendPreview(draftSettings())));

        VBox root = new VBox(16,
                buildHeader(),
                buildPlanningSection(),
                buildSmtpSection(),
                buildManagerSection(),
                buildSupplierSection(),
                buildHistorySection(),
                buildPlaceholdersSection()
        );
        root.setPadding(new Insets(16));
        root.setFillWidth(true);

        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        getDialogPane().setContent(scroll);

        Button saveButton = (Button) getDialogPane().lookupButton(saveType);
        saveButton.addEventFilter(ActionEvent.ACTION, event -> {
            String error = validateSettings();
            if (error != null) {
                Dialogs.error(ownerWindow(), error);
                event.consume();
            }
        });

        ChangeListener<Object> updater = (obs, oldValue, newValue) -> {
            updateSummary();
            updateUiState();
            updatePreviews();
        };

        leadDaysSpinner.valueProperty().addListener(updater);
        reminderHourSpinner.valueProperty().addListener(updater);
        reminderMinuteSpinner.valueProperty().addListener(updater);
        repeatHoursSpinner.valueProperty().addListener(updater);
        snoozeMinutesSpinner.valueProperty().addListener(updater);
        highlightOverdueCheck.selectedProperty().addListener(updater);
        desktopPopupCheck.selectedProperty().addListener(updater);
        managerEmailEnabledCheck.selectedProperty().addListener(updater);
        managerRecipientField.textProperty().addListener(updater);
        emailFromField.textProperty().addListener(updater);
        smtpHostField.textProperty().addListener(updater);
        smtpPortSpinner.valueProperty().addListener(updater);
        smtpUserField.textProperty().addListener(updater);
        smtpPasswordField.textProperty().addListener(updater);
        smtpSecurityBox.valueProperty().addListener(updater);
        supplierEmailEnabledCheck.selectedProperty().addListener(updater);
        supplierSendOnDueDateCheck.selectedProperty().addListener(updater);
        managerSubjectField.textProperty().addListener(updater);
        managerBodyArea.textProperty().addListener(updater);
        supplierSubjectField.textProperty().addListener(updater);
        supplierBodyArea.textProperty().addListener(updater);

        updateSummary();
        updateUiState();
        updatePreviews();
        refreshHistory();

        setResultConverter(buttonType -> buttonType == saveType ? draftSettings().normalized() : null);
    }

    private Node buildHeader() {
        Label title = new Label("Relances automatiques");
        title.getStyleClass().add("dialog-title");

        Label subtitle = new Label("Configure les alertes bureau, les emails internes et les relances envoyées aux prestataires.");
        subtitle.getStyleClass().add("form-hint");
        subtitle.setWrapText(true);

        VBox header = new VBox(6, title, subtitle, summaryLabel);
        header.setFillWidth(true);
        return header;
    }

    private Node buildPlanningSection() {
        GridPane grid = grid();
        int row = 0;
        grid.add(label("Préavis (jours)"), 0, row);
        grid.add(leadDaysSpinner, 1, row++);
        grid.add(label("Heure de lancement"), 0, row);
        grid.add(timeBox(reminderHourSpinner, reminderMinuteSpinner), 1, row++);
        grid.add(label("Relance auto (heures)"), 0, row);
        grid.add(repeatHoursSpinner, 1, row++);
        grid.add(label("Report (minutes)"), 0, row);
        grid.add(snoozeMinutesSpinner, 1, row++);

        VBox box = section("Planification", grid);
        box.getChildren().addAll(
                highlightOverdueCheck,
                desktopPopupCheck,
                desktopPreviewButton
        );
        return box;
    }

    private Node buildSmtpSection() {
        GridPane grid = grid();
        int row = 0;
        grid.add(label("Expéditeur"), 0, row);
        grid.add(emailFromField, 1, row++);
        grid.add(label("Hôte SMTP"), 0, row);
        grid.add(smtpHostField, 1, row++);
        grid.add(label("Port"), 0, row);
        grid.add(smtpPortSpinner, 1, row++);
        grid.add(label("Sécurité"), 0, row);
        grid.add(smtpSecurityBox, 1, row++);
        grid.add(label("Utilisateur"), 0, row);
        grid.add(smtpUserField, 1, row++);
        grid.add(label("Mot de passe"), 0, row);
        grid.add(smtpPasswordField, 1, row);

        Label hint = hint("Ces réglages sont partagés par les emails du gestionnaire et ceux envoyés aux prestataires.");
        VBox box = section("Transport SMTP", grid, hint);
        return box;
    }

    private Node buildManagerSection() {
        GridPane grid = grid();
        int row = 0;
        grid.add(label("Destinataire"), 0, row);
        grid.add(managerRecipientField, 1, row++);
        grid.add(label("Objet"), 0, row);
        grid.add(managerSubjectField, 1, row++);
        grid.add(label("Corps"), 0, row);
        grid.add(managerBodyArea, 1, row);

        VBox preview = previewCard(managerPreviewTitle, managerPreviewBody);
        VBox box = section("Emails gestionnaire", managerEmailEnabledCheck, grid, managerTestButton, preview);
        return box;
    }

    private Node buildSupplierSection() {
        GridPane grid = grid();
        int row = 0;
        grid.add(label("Objet"), 0, row);
        grid.add(supplierSubjectField, 1, row++);
        grid.add(label("Corps"), 0, row);
        grid.add(supplierBodyArea, 1, row);

        VBox preview = previewCard(supplierPreviewTitle, supplierPreviewBody);
        VBox box = section("Emails prestataires", supplierEmailEnabledCheck, supplierSendOnDueDateCheck, grid, supplierTestButton, preview);
        return box;
    }

    private Node buildHistorySection() {
        Label hint = hint("Historique des derniers emails générés par le moteur automatique. Les erreurs SMTP et les statuts d'envoi apparaissent ici.");
        VBox box = section("Historique des relances", hint, historyArea);
        return box;
    }

    private Node buildPlaceholdersSection() {
        Label title = new Label("Variables disponibles");
        title.getStyleClass().add("dialog-section-title");

        FlowPane tags = new FlowPane(8, 8);
        String[] placeholders = {
                "{{prestataire}}",
                "{{facture}}",
                "{{echeance}}",
                "{{montant}}",
                "{{delai}}",
                "{{delai_jours}}",
                "{{statut}}"
        };
        for (String placeholder : placeholders) {
            Label pill = new Label(placeholder);
            pill.getStyleClass().add("placeholder-pill");
            tags.getChildren().add(pill);
        }

        Label info = hint("Les modèles sont partagés partout dans le moteur de relance. Le rendu ci-dessus se met à jour automatiquement.");
        VBox box = new VBox(8, title, tags, info);
        box.getStyleClass().add("dialog-section");
        return box;
    }

    private VBox previewCard(Label title, Label body) {
        VBox preview = new VBox(6, title, new Separator(), body);
        preview.getStyleClass().add("notification-preview-card");
        preview.setPadding(new Insets(10));
        preview.setFillWidth(true);
        return preview;
    }

    private GridPane grid() {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        return grid;
    }

    private VBox section(String title, Node... content) {
        Label header = new Label(title);
        header.getStyleClass().add("dialog-section-title");
        VBox box = new VBox(10, header);
        box.getStyleClass().add("dialog-section");
        box.getChildren().addAll(content);
        box.setFillWidth(true);
        for (Node node : content) {
            if (node instanceof TextArea area) {
                VBox.setVgrow(area, Priority.NEVER);
            } else {
                VBox.setVgrow(node, Priority.NEVER);
            }
        }
        return box;
    }

    private Label label(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("form-label");
        return label;
    }

    private Label hint(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("form-hint");
        label.setWrapText(true);
        return label;
    }

    private Node timeBox(Spinner<Integer> hour, Spinner<Integer> minute) {
        HBox box = new HBox(6, hour, new Label(":"), minute);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private Spinner<Integer> integerSpinner(int min, int max, int value, int step) {
        Spinner<Integer> spinner = new Spinner<>();
        SpinnerValueFactory.IntegerSpinnerValueFactory factory =
                new SpinnerValueFactory.IntegerSpinnerValueFactory(min, max, value);
        factory.setAmountToStepBy(step);
        spinner.setValueFactory(factory);
        spinner.setEditable(false);
        spinner.setPrefWidth(110);
        return spinner;
    }

    private NotificationSettings draftSettings() {
        SmtpSecurity security = smtpSecurityBox.getValue() == null ? SmtpSecurity.STARTTLS : smtpSecurityBox.getValue();
        return new NotificationSettings(
                leadDaysSpinner.getValue(),
                reminderHourSpinner.getValue(),
                reminderMinuteSpinner.getValue(),
                repeatHoursSpinner.getValue(),
                highlightOverdueCheck.isSelected(),
                desktopPopupCheck.isSelected(),
                snoozeMinutesSpinner.getValue(),
                managerEmailEnabledCheck.isSelected(),
                managerRecipientField.getText(),
                emailFromField.getText(),
                smtpHostField.getText(),
                smtpPortSpinner.getValue(),
                smtpUserField.getText(),
                smtpPasswordField.getText(),
                security,
                managerSubjectField.getText(),
                managerBodyArea.getText(),
                supplierEmailEnabledCheck.isSelected(),
                supplierSendOnDueDateCheck.isSelected(),
                supplierSubjectField.getText(),
                supplierBodyArea.getText()
        );
    }

    private void updateSummary() {
        summaryLabel.setText(draftSettings().normalized().summary(Locale.FRENCH));
    }

    private void updateUiState() {
        boolean anyEmailFlow = managerEmailEnabledCheck.isSelected() || supplierEmailEnabledCheck.isSelected();
        managerRecipientField.setDisable(!managerEmailEnabledCheck.isSelected());
        managerSubjectField.setDisable(!managerEmailEnabledCheck.isSelected());
        managerBodyArea.setDisable(!managerEmailEnabledCheck.isSelected());

        supplierSendOnDueDateCheck.setDisable(!supplierEmailEnabledCheck.isSelected());
        supplierSubjectField.setDisable(!supplierEmailEnabledCheck.isSelected());
        supplierBodyArea.setDisable(!supplierEmailEnabledCheck.isSelected());

        emailFromField.setDisable(!anyEmailFlow);
        smtpHostField.setDisable(!anyEmailFlow);
        smtpPortSpinner.setDisable(!anyEmailFlow);
        smtpUserField.setDisable(!anyEmailFlow);
        smtpPasswordField.setDisable(!anyEmailFlow);
        smtpSecurityBox.setDisable(!anyEmailFlow);

        boolean serviceAvailable = AppServices.hasNotificationService();
        managerTestButton.setDisable(!serviceAvailable || !draftSettings().normalized().emailReady());
        supplierTestButton.setDisable(!serviceAvailable || !draftSettings().normalized().supplierEmailReady());
        desktopPreviewButton.setDisable(!serviceAvailable || !desktopPopupCheck.isSelected());
    }

    private void updatePreviews() {
        NotificationSettings draft = draftSettings().normalized();
        NotificationTemplateEngine.Context base = NotificationTemplateEngine.sampleContext();
        NotificationTemplateEngine.Context context = new NotificationTemplateEngine.Context(
                base.prestataire(),
                base.facture(),
                base.dueDate(),
                base.montant(),
                base.relativeDelay(),
                base.deltaDays(),
                base.overdue()
        );

        String managerSubject = NotificationTemplateEngine.render(draft.subjectTemplate(), context);
        String managerBody = NotificationTemplateEngine.render(draft.bodyTemplate(), context);
        String supplierSubject = NotificationTemplateEngine.render(draft.supplierSubjectTemplate(), context);
        String supplierBody = NotificationTemplateEngine.render(draft.supplierBodyTemplate(), context);

        managerPreviewTitle.setText(managerSubject.isBlank() ? "Alerte échéance - Studio Atlas - Facture maintenance" : managerSubject);
        managerPreviewBody.setText(managerBody.isBlank()
                ? "Le prestataire Studio Atlas a une facture qui arrive dans 3 jours."
                : managerBody);
        supplierPreviewTitle.setText(supplierSubject.isBlank() ? "Rappel de paiement - Facture maintenance" : supplierSubject);
        supplierPreviewBody.setText(supplierBody.isBlank()
                ? "Bonjour Studio Atlas, nous vous rappelons que votre facture arrive à échéance."
                : supplierBody);
    }

    private void refreshHistory() {
        List<Rappel> history = AppServices.notificationServiceOptional()
                .map(service -> service.recentReminders(20))
                .orElseGet(List::of);
        if (history.isEmpty()) {
            historyArea.setText("Aucun rappel enregistré pour le moment.");
            return;
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        StringBuilder sb = new StringBuilder();
        for (Rappel rappel : history) {
            String when = rappel.sentAt() != null ? rappel.sentAt().format(formatter) : rappel.dateEnvoi().format(formatter);
            sb.append('[')
                    .append(when)
                    .append("] ")
                    .append(rappel.statut())
                    .append(" · ")
                    .append(rappel.type())
                    .append(" · ")
                    .append(rappel.dest());
            if (rappel.lastError() != null && !rappel.lastError().isBlank()) {
                sb.append(" · erreur: ").append(rappel.lastError());
            }
            sb.append('\n');
        }
        historyArea.setText(sb.toString().stripTrailing());
    }

    private String validateSettings() {
        NotificationSettings draft = draftSettings().normalized();
        if (!draft.hasAnyEmailFlow()) {
            return null;
        }
        if (draft.smtpHost() == null || draft.smtpHost().isBlank()) {
            return "Hôte SMTP obligatoire.";
        }
        if (draft.smtpPort() < 1 || draft.smtpPort() > 65535) {
            return "Port SMTP invalide.";
        }
        if (draft.emailEnabled() && (draft.emailRecipient() == null || draft.emailRecipient().isBlank())) {
            return "Adresse email du gestionnaire obligatoire.";
        }
        return null;
    }

    private Window ownerWindow() {
        return getDialogPane().getScene() == null ? null : getDialogPane().getScene().getWindow();
    }
}

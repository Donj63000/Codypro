package org.example.gui;

import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.example.AppServices;
import org.example.model.NotificationSettings;
import org.example.util.NotificationTemplateEngine;

import java.util.Locale;

public final class NotificationSettingsDialog extends Dialog<NotificationSettings> {

    private final Spinner<Integer> leadDaysSpinner;
    private final Spinner<Integer> reminderHourSpinner;
    private final Spinner<Integer> reminderMinuteSpinner;
    private final Spinner<Integer> repeatHoursSpinner;
    private final Spinner<Integer> snoozeMinutesSpinner;
    private final CheckBox highlightOverdueCheck;
    private final CheckBox desktopPopupCheck;
    private final TextField subjectField;
    private final TextArea bodyArea;
    private final Label summaryLabel = new Label();
    private final Label previewTitle = new Label();
    private final Label previewBody = new Label();

    public NotificationSettingsDialog(Window owner, NotificationSettings baseSettings) {
        NotificationSettings settings = baseSettings == null ? NotificationSettings.defaults() : baseSettings;
        if (owner != null) {
            initOwner(owner);
        }
        setTitle("Configurer les notifications");

        ButtonType cancelType = ButtonType.CANCEL;
        ButtonType saveType = new ButtonType("Enregistrer", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(cancelType, saveType);
        getDialogPane().setPrefSize(720, 640);
        getDialogPane().setMinWidth(680);
        getDialogPane().setMinHeight(600);
        getDialogPane().setPadding(new Insets(16, 18, 16, 18));
        getDialogPane().getStyleClass().add("notification-settings-dialog");

        Label header = new Label("Notifications personnalisées");
        header.getStyleClass().add("dialog-title");
        header.setWrapText(true);

        summaryLabel.getStyleClass().add("dialog-subtitle");
        summaryLabel.setWrapText(true);

        leadDaysSpinner = integerSpinner(1, 60, settings.leadDays(), 1);
        reminderHourSpinner = integerSpinner(0, 23, settings.reminderHour(), 1);
        reminderMinuteSpinner = integerSpinner(0, 55, settings.reminderMinute(), 5);
        repeatHoursSpinner = integerSpinner(0, 72, settings.repeatEveryHours(), 1);
        snoozeMinutesSpinner = integerSpinner(5, 720, settings.snoozeMinutes(), 5);
        leadDaysSpinner.setTooltip(new Tooltip("Nombre de jours avant l'échéance pour déclencher le premier rappel."));
        reminderHourSpinner.setTooltip(new Tooltip("Heure d'envoi du rappel initial (heure locale)."));
        reminderMinuteSpinner.setTooltip(new Tooltip("Minutes précises d'envoi pour le rappel initial."));
        repeatHoursSpinner.setTooltip(new Tooltip("Cadence de répétition automatique après le premier rappel."));
        snoozeMinutesSpinner.setTooltip(new Tooltip("Durée du report lorsqu'un rappel est remis à plus tard."));

        highlightOverdueCheck = new CheckBox("Accentuer visuellement les factures en retard");
        highlightOverdueCheck.setSelected(settings.highlightOverdue());
        highlightOverdueCheck.setTooltip(new Tooltip("Ajoute un marquage de couleur sur les factures dont l'échéance est dépassée."));

        desktopPopupCheck = new CheckBox("Afficher des notifications système (Windows/macOS/Linux)");
        desktopPopupCheck.setSelected(settings.desktopPopup());
        desktopPopupCheck.setTooltip(new Tooltip("Affiche une alerte native même si l'application est masquée."));

        subjectField = new TextField(settings.subjectTemplate());
        subjectField.setPrefColumnCount(36);
        subjectField.setPromptText("Facture {{facture}} – échéance le {{echeance}}");

        bodyArea = new TextArea(settings.bodyTemplate());
        bodyArea.setWrapText(true);
        bodyArea.setPrefRowCount(5);
        bodyArea.setPromptText("Bonjour {{prestataire}}, votre facture {{facture}} arrive à échéance le {{echeance}}...");

        GridPane schedulingGrid = new GridPane();
        schedulingGrid.setHgap(12);
        schedulingGrid.setVgap(12);
        ColumnConstraints labelCol = new ColumnConstraints();
        labelCol.setPercentWidth(56);
        ColumnConstraints inputCol = new ColumnConstraints();
        inputCol.setPercentWidth(44);
        inputCol.setHgrow(Priority.ALWAYS);
        schedulingGrid.getColumnConstraints().setAll(labelCol, inputCol);
        schedulingGrid.add(label("Préavis (jours)"), 0, 0);
        schedulingGrid.add(leadDaysSpinner, 1, 0);
        schedulingGrid.add(label("Heure d'alerte"), 0, 1);
        schedulingGrid.add(timeBox(reminderHourSpinner, reminderMinuteSpinner), 1, 1);
        schedulingGrid.add(label("Rappel auto (heures)"), 0, 2);
        schedulingGrid.add(repeatHoursSpinner, 1, 2);
        schedulingGrid.add(label("Report (minutes)"), 0, 3);
        schedulingGrid.add(snoozeMinutesSpinner, 1, 3);
        schedulingGrid.getStyleClass().add("form-grid");

        VBox optionsBox = new VBox(8, highlightOverdueCheck, desktopPopupCheck);
        optionsBox.setPadding(new Insets(4, 0, 0, 0));

        previewTitle.getStyleClass().setAll("notification-preview-title");
        previewTitle.setWrapText(true);
        previewBody.getStyleClass().setAll("notification-preview-body");
        previewBody.setWrapText(true);
        VBox previewCard = new VBox(8, previewTitle, previewBody);
        previewCard.getStyleClass().setAll("notification-preview-card");
        previewCard.setFillWidth(true);

        Label schedulingHint = hintLabel("Choisissez précisément quand prévenir les prestataires afin qu'ils aient le temps de régulariser leur situation.");
        VBox schedulingSectionContent = new VBox(14, schedulingGrid, schedulingHint);
        schedulingSectionContent.setFillWidth(true);

        Label highlightHint = hintLabel("Cette option colore fortement les factures échues pour attirer l'attention lors des revues quotidiennes.");
        Label desktopHint = hintLabel("Fait apparaître un toast natif même si l'application est réduite ou masquée.");
        VBox optionsSectionContent = new VBox(10, optionsBox, highlightHint, desktopHint);
        optionsSectionContent.setFillWidth(true);

        FlowPane placeholderTags = new FlowPane(8, 8);
        placeholderTags.getStyleClass().add("placeholder-tags");
        String[] placeholders = {"{{prestataire}}", "{{facture}}", "{{echeance}}", "{{montant}}", "{{delai}}", "{{delai_jours}}", "{{statut}}"};
        for (String token : placeholders) {
            Label pill = new Label(token);
            pill.getStyleClass().add("placeholder-pill");
            placeholderTags.getChildren().add(pill);
        }

        Label placeholdersTitle = new Label("Variables dynamiques");
        placeholdersTitle.getStyleClass().add("dialog-section-subtitle");
        Label templateHint = hintLabel("Combinez les variables avec du texte libre pour conserver un ton cohérent sur toutes les relances.");

        Label subjectLabel = label("Titre du message");
        Label subjectHelper = hintLabel("S'affiche comme objet d'e-mail ou entête de notification.");
        Label bodyLabel = label("Corps du message");
        Label bodyHelper = hintLabel("Décrivez clairement ce que le destinataire doit faire et dans quels délais.");

        Label previewBadge = new Label("APERÇU EN DIRECT");
        previewBadge.getStyleClass().add("dialog-pill");
        Label previewHint = hintLabel("Le rendu ci-dessous s'actualise automatiquement selon les paramètres et les modèles saisis.");
        Button previewButton = new Button("Tester la notification");
        previewButton.getStyleClass().add("accent");
        previewButton.setMaxWidth(Double.MAX_VALUE);
        previewButton.setDisable(!AppServices.hasNotificationService() || !settings.desktopPopup());
        boolean hasTray = AppServices.trayManagerOptional().isPresent();
        if (!AppServices.hasNotificationService()) {
            previewButton.setTooltip(new Tooltip("Notifications indisponibles : aucune alerte ne peut être montrée."));
        } else if (hasTray) {
            previewButton.setTooltip(new Tooltip("Affiche immédiatement une notification système avec les réglages courants."));
        } else {
            previewButton.setTooltip(new Tooltip("Affiche un aperçu dans l'application lorsque les notifications système ne sont pas supportées."));
        }
        previewButton.setOnAction(e -> AppServices.notificationServiceOptional().ifPresent(service -> service.sendPreview(draftSettings())));
        desktopPopupCheck.selectedProperty().addListener((obs, oldVal, newVal) ->
                previewButton.setDisable(!newVal || !AppServices.hasNotificationService()));

        VBox previewBox = new VBox(10, previewBadge, previewHint, previewCard, previewButton);
        previewBox.setFillWidth(true);

        VBox templateFields = new VBox(10,
                subjectLabel,
                subjectField,
                subjectHelper,
                bodyLabel,
                bodyArea,
                bodyHelper
        );
        templateFields.setFillWidth(true);

        VBox templateSectionContent = new VBox(14,
                templateFields,
                placeholdersTitle,
                placeholderTags,
                templateHint,
                previewBox
        );
        templateSectionContent.setFillWidth(true);

        VBox schedulingSection = section("Planification des rappels", "Définissez le timing des notifications automatiques.", schedulingSectionContent);
        VBox optionsSection = section("Visibilité côté application", "Mettez en avant les situations urgentes pour votre équipe.", optionsSectionContent);
        VBox templateSection = section("Contenu du message", "Personnalisez le ton et les variables partagées avec les prestataires.", templateSectionContent);

        VBox leftColumn = new VBox(16, schedulingSection, optionsSection);
        leftColumn.getStyleClass().add("dialog-column");
        VBox rightColumn = new VBox(templateSection);
        rightColumn.getStyleClass().add("dialog-column");
        VBox.setVgrow(templateSection, Priority.ALWAYS);
        leftColumn.setFillWidth(true);
        rightColumn.setFillWidth(true);
        leftColumn.setMaxWidth(Double.MAX_VALUE);
        rightColumn.setMaxWidth(Double.MAX_VALUE);

        FlowPane columns = new FlowPane(Orientation.HORIZONTAL, leftColumn, rightColumn);
        columns.setAlignment(Pos.TOP_LEFT);
        columns.setHgap(18);
        columns.setVgap(18);
        columns.setPrefWrapLength(620);
        columns.getStyleClass().add("dialog-flow");
        columns.setMaxWidth(Double.MAX_VALUE);

        VBox headerBox = new VBox(6, header, summaryLabel);
        headerBox.setFillWidth(true);

        ScrollPane scroller = new ScrollPane(columns);
        scroller.setFitToWidth(true);
        scroller.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroller.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroller.getStyleClass().add("dialog-scroll-pane");

        VBox content = new VBox(20, headerBox, new Separator(), scroller);
        content.setFillWidth(true);
        getDialogPane().setContent(content);

        ChangeListener<Object> summaryUpdater = (obs, o, n) -> updateSummary();
        leadDaysSpinner.valueProperty().addListener(summaryUpdater);
        reminderHourSpinner.valueProperty().addListener(summaryUpdater);
        reminderMinuteSpinner.valueProperty().addListener(summaryUpdater);
        repeatHoursSpinner.valueProperty().addListener(summaryUpdater);
        snoozeMinutesSpinner.valueProperty().addListener(summaryUpdater);
        highlightOverdueCheck.selectedProperty().addListener(summaryUpdater);

        ChangeListener<Object> previewUpdater = (obs, o, n) -> updatePreview();
        subjectField.textProperty().addListener(previewUpdater);
        bodyArea.textProperty().addListener(previewUpdater);
        leadDaysSpinner.valueProperty().addListener(previewUpdater);

        updateSummary();
        updatePreview();

        setResultConverter(bt -> bt == saveType ? draftSettings().normalized() : null);
    }

    private Spinner<Integer> integerSpinner(int min, int max, int value, int step) {
        Spinner<Integer> spinner = new Spinner<>();
        SpinnerValueFactory.IntegerSpinnerValueFactory factory =
                new SpinnerValueFactory.IntegerSpinnerValueFactory(min, max, value);
        factory.setAmountToStepBy(step);
        spinner.setValueFactory(factory);
        spinner.setEditable(false);
        spinner.setPrefWidth(90);
        return spinner;
    }

    private Label label(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("form-label");
        return label;
    }

    private Node timeBox(Spinner<Integer> hour, Spinner<Integer> minute) {
        Label colon = new Label(":");
        colon.getStyleClass().add("form-label");
        HBox box = new HBox(6, hour, colon, minute);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private NotificationSettings draftSettings() {
        return new NotificationSettings(
                leadDaysSpinner.getValue(),
                reminderHourSpinner.getValue(),
                reminderMinuteSpinner.getValue(),
                repeatHoursSpinner.getValue(),
                highlightOverdueCheck.isSelected(),
                desktopPopupCheck.isSelected(),
                snoozeMinutesSpinner.getValue(),
                subjectField.getText(),
                bodyArea.getText()
        );
    }

    private void updateSummary() {
        NotificationSettings current = draftSettings().normalized();
        summaryLabel.setText(current.summary(Locale.FRENCH));
    }

    private void updatePreview() {
        NotificationSettings draft = draftSettings();
        NotificationTemplateEngine.Context base = NotificationTemplateEngine.sampleContext();
        NotificationTemplateEngine.Context ctx = new NotificationTemplateEngine.Context(
                base.prestataire(),
                base.facture(),
                base.dueDate(),
                base.montant(),
                relativeLabel(draft.leadDays()),
                draft.leadDays(),
                false
        );
        String subject = NotificationTemplateEngine.render(draft.subjectTemplate(), ctx);
        String body = NotificationTemplateEngine.render(draft.bodyTemplate(), ctx);
        previewTitle.setText(subject.isBlank() ? "Facture exemple — échéance le 12/05/2024" : subject);
        previewBody.setText(body.isBlank()
                ? "La facture de maintenance pour Studio Atlas arrive dans 3 jours. Statut : À venir."
                : body);
    }

    private static String relativeLabel(int lead) {
        if (lead <= 0) {
            return "aujourd'hui";
        }
        if (lead == 1) {
            return "dans 1 jour";
        }
        return "dans " + lead + " jours";
    }

    private VBox section(String title, String subtitle, Node content) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("dialog-section-title");
        titleLabel.setWrapText(true);

        VBox box = new VBox(12);
        box.getStyleClass().add("dialog-section");
        box.getChildren().add(titleLabel);

        if (subtitle != null && !subtitle.isBlank()) {
            Label subtitleLabel = new Label(subtitle);
            subtitleLabel.getStyleClass().add("dialog-section-subtitle");
            subtitleLabel.setWrapText(true);
            box.getChildren().add(subtitleLabel);
        }

        VBox.setVgrow(content, Priority.ALWAYS);
        box.getChildren().add(content);
        box.setFillWidth(true);
        return box;
    }

    private Label hintLabel(String text) {
        Label hint = new Label(text);
        hint.getStyleClass().add("form-hint");
        hint.setWrapText(true);
        return hint;
    }
}

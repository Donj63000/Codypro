package org.example.gui;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
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
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.Duration;
import javafx.util.StringConverter;
import org.example.AppServices;
import org.example.model.NotificationSettings;
import org.example.model.Rappel;
import org.example.model.SmtpSecurity;
import org.example.notifications.NotificationService;
import org.example.util.NotificationTemplateEngine;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

public final class NotificationSettingsDialog extends Dialog<NotificationSettings> {

    private static final DateTimeFormatter HISTORY_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

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
    private final TextField emailFromNameField;
    private final TextField emailReplyToField;
    private final TextArea emailSignatureArea;

    private final TextField smtpHostField;
    private final Spinner<Integer> smtpPortSpinner;
    private final TextField smtpUserField;
    private final PasswordField smtpPasswordField;
    private final TextField smtpPasswordVisibleField;
    private final CheckBox showPasswordCheck = new CheckBox("Afficher le mot de passe");
    private final ComboBox<SmtpSecurity> smtpSecurityBox;
    private final ComboBox<TransportPreset> transportPresetBox;
    private final Label transportPresetHint = new Label();

    private final CheckBox supplierEmailEnabledCheck;
    private final CheckBox supplierSendOnDueDateCheck;

    private final TextField managerSubjectField;
    private final TextArea managerBodyArea;
    private final TextField supplierSubjectField;
    private final TextArea supplierBodyArea;

    private final Button managerTestButton = new Button("Envoyer un test gestionnaire");
    private final Button supplierTestButton = new Button("Envoyer un aperçu prestataire");
    private final Button desktopPreviewButton = new Button("Tester la notification bureau");
    private final Button runNowButton = new Button("Lancer le moteur maintenant");
    private final Button refreshHistoryButton = new Button("Actualiser l'historique");
    private final Button suggestAliasButton = new Button("Suggérer une adresse dédiée");
    private final Button applyTransportPresetButton = new Button("Appliquer le préréglage");

    private final Label summaryLabel = new Label();
    private final Label automationPill = new Label();
    private final Label smtpPill = new Label();

    private final Label smtpStateValue = new Label();
    private final Label smtpStateCaption = new Label();
    private final Label managerStateValue = new Label();
    private final Label managerStateCaption = new Label();
    private final Label supplierStateValue = new Label();
    private final Label supplierStateCaption = new Label();
    private final Label queueStateValue = new Label();
    private final Label queueStateCaption = new Label();
    private final Label upcomingStateValue = new Label();
    private final Label upcomingStateCaption = new Label();
    private final Label overdueStateValue = new Label();
    private final Label overdueStateCaption = new Label();

    private final Label managerPreviewTitle = new Label();
    private final Label managerPreviewBody = new Label();
    private final Label supplierPreviewTitle = new Label();
    private final Label supplierPreviewBody = new Label();
    private final Label diagnosticsLabel = new Label();
    private final Label runtimeLabel = new Label();

    private final ObservableList<Rappel> historyItems = FXCollections.observableArrayList();
    private final FilteredList<Rappel> filteredHistory = new FilteredList<>(historyItems, item -> true);
    private final ComboBox<HistoryFilter> historyFilterBox = new ComboBox<>();
    private final TableView<Rappel> historyTable = new TableView<>(filteredHistory);
    private final TextArea historyDetailsArea = new TextArea();

    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "reminder-center-worker");
        thread.setDaemon(true);
        return thread;
    });
    private final PauseTransition refreshDebounce = new PauseTransition(Duration.millis(250));

    public NotificationSettingsDialog(Window owner, NotificationSettings baseSettings) {
        NotificationSettings settings = baseSettings == null ? NotificationSettings.defaults() : baseSettings.normalized();
        if (owner != null) {
            initOwner(owner);
        }

        setTitle("Centre emailing & relances");
        ButtonType cancelType = ButtonType.CANCEL;
        ButtonType saveType = new ButtonType("Enregistrer", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(cancelType, saveType);
        getDialogPane().setPrefSize(1120, 860);
        getDialogPane().setMinWidth(900);
        getDialogPane().getStyleClass().addAll("notification-settings-dialog", "reminder-center-dialog");

        leadDaysSpinner = integerSpinner(1, 60, settings.leadDays(), 1);
        reminderHourSpinner = integerSpinner(0, 23, settings.reminderHour(), 1);
        reminderMinuteSpinner = integerSpinner(0, 55, settings.reminderMinute(), 5);
        repeatHoursSpinner = integerSpinner(0, 72, settings.repeatEveryHours(), 1);
        snoozeMinutesSpinner = integerSpinner(5, 720, settings.snoozeMinutes(), 5);

        highlightOverdueCheck = new CheckBox("Mettre en évidence les retards dans le tableau de bord");
        highlightOverdueCheck.setSelected(settings.highlightOverdue());

        desktopPopupCheck = new CheckBox("Afficher aussi une notification de bureau locale");
        desktopPopupCheck.setSelected(settings.desktopPopup());

        managerEmailEnabledCheck = new CheckBox("Activer les e-mails internes au gestionnaire");
        managerEmailEnabledCheck.setSelected(settings.emailEnabled());

        managerRecipientField = new TextField(settings.emailRecipient());
        managerRecipientField.setPromptText("gestionnaire@entreprise.fr");

        emailFromField = new TextField(settings.emailFrom());
        emailFromField.setPromptText("relances@entreprise.fr");
        emailFromNameField = new TextField(settings.emailFromName());
        emailFromNameField.setPromptText("Prestataires Manager");
        emailReplyToField = new TextField(settings.emailReplyTo());
        emailReplyToField.setPromptText("support@entreprise.fr");
        emailSignatureArea = new TextArea(settings.emailSignature());
        emailSignatureArea.setWrapText(true);
        emailSignatureArea.setPrefRowCount(4);

        smtpHostField = new TextField(settings.smtpHost());
        smtpHostField.setPromptText("smtp.entreprise.fr");
        smtpPortSpinner = integerSpinner(1, 65535, settings.smtpPort(), 1);
        smtpPortSpinner.setEditable(true);
        smtpUserField = new TextField(settings.smtpUsername());
        smtpUserField.setPromptText("identifiant SMTP");
        smtpPasswordField = new PasswordField();
        smtpPasswordField.setText(settings.smtpPassword());
        smtpPasswordField.setPromptText("mot de passe SMTP");
        smtpPasswordVisibleField = new TextField(settings.smtpPassword());
        smtpPasswordVisibleField.setPromptText("mot de passe SMTP");
        smtpPasswordVisibleField.textProperty().bindBidirectional(smtpPasswordField.textProperty());
        smtpPasswordVisibleField.visibleProperty().bind(showPasswordCheck.selectedProperty());
        smtpPasswordVisibleField.managedProperty().bind(showPasswordCheck.selectedProperty());
        smtpPasswordField.visibleProperty().bind(showPasswordCheck.selectedProperty().not());
        smtpPasswordField.managedProperty().bind(showPasswordCheck.selectedProperty().not());

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

        transportPresetBox = new ComboBox<>();
        transportPresetBox.getItems().setAll(TransportPreset.values());
        transportPresetBox.setValue(TransportPreset.from(settings));
        transportPresetBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(TransportPreset value) {
                return value == null ? "" : value.label;
            }

            @Override
            public TransportPreset fromString(String value) {
                return TransportPreset.MANUAL;
            }
        });

        supplierEmailEnabledCheck = new CheckBox("Activer les relances automatiques envoyées aux prestataires");
        supplierEmailEnabledCheck.setSelected(settings.supplierEmailEnabled());

        supplierSendOnDueDateCheck = new CheckBox("Envoyer aussi un rappel le jour exact de l'échéance");
        supplierSendOnDueDateCheck.setSelected(settings.supplierSendOnDueDate());

        managerSubjectField = new TextField(settings.subjectTemplate());
        managerSubjectField.setPromptText("Alerte échéance - {{prestataire}} - {{facture}}");
        managerBodyArea = new TextArea(settings.bodyTemplate());
        managerBodyArea.setWrapText(true);
        managerBodyArea.setPrefRowCount(7);

        supplierSubjectField = new TextField(settings.supplierSubjectTemplate());
        supplierSubjectField.setPromptText("Rappel de paiement - {{facture}} - échéance {{echeance}}");
        supplierBodyArea = new TextArea(settings.supplierBodyTemplate());
        supplierBodyArea.setWrapText(true);
        supplierBodyArea.setPrefRowCount(8);

        summaryLabel.getStyleClass().add("dialog-subtitle");
        summaryLabel.setWrapText(true);
        diagnosticsLabel.getStyleClass().add("form-hint");
        diagnosticsLabel.setWrapText(true);
        runtimeLabel.getStyleClass().add("form-hint");
        runtimeLabel.setWrapText(true);
        transportPresetHint.getStyleClass().add("form-hint");
        transportPresetHint.setWrapText(true);

        managerPreviewTitle.getStyleClass().add("notification-preview-title");
        managerPreviewTitle.setWrapText(true);
        managerPreviewBody.getStyleClass().add("notification-preview-body");
        managerPreviewBody.setWrapText(true);
        supplierPreviewTitle.getStyleClass().add("notification-preview-title");
        supplierPreviewTitle.setWrapText(true);
        supplierPreviewBody.getStyleClass().add("notification-preview-body");
        supplierPreviewBody.setWrapText(true);

        configureStatusLabel(smtpStateValue, smtpStateCaption);
        configureStatusLabel(managerStateValue, managerStateCaption);
        configureStatusLabel(supplierStateValue, supplierStateCaption);
        configureStatusLabel(queueStateValue, queueStateCaption);
        configureStatusLabel(upcomingStateValue, upcomingStateCaption);
        configureStatusLabel(overdueStateValue, overdueStateCaption);
        configurePill(automationPill);
        configurePill(smtpPill);

        historyFilterBox.getItems().setAll(HistoryFilter.values());
        historyFilterBox.setValue(HistoryFilter.ALL);
        historyFilterBox.setMaxWidth(Double.MAX_VALUE);

        configureHistoryTable();
        historyDetailsArea.setEditable(false);
        historyDetailsArea.setWrapText(true);
        historyDetailsArea.setPrefRowCount(8);
        historyDetailsArea.getStyleClass().add("history-details");

        managerTestButton.getStyleClass().add("accent");
        supplierTestButton.getStyleClass().add("accent");
        runNowButton.getStyleClass().add("accent");
        desktopPreviewButton.getStyleClass().add("outline");
        refreshHistoryButton.getStyleClass().add("outline");
        suggestAliasButton.getStyleClass().add("outline");
        applyTransportPresetButton.getStyleClass().add("outline");

        managerTestButton.setOnAction(event -> runManagerEmailTest());
        supplierTestButton.setOnAction(event -> runSupplierEmailTest());
        desktopPreviewButton.setOnAction(event -> runDesktopPreview());
        runNowButton.setOnAction(event -> runAutomationNow());
        refreshHistoryButton.setOnAction(event -> refreshHistory());
        suggestAliasButton.setOnAction(event -> suggestDedicatedAddress());
        applyTransportPresetButton.setOnAction(event -> applySelectedTransportPreset());
        transportPresetBox.valueProperty().addListener((obs, oldValue, newValue) -> updateTransportPresetHint());
        historyFilterBox.valueProperty().addListener((obs, oldValue, newValue) -> updateHistoryFilter());
        historyTable.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> updateHistoryDetails(newValue));

        TabPane tabs = new TabPane(
                tab("Vue d'ensemble", buildOverviewTab()),
                tab("Transport e-mail", buildTransportTab()),
                tab("Automatisation", buildAutomationTab()),
                tab("Modèles & aperçus", buildTemplatesTab()),
                tab("Historique", buildHistoryTab())
        );
        tabs.getStyleClass().add("reminder-tabs");
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        VBox root = new VBox(16, buildHero(), tabs);
        root.setPadding(new Insets(18));
        root.setFillWidth(true);

        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("dialog-scroll-pane");
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
            refreshDebounce.playFromStart();
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
        emailFromNameField.textProperty().addListener(updater);
        emailReplyToField.textProperty().addListener(updater);
        emailSignatureArea.textProperty().addListener(updater);
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

        refreshDebounce.setOnFinished(event -> refreshRemoteState());

        updateTransportPresetHint();
        updateSummary();
        updateUiState();
        updatePreviews();
        refreshRemoteState();
        refreshHistory();

        setOnHidden(event -> worker.shutdownNow());
        setResultConverter(buttonType -> buttonType == saveType ? draftSettings().normalized() : null);
    }

    private Node buildHero() {
        Label title = new Label("Centre emailing & relances");
        title.getStyleClass().add("dialog-title");

        Label subtitle = new Label("Configure l'expédition des e-mails, la planification automatique et les modèles utilisés pour le gestionnaire et les prestataires.");
        subtitle.getStyleClass().add("form-hint");
        subtitle.setWrapText(true);

        HBox pillRow = new HBox(8, automationPill, smtpPill);
        pillRow.setAlignment(Pos.CENTER_LEFT);

        VBox hero = new VBox(8, title, subtitle, pillRow, summaryLabel, runtimeLabel);
        hero.getStyleClass().add("reminder-hero");
        return hero;
    }

    private Node buildOverviewTab() {
        FlowPane cards = new FlowPane(12, 12);
        cards.getChildren().addAll(
                statusCard("Transport SMTP", smtpStateValue, smtpStateCaption),
                statusCard("Emails gestionnaire", managerStateValue, managerStateCaption),
                statusCard("Emails prestataires", supplierStateValue, supplierStateCaption),
                statusCard("File d'envoi", queueStateValue, queueStateCaption),
                statusCard("Échéances proches", upcomingStateValue, upcomingStateCaption),
                statusCard("Retards", overdueStateValue, overdueStateCaption)
        );
        cards.getStyleClass().add("status-cards");

        FlowPane actions = new FlowPane(10, 10);
        actions.getChildren().addAll(managerTestButton, supplierTestButton, desktopPreviewButton, runNowButton, refreshHistoryButton);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox help = section(
                "Comment le module fonctionne",
                "Le logiciel pilote une boîte e-mail existante via SMTP. Il ne crée pas la boîte automatiquement, mais il peut suggérer une adresse dédiée et automatiser les relances tant que l'application reste ouverte ou réduite dans le tray.",
                diagnosticsLabel
        );

        VBox box = new VBox(14, cards, actions, help);
        box.setFillWidth(true);
        return scrollable(box);
    }

    private Node buildTransportTab() {
        VBox identity = section(
                "Identité d'envoi",
                "Définissez l'adresse utilisée par le logiciel, le nom affiché à l'expéditeur et une signature automatique ajoutée à tous les e-mails.",
                formGrid(
                        row("Nom affiché", emailFromNameField),
                        row("Adresse d'expédition", emailFromField),
                        row("Adresse de réponse", emailReplyToField),
                        row("Signature automatique", emailSignatureArea)
                ),
                suggestAliasButton,
                hint("Le logiciel peut suggérer une adresse dédiée comme relances@votredomaine.fr. La boîte doit exister chez votre hébergeur ou votre fournisseur e-mail.")
        );

        StackPane passwordStack = new StackPane(smtpPasswordField, smtpPasswordVisibleField);
        VBox transport = section(
                "Connexion SMTP",
                "Renseignez les paramètres de votre serveur d'envoi. Les préréglages ci-dessous appliquent des profils techniques génériques pour gagner du temps.",
                formGrid(
                        row("Préréglage", new HBox(8, transportPresetBox, applyTransportPresetButton)),
                        row("Hôte SMTP", smtpHostField),
                        row("Port", smtpPortSpinner),
                        row("Sécurité", smtpSecurityBox),
                        row("Utilisateur", smtpUserField),
                        row("Mot de passe", new VBox(6, passwordStack, showPasswordCheck))
                ),
                transportPresetHint
        );

        VBox notes = section(
                "Bonnes pratiques",
                "Pour des relances réellement fiables, créez une boîte dédiée aux relances, utilisez une signature claire et vérifiez que l'adresse d'expédition est acceptée par votre serveur SMTP.",
                hint("Si votre fournisseur demande un mot de passe d'application ou un relais SMTP spécifique, renseignez-le ici."),
                hint("L'adresse de réponse peut être différente de l'adresse d'expédition si votre organisation veut centraliser les réponses ailleurs.")
        );

        VBox box = new VBox(14, identity, transport, notes);
        box.setFillWidth(true);
        return scrollable(box);
    }

    private Node buildAutomationTab() {
        VBox planning = section(
                "Planification générale",
                "Ces réglages pilotent la détection des échéances, l'heure d'exécution et la fréquence de répétition tant qu'une facture n'est pas réglée.",
                formGrid(
                        row("Préavis avant échéance", leadDaysSpinner),
                        row("Heure de déclenchement", timeBox(reminderHourSpinner, reminderMinuteSpinner)),
                        row("Répéter les relances d'impayé", repeatHoursSpinner, hintInline("heures")),
                        row("Snooze du tray", snoozeMinutesSpinner, hintInline("minutes")),
                        row("Dashboard", new VBox(6, highlightOverdueCheck, desktopPopupCheck))
                )
        );

        VBox manager = section(
                "Flux interne · gestionnaire",
                "Le gestionnaire reçoit un e-mail automatique quand une facture approche, arrive à échéance ou reste impayée.",
                managerEmailEnabledCheck,
                formGrid(row("Boîte du gestionnaire", managerRecipientField)),
                hint("Vous pouvez laisser le flux désactivé pendant la configuration puis utiliser le bouton de test depuis l'onglet Vue d'ensemble.")
        );

        VBox supplier = section(
                "Flux externe · prestataires",
                "Les prestataires reçoivent les relances à partir de l'adresse e-mail enregistrée sur leur fiche.",
                supplierEmailEnabledCheck,
                supplierSendOnDueDateCheck,
                hint("Un prestataire sans adresse e-mail reste visible dans l'état du centre de relances afin que vous puissiez compléter sa fiche.")
        );

        VBox box = new VBox(14, planning, manager, supplier);
        box.setFillWidth(true);
        return scrollable(box);
    }

    private Node buildTemplatesTab() {
        VBox placeholders = section(
                "Variables disponibles",
                "Les modèles utilisent des variables simples qui sont remplacées automatiquement à l'envoi.",
                placeholderFlow()
        );

        VBox managerPreview = previewCard(managerPreviewTitle, managerPreviewBody);
        VBox manager = section(
                "Modèle gestionnaire",
                "Objet et contenu envoyés au gestionnaire. La signature automatique est ajoutée après le corps du message.",
                formGrid(
                        row("Objet", managerSubjectField),
                        row("Corps", managerBodyArea)
                ),
                managerPreview
        );

        VBox supplierPreview = previewCard(supplierPreviewTitle, supplierPreviewBody);
        VBox supplier = section(
                "Modèle prestataire",
                "Objet et contenu envoyés au prestataire. Adaptez le ton et la formule de politesse selon votre activité.",
                formGrid(
                        row("Objet", supplierSubjectField),
                        row("Corps", supplierBodyArea)
                ),
                supplierPreview
        );

        VBox box = new VBox(14, placeholders, manager, supplier);
        box.setFillWidth(true);
        return scrollable(box);
    }

    private Node buildHistoryTab() {
        Button refreshButton = new Button("Actualiser");
        refreshButton.getStyleClass().add("outline");
        refreshButton.disableProperty().bind(refreshHistoryButton.disabledProperty());
        refreshButton.setOnAction(event -> refreshHistory());

        Button runNowHistoryButton = new Button("Lancer le moteur maintenant");
        runNowHistoryButton.getStyleClass().add("accent");
        runNowHistoryButton.disableProperty().bind(runNowButton.disabledProperty());
        runNowHistoryButton.setOnAction(event -> runAutomationNow());

        HBox filterBar = new HBox(10, new Label("Afficher"), historyFilterBox, refreshButton, runNowHistoryButton);
        filterBar.setAlignment(Pos.CENTER_LEFT);

        VBox tableWrapper = new VBox(10, filterBar, historyTable, historyDetailsArea);
        VBox.setVgrow(historyTable, Priority.ALWAYS);
        VBox.setVgrow(historyDetailsArea, Priority.NEVER);

        VBox box = section(
                "Historique des relances",
                "Retrouvez ici les e-mails planifiés, envoyés, en échec ou ignorés. La fiche détaille la cible, le contenu et les erreurs éventuelles.",
                tableWrapper
        );
        VBox.setVgrow(tableWrapper, Priority.ALWAYS);

        VBox root = new VBox(box);
        VBox.setVgrow(box, Priority.ALWAYS);
        root.setFillWidth(true);
        return root;
    }

    private ScrollPane scrollable(Node content) {
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("dialog-scroll-pane");
        return scroll;
    }

    private Tab tab(String title, Node content) {
        Tab tab = new Tab(title, content);
        tab.setClosable(false);
        return tab;
    }

    private VBox section(String title, String subtitle, Node... content) {
        Label header = new Label(title);
        header.getStyleClass().add("dialog-section-title");

        VBox box = new VBox(10, header);
        box.getStyleClass().add("dialog-section");
        box.setFillWidth(true);

        if (subtitle != null && !subtitle.isBlank()) {
            Label sub = new Label(subtitle);
            sub.getStyleClass().add("dialog-section-subtitle");
            sub.setWrapText(true);
            box.getChildren().add(sub);
        }

        box.getChildren().addAll(content);
        return box;
    }

    private GridPane formGrid(Node... rows) {
        GridPane grid = new GridPane();
        grid.getStyleClass().add("form-grid");
        grid.setHgap(14);
        grid.setVgap(14);
        ColumnConstraints left = new ColumnConstraints();
        left.setMinWidth(190);
        left.setPrefWidth(220);
        ColumnConstraints right = new ColumnConstraints();
        right.setHgrow(Priority.ALWAYS);
        right.setFillWidth(true);
        grid.getColumnConstraints().setAll(left, right);

        for (int i = 0; i < rows.length; i++) {
            Node row = rows[i];
            grid.add(row, 0, i, 2, 1);
        }
        return grid;
    }

    private Node row(String labelText, Node main) {
        return row(labelText, main, null);
    }

    private Node row(String labelText, Node main, Node trailing) {
        Label label = new Label(labelText);
        label.getStyleClass().add("form-label");
        VBox wrapper = new VBox(6, label);
        wrapper.setFillWidth(true);

        HBox line = new HBox(10, main);
        line.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(main, Priority.ALWAYS);
        if (main instanceof Region region) {
            region.setMaxWidth(Double.MAX_VALUE);
        }
        if (trailing != null) {
            line.getChildren().add(trailing);
        }
        wrapper.getChildren().add(line);
        return wrapper;
    }

    private Label hint(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("form-hint");
        label.setWrapText(true);
        return label;
    }

    private Label hintInline(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("form-hint");
        return label;
    }

    private Node timeBox(Spinner<Integer> hour, Spinner<Integer> minute) {
        HBox box = new HBox(8, hour, new Label(":"), minute);
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
        spinner.setPrefWidth(120);
        spinner.setMaxWidth(120);
        return spinner;
    }

    private FlowPane placeholderFlow() {
        FlowPane tags = new FlowPane(8, 8);
        tags.getStyleClass().add("placeholder-tags");
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
        return tags;
    }

    private VBox previewCard(Label title, Label body) {
        VBox preview = new VBox(8, title, new Separator(), body);
        preview.getStyleClass().add("notification-preview-card");
        preview.setPadding(new Insets(12));
        preview.setFillWidth(true);
        return preview;
    }

    private VBox statusCard(String title, Label value, Label caption) {
        Label header = new Label(title);
        header.getStyleClass().add("status-card-title");
        VBox box = new VBox(6, header, value, caption);
        box.getStyleClass().add("status-card");
        box.setPrefWidth(220);
        box.setMinWidth(220);
        return box;
    }

    private void configureStatusLabel(Label value, Label caption) {
        value.getStyleClass().add("status-card-value");
        value.setWrapText(true);
        caption.getStyleClass().add("status-card-caption");
        caption.setWrapText(true);
    }

    private void configurePill(Label label) {
        label.getStyleClass().add("status-pill");
    }

    private void configureHistoryTable() {
        TableColumn<Rappel, String> whenCol = new TableColumn<>("Date");
        whenCol.setCellValueFactory(data -> new SimpleStringProperty(formatReminderDate(data.getValue())));
        whenCol.setPrefWidth(140);

        TableColumn<Rappel, String> flowCol = new TableColumn<>("Flux");
        flowCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().flowLabel()));
        flowCol.setPrefWidth(180);

        TableColumn<Rappel, String> recipientCol = new TableColumn<>("Destinataire");
        recipientCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().dest()));
        recipientCol.setPrefWidth(220);

        TableColumn<Rappel, String> statusCol = new TableColumn<>("Statut");
        statusCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().statusLabel()));
        statusCol.setPrefWidth(110);

        TableColumn<Rappel, Number> attemptsCol = new TableColumn<>("Essais");
        attemptsCol.setCellValueFactory(data -> new SimpleIntegerProperty(data.getValue().attemptCount()));
        attemptsCol.setPrefWidth(80);

        TableColumn<Rappel, String> errorCol = new TableColumn<>("Dernière erreur");
        errorCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().lastError()));
        errorCol.setPrefWidth(300);

        historyTable.getColumns().setAll(whenCol, flowCol, recipientCol, statusCol, attemptsCol, errorCol);
        historyTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        historyTable.setPlaceholder(new Label("Aucun rappel enregistré pour le moment."));
        historyTable.setPrefHeight(340);
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
                emailFromNameField.getText(),
                emailReplyToField.getText(),
                emailSignatureArea.getText(),
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
        NotificationSettings draft = draftSettings().normalized();
        summaryLabel.setText(draft.summary(Locale.FRENCH));
        runtimeLabel.setText("Automatisation locale : l'application doit rester ouverte ou réduite dans le tray pour que les relances partent réellement.");
    }

    private void updateUiState() {
        NotificationSettings draft = draftSettings().normalized();
        boolean serviceAvailable = AppServices.hasNotificationService();

        managerTestButton.setDisable(!serviceAvailable || !draft.smtpReady() || !NotificationSettings.looksLikeEmail(draft.emailRecipient()));
        supplierTestButton.setDisable(!serviceAvailable || !draft.smtpReady() || !NotificationSettings.looksLikeEmail(firstNonBlank(draft.emailRecipient(), draft.resolvedSenderAddress())));
        desktopPreviewButton.setDisable(!serviceAvailable);
        runNowButton.setDisable(!serviceAvailable);
        refreshHistoryButton.setDisable(!serviceAvailable);

        String diagnostics = buildDiagnostics(draft);
        diagnosticsLabel.setText(diagnostics);
        setPill(automationPill, draft.hasAnyEmailFlow() || draft.desktopPopup() ? "Automatisation prête" : "Automatisation désactivée",
                draft.hasAnyEmailFlow() || draft.desktopPopup() ? "status-pill-ok" : "status-pill-off");
        setPill(smtpPill, draft.smtpReady() ? "SMTP prêt" : "SMTP à compléter",
                draft.smtpReady() ? "status-pill-ok" : "status-pill-warn");
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
        String managerBody = draft.applySignature(NotificationTemplateEngine.render(draft.bodyTemplate(), context));
        String supplierSubject = NotificationTemplateEngine.render(draft.supplierSubjectTemplate(), context);
        String supplierBody = draft.applySignature(NotificationTemplateEngine.render(draft.supplierBodyTemplate(), context));

        managerPreviewTitle.setText(managerSubject.isBlank() ? "Alerte échéance - Studio Atlas - Maintenance serveur T2" : managerSubject);
        managerPreviewBody.setText(managerBody.isBlank()
                ? "Le prestataire Studio Atlas a une facture qui arrive dans 3 jours."
                : managerBody);
        supplierPreviewTitle.setText(supplierSubject.isBlank() ? "Rappel de paiement - Maintenance serveur T2" : supplierSubject);
        supplierPreviewBody.setText(supplierBody.isBlank()
                ? "Bonjour Studio Atlas, nous vous rappelons que votre facture arrive à échéance."
                : supplierBody);
    }

    private void refreshRemoteState() {
        NotificationSettings draft = draftSettings().normalized();
        renderDraftSnapshot(draft);

        AppServices.notificationServiceOptional().ifPresent(service ->
                CompletableFuture.supplyAsync(() -> service.snapshot(draft), worker)
                        .whenComplete((snapshot, error) -> Platform.runLater(() -> {
                            if (error != null || snapshot == null) {
                                return;
                            }
                            renderSnapshot(snapshot);
                        }))
        );
    }

    private void renderDraftSnapshot(NotificationSettings draft) {
        smtpStateValue.setText(draft.smtpReady() ? "Prêt à envoyer" : "À configurer");
        smtpStateCaption.setText(draft.senderSummary());

        managerStateValue.setText(draft.emailEnabled() ? "Activé" : "Désactivé");
        managerStateCaption.setText(NotificationSettings.looksLikeEmail(draft.emailRecipient())
                ? draft.emailRecipient()
                : "Adresse du gestionnaire à compléter");

        supplierStateValue.setText(draft.supplierEmailEnabled() ? "Activé" : "Désactivé");
        supplierStateCaption.setText("Préavis J-" + draft.leadDays() + (draft.supplierSendOnDueDate() ? " + rappel jour J" : ""));

        queueStateValue.setText("—");
        queueStateCaption.setText("Actualisation en cours");
        upcomingStateValue.setText("—");
        upcomingStateCaption.setText("Calcul des échéances");
        overdueStateValue.setText("—");
        overdueStateCaption.setText("Calcul des retards");
    }

    private void renderSnapshot(NotificationService.ReminderSnapshot snapshot) {
        NotificationSettings draft = draftSettings().normalized();

        smtpStateValue.setText(draft.smtpReady() ? "Prêt à envoyer" : "À configurer");
        smtpStateCaption.setText(snapshot.senderSummary());

        managerStateValue.setText(draft.emailEnabled() ? "Activé" : "Désactivé");
        managerStateCaption.setText(snapshot.managerSummary());

        supplierStateValue.setText(draft.supplierEmailEnabled() ? "Activé" : "Désactivé");
        if (snapshot.missingSupplierEmailCount() <= 0) {
            supplierStateCaption.setText("Toutes les fiches concernées ont une adresse e-mail.");
        } else if (snapshot.missingSupplierEmailCount() == 1) {
            supplierStateCaption.setText("1 fiche prestataire concernée n'a pas d'adresse e-mail.");
        } else {
            supplierStateCaption.setText(snapshot.missingSupplierEmailCount() + " fiches prestataires concernées n'ont pas d'adresse e-mail.");
        }

        queueStateValue.setText(snapshot.pendingCount() + " en attente");
        queueStateCaption.setText(snapshot.failedCount() + " en échec · " + snapshot.sentCount() + " envoyés · " + snapshot.skippedCount() + " ignorés");

        upcomingStateValue.setText(Integer.toString(snapshot.upcomingCount()));
        upcomingStateCaption.setText(snapshot.upcomingCount() <= 1 ? "échéance proche" : "échéances proches");

        overdueStateValue.setText(Integer.toString(snapshot.overdueCount()));
        overdueStateCaption.setText(snapshot.overdueCount() <= 1 ? "retard" : "retards");
    }

    private void refreshHistory() {
        historyItems.clear();
        historyDetailsArea.setText("");
        AppServices.notificationServiceOptional().ifPresent(service ->
                CompletableFuture.supplyAsync(() -> service.recentReminders(200), worker)
                        .whenComplete((history, error) -> Platform.runLater(() -> {
                            if (error != null || history == null) {
                                historyDetailsArea.setText("Impossible de charger l'historique.");
                                return;
                            }
                            historyItems.setAll(history);
                            updateHistoryFilter();
                            if (!historyItems.isEmpty()) {
                                historyTable.getSelectionModel().selectFirst();
                            }
                            refreshRemoteState();
                        }))
        );
    }

    private void updateHistoryFilter() {
        HistoryFilter filter = historyFilterBox.getValue() == null ? HistoryFilter.ALL : historyFilterBox.getValue();
        filteredHistory.setPredicate(filter::matches);
    }

    private void updateHistoryDetails(Rappel rappel) {
        if (rappel == null) {
            historyDetailsArea.setText("");
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Date planifiée : ").append(formatReminderDate(rappel)).append('\n');
        if (rappel.sentAt() != null) {
            sb.append("Date d'envoi : ").append(rappel.sentAt().format(HISTORY_FORMAT)).append('\n');
        }
        sb.append("Flux : ").append(rappel.flowLabel()).append('\n');
        sb.append("Statut : ").append(rappel.statusLabel()).append('\n');
        sb.append("Destinataire : ").append(rappel.dest()).append('\n');
        sb.append("Essais : ").append(rappel.attemptCount()).append('\n');
        if (rappel.lastError() != null && !rappel.lastError().isBlank()) {
            sb.append("Erreur : ").append(rappel.lastError()).append('\n');
        }
        sb.append('\n').append("Objet").append('\n');
        sb.append(rappel.sujet()).append('\n').append('\n');
        sb.append("Contenu").append('\n');
        sb.append(rappel.corps());
        historyDetailsArea.setText(sb.toString().stripTrailing());
    }

    private void runManagerEmailTest() {
        String error = validateSettings();
        if (error != null) {
            Dialogs.error(ownerWindow(), error);
            return;
        }
        runDeliveryAction(
                managerTestButton,
                "Envoi...",
                () -> AppServices.notificationService().testManagerEmail(draftSettings())
        );
    }

    private void runSupplierEmailTest() {
        String error = validateSettings();
        if (error != null) {
            Dialogs.error(ownerWindow(), error);
            return;
        }
        runDeliveryAction(
                supplierTestButton,
                "Envoi...",
                () -> AppServices.notificationService().testSupplierEmail(draftSettings())
        );
    }

    private void runDesktopPreview() {
        runDeliveryAction(
                desktopPreviewButton,
                "Test...",
                () -> AppServices.notificationService().testDesktopNotification(draftSettings())
        );
    }

    private void runDeliveryAction(Button button, String busyText, Supplier<NotificationService.DeliveryCheck> action) {
        if (!AppServices.hasNotificationService()) {
            Dialogs.error(ownerWindow(), "Le moteur de notifications n'est pas disponible.");
            return;
        }
        String previous = button.getText();
        button.setDisable(true);
        button.setText(busyText);
        CompletableFuture.supplyAsync(action, worker)
                .whenComplete((result, error) -> Platform.runLater(() -> {
                    button.setText(previous);
                    updateUiState();
                    if (error != null) {
                        Dialogs.error(ownerWindow(), "Le test n'a pas pu être exécuté.", error);
                    } else if (result != null && result.success()) {
                        Dialogs.info(ownerWindow(), result.title(), result.message());
                    } else if (result != null) {
                        Dialogs.error(ownerWindow(), result.message());
                    }
                    refreshHistory();
                    refreshRemoteState();
                }));
    }

    private void runAutomationNow() {
        if (!AppServices.hasNotificationService()) {
            Dialogs.error(ownerWindow(), "Le moteur de relances n'est pas disponible.");
            return;
        }
        String error = validateSettings();
        if (error != null) {
            Dialogs.error(ownerWindow(), error);
            return;
        }
        AppServices.notificationService().runNow(draftSettings());
        PauseTransition pause = new PauseTransition(Duration.seconds(1));
        pause.setOnFinished(event -> {
            refreshHistory();
            refreshRemoteState();
        });
        pause.playFromStart();
    }

    private void suggestDedicatedAddress() {
        String domain = extractDomain(managerRecipientField.getText());
        if (domain.isBlank()) {
            domain = extractDomain(emailFromField.getText());
        }
        if (domain.isBlank()) {
            domain = extractDomain(smtpUserField.getText());
        }
        if (domain.isBlank()) {
            Dialogs.warning(ownerWindow(), "Domaine manquant", "Renseignez d'abord une adresse existante du type contact@votredomaine.fr pour que le logiciel puisse vous proposer une adresse dédiée.");
            return;
        }

        List<String> candidates = List.of(
                "relances@" + domain,
                "facturation@" + domain,
                "prestataires@" + domain
        );
        ComboBox<String> chooser = new ComboBox<>(FXCollections.observableArrayList(candidates));
        chooser.setMaxWidth(Double.MAX_VALUE);
        chooser.getSelectionModel().selectFirst();

        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Adresse suggérée");
        if (ownerWindow() != null) {
            dialog.initOwner(ownerWindow());
        }
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
        dialog.setHeaderText("Choisissez l'adresse dédiée à utiliser pour les relances");
        dialog.getDialogPane().setContent(new VBox(10,
                new Label("Le logiciel suggère une adresse liée à votre domaine. La boîte doit exister chez votre fournisseur avant de pouvoir envoyer des e-mails."),
                chooser
        ));
        ThemeManager.apply(dialog);
        dialog.setResultConverter(buttonType -> buttonType == ButtonType.OK ? chooser.getValue() : null);
        dialog.showAndWait().ifPresent(emailFromField::setText);
    }

    private void applySelectedTransportPreset() {
        TransportPreset preset = transportPresetBox.getValue();
        if (preset == null) {
            return;
        }
        preset.apply(this);
        updateTransportPresetHint();
        updateUiState();
        refreshRemoteState();
    }

    private void updateTransportPresetHint() {
        TransportPreset preset = transportPresetBox.getValue() == null ? TransportPreset.MANUAL : transportPresetBox.getValue();
        transportPresetHint.setText(preset.description);
    }

    private String validateSettings() {
        List<String> errors = draftSettings().normalized().validationErrors();
        if (errors.isEmpty()) {
            return null;
        }
        return String.join("\n", errors);
    }

    private String buildDiagnostics(NotificationSettings draft) {
        StringBuilder sb = new StringBuilder();
        sb.append("Expéditeur résolu : ").append(draft.senderSummary()).append('\n');
        sb.append("Préavis : J-").append(draft.leadDays()).append(" · heure de déclenchement : ")
                .append(String.format(Locale.FRANCE, "%02d:%02d", draft.reminderHour(), draft.reminderMinute())).append('\n');
        if (draft.repeatEveryHours() <= 0) {
            sb.append("Relances répétées : désactivées");
        } else {
            sb.append("Relances répétées : toutes les ").append(draft.repeatEveryHours()).append(" heures");
        }
        if (draft.smtpHost().isBlank()) {
            sb.append('\n').append("Serveur SMTP : à compléter");
        } else {
            sb.append('\n').append("Serveur SMTP : ").append(draft.smtpHost()).append(':').append(draft.smtpPort())
                    .append(" · ").append(draft.smtpSecurity().label());
        }
        return sb.toString();
    }

    private void setPill(Label label, String text, String stateClass) {
        label.setText(text);
        label.getStyleClass().setAll("status-pill", stateClass);
    }

    private static String formatReminderDate(Rappel rappel) {
        if (rappel == null || rappel.dateEnvoi() == null) {
            return "";
        }
        return rappel.dateEnvoi().format(HISTORY_FORMAT);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private static String extractDomain(String email) {
        String trimmed = email == null ? "" : email.trim();
        int at = trimmed.indexOf('@');
        if (at < 0 || at >= trimmed.length() - 1) {
            return "";
        }
        return trimmed.substring(at + 1).trim();
    }

    private Window ownerWindow() {
        return getDialogPane().getScene() == null ? null : getDialogPane().getScene().getWindow();
    }

    private enum HistoryFilter {
        ALL("Tous"),
        PENDING("En attente"),
        SENT("Envoyés"),
        FAILED("Échecs"),
        SKIPPED("Ignorés");

        private final String label;

        HistoryFilter(String label) {
            this.label = label;
        }

        boolean matches(Rappel rappel) {
            if (rappel == null) {
                return false;
            }
            return switch (this) {
                case ALL -> true;
                case PENDING -> Rappel.STATUS_PENDING.equals(rappel.statut());
                case SENT -> Rappel.STATUS_SENT.equals(rappel.statut());
                case FAILED -> Rappel.STATUS_FAILED.equals(rappel.statut());
                case SKIPPED -> Rappel.STATUS_SKIPPED.equals(rappel.statut());
            };
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private enum TransportPreset {
        MANUAL("Configuration manuelle", null, null, "Saisissez les paramètres exacts fournis par votre hébergeur ou votre service informatique."),
        STARTTLS("SMTP sécurisé · STARTTLS 587", 587, SmtpSecurity.STARTTLS, "Profil générique conseillé pour la plupart des serveurs SMTP modernes."),
        SSL("SMTP chiffré · SSL/TLS 465", 465, SmtpSecurity.SSL, "Profil générique utile quand le chiffrement doit être établi dès l'ouverture de la connexion."),
        RELAY("Relais interne · port 25", 25, SmtpSecurity.NONE, "Profil minimal réservé à un relais local ou à une infrastructure interne de confiance.");

        private final String label;
        private final Integer port;
        private final SmtpSecurity security;
        private final String description;

        TransportPreset(String label, Integer port, SmtpSecurity security, String description) {
            this.label = label;
            this.port = port;
            this.security = security;
            this.description = description;
        }

        void apply(NotificationSettingsDialog dialog) {
            if (port != null) {
                dialog.smtpPortSpinner.getValueFactory().setValue(port);
            }
            if (security != null) {
                dialog.smtpSecurityBox.setValue(security);
            }
        }

        static TransportPreset from(NotificationSettings settings) {
            if (settings == null) {
                return MANUAL;
            }
            if (settings.smtpPort() == 587 && settings.smtpSecurity() == SmtpSecurity.STARTTLS) {
                return STARTTLS;
            }
            if (settings.smtpPort() == 465 && settings.smtpSecurity() == SmtpSecurity.SSL) {
                return SSL;
            }
            if (settings.smtpPort() == 25 && settings.smtpSecurity() == SmtpSecurity.NONE) {
                return RELAY;
            }
            return MANUAL;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}

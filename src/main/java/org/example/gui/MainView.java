package org.example.gui;

import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.example.AppServices;
import org.example.dao.DB;
import org.example.model.Facture;
import org.example.model.NotificationSettings;
import org.example.model.Prestataire;
import org.example.model.ServiceRow;
import org.example.model.ServiceStatus;
import org.example.pdf.PDF;
import org.example.gui.PrestataireFormDialog;
import org.example.gui.ServiceFormDialog;
import org.example.gui.FactureFormDialog;
import org.example.security.AuthService;
import org.example.gui.account.AccountManagerDialog;
import org.example.util.NotificationTemplateEngine;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainView {
    private final Stage stage;
    private final DB dao;
    private final AuthService authService;
    private final AuthService.Session session;
    private final Instant loginStarted;
    private final BorderPane root = new BorderPane();

    // UI elements
    private final TableView<Prestataire> table = new TableView<>();
    private final ObservableList<Prestataire> items = FXCollections.observableArrayList();
    private final TextField tfSearch = new TextField();
    private final Button btnAdd = new Button("Ajouter");
    private final Button btnEdit = new Button("Modifier");
    private final Button btnDelete = new Button("Supprimer");
    private final Button btnFactures = new Button("Factures");
    private final Button btnExport = new Button("Exporter PDF");
    private final Button btnRefresh = new Button("Recharger");
    private final Button btnNotifications = new Button("Configurer notifications");
    private final Button btnAccount = new Button("Configuration du compte");

    private final GridPane fichePane = new GridPane();
    private final TableView<ServiceRow> tvServices = new TableView<>();
    private final TableView<Facture> tvFactures = new TableView<>();
    private final Label serviceTypeValue = new Label();
    private final TextArea serviceNotesArea = new TextArea();
    private final VBox serviceInfoPane = new VBox(6);
    private final HBox serviceStatusBar = new HBox(8);
    private final ToggleGroup detailsToggleGroup = new ToggleGroup();
    private final ToggleButton btnDetailFiche = createDetailButton("Fiche");
    private final ToggleButton btnDetailServices = createDetailButton("Services");
    private final ToggleButton btnDetailFactures = createDetailButton("Factures");
    private final StackPane detailStack = new StackPane();
    private final HBox metricsBar = new HBox(16);
    private final Label metricPrestatairesValue = createMetricValueLabel();
    private final Label metricPrestatairesSubtitle = createMetricSubtitleLabel();
    private final Label metricImpayesValue = createMetricValueLabel();
    private final Label metricImpayesSubtitle = createMetricSubtitleLabel();
    private final Label metricSatisfactionValue = createMetricValueLabel();
    private final Label metricSatisfactionSubtitle = createMetricSubtitleLabel();
    private final VBox alertsPanel = new VBox(8);
    private final Label alertsBadge = new Label("ALERTE IMPAYES");
    private final Label alertsSummary = new Label();
    private final VBox alertsList = new VBox(4);
    private final Label sessionLabel = new Label();
    private final Timeline sessionTimeline = new Timeline();


    // background
    private final ExecutorService exec = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ui-loader");
        t.setDaemon(true);
        return t;
    });
    private final PauseTransition searchDebounce = new PauseTransition(Duration.millis(250));

    private final NumberFormat money = NumberFormat.getCurrencyInstance(Locale.FRANCE);
    private NotificationSettings notificationSettings;

    public MainView(Stage stage, DB dao, AuthService authService, AuthService.Session session, Instant loginStarted) {
        this.stage = stage;
        this.dao = dao;
        this.authService = authService;
        this.session = session;
        this.loginStarted = loginStarted;

        java.util.stream.Stream.of(btnAdd, btnEdit, btnDelete, btnNotifications, btnFactures, btnExport, btnRefresh)
              .forEach(b -> b.setMinWidth(Region.USE_PREF_SIZE));
        btnAccount.setMinWidth(Region.USE_PREF_SIZE);
        btnNotifications.setMinWidth(Region.USE_PREF_SIZE);

        root.getStyleClass().add("app-root");
        serviceInfoPane.getStyleClass().add("service-summary");
        serviceStatusBar.getStyleClass().add("service-status-bar");
        serviceStatusBar.setVisible(false);
        serviceStatusBar.setManaged(false);
        detailStack.getStyleClass().add("details-stack");
        metricsBar.getStyleClass().add("metrics-bar");
        items.addListener((ListChangeListener<Prestataire>) change -> updateMetrics());
        btnAccount.getStyleClass().addAll("outline", "header-action");
        btnAccount.setOnAction(e -> openAccountManager());
        btnNotifications.getStyleClass().addAll("outline", "header-action");
        btnNotifications.setTooltip(new Tooltip("Configurer le préavis et la présentation des alertes."));
        btnNotifications.setOnAction(e -> openNotificationSettings());
        sessionLabel.getStyleClass().add("session-indicator");

        notificationSettings = fetchNotificationSettings();

        // Top: menu + header
        MenuBar menuBar = new MenuBar();
        menuBar.getMenus().add(new Menu("Fichier"));
        menuBar.getStyleClass().add("app-menu");

        ImageView logoView = loadLogoImage();
        VBox header = buildHeader(logoView);
        VBox topContainer = new VBox(menuBar, header);
        topContainer.getStyleClass().add("top-container");
        root.setTop(topContainer);

        // Center: toolbar + table + details
        Node center = buildCenter();
        root.setCenter(center);

        // initial load
        updateMetrics();
        reload("");
        initSessionTracking();
    }

    public Parent getRoot() { return root; }

    public void shutdownExecutor() {
        exec.shutdownNow();
        sessionTimeline.stop();
    }

    private ImageView loadLogoImage() {
        try {
            var url = MainView.class.getResource("/img.png");
            if (url == null) {
                return null;
            }
            Image image = new Image(url.toExternalForm());
            ImageView view = new ImageView(image);
            view.setFitHeight(40);
            view.setPreserveRatio(true);
            view.setSmooth(true);
            return view;
        } catch (Exception ex) {
            return null;
        }
    }

    private VBox buildHeader(ImageView logoView) {
        Label titleLabel = new Label("Gestion des Prestataires");
        titleLabel.getStyleClass().add("app-title");

        Label subtitleLabel = new Label("Suivez services, factures et relances sans quitter votre tableau de bord");
        subtitleLabel.getStyleClass().add("app-subtitle");

        VBox titleGroup = new VBox(4, titleLabel, subtitleLabel);
        titleGroup.getStyleClass().add("title-block");

        HBox sessionBox = new HBox(12, sessionLabel, btnNotifications, btnAccount);
        sessionBox.setAlignment(Pos.CENTER_RIGHT);
        sessionBox.getStyleClass().add("session-box");

        HBox headerLine = new HBox(16);
        headerLine.setAlignment(Pos.CENTER_LEFT);
        headerLine.getStyleClass().add("app-header");
        if (logoView != null) {
            logoView.getStyleClass().add("app-logo");
            HBox.setMargin(logoView, new Insets(0, 12, 0, 0));
            headerLine.getChildren().add(logoView);
        }
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        headerLine.getChildren().addAll(titleGroup, spacer, sessionBox);

        metricsBar.setAlignment(Pos.CENTER_LEFT);
        if (!metricsBar.getStyleClass().contains("metrics-bar")) {
            metricsBar.getStyleClass().add("metrics-bar");
        }
        metricsBar.getChildren().setAll(
                createMetricCard("Prestataires actifs", metricPrestatairesValue, metricPrestatairesSubtitle),
                createMetricCard("Total impayés", metricImpayesValue, metricImpayesSubtitle),
                createMetricCard("Note moyenne", metricSatisfactionValue, metricSatisfactionSubtitle)
        );
        metricsBar.getChildren().forEach(node -> HBox.setHgrow(node, Priority.ALWAYS));

        VBox header = new VBox(20, headerLine, metricsBar, buildAlertsPanel());
        header.getStyleClass().add("header-wrapper");
        return header;
    }

    private void openAccountManager() {
        AccountManagerDialog.open(stage, authService, session, () -> {
            updateSessionLabel();
            reload(tfSearch.getText().trim());
        });
    }

    private NotificationSettings fetchNotificationSettings() {
        try {
            return dao.loadNotificationSettings();
        } catch (Exception ex) {
            ex.printStackTrace();
            Platform.runLater(() -> showError(new RuntimeException("Paramètres de notification indisponibles : " + ex.getMessage(), ex)));
            return NotificationSettings.defaults();
        }
    }

    private void openNotificationSettings() {
        NotificationSettingsDialog dialog = new NotificationSettingsDialog(stage, currentNotificationSettings());
        ThemeManager.apply(dialog);
        dialog.showAndWait().ifPresent(this::persistNotificationSettings);
    }

    private void persistNotificationSettings(NotificationSettings updated) {
        if (updated == null) {
            return;
        }
        notificationSettings = updated.normalized();
        NotificationSettings toPersist = notificationSettings;
        exec.submit(() -> {
            try {
                dao.saveNotificationSettings(toPersist);
                Platform.runLater(() -> {
                    AppServices.notificationServiceOptional().ifPresent(service -> service.updateSettings(notificationSettings));
                    AppServices.trayManagerOptional().ifPresent(manager -> manager.updateSnoozeMinutes(notificationSettings.snoozeMinutes()));
                    updateAlerts();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> showError(new RuntimeException("Paramètres de notification non enregistrés : " + ex.getMessage(), ex)));
            }
        });
    }

    private NotificationSettings currentNotificationSettings() {
        return notificationSettings == null ? NotificationSettings.defaults() : notificationSettings;
    }

    private void initSessionTracking() {
        updateSessionLabel();
        sessionTimeline.stop();
        sessionTimeline.getKeyFrames().setAll(
                new KeyFrame(Duration.seconds(1), e -> updateSessionLabel())
        );
        sessionTimeline.setCycleCount(Timeline.INDEFINITE);
        sessionTimeline.play();
    }

    private void updateSessionLabel() {
        if (session == null || loginStarted == null) {
            sessionLabel.setText("Session déconnectée");
            return;
        }
        java.time.Duration elapsed = java.time.Duration.between(loginStarted, Instant.now());
        if (elapsed.isNegative()) {
            elapsed = java.time.Duration.ZERO;
        }
        sessionLabel.setText(String.format("Session connectée : %s · %s",
                session.username(), formatElapsed(elapsed)));
    }

    private static String formatElapsed(java.time.Duration elapsed) {
        long totalSeconds = elapsed.getSeconds();
        if (totalSeconds < 0) totalSeconds = 0;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    private VBox buildAlertsPanel() {
        alertsPanel.getStyleClass().setAll("alerts-panel");
        alertsPanel.setFillWidth(true);
        alertsPanel.setSpacing(8);
        alertsPanel.setVisible(false);
        alertsPanel.setManaged(false);

        alertsBadge.getStyleClass().setAll("alerts-badge");
        alertsSummary.getStyleClass().setAll("alerts-summary");
        alertsSummary.setWrapText(true);

        alertsList.getStyleClass().setAll("alerts-list");
        alertsList.setSpacing(8);
        alertsList.setFillWidth(true);

        HBox header = new HBox(8, alertsBadge, alertsSummary);
        header.getStyleClass().add("alerts-header");
        header.setAlignment(Pos.CENTER_LEFT);

        alertsPanel.getChildren().setAll(header, alertsList);
        return alertsPanel;
    }

    // ============================ CONSTRUCTION UI ============================

    private Node buildCenter() {
        HBox toolbar = buildToolbar();
        buildPrestatairesTable();

        VBox tableSection = new VBox(12, toolbar, table);
        tableSection.getStyleClass().addAll("panel-card", "table-section");
        tableSection.setFillWidth(true);
        VBox.setVgrow(table, Priority.ALWAYS);

        Node details = buildDetailsPanel();

        SplitPane split = new SplitPane(tableSection, details);
        split.getStyleClass().add("main-split");
        split.setOrientation(Orientation.HORIZONTAL);
        split.setDividerPositions(0.56);

        StackPane workspace = new StackPane(split);
        workspace.getStyleClass().add("workspace");
        return workspace;
    }

    private HBox buildToolbar() {
        tfSearch.setPromptText("Rechercher (nom, societe, email, telephone)...");
        tfSearch.setPrefColumnCount(32);
        addStyleClass(tfSearch, "search-field");

        searchDebounce.setOnFinished(e -> reload(tfSearch.getText().trim()));
        tfSearch.textProperty().addListener((o, ov, nv) -> searchDebounce.playFromStart());
        tfSearch.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ENTER) reload(tfSearch.getText().trim()); });

        btnAdd.setOnAction(e -> onAdd());
        btnEdit.setOnAction(e -> onEdit());
        btnDelete.setOnAction(e -> onDelete());
        btnFactures.setOnAction(e -> onManageFactures());
        btnExport.setOnAction(e -> onExportPdf());
        btnRefresh.setOnAction(e -> reload(tfSearch.getText().trim()));

        addStyleClass(btnAdd, "accent");
        addStyleClass(btnDelete, "danger");
        addStyleClass(btnEdit, "outline");
        for (Button ghost : List.of(btnFactures, btnExport, btnRefresh)) {
            addStyleClass(ghost, "ghost");
        }

        HBox left = new HBox(tfSearch);
        left.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(tfSearch, Priority.ALWAYS);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox right = new HBox(8, btnAdd, btnEdit, btnDelete, new Separator(Orientation.VERTICAL), btnFactures, btnExport, btnRefresh);
        right.setAlignment(Pos.CENTER_RIGHT);
        right.getStyleClass().add("action-group");

        HBox bar = new HBox(16, left, spacer, right);
        bar.getStyleClass().add("action-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private void buildPrestatairesTable() {
        table.setItems(items);
        addStyleClass(table, "modern-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        table.setPlaceholder(new Label("Aucun prestataire pour le moment"));

        TableColumn<Prestataire, String> cNom = new TableColumn<>("Nom");
        cNom.setMinWidth(140);
        cNom.setCellValueFactory(cd -> cd.getValue().nomProperty());

        TableColumn<Prestataire, String> cSoc = new TableColumn<>("Societe");
        cSoc.setMinWidth(120);
        cSoc.setCellValueFactory(cd -> cd.getValue().societeProperty());

        TableColumn<Prestataire, String> cTel = new TableColumn<>("Telephone");
        cTel.setMinWidth(110);
        cTel.setCellValueFactory(cd -> cd.getValue().telephoneProperty());

        TableColumn<Prestataire, String> cMail = new TableColumn<>("Email");
        cMail.setMinWidth(160);
        cMail.setCellValueFactory(cd -> cd.getValue().emailProperty());

        TableColumn<Prestataire, Number> cNote = new TableColumn<>("Note");
        cNote.setMinWidth(60);
        cNote.setMaxWidth(80);
        cNote.setCellValueFactory(cd -> cd.getValue().noteProperty());

        TableColumn<Prestataire, Number> cImpayes = new TableColumn<>("Impayes");
        cImpayes.setMinWidth(80);
        cImpayes.setMaxWidth(100);
        cImpayes.setCellValueFactory(cd -> cd.getValue().impayesProperty());

        table.getColumns().setAll(cNom, cSoc, cTel, cMail, cNote, cImpayes);
        table.getSelectionModel().selectedItemProperty().addListener((obs, old, cur) -> showDetails(cur));

        // Double clic -> modifier
        table.setRowFactory(tv -> {
            TableRow<Prestataire> row = new TableRow<>();
            row.setOnMouseClicked(e -> { if (e.getClickCount()==2 && !row.isEmpty()) onEdit(); });
            return row;
        });
        // Raccourcis
        table.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            if (e.isControlDown() && e.getCode()==KeyCode.N) { onAdd(); e.consume(); }
            else if (e.getCode()==KeyCode.ENTER) { onEdit(); e.consume(); }
            else if (e.getCode()==KeyCode.DELETE) { onDelete(); e.consume(); }
        });
    }

    private Node buildDetailsPanel() {
        // FICHE
        fichePane.setHgap(8);
        fichePane.setVgap(6);
        fichePane.setPadding(new Insets(8));
        int r = 0;
        fichePane.add(row("Nom"), 0, r);        fichePane.add(rowValue(), 1, r++);
        fichePane.add(row("Societe"), 0, r);    fichePane.add(rowValue(), 1, r++);
        fichePane.add(row("Telephone"), 0, r);  fichePane.add(rowValue(), 1, r++);
        fichePane.add(row("Email"), 0, r);      fichePane.add(rowValue(), 1, r++);
        fichePane.add(row("Note"), 0, r);       fichePane.add(rowValue(), 1, r++);
        fichePane.add(row("Contrat"), 0, r);    fichePane.add(rowValue(), 1, r++);

        // SERVICES
        tvServices.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tvServices.setPlaceholder(new Label("Aucun service"));
        TableColumn<ServiceRow, String> csDate = new TableColumn<>("Date");
        csDate.setMinWidth(90);
        csDate.setCellValueFactory(cd -> new javafx.beans.property.ReadOnlyStringWrapper(cd.getValue().date()));

        TableColumn<ServiceRow, ServiceStatus> csStatus = new TableColumn<>("Statut");
        csStatus.setMinWidth(120);
        csStatus.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().status()));
        csStatus.setCellFactory(col -> new TableCell<>() {
            private final Label pill = new Label();
            {
                pill.getStyleClass().add("status-pill");
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            }
            @Override protected void updateItem(ServiceStatus status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setGraphic(null);
                } else {
                    pill.setText(status.label());
                    pill.getStyleClass().setAll("status-pill", "status-pill--" + status.cssClassSuffix());
                    setGraphic(pill);
                }
            }
        });

        TableColumn<ServiceRow, String> csDesc = new TableColumn<>("Description");
        csDesc.setCellValueFactory(cd -> new javafx.beans.property.ReadOnlyStringWrapper(cd.getValue().desc()));
        tvServices.getColumns().setAll(csDate, csStatus, csDesc);

        tvServices.getItems().addListener((ListChangeListener<ServiceRow>) change -> updateDetailCounts());

        tvServices.setRowFactory(table -> {
            TableRow<ServiceRow> row = new TableRow<>();
            ContextMenu statusMenu = new ContextMenu();
            for (ServiceStatus status : ServiceStatus.values()) {
                MenuItem item = new MenuItem(status.label());
                item.setOnAction(e -> {
                    ServiceRow current = row.getItem();
                    if (current != null) onChangeServiceStatus(current, status);
                });
                statusMenu.getItems().add(item);
            }
            row.contextMenuProperty().bind(Bindings.when(row.emptyProperty()).then((ContextMenu) null).otherwise(statusMenu));
            return row;
        });

        serviceTypeValue.getStyleClass().add("service-type-value");
        serviceTypeValue.setWrapText(true);
        serviceNotesArea.setWrapText(true);
        serviceNotesArea.setEditable(false);
        serviceNotesArea.setFocusTraversable(false);
        serviceNotesArea.setPrefRowCount(4);
        serviceNotesArea.getStyleClass().add("details-notes");
        serviceInfoPane.getChildren().setAll(
                new Label("Type de service :"),
                serviceTypeValue,
                new Label("Description :"),
                serviceNotesArea
        );
        serviceInfoPane.setVisible(false);
        serviceInfoPane.setManaged(false);
        serviceInfoPane.setFillWidth(true);
        serviceStatusBar.setAlignment(Pos.CENTER_LEFT);

        VBox servicesPane = new VBox(12, serviceInfoPane, serviceStatusBar, tvServices);
        servicesPane.setFillWidth(true);
        VBox.setVgrow(tvServices, Priority.ALWAYS);

        // FACTURES
        tvFactures.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tvFactures.setPlaceholder(new Label("Aucune facture"));
        TableColumn<Facture, String> cfDesc = new TableColumn<>("Description");
        cfDesc.setCellValueFactory(cd -> cd.getValue().descriptionProperty());
        TableColumn<Facture, String> cfEch = new TableColumn<>("Echeance");
        cfEch.setMinWidth(90);
        cfEch.setCellValueFactory(cd -> new javafx.beans.property.ReadOnlyStringWrapper(cd.getValue().getEcheanceFr()));
        TableColumn<Facture, String> cfMontant = new TableColumn<>("Montant TTC");
        cfMontant.setMinWidth(110);
        cfMontant.setCellValueFactory(cd -> new javafx.beans.property.ReadOnlyStringWrapper(money.format(
                cd.getValue().getMontantTtc() == null ? 0.0 : cd.getValue().getMontantTtc().doubleValue())));
        TableColumn<Facture, String> cfPaye = new TableColumn<>("Payee");
        cfPaye.setMinWidth(70);
        cfPaye.setMaxWidth(80);
        cfPaye.setCellValueFactory(cd -> new javafx.beans.property.ReadOnlyStringWrapper(cd.getValue().isPaye() ? "Oui" : "Non"));
        tvFactures.getColumns().setAll(cfDesc, cfEch, cfMontant, cfPaye);

        VBox facturesPane = new VBox(tvFactures);
        facturesPane.setFillWidth(true);
        VBox.setVgrow(tvFactures, Priority.ALWAYS);

        Node ficheCard = createDetailsCard(fichePane);
        Node servicesCard = createDetailsCard(servicesPane);
        Node facturesCard = createDetailsCard(facturesPane);

        btnDetailFiche.setUserData(ficheCard);
        btnDetailServices.setUserData(servicesCard);
        btnDetailFactures.setUserData(facturesCard);

        detailStack.getChildren().setAll(ficheCard, servicesCard, facturesCard);
        if (!detailStack.getStyleClass().contains("details-stack")) {
            detailStack.getStyleClass().add("details-stack");
        }
        showDetailSection(ficheCard);

        detailsToggleGroup.selectedToggleProperty().addListener((obs, previous, selected) -> {
            if (selected != null) {
                Node target = (Node) selected.getUserData();
                if (target != null) {
                    showDetailSection(target);
                }
            }
        });
        detailsToggleGroup.selectToggle(btnDetailFiche);

        HBox toggleBar = new HBox(btnDetailFiche, btnDetailServices, btnDetailFactures);
        toggleBar.setSpacing(6);
        toggleBar.setAlignment(Pos.CENTER_LEFT);
        toggleBar.getStyleClass().add("details-toggle-bar");
        for (ToggleButton btn : new ToggleButton[]{btnDetailFiche, btnDetailServices, btnDetailFactures}) {
            btn.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(btn, Priority.ALWAYS);
        }

        VBox right = new VBox(18, toggleBar, detailStack);
        VBox.setVgrow(detailStack, Priority.ALWAYS);
        right.getStyleClass().addAll("panel-card", "details-container");
        right.setPadding(new Insets(20));
        right.setFillWidth(true);
        right.setPrefWidth(500);
        return right;
    }

    private static Label row(String name) {
        Label l = new Label(name + " :");
        l.getStyleClass().add("form-label");
        return l;
    }
    private static Label rowValue() {
        Label l = new Label();
        l.getStyleClass().add("form-value");
        l.setWrapText(true);
        return l;
    }

    // ============================ CHARGEMENT ============================

    private void reload(String query) {
        btnEdit.setDisable(true);
        btnDelete.setDisable(true);
        items.clear();

        exec.submit(() -> {
            try {
                List<Prestataire> lst = dao.list(query == null ? "" : query);
                Platform.runLater(() -> {
                    items.setAll(lst);
                    if (!lst.isEmpty()) {
                        table.getSelectionModel().select(0);
                    } else {
                        clearDetails();
                    }
                    updateMetrics();
                    updateAlerts();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    table.setPlaceholder(errorLabel("Erreur de chargement : " + ex.getMessage()));
                    clearDetails();
                    updateMetrics();
                    updateAlerts();
                });
                ex.printStackTrace();
            }
        });
    }

    private void refreshCurrentServices() {
        Prestataire current = table.getSelectionModel().getSelectedItem();
        if (current != null) {
            showDetails(current);
        }
        updateAlerts();
    }

    private void showDetails(Prestataire p) {
        btnEdit.setDisable(p == null);
        btnDelete.setDisable(p == null);
        btnDetailServices.setDisable(p == null);
        btnDetailFactures.setDisable(p == null);

        int row = 0;
        setFicheValue(row++, p != null ? p.getNom() : "");
        setFicheValue(row++, p != null ? p.getSociete() : "");
        setFicheValue(row++, p != null ? p.getTelephone() : "");
        setFicheValue(row++, p != null ? p.getEmail() : "");
        setFicheValue(row++, p != null ? Integer.toString(p.getNote()) : "");
        setFicheValue(row++, p != null ? p.getDateContrat() : "");

        tvServices.getItems().clear();
        tvFactures.getItems().clear();
        serviceNotesArea.clear();
        serviceInfoPane.setVisible(false);
        serviceInfoPane.setManaged(false);
        updateDetailCounts();
        if (p == null) {
            serviceTypeValue.setText("");
            ensureValidDetailSelection();
            return;
        }

        String type = p.getFacturation();
        String notes = p.getServiceNotes();
        boolean hasType = type != null && !type.isBlank();
        boolean hasNotes = notes != null && !notes.isBlank();
        if (hasType || hasNotes) {
            serviceTypeValue.setText(hasType ? type : "Non renseigne");
            serviceNotesArea.setText(hasNotes ? notes : "Aucune description");
            serviceInfoPane.setVisible(true);
            serviceInfoPane.setManaged(true);
        } else {
            serviceTypeValue.setText("Non renseigne");
        }

        exec.submit(() -> {
            try {
                List<ServiceRow> services = dao.services(p.getId());
                List<Facture> factures = dao.facturesPrestataire(p.getId());
                Platform.runLater(() -> {
                    tvServices.getItems().setAll(services);
                    tvFactures.getItems().setAll(factures);
                    updateDetailCounts();
                    ensureValidDetailSelection();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    tvServices.setPlaceholder(errorLabel("Erreur chargement services"));
                    tvFactures.setPlaceholder(errorLabel("Erreur chargement factures"));
                    updateDetailCounts();
                    ensureValidDetailSelection();
                });
                ex.printStackTrace();
            }
        });
    }

    private void clearDetails() { showDetails(null); }

    private void setFicheValue(int rowIndex, String value) {
        Node n = getNodeFromGridPane(fichePane, 1, rowIndex);
        if (n instanceof Label l) l.setText(value != null ? value : "");
    }

    private static Node getNodeFromGridPane(GridPane grid, int col, int row) {
        for (Node n : grid.getChildren()) {
            Integer r = GridPane.getRowIndex(n);
            Integer c = GridPane.getColumnIndex(n);
            if ((r == null ? 0 : r) == row && (c == null ? 0 : c) == col) return n;
        }
        return null;
    }

    private void ensureValidDetailSelection() {
        if (detailStack.getChildren().isEmpty()) return;
        Toggle selected = detailsToggleGroup.getSelectedToggle();
        if (selected instanceof ToggleButton btn && btn.isDisable()) {
            detailsToggleGroup.selectToggle(btnDetailFiche);
            selected = detailsToggleGroup.getSelectedToggle();
        }
        if (selected == null) {
            detailsToggleGroup.selectToggle(btnDetailFiche);
            selected = detailsToggleGroup.getSelectedToggle();
        }
        if (selected != null) {
            Object data = selected.getUserData();
            if (data instanceof Node node) {
                showDetailSection(node);
            }
        }
    }

    private void updateDetailCounts() {
        btnDetailServices.setText(labelWithCount("Services", tvServices.getItems().size()));
        btnDetailFactures.setText(labelWithCount("Factures", tvFactures.getItems().size()));
        updateServiceSummary();
    }

    private void updateServiceSummary() {
        ObservableList<ServiceRow> rows = tvServices.getItems();
        serviceStatusBar.getChildren().clear();
        boolean hasServices = rows != null && !rows.isEmpty();
        serviceStatusBar.setVisible(hasServices);
        serviceStatusBar.setManaged(hasServices);
        if (!hasServices) return;

        Map<ServiceStatus, Long> counts = new EnumMap<>(ServiceStatus.class);
        for (ServiceStatus status : ServiceStatus.values()) {
            counts.put(status, 0L);
        }
        for (ServiceRow row : rows) {
            ServiceStatus status = row.status() == null ? ServiceStatus.EN_ATTENTE : row.status();
            counts.merge(status, 1L, Long::sum);
        }
        for (ServiceStatus status : ServiceStatus.values()) {
            long count = counts.getOrDefault(status, 0L);
            Label chip = new Label(status.label() + " : " + count);
            chip.getStyleClass().setAll("status-pill", "status-pill--" + status.cssClassSuffix(), "status-pill--summary");
            serviceStatusBar.getChildren().add(chip);
        }
    }

    private static String labelWithCount(String base, int count) {
        return count > 0 ? base + " (" + count + ")" : base;
    }

    private ToggleButton createDetailButton(String text) {
        ToggleButton btn = new ToggleButton(text);
        btn.setFocusTraversable(false);
        btn.getStyleClass().add("details-toggle");
        btn.setToggleGroup(detailsToggleGroup);
        return btn;
    }

    private void showDetailSection(Node section) {
        for (Node child : detailStack.getChildren()) {
            boolean visible = child == section;
            child.setVisible(visible);
            child.setManaged(visible);
        }
    }

    private Node createDetailsCard(Node content) {
        VBox wrapper = new VBox(content);
        wrapper.getStyleClass().add("details-card");
        wrapper.setFillWidth(true);
        if (content instanceof Region region) {
            region.setMaxWidth(Double.MAX_VALUE);
            VBox.setVgrow(content, content == fichePane ? Priority.NEVER : Priority.ALWAYS);
        }
        return wrapper;
    }

    private VBox createMetricCard(String title, Label value, Label subtitle) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("metric-title");

        VBox card = new VBox(6, titleLabel, value, subtitle);
        card.getStyleClass().add("metric-card");
        card.setFillWidth(true);
        HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    private Label createMetricValueLabel() {
        Label label = new Label("--");
        label.getStyleClass().add("metric-value");
        return label;
    }

    private Label createMetricSubtitleLabel() {
        Label label = new Label();
        label.getStyleClass().add("metric-subtitle");
        return label;
    }

    private void updateMetrics() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::updateMetrics);
            return;
        }

        int total = items.size();
        metricPrestatairesValue.setText(Integer.toString(total));
        metricPrestatairesSubtitle.setText(total == 0
                ? "Ajoutez votre premier prestataire"
                : total == 1 ? "Contact suivi" : total + " contacts suivis");

        int totalImpayes = items.stream().mapToInt(Prestataire::getImpayes).sum();
        metricImpayesValue.setText(money.format(totalImpayes));
        long overdue = items.stream().filter(p -> p.getImpayes() > 0).count();
        metricImpayesSubtitle.setText(overdue == 0
                ? "Aucun retard"
                : overdue == 1 ? "1 relance à faire" : overdue + " relances à faire");

        double average = items.stream().mapToInt(Prestataire::getNote).average().orElse(Double.NaN);
        if (Double.isNaN(average)) {
            metricSatisfactionValue.setText("--");
        } else {
            metricSatisfactionValue.setText(String.format(Locale.FRANCE, "%.0f %%", average));
        }
        long rated = items.stream().filter(p -> p.getNote() > 0).count();
        metricSatisfactionSubtitle.setText(rated == 0
                ? "Pas encore de notes"
                : rated == 1 ? "1 avis enregistré" : rated + " avis enregistrés");
    }


    private void updateAlerts() {
        NotificationSettings cfg = currentNotificationSettings();
        exec.submit(() -> {
            try {
                LocalDateTime now = LocalDateTime.now();
                LocalDateTime limit = now.plusDays(Math.max(1, cfg.leadDays()));
                List<Facture> factures = dao.facturesImpayeesAvant(limit);
                Map<Integer, Prestataire> cache = new HashMap<>();
                List<AlertInfo> alerts = new ArrayList<>();
                for (Facture facture : factures) {
                    if (facture == null || facture.isPaye()) continue;
                    LocalDate due = facture.getEcheance();
                    if (due == null) continue;
                    Prestataire prestataire = cache.computeIfAbsent(
                            facture.getPrestataireId(),
                            id -> dao.findPrestataire(id)
                    );
                    if (prestataire == null) continue;
                    alerts.add(new AlertInfo(
                            safePrestataireName(prestataire),
                            safeFactureDescription(facture),
                            facture.getMontantTtc(),
                            due,
                            due.isBefore(now.toLocalDate())
                    ));
                }
                alerts.sort(Comparator.comparing(AlertInfo::dueDate));
                Platform.runLater(() -> renderAlerts(alerts));
            } catch (Exception ex) {
                Platform.runLater(() -> renderAlertsError(ex));
            }
        });
    }

    private record AlertInfo(String prestataireName,
                             String description,
                             BigDecimal montant,
                             LocalDate dueDate,
                             boolean overdue) { }

    private void renderAlerts(List<AlertInfo> alerts) {
        alertsList.getChildren().clear();
        if (alerts == null || alerts.isEmpty()) {
            alertsPanel.setVisible(false);
            alertsPanel.setManaged(false);
            alertsSummary.setText("");
            return;
        }

        alertsPanel.setVisible(true);
        alertsPanel.setManaged(true);

        NotificationSettings cfg = currentNotificationSettings();
        long overdueCount = alerts.stream().filter(AlertInfo::overdue).count();
        int total = alerts.size();

        boolean hasOverdue = overdueCount > 0;
        boolean highlight = cfg.highlightOverdue();
        String panelClass;
        String badgeClass;
        if (hasOverdue) {
            panelClass = highlight ? "alerts-panel--danger" : "alerts-panel--info";
            badgeClass = highlight ? "alerts-badge--danger" : "alerts-badge--info";
        } else {
            panelClass = highlight ? "alerts-panel--warning" : "alerts-panel--info";
            badgeClass = highlight ? "alerts-badge--warning" : "alerts-badge--info";
        }
        alertsPanel.getStyleClass().setAll("alerts-panel", panelClass);
        alertsBadge.getStyleClass().setAll("alerts-badge", badgeClass);
        alertsSummary.setText(buildAlertsSummary(total, overdueCount, cfg.leadDays()));

        LocalDateTime now = LocalDateTime.now();
        for (AlertInfo info : alerts) {
            NotificationTemplateEngine.Context ctx = toTemplateContext(info, now);
            String title = NotificationTemplateEngine.render(cfg.subjectTemplate(), ctx);
            String body = NotificationTemplateEngine.render(cfg.bodyTemplate(), ctx);
            Label titleLabel = new Label(title.isBlank() ? defaultAlertTitle(info) : title);
            titleLabel.getStyleClass().add("alerts-item-title");
            Label bodyLabel = new Label(body.isBlank() ? defaultAlertBody(info, now) : body);
            bodyLabel.getStyleClass().add("alerts-item-body");
            bodyLabel.setWrapText(true);
            VBox card = new VBox(4, titleLabel, bodyLabel);
            card.getStyleClass().setAll("alerts-item-card", "alerts-item");
            card.setFillWidth(true);
            alertsList.getChildren().add(card);
        }
    }

    private static String describeTiming(LocalDateTime reference, LocalDate due) {
        if (due == null) return "";
        LocalDate today = reference.toLocalDate();
        if (due.isEqual(today)) {
            return "échéance aujourd'hui";
        }
        if (due.isAfter(today)) {
            long days = ChronoUnit.DAYS.between(today, due);
            if (days <= 0) {
                return "échéance aujourd'hui";
            }
            if (days == 1) {
                return "dans 1 jour";
            }
            return "dans " + days + " jours";
        }
        long daysLate = ChronoUnit.DAYS.between(due, today);
        if (daysLate <= 0) {
            return "échéance aujourd'hui";
        }
        if (daysLate == 1) {
            return "en retard depuis 1 jour";
        }
        return "en retard depuis " + daysLate + " jours";
    }

    private String buildAlertsSummary(int total, long overdueCount, int leadDays) {
        if (total <= 0) {
            return "";
        }
        if (overdueCount == total) {
            return total == 1 ? "1 facture en retard" : total + " factures en retard";
        }
        if (overdueCount > 0) {
            return total + " factures à surveiller · " + overdueCount + " en retard";
        }
        String horizon = horizonLabel(leadDays);
        return total == 1
                ? "1 facture arrive à échéance " + horizon
                : total + " factures arrivent à échéance " + horizon;
    }

    private String horizonLabel(int leadDays) {
        int days = Math.max(leadDays, 1);
        return switch (days) {
            case 1 -> "dans les 24 prochaines heures";
            case 2 -> "dans les 48 prochaines heures";
            default -> "dans les " + days + " prochains jours";
        };
    }

    private String defaultAlertTitle(AlertInfo info) {
        return info.prestataireName() + " — " + info.description();
    }

    private String defaultAlertBody(AlertInfo info, LocalDateTime now) {
        StringBuilder builder = new StringBuilder();
        String amount = formatAmount(info.montant());
        if (!amount.isEmpty()) {
            builder.append(amount);
        }
        String due = formatDueDate(info.dueDate());
        if (!due.isEmpty()) {
            if (builder.length() > 0) builder.append(" · ");
            builder.append("Échéance le ").append(due);
        }
        String timing = describeTiming(now, info.dueDate());
        if (!timing.isBlank()) {
            if (builder.length() > 0) builder.append(" · ");
            builder.append(timing);
        }
        return builder.toString();
    }

    private String formatAmount(BigDecimal montant) {
        if (montant == null) {
            return "";
        }
        try {
            return money.format(montant);
        } catch (IllegalArgumentException ignore) {
            return montant.toPlainString() + " €";
        }
    }

    private String formatDueDate(LocalDate due) {
        if (due == null) {
            return "";
        }
        return due.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }

    private NotificationTemplateEngine.Context toTemplateContext(AlertInfo info, LocalDateTime now) {
        LocalDate due = info.dueDate();
        long deltaDays = due == null ? 0 : ChronoUnit.DAYS.between(now.toLocalDate(), due);
        return new NotificationTemplateEngine.Context(
                info.prestataireName(),
                info.description(),
                due,
                formatAmount(info.montant()),
                describeTiming(now, due),
                deltaDays,
                info.overdue()
        );
    }

    private void renderAlertsError(Exception ex) {
        ex.printStackTrace();
        alertsList.getChildren().clear();
        alertsPanel.setVisible(false);
        alertsPanel.setManaged(false);
        alertsSummary.setText("");
    }

    private static String safePrestataireName(Prestataire prestataire) {
        if (prestataire == null) {
            return "Prestataire";
        }
        String nom = prestataire.getNom();
        if (nom != null && !nom.isBlank()) {
            return nom.trim();
        }
        return "Prestataire #" + prestataire.getId();
    }

    private static String safeFactureDescription(Facture facture) {
        if (facture == null) {
            return "Facture";
        }
        String desc = facture.getDescription();
        if (desc != null && !desc.isBlank()) {
            return desc.trim();
        }
        return "Facture " + facture.getId();
    }

    private static void addStyleClass(Node node, String style) {
        if (node == null || style == null || style.isBlank()) {
            return;
        }
        if (!node.getStyleClass().contains(style)) {
            node.getStyleClass().add(style);
        }
    }

    private void onChangeServiceStatus(ServiceRow row, ServiceStatus status) {
        if (row == null || status == null) return;
        if (row.status() == status) return;
        Integer id = row.id();
        if (id == null) return;

        exec.submit(() -> {
            try {
                dao.updateServiceStatus(id, status);
                Platform.runLater(() -> {
                    ObservableList<ServiceRow> rows = tvServices.getItems();
                    int idx = rows.indexOf(row);
                    if (idx >= 0) {
                        rows.set(idx, row.withStatus(status));
                        updateDetailCounts();
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> showError(new RuntimeException("Statut service non mis a jour : " + ex.getMessage())));
            }
        });
    }

    // ============================ ACTIONS ============================

    private void onAdd() {
        PrestataireFormDialog dlg = new PrestataireFormDialog(dao, null, this::refreshCurrentServices);
        ThemeManager.apply(dlg);
        dlg.showAndWait().ifPresent(p -> exec.submit(() -> {
            try { int id = dao.insertPrestataire(p); p.idProperty().set(id);
                Platform.runLater(() -> { items.add(p); table.getSelectionModel().select(p); });
            } catch (Exception ex) { Platform.runLater(() -> showError(ex)); }
        }));
    }

    private void onEdit() {
        Prestataire sel = table.getSelectionModel().getSelectedItem(); if (sel == null) return;
        PrestataireFormDialog dlg = new PrestataireFormDialog(dao, clonePrestataire(sel), this::refreshCurrentServices);
        ThemeManager.apply(dlg);
        dlg.showAndWait().ifPresent(p -> exec.submit(() -> {
            try { p.idProperty().set(sel.getId()); dao.updatePrestataire(p);
                Platform.runLater(() -> { int i = items.indexOf(sel); items.set(i, p); table.getSelectionModel().select(p); });
            } catch (Exception ex) { Platform.runLater(() -> showError(ex)); }
        }));
    }

    private void onDelete() {
        Prestataire sel = table.getSelectionModel().getSelectedItem(); if (sel == null) return;
        if (!confirm("Supprimer " + sel.getNom() + " et ses donnees ?")) return;
        exec.submit(() -> {
            try { dao.deletePrestataire(sel.getId());
                Platform.runLater(() -> { items.remove(sel); clearDetails(); });
            } catch (Exception ex) { Platform.runLater(() -> showError(ex)); }
        });
    }

    private void onManageFactures() {
        Prestataire selected = table.getSelectionModel().getSelectedItem(); if (selected == null) return;
        FacturesManagerDialog dlg = new FacturesManagerDialog(dao, selected.getId(), selected.getNom());
        ThemeManager.apply(dlg);
        dlg.showAndWait();
        if (dlg.hasMutations()) {
            refreshCurrentServices();
        }
    }
    private static Prestataire clonePrestataire(Prestataire p) {
        Prestataire c = p.copyWithoutId();
        c.idProperty().set(p.getId());
        return c;
    }
    private boolean confirm(String msg) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, msg, ButtonType.YES, ButtonType.NO);
        ThemeManager.apply(a); return a.showAndWait().orElse(ButtonType.NO)==ButtonType.YES;
    }
    private void showError(Exception ex) { new Alert(Alert.AlertType.ERROR, ex.getMessage()).showAndWait(); }

    private void onExportPdf() {
        Prestataire p = table.getSelectionModel().getSelectedItem();
        if (p == null) {
            Alert a = new Alert(Alert.AlertType.INFORMATION, "Selectionnez un prestataire.");
            ThemeManager.apply(a);
            a.showAndWait();
            return;
        }
        javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
        fc.setTitle("Exporter la fiche de " + p.getNom());
        fc.getExtensionFilters().setAll(new javafx.stage.FileChooser.ExtensionFilter("PDF", "*.pdf"));
        java.io.File f = fc.showSaveDialog(stage);
        if (f == null) return;
        exec.submit(() -> {
            try {
                PDF.exportFichePrestataire(p, f.toPath());
                Platform.runLater(() -> {
                    Alert ok = new Alert(Alert.AlertType.INFORMATION, "Exporte : " + f.getAbsolutePath());
                    ThemeManager.apply(ok);
                    ok.showAndWait();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    Alert err = new Alert(Alert.AlertType.ERROR, "Export impossible : " + ex.getMessage());
                    ThemeManager.apply(err);
                    err.showAndWait();
                });
            }
        });
    }

    // ============================ UTIL ============================

    private static Label errorLabel(String msg) {
        Label l = new Label(msg);
        l.setStyle("-fx-text-fill: -fx-danger;");
        return l;
    }
}

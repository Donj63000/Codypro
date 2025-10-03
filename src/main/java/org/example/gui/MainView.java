package org.example.gui;

import javafx.animation.PauseTransition;
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
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.example.dao.DB;
import org.example.dao.MailPrefsDAO;
import org.example.model.Facture;
import org.example.model.Prestataire;
import org.example.model.ServiceRow;
import org.example.model.ServiceStatus;
import org.example.pdf.PDF;
import org.example.gui.PrestataireFormDialog;
import org.example.gui.ServiceFormDialog;
import org.example.gui.FactureFormDialog;

import java.text.NumberFormat;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainView {
    private final Stage stage;
    private final DB dao;
    private final MailPrefsDAO mailDao;
    private final BorderPane root = new BorderPane();

    // UI elements
    private final TableView<Prestataire> table = new TableView<>();
    private final ObservableList<Prestataire> items = FXCollections.observableArrayList();
    private final TextField tfSearch = new TextField();
    private final Button btnAdd = new Button("Ajouter");
    private final Button btnEdit = new Button("Modifier");
    private final Button btnDelete = new Button("Supprimer");
    private final Button btnExport = new Button("Exporter PDF");
    private final Button btnRefresh = new Button("Recharger");

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

    // background
    private final ExecutorService exec = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ui-loader");
        t.setDaemon(true);
        return t;
    });
    private final PauseTransition searchDebounce = new PauseTransition(Duration.millis(250));

    private final NumberFormat money = NumberFormat.getCurrencyInstance(Locale.FRANCE);

    public MainView(Stage stage, DB dao, MailPrefsDAO mailDao) {
        this.stage = stage;
        this.dao = dao;
        this.mailDao = mailDao;

        java.util.stream.Stream.of(btnAdd, btnEdit, btnDelete, btnExport, btnRefresh)
              .forEach(b -> b.setMinWidth(Region.USE_PREF_SIZE));

        // Top: title + mail settings
        MenuBar menuBar = new MenuBar();
        Menu menuFile = new Menu("Fichier");
        Menu menuTools = new Menu("Outils");
        MenuItem miMail = new MenuItem("Parametres e-mail");
        miMail.setOnAction(e -> MailQuickSetupDialog.open(this.stage, this.mailDao));
        menuTools.getItems().addAll(miMail);
        menuBar.getMenus().addAll(menuFile, menuTools);

        HBox header = new HBox(new Label("Gestion des Prestataires"));
        header.setPadding(new Insets(8));
        header.setSpacing(8);
        Button bMail = new Button("Parametres e-mail");
        bMail.getStyleClass().add("accent");
        bMail.setOnAction(e -> MailQuickSetupDialog.open(this.stage, this.mailDao));
        header.getChildren().add(bMail);
        root.setTop(new BorderPane(header, menuBar, null, null, null));

        // Center: toolbar + table + details in split
        Node center = buildCenter();
        root.setCenter(center);

        // initial load
        reload("");
    }

    public Parent getRoot() { return root; }

    public void shutdownExecutor() { exec.shutdownNow(); }

    // ============================ CONSTRUCTION UI ============================

    private Node buildCenter() {
        HBox toolbar = buildToolbar();
        buildPrestatairesTable();
        Node details = buildDetailsPanel();

        SplitPane split = new SplitPane(new VBox(8, toolbar, table), details);
        split.setOrientation(Orientation.HORIZONTAL);
        split.setDividerPositions(0.55);
        VBox.setVgrow(table, Priority.ALWAYS);
        return split;
    }

    private HBox buildToolbar() {
        tfSearch.setPromptText("Rechercher (nom, societe, email, telephone)...");
        tfSearch.setPrefColumnCount(32);

        searchDebounce.setOnFinished(e -> reload(tfSearch.getText().trim()));
        tfSearch.textProperty().addListener((o, ov, nv) -> searchDebounce.playFromStart());
        tfSearch.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ENTER) reload(tfSearch.getText().trim()); });

        btnAdd.setOnAction(e -> onAdd());
        btnEdit.setOnAction(e -> onEdit());
        btnDelete.setOnAction(e -> onDelete());
        btnExport.setOnAction(e -> onExportPdf());
        btnRefresh.setOnAction(e -> reload(tfSearch.getText().trim()));

        HBox left = new HBox(8, new Label("Recherche :"), tfSearch);
        left.setAlignment(Pos.CENTER_LEFT);
        HBox right = new HBox(8, btnAdd, btnEdit, btnDelete, new Separator(Orientation.VERTICAL), btnExport, btnRefresh);
        right.setAlignment(Pos.CENTER_RIGHT);

        HBox bar = new HBox(16, left, new HBox(), right);
        HBox.setHgrow(bar.getChildren().get(1), Priority.ALWAYS);
        bar.setPadding(new Insets(0, 0, 6, 0));
        return bar;
    }

    private void buildPrestatairesTable() {
        table.setItems(items);
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

        VBox servicesPane = new VBox(12, serviceInfoPane, tvServices);
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

        VBox right = new VBox(12, toggleBar, detailStack);
        VBox.setVgrow(detailStack, Priority.ALWAYS);
        right.setPadding(new Insets(0, 0, 0, 8));
        right.setPrefWidth(460);
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
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    table.setPlaceholder(errorLabel("Erreur de chargement : " + ex.getMessage()));
                    clearDetails();
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

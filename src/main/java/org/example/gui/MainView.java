package org.example.gui;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.example.dao.DB;
import org.example.model.Facture;
import org.example.model.Prestataire;
import org.example.pdf.PDF;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainView {
    private static final DateTimeFormatter DATE_FR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final BorderPane root = new BorderPane();
    private final DB dao;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "gui-bg"));

    private final TableView<Prestataire> table = new TableView<>();
    private final TextField search = new TextField();
    private final Label[] detailLabels = new Label[7];
    private final ProgressBar noteBar = new ProgressBar(0);

    public MainView(Stage stage, DB dao) {
        this.dao = dao;
        buildLayout(stage);
        refresh("");
        stage.setOnCloseRequest(e -> executor.shutdown());
    }

    public Parent getRoot() {
        return root;
    }

    private <T> void runAsync(java.util.concurrent.Callable<T> work, java.util.function.Consumer<T> ui) {
        javafx.concurrent.Task<T> task = new javafx.concurrent.Task<>() {
            @Override
            protected T call() throws Exception {
                return work.call();
            }
        };
        task.setOnSucceeded(ev -> Platform.runLater(() -> ui.accept(task.getValue())));
        task.setOnFailed(ev -> {
            Throwable ex = task.getException();
            if (ex != null) ex.printStackTrace();
            Platform.runLater(() -> alert(ex == null ? "Erreur" : ex.getMessage()));
        });
        executor.submit(task);
    }

    private void runAsync(Runnable work, Runnable ui) {
        runAsync(() -> {
            work.run();
            return null;
        }, v -> {
            if (ui != null) ui.run();
        });
    }

    private void buildLayout(Stage stage) {
        HBox topBar = new HBox(10, new Label("Recherche : "), search);
        topBar.setPadding(new Insets(10));
        search.textProperty().addListener((obs, o, n) -> refresh(n));

        createTable(stage);

        VBox right = buildDetailPane(stage);
        right.setPrefWidth(320);

        HBox bottom = buildBottomBar(stage);

        root.setTop(topBar);
        root.setCenter(table);
        root.setRight(right);
        root.setBottom(bottom);
    }

    private void createTable(Stage stage) {
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        String[] cols = {"Nom","Société","Téléphone","Email","Note","Date contrat"};
        String[] props = {"nom","societe","telephone","email","note","dateContrat"};
        for (int i = 0; i < cols.length; i++) {
            TableColumn<Prestataire, ?> c = new TableColumn<>(cols[i]);
            c.setCellValueFactory(new PropertyValueFactory<>(props[i]));
            table.getColumns().add(c);
        }

        TableColumn<Prestataire, Boolean> cOk = new TableColumn<>("Toutes factures payées");
        cOk.setCellValueFactory(data ->
                new ReadOnlyBooleanWrapper(data.getValue().getImpayes() == 0));

        cOk.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Boolean val, boolean empty) {
                super.updateItem(val, empty);
                if (empty || val == null) {
                    setText(null);
                    getStyleClass().removeAll("cell-paid","cell-unpaid");
                } else {
                    setText(val ? "\u2714" : "\u2717");
                    getStyleClass().removeAll("cell-paid","cell-unpaid");
                    getStyleClass().add(val ? "cell-paid" : "cell-unpaid");
                }
            }
        });
        table.getColumns().add(cOk);
        table.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> updateDetails(n));
        table.setPrefWidth(580);
    }

    private void refresh(String filtre) {
        runAsync(() -> dao.list(filtre), list -> {
            table.setItems(FXCollections.observableArrayList(list));
            updateDetails(null);
        });
    }

    private VBox buildDetailPane(Stage stage) {
        VBox v = new VBox(8);
        v.setPadding(new Insets(10));

        String[] lab = {"Nom","Société","Téléphone","Email","Note","Facturation","Date contrat"};
        for (int i = 0; i < lab.length; i++) {
            Label key = new Label(lab[i] + " :");
            key.setStyle("-fx-font-weight:bold");
            detailLabels[i] = new Label();
            HBox line = new HBox(6, key, detailLabels[i]);
            v.getChildren().add(line);
        }
        noteBar.setPrefWidth(200);
        v.getChildren().add(noteBar);
        return v;
    }

    private void updateDetails(Prestataire p) {
        if (p == null) {
            Arrays.stream(detailLabels).forEach(l -> l.setText(""));
            noteBar.setProgress(0);
            return;
        }
        detailLabels[0].setText(p.getNom());
        detailLabels[1].setText(p.getSociete());
        detailLabels[2].setText(p.getTelephone());
        detailLabels[3].setText(p.getEmail());
        detailLabels[4].setText(p.getNote() + " %");
        detailLabels[5].setText(p.getFacturation());
        detailLabels[6].setText(p.getDateContrat());
        noteBar.setProgress(p.getNote() / 100.0);
    }

    private HBox buildBottomBar(Stage stage) {
        Button bAdd = new Button("Nouveau");
        Button bEdit = new Button("Modifier");
        Button bDel = new Button("Supprimer");
        Button bService = new Button("Ajout service");
        Button bHist = new Button("Historique");
        Button bFact = new Button("Factures");
        Button bPDF = new Button("Fiche PDF");
        Button bPDFAll = new Button("PDF global");

        bAdd.setOnAction(e -> editDialog(null));
        bEdit.setOnAction(e -> editDialog(table.getSelectionModel().getSelectedItem()));
        bDel.setOnAction(e -> {
            Prestataire p = table.getSelectionModel().getSelectedItem();
            if (p != null && confirm("Supprimer " + p.getNom() + " ?")) {
                runAsync(() -> dao.delete(p.getId()), () -> refresh(search.getText()));
            }
        });
        bService.setOnAction(e -> addServiceDialog());
        bHist.setOnAction(e -> showHistoryDialog());
        bFact.setOnAction(e -> showFacturesDialog());
        bPDF.setOnAction(e -> {
            Prestataire p = table.getSelectionModel().getSelectedItem();
            if (p != null) {
                FileChooser fc = new FileChooser();
                fc.setInitialFileName(p.getNom() + "_fiche.pdf");
                fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
                Path f = Optional.ofNullable(fc.showSaveDialog(stage)).map(java.io.File::toPath).orElse(null);
                if (f != null) {
                    runAsync(() -> {
                    PDF.generateFiche(f, p);
                    return null;
                    }, v -> new Alert(Alert.AlertType.INFORMATION, "Fiche PDF exportée", ButtonType.OK).showAndWait());
                }
            }
        });
        bPDFAll.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setInitialFileName("Historique_global_prestataires.pdf");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
            Path f = Optional.ofNullable(fc.showSaveDialog(stage)).map(java.io.File::toPath).orElse(null);
            if (f != null) {
                runAsync(() -> {
                    PDF.generateHistorique(f, dao);
                    return null;
                }, v -> new Alert(Alert.AlertType.INFORMATION, "Historique PDF exporté", ButtonType.OK).showAndWait());
            }
        });

        HBox hb = new HBox(8, bAdd, bEdit, bDel, bService, bHist, bFact, bPDF, bPDFAll);
        hb.setPadding(new Insets(10));
        return hb;
    }

    private void editDialog(Prestataire src) {
        Dialog<Prestataire> d = new Dialog<>();
        d.setTitle(src == null ? "Nouveau Prestataire" : "Modifier Prestataire");
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        GridPane gp = new GridPane();
        gp.setHgap(8);
        gp.setVgap(8);
        String[] lab = {"Nom","Société","Téléphone","Email","Note (0-100)","Facturation","Date contrat (dd/MM/yyyy)"};
        TextField[] fields = new TextField[lab.length];
        Pattern mailRegex = Pattern.compile("[^@]+@[^@]+\\.[^@]+");

        for (int i = 0; i < lab.length; i++) {
            gp.add(new Label(lab[i] + "  :"), 0, i);
            fields[i] = new TextField();
            switch (i) {
                case 0 -> fields[i].setText(src == null ? "" : src.getNom());
                case 1 -> fields[i].setText(src == null ? "" : src.getSociete());
                case 2 -> fields[i].setText(src == null ? "" : src.getTelephone());
                case 3 -> fields[i].setText(src == null ? "" : src.getEmail());
                case 4 -> fields[i].setText(src == null ? "0" : String.valueOf(src.getNote()));
                case 5 -> fields[i].setText(src == null ? "" : src.getFacturation());
                case 6 -> fields[i].setText(src == null ? DATE_FR.format(LocalDate.now()) : src.getDateContrat());
            }
            gp.add(fields[i], 1, i);
        }
        d.getDialogPane().setContent(gp);

        Button ok = (Button) d.getDialogPane().lookupButton(ButtonType.OK);
        ok.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
            try {
                String nom = fields[0].getText().trim();
                if (nom.isEmpty()) throw new IllegalArgumentException("Nom obligatoire.");
                if (!fields[3].getText().isBlank() && !mailRegex.matcher(fields[3].getText()).matches())
                    throw new IllegalArgumentException("Email invalide.");
                int note = Integer.parseInt(fields[4].getText());
                if (note < 0 || note > 100) throw new IllegalArgumentException("Note 0-100.");
                String dateStr = fields[6].getText().trim();
                if (dateStr.isEmpty()) throw new IllegalArgumentException("Date obligatoire.");
                LocalDate.parse(dateStr, DATE_FR);
            } catch (Exception e) {
                alert(e.getMessage());
                ev.consume();
            }
        });

        d.setResultConverter(bt -> {
            if (bt == ButtonType.OK) {
                return new Prestataire(
                        src == null ? 0 : src.getId(),
                        fields[0].getText(),
                        fields[1].getText(),
                        fields[2].getText(),
                        fields[3].getText(),
                        Integer.parseInt(fields[4].getText()),
                        fields[5].getText(),
                        fields[6].getText()
                );
            }
            return null;
        });
        Optional<Prestataire> res = d.showAndWait();
        res.ifPresent(p -> runAsync(() -> {
            if (src == null) dao.add(p.copyWithoutId());
            else dao.update(p);
        }, () -> refresh(search.getText())));
    }

    private void addServiceDialog() {
        Prestataire p = table.getSelectionModel().getSelectedItem();
        if (p == null) {
            alert("Sélectionnez un prestataire.");
            return;
        }
        TextInputDialog td = new TextInputDialog();
        td.setTitle("Nouveau service");
        td.setHeaderText("Description du service");
        td.showAndWait().ifPresent(desc -> {
            if (!desc.isBlank()) runAsync(() -> dao.addService(p.getId(), desc), null);
        });
    }

    private void showHistoryDialog() {
        Prestataire p = table.getSelectionModel().getSelectedItem();
        if (p == null) {
            alert("Sélectionnez un prestataire.");
            return;
        }
        Stage win = new Stage();
        win.setTitle("Historique — " + p.getNom());
        VBox vb = new VBox(6);
        vb.setPadding(new Insets(10));
        vb.getChildren().add(new Label("Chargement..."));
        win.setScene(new Scene(new ScrollPane(vb), 400, 400));
        win.initModality(Modality.WINDOW_MODAL);
        win.show();
        runAsync(() -> dao.services(p.getId()), list -> {
            vb.getChildren().clear();
            list.forEach(sr -> vb.getChildren().add(new Label(sr.date() + " — " + sr.desc())));
        });
    }

    private void showFacturesDialog(){
        Prestataire p = table.getSelectionModel().getSelectedItem();
        if(p==null){ alert("Sélectionnez un prestataire."); return; }

        Stage win = new Stage();
        win.setTitle("Factures — "+p.getNom());

        /* ====== TableView ====== */
        TableView<Facture> tv = new TableView<>();
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        TableColumn<Facture, Boolean> cPaye = new TableColumn<>("Réglée");
        cPaye.setCellValueFactory(new PropertyValueFactory<>("paye"));

        cPaye.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Boolean val, boolean empty) {
                super.updateItem(val, empty);
                if (empty || val == null) {
                    setText(null);
                    getStyleClass().removeAll("cell-paid","cell-unpaid");
                } else {
                    setText(val ? "\u2714" /*✓*/ : "\u2717" /*✗*/);
                    getStyleClass().removeAll("cell-paid","cell-unpaid");
                    getStyleClass().add(val ? "cell-paid" : "cell-unpaid");
                }
            }
        });

        tv.getColumns().addAll(
            col("Échéance","echeanceFr"),
            col("Description","description"),
            col("Montant","montant"),
            cPaye,
            col("Date paiement","datePaiementFr")
        );
        tv.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        tv.setRowFactory(tb -> {
            TableRow<Facture> row = new TableRow<>();
            row.setOnMouseClicked(ev -> {
                if(ev.getClickCount()==2 && !row.isEmpty()){
                    Facture f = row.getItem();
                    runAsync(() -> dao.setFacturePayee(f.getId(), !f.isPaye()),
                             () -> refreshFactures(p, tv));
                }
            });
            return row;
        });

        /* ====== boutons ====== */
        Button bAdd = new Button("Nouvelle facture");
        Button bToggle = new Button("Marquer réglée / attente");
        Button bClose = new Button("Fermer");

        bAdd.setOnAction(ev -> addFactureDialog(p, tv));
        bToggle.setOnAction(ev -> {
            Facture f = tv.getSelectionModel().getSelectedItem();
            if(f!=null){
                runAsync(() -> dao.setFacturePayee(f.getId(), !f.isPaye()),
                         () -> refreshFactures(p, tv));
            }
        });
        bClose.setOnAction(ev -> win.close());

        HBox buttons = new HBox(10,bAdd,bToggle,bClose);
        buttons.setPadding(new Insets(10));

        VBox root = new VBox(10, tv, buttons);
        root.setPadding(new Insets(10));
        win.setScene(new Scene(root, 600, 400));
        win.initModality(Modality.WINDOW_MODAL);
        win.show();

        /* charge les données */
        refreshFactures(p, tv);
    }

    /* Helpers */
    private TableColumn<Facture,?> col(String title,String prop){
        TableColumn<Facture,?> c = new TableColumn<>(title);
        c.setCellValueFactory(new PropertyValueFactory<>(prop));
        return c;
    }
    private void refreshFactures(Prestataire p, TableView<Facture> tv){
        runAsync(() -> dao.factures(p.getId(), null),
                 list -> {
                     tv.setItems(FXCollections.observableArrayList(list));
                     refresh(search.getText());
                 });
    }

    private void addFactureDialog(Prestataire p, TableView<Facture> tv){
        Dialog<Facture> d = new Dialog<>();
        d.setTitle("Nouvelle facture – "+p.getNom());
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        GridPane gp = new GridPane(); gp.setHgap(8); gp.setVgap(8);
        DatePicker dpEch = new DatePicker(LocalDate.now());
        TextField tfDesc = new TextField();
        TextField tfMont = new TextField("0");

        gp.addRow(0,new Label("Échéance :"), dpEch);
        gp.addRow(1,new Label("Description :"), tfDesc);
        gp.addRow(2,new Label("Montant HT (€) :"), tfMont);

        d.getDialogPane().setContent(gp);

        d.setResultConverter(bt -> {
            if(bt==ButtonType.OK){
                return new Facture(0,p.getId(),tfDesc.getText(),
                                   dpEch.getValue(),
                                   Double.parseDouble(tfMont.getText()),
                                   false,null);
            }
            return null;
        });
        d.showAndWait().ifPresent(f ->
            runAsync(() -> dao.addFacture(f),
                     () -> refreshFactures(p, tv)));
    }

    private boolean confirm(String msg) {
        return new Alert(Alert.AlertType.CONFIRMATION, msg, ButtonType.YES, ButtonType.NO)
                .showAndWait().orElse(ButtonType.NO) == ButtonType.YES;
    }

    private void alert(String msg) {
        new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK).showAndWait();
    }

    public void shutdownExecutor() {
        executor.shutdownNow();
    }
}

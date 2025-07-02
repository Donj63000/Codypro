package org.example.gui;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.example.dao.DB;
import org.example.model.Prestataire;
import org.example.pdf.PDF;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

public class MainView {
    private static final DateTimeFormatter DATE_FR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final BorderPane root = new BorderPane();
    private final DB dao;

    private final TableView<Prestataire> table = new TableView<>();
    private final TextField search = new TextField();
    private final Label[] detailLabels = new Label[7];
    private final ProgressBar noteBar = new ProgressBar(0);

    public MainView(Stage stage, DB dao) {
        this.dao = dao;
        buildLayout(stage);
        refresh("");
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
        new Thread(task, "gui-bg").start();
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
        String[] cols = {"Nom","Société","Téléphone","Email","Note","Facturation","Date contrat"};
        String[] props = {"nom","societe","telephone","email","note","facturation","dateContrat"};
        for (int i = 0; i < cols.length; i++) {
            TableColumn<Prestataire, ?> c = new TableColumn<>(cols[i]);
            c.setCellValueFactory(new PropertyValueFactory<>(props[i]));
            table.getColumns().add(c);
        }
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
                    }, () -> new Alert(Alert.AlertType.INFORMATION, "Fiche PDF exportée", ButtonType.OK).showAndWait());
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
                }, () -> new Alert(Alert.AlertType.INFORMATION, "Historique PDF exporté", ButtonType.OK).showAndWait());
            }
        });

        HBox hb = new HBox(8, bAdd, bEdit, bDel, bService, bHist, bPDF, bPDFAll);
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
            fields[i] = new TextField(src == null ? "" : switch (i) {
                case 0 -> "";
                case 1 -> "";
                case 2 -> "";
                case 3 -> "";
                case 4 -> "0";
                case 5 -> "";
                default -> DATE_FR.format(LocalDate.now());
            });
            gp.add(fields[i], 1, i);
        }
        if (src != null) {
            fields[0].setText(src.getNom());
            fields[1].setText(src.getSociete());
            fields[2].setText(src.getTelephone());
            fields[3].setText(src.getEmail());
            fields[4].setText("" + src.getNote());
            fields[5].setText(src.getFacturation());
            fields[6].setText(src.getDateContrat());
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
                LocalDate.parse(fields[6].getText(), DATE_FR);
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

    private boolean confirm(String msg) {
        return new Alert(Alert.AlertType.CONFIRMATION, msg, ButtonType.YES, ButtonType.NO)
                .showAndWait().orElse(ButtonType.NO) == ButtonType.YES;
    }

    private void alert(String msg) {
        new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK).showAndWait();
    }
}

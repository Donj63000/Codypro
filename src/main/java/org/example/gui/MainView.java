package org.example.gui;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import java.math.BigDecimal;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.example.dao.DB;
import org.example.model.Facture;
import org.example.model.Prestataire;
import org.example.model.Rappel;
import org.example.pdf.PDF;
import org.example.mail.Mailer;
import org.example.mail.MailPrefs;
import org.example.dao.MailPrefsDAO;
import org.example.gui.MailQuickSetupDialog;
import org.example.gui.ThemeManager;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainView {
    private static final DateTimeFormatter DATE_FR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final BorderPane root = new BorderPane();
    private final DB dao;
    private final MailPrefsDAO mailPrefsDao;

    // Utilise un pool de travail pour éviter de bloquer l'interface
    // lorsqu'une tâche prend du temps (I/O, PDF, DAO...).
    private final ExecutorService executor = Executors.newWorkStealingPool();

    private final TableView<Prestataire> table = new TableView<>();
    private final TextField search = new TextField();
    private final Label[] detailLabels = new Label[7];
    private final ProgressBar noteBar = new ProgressBar(0);

    public MainView(Stage stage, DB dao, MailPrefsDAO prefsDao) {
        this.dao = dao;
        this.mailPrefsDao = prefsDao;
        buildLayout(stage);
        refresh("");
        stage.setOnCloseRequest(e -> executor.shutdown());
    }

    /**
     * Convenience constructor used by older code or tests that don't
     * explicitly provide a {@link MailPrefsDAO} instance.
     */
    public MainView(Stage stage, DB dao, javax.crypto.SecretKey key) {
        this(stage, dao, new MailPrefsDAO(dao, key));
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
            if (isAuthException(ex)) {
                mailPrefsDao.invalidateOAuth();
                Platform.runLater(() -> alert("Authentification expirée\u202f: merci de reconfigurer votre compte e-mail."));
            } else {
                Platform.runLater(() -> alert(ex == null ? "Erreur" : ex.getMessage()));
            }
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

    private static boolean isAuthException(Throwable ex) {
        while (ex != null) {
            if (ex instanceof jakarta.mail.AuthenticationFailedException ||
                ex instanceof com.google.api.client.auth.oauth2.TokenResponseException) {
                return true;
            }
            ex = ex.getCause();
        }
        return false;
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
        root.setPadding(new Insets(8));
    }

    private void createTable(Stage stage) {
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        TableColumn<Prestataire, String> cNom = new TableColumn<>("Nom");
        cNom.setCellValueFactory(new PropertyValueFactory<>("nom"));

        TableColumn<Prestataire, String> cSociete = new TableColumn<>("Société");
        cSociete.setCellValueFactory(new PropertyValueFactory<>("societe"));

        TableColumn<Prestataire, String> cTel = new TableColumn<>("Téléphone");
        cTel.setCellValueFactory(new PropertyValueFactory<>("telephone"));

        TableColumn<Prestataire, String> cMail = new TableColumn<>("Email");
        cMail.setCellValueFactory(new PropertyValueFactory<>("email"));

        TableColumn<Prestataire, Integer> cNote = new TableColumn<>("Note");
        cNote.setCellValueFactory(new PropertyValueFactory<>("note"));

        TableColumn<Prestataire, String> cDate = new TableColumn<>("Date contrat");
        cDate.setCellValueFactory(new PropertyValueFactory<>("dateContrat"));

        table.getColumns().addAll(cNom, cSociete, cTel, cMail, cNote, cDate);

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
        VBox v = new VBox(10);
        v.setId("detail-pane");
        v.setPadding(new Insets(12));

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
        Button bPrefsMail = new Button("Mail…");
        Button bMailPrefs = bPrefsMail;

        bAdd.getStyleClass().add("accent");
        bFact.getStyleClass().add("accent");
        bMailPrefs.getStyleClass().add("accent");

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

        bPrefsMail.setOnAction(e -> MailQuickSetupDialog.open(stage, mailPrefsDao));
        HBox hb = new HBox(16, bAdd, bEdit, bDel, bService, bHist, bFact, bPDF, bPDFAll, bPrefsMail);
        hb.setPadding(new Insets(10));
        hb.setAlignment(Pos.CENTER_LEFT);
        return hb;
    }

    private void editDialog(Prestataire src) {
        Dialog<Prestataire> d = new Dialog<>();
        ThemeManager.apply(d);
        d.setTitle(src == null ? "Nouveau Prestataire" : "Modifier Prestataire");
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        GridPane gp = new GridPane();
        gp.setHgap(8);
        gp.setVgap(8);
        gp.setPadding(new Insets(12));
        String[] lab = {"Nom","Société","Téléphone","Email","Note (0-100)","Facturation","Date contrat (dd/MM/yyyy)"};
        TextField[] fields = new TextField[lab.length];
        Pattern mailRegex = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$");

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
                if (nom.contains("\n") || nom.contains("\"")) {
                    throw new IllegalArgumentException("Caractère interdit dans le nom");
                }
                checkUserInput(nom, "nom");

                if (!fields[3].getText().isBlank() && !mailRegex.matcher(fields[3].getText()).matches())
                    throw new IllegalArgumentException("Email invalide.");

                for (int i : new int[]{1,2,3,5,6}) {
                    checkUserInput(fields[i].getText(), lab[i].toLowerCase());
                }

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
        ThemeManager.apply(td);
        td.setTitle("Nouveau service");
        td.setHeaderText("Description du service");
        td.showAndWait().ifPresent(desc -> {
            if (!desc.isBlank()) {
                try {
                    checkUserInput(desc, "description");
                    runAsync(() -> dao.addService(p.getId(), desc), null);
                } catch (Exception e) {
                    alert(e.getMessage());
                }
            }
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

        Label title = new Label("Historique — " + p.getNom());
        title.getStyleClass().add("title");

        VBox vb = new VBox(6, title);
        vb.setPadding(new Insets(12));
        vb.getChildren().add(new Label("Chargement..."));
        Scene sc = new Scene(new ScrollPane(vb), 400, 400);
        ThemeManager.apply(sc);
        win.setScene(sc);
        win.initModality(Modality.WINDOW_MODAL);
        win.show();
        runAsync(() -> dao.services(p.getId()), list -> {
            vb.getChildren().setAll(title);
            list.forEach(sr -> vb.getChildren().add(new Label(sr.date() + " — " + sr.desc())));
        });
    }

    private void showFacturesDialog(){
        Prestataire p = table.getSelectionModel().getSelectedItem();
        if(p==null){ alert("Sélectionnez un prestataire."); return; }

        Stage win = new Stage();
        win.setTitle("Factures — "+p.getNom());

        Label title = new Label("Factures — "+p.getNom());
        title.getStyleClass().add("title");

        /* ====== TableView ====== */
        TableView<Facture> tv = new TableView<>();
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        Label totalTtc = new Label();
        totalTtc.setStyle("-fx-font-weight:bold");
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

        TableColumn<Facture, String> cEcheance = new TableColumn<>("Échéance");
        cEcheance.setCellValueFactory(new PropertyValueFactory<>("echeanceFr"));

        TableColumn<Facture, String> cDescription = new TableColumn<>("Description");
        cDescription.setCellValueFactory(new PropertyValueFactory<>("description"));

        TableColumn<Facture, BigDecimal> cMontantHt = new TableColumn<>("HT");
        cMontantHt.setCellValueFactory(new PropertyValueFactory<>("montantHt"));

        TableColumn<Facture, BigDecimal> cTvaPct = new TableColumn<>("TVA %");
        cTvaPct.setCellValueFactory(new PropertyValueFactory<>("tvaPct"));

        TableColumn<Facture, BigDecimal> cMontant = new TableColumn<>("TTC");
        cMontant.setCellValueFactory(new PropertyValueFactory<>("montantTtc"));

        TableColumn<Facture, String> cDatePay = new TableColumn<>("Date paiement");
        cDatePay.setCellValueFactory(new PropertyValueFactory<>("datePaiementFr"));

        tv.getColumns().addAll(cEcheance, cDescription, cMontantHt, cTvaPct,
                               cMontant, cPaye, cDatePay);
        tv.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        tv.setRowFactory(tb -> {
            TableRow<Facture> row = new TableRow<>();
            row.setOnMouseClicked(ev -> {
                if(ev.getClickCount()==2 && !row.isEmpty()){
                    Facture f = row.getItem();
                    runAsync(() -> dao.setFacturePayee(f.getId(), !f.isPaye()),
                             () -> refreshFactures(p, tv, totalTtc));
                }
            });
            return row;
        });

        /* ====== boutons ====== */
        Button bAdd = new Button("Nouvelle facture");
        Button bMail = new Button("Rappel e-mail");
        Button bToggle = new Button("Marquer réglée / attente");
        Button bClose = new Button("Fermer");

        bAdd.setOnAction(ev -> addFactureDialog(p, tv, totalTtc));
        bMail.setOnAction(ev -> {
            Facture f = tv.getSelectionModel().getSelectedItem();
            if(f==null){ alert("Sélectionnez une facture."); return; }
            if(f.isPaye()){ alert("La facture est déjà réglée."); return; }
            afficherDialogRappel(p, f);
        });
        bToggle.setOnAction(ev -> {
            Facture f = tv.getSelectionModel().getSelectedItem();
            if(f!=null){
                runAsync(() -> dao.setFacturePayee(f.getId(), !f.isPaye()),
                         () -> refreshFactures(p, tv, totalTtc));
            }
        });
        bClose.setOnAction(ev -> win.close());

        HBox buttons = new HBox(10,bAdd,bToggle,bClose);
        buttons.setPadding(new Insets(10));
        buttons.getChildren().add(1, bMail); // après "Nouvelle facture"

        VBox root = new VBox(10, title, tv, totalTtc, buttons);
        root.setPadding(new Insets(12));
        Scene sc = new Scene(root, 600, 400);
        ThemeManager.apply(sc);
        win.setScene(sc);
        win.initModality(Modality.WINDOW_MODAL);
        win.show();

        /* charge les données */
        refreshFactures(p, tv, totalTtc);
    }

    /* Helpers */
    private void refreshFactures(Prestataire p, TableView<Facture> tv, Label lbl){
        runAsync(() -> dao.factures(p.getId(), null),
                 list -> {
                     tv.setItems(FXCollections.observableArrayList(list));
                     BigDecimal sum = list.stream()
                            .map(Facture::getMontantTtc)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                     lbl.setText(String.format("Total TTC : %.2f €", sum));
                     refresh(search.getText());
                 });
    }

    private void addFactureDialog(Prestataire p, TableView<Facture> tv, Label lbl){
        Dialog<Facture> d = new Dialog<>();
        ThemeManager.apply(d);
        d.setTitle("Nouvelle facture – "+p.getNom());
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        GridPane gp = new GridPane(); gp.setHgap(8); gp.setVgap(8); gp.setPadding(new Insets(12));
        DatePicker dpEch = new DatePicker(LocalDate.now());
        TextField tfDesc = new TextField();
        TextField tfMont = new TextField("0");
        TextField tfTva = new TextField("20");
        Label lblTtc = new Label();

        DoubleBinding ttcBinding = Bindings.createDoubleBinding(() -> {
            try {
                double ht  = Double.parseDouble(tfMont.getText());
                double pct = Double.parseDouble(tfTva.getText());
                return ht * (1 + pct/100.0);
            } catch (Exception ex) {
                return 0.0;
            }
        }, tfMont.textProperty(), tfTva.textProperty());
        lblTtc.textProperty().bind(Bindings.format("%.2f", ttcBinding));

        gp.addRow(0,new Label("Échéance :"), dpEch);
        gp.addRow(1,new Label("Description :"), tfDesc);
        gp.addRow(2,new Label("Montant HT (€) :"), tfMont);
        gp.addRow(3,new Label("TVA (%) :"), tfTva);
        gp.addRow(4,new Label("TTC (€) :"), lblTtc);

        d.getDialogPane().setContent(gp);

        Button ok = (Button) d.getDialogPane().lookupButton(ButtonType.OK);
        ok.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
            try {
                checkUserInput(tfDesc.getText(), "description");
                new BigDecimal(tfMont.getText());
                new BigDecimal(tfTva.getText());
            } catch (Exception e) {
                alert(e.getMessage());
                ev.consume();
            }
        });

        d.setResultConverter(bt -> {
            if(bt==ButtonType.OK){
                BigDecimal ht  = new BigDecimal(tfMont.getText());
                BigDecimal pct = new BigDecimal(tfTva.getText());
                BigDecimal mtva = ht.multiply(pct).divide(BigDecimal.valueOf(100));
                BigDecimal ttc  = ht.add(mtva);
                return new Facture(0,p.getId(),tfDesc.getText(),
                                   dpEch.getValue(),
                                   ht,pct,mtva,ttc,
                                   false,null,false);
            }
            return null;
        });
        d.showAndWait().ifPresent(f ->
            runAsync(() -> dao.addFacture(f),
                     () -> refreshFactures(p, tv, lbl)));
    }

    private void afficherDialogRappel(Prestataire pr, Facture f){
        Dialog<Void> dlg = new Dialog<>();
        ThemeManager.apply(dlg);
        dlg.setTitle("Rappel – "+pr.getNom());

        GridPane gp = new GridPane(); gp.setHgap(8); gp.setVgap(10); gp.setPadding(new Insets(12));
        TextField tfDest  = new TextField(pr.getEmail());
        TextField tfSujet = new TextField("Rappel de paiement – facture du "+f.getEcheanceFr());
        TextArea  taCorps = new TextArea("""
        Bonjour %NOM%,

        Nous n'avons pas encore reçu le règlement de votre facture de %MONTANT% € (échéance %ECHEANCE%).

        Merci de procéder au paiement ou de nous tenir informés.

        Cordialement.
    """.replace("%NOM%", pr.getNom())
       .replace("%MONTANT%", String.format("%.2f",f.getMontantTtc()))
       .replace("%ECHEANCE%", f.getEcheanceFr())
    );
        taCorps.setPrefRowCount(8);

        DatePicker dpDate = new DatePicker(f.getEcheance());
        CheckBox   cbNow  = new CheckBox("Envoyer immédiatement");
        cbNow.setSelected(true);
        cbNow.selectedProperty().addListener((o,b,b2)->dpDate.setDisable(b2));

        gp.addRow(0,new Label("Destinataire :"), tfDest);
        gp.addRow(1,new Label("Sujet :"),        tfSujet);
        gp.addRow(2,new Label("Message :"),      taCorps);
        gp.addRow(3, cbNow, new Label("… sinon à la date :")); gp.add(dpDate,2,3);

        dlg.getDialogPane().setContent(gp);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Button ok = (Button) dlg.getDialogPane().lookupButton(ButtonType.OK);
        ok.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
            try {
                checkUserInput(tfDest.getText(), "destinataire");
                checkUserInput(tfSujet.getText(), "sujet");
            } catch (Exception e) {
                alert(e.getMessage());
                ev.consume();
            }
        });

        dlg.setResultConverter(bt -> {
            if(bt==ButtonType.OK){
                MailPrefs cfg = mailPrefsDao.load();     // charger la config
                if(cbNow.isSelected()){
                    runAsync(() -> {
                        Map<String,String> v = Mailer.vars(pr,f);
                        Mailer.send(mailPrefsDao, cfg,
                                    tfDest.getText(),
                                    Mailer.subjToPresta(cfg,v),
                                    taCorps.getText());     // ici on ne ré‑injecte pas, l’user a déjà édité
                        return null;
                    }, _v -> alert("E‑mail envoyé."));
                }else{
                    LocalDateTime when = dpDate.getValue().atTime(8,0); // 8h par défaut
                    Rappel r = new Rappel(0, f.getId(),
                            tfDest.getText(), tfSujet.getText(),
                            taCorps.getText(), when, false);
                    runAsync( () -> dao.addRappel(r),
                              () -> alert("Rappel enregistré pour le "+when.toLocalDate()) );
                }
            }
            return null;
        });
        dlg.showAndWait();
    }

    private boolean confirm(String msg) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, msg, ButtonType.YES, ButtonType.NO);
        ThemeManager.apply(a);
        return a.showAndWait().orElse(ButtonType.NO) == ButtonType.YES;
    }

    private void alert(String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        ThemeManager.apply(a);
        a.showAndWait();
    }

    private static void checkUserInput(String val, String champ) {
        if (val == null) return;
        if (val.contains("\n") || val.contains("\"")) {
            throw new IllegalArgumentException("Caractère interdit dans le " + champ);
        }
        for (int i = 0; i < val.length(); i++) {
            if (val.charAt(i) < 32) {
                throw new IllegalArgumentException("Caractère interdit dans le " + champ);
            }
        }
    }

    public void shutdownExecutor() {
        executor.shutdownNow();
    }
}

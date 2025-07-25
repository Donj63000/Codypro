package org.example.gui;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.example.dao.DB;
import org.example.dao.MailPrefsDAO;
import org.example.mail.Mailer;
import org.example.mail.MailPrefs;
import org.example.model.Facture;
import org.example.model.Prestataire;
import org.example.model.Rappel;
import org.example.pdf.PDF;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public final class MainView {
    private static final DateTimeFormatter DATE_FR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final BorderPane root = new BorderPane();
    private final DB dao;
    private final MailPrefsDAO mailPrefsDao;
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

    public MainView(Stage stage, DB dao, javax.crypto.SecretKey key) {
        this(stage, dao, new MailPrefsDAO(dao, key));
    }

    public Parent getRoot() {
        return root;
    }

    private <T> void runAsync(Callable<T> work, Consumer<T> ui) {
        javafx.concurrent.Task<T> t = new javafx.concurrent.Task<>() {
            @Override protected T call() throws Exception { return work.call(); }
        };
        t.setOnSucceeded(e -> Platform.runLater(() -> ui.accept(t.getValue())));
        t.setOnFailed(e -> {
            Throwable ex = t.getException();
            if (ex != null) ex.printStackTrace();
            if (isAuthException(ex)) {
                mailPrefsDao.invalidateOAuth();
                Platform.runLater(() -> alert("Authentification expirée : reconfigurez le compte e‑mail."));
            } else {
                Platform.runLater(() -> alert(ex == null ? "Erreur" : ex.getMessage()));
            }
        });
        executor.submit(t);
    }

    private void runAsync(Runnable work, Runnable ui) {
        runAsync(() -> { work.run(); return null; }, v -> { if (ui != null) ui.run(); });
    }

    private static boolean isAuthException(Throwable ex) {
        while (ex != null) {
            if (ex instanceof jakarta.mail.AuthenticationFailedException ||
                    ex instanceof com.google.api.client.auth.oauth2.TokenResponseException) return true;
            ex = ex.getCause();
        }
        return false;
    }

    private void buildLayout(Stage stage) {
        HBox top = new HBox(10, new Label("Recherche : "), search);
        top.setPadding(new Insets(10));
        search.textProperty().addListener((o, p, n) -> refresh(n));
        createTable();
        VBox right = buildDetailPane(); right.setPrefWidth(320);
        HBox bottom = buildBottomBar(stage);
        root.setTop(top); root.setCenter(table); root.setRight(right); root.setBottom(bottom);
        root.setPadding(new Insets(8));
    }

    private void createTable() {
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

        TableColumn<Prestataire, Boolean> cOk = new TableColumn<>("Toutes factures payées");
        cOk.setCellValueFactory(d -> new ReadOnlyBooleanWrapper(d.getValue().getImpayes() == 0));
        cOk.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Boolean v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) {
                    setText(null); getStyleClass().removeAll("cell-paid","cell-unpaid");
                } else {
                    setText(v ? "✓" : "✗");
                    getStyleClass().removeAll("cell-paid","cell-unpaid");
                    getStyleClass().add(v ? "cell-paid" : "cell-unpaid");
                }
            }
        });

        table.getColumns().addAll(cNom, cSociete, cTel, cMail, cNote, cDate, cOk);
        table.getSelectionModel().selectedItemProperty().addListener((o, p, n) -> updateDetails(n));
        table.setPrefWidth(580);
    }

    private void refresh(String f) {
        runAsync(() -> dao.list(f), list -> {
            table.setItems(FXCollections.observableArrayList(list));
            updateDetails(null);
        });
    }

    private VBox buildDetailPane() {
        VBox v = new VBox(10); v.setId("detail-pane"); v.setPadding(new Insets(12));
        String[] lab = {"Nom", "Société", "Téléphone", "Email", "Note", "Facturation", "Date contrat"};
        for (int i = 0; i < lab.length; i++) {
            Label key = new Label(lab[i] + " :"); key.setStyle("-fx-font-weight:bold");
            detailLabels[i] = new Label();
            v.getChildren().add(new HBox(6, key, detailLabels[i]));
        }
        noteBar.setPrefWidth(200); v.getChildren().add(noteBar);
        return v;
    }

    private void updateDetails(Prestataire p) {
        if (p == null) {
            Arrays.stream(detailLabels).forEach(l -> l.setText("")); noteBar.setProgress(0); return;
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
        Button bServ = new Button("Ajout service");
        Button bHist = new Button("Historique");
        Button bFact = new Button("Factures");
        Button bPDF = new Button("Fiche PDF");
        Button bPDFAll = new Button("PDF global");
        Button bMail = new Button("Mail…");
        bAdd.getStyleClass().add("accent"); bFact.getStyleClass().add("accent"); bMail.getStyleClass().add("accent");

        bAdd.setOnAction(e -> editDialog(null));
        bEdit.setOnAction(e -> editDialog(table.getSelectionModel().getSelectedItem()));
        bDel.setOnAction(e -> {
            Prestataire p = table.getSelectionModel().getSelectedItem();
            if (p != null && confirm("Supprimer " + p.getNom() + " ?"))
                runAsync(() -> dao.delete(p.getId()), () -> refresh(search.getText()));
        });
        bServ.setOnAction(e -> addServiceDialog());
        bHist.setOnAction(e -> showHistoryDialog());
        bFact.setOnAction(e -> showFacturesDialog());
        bPDF.setOnAction(e -> exportFiche(stage));
        bPDFAll.setOnAction(e -> exportHistorique(stage));
        bMail.setOnAction(e -> MailQuickSetupDialog.open(stage, mailPrefsDao));

        HBox bar = new HBox(16, bAdd, bEdit, bDel, bServ, bHist, bFact, bPDF, bPDFAll, bMail);
        bar.setPadding(new Insets(10)); bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private void exportFiche(Stage stage) {
        Prestataire p = table.getSelectionModel().getSelectedItem();
        if (p == null) return;
        FileChooser fc = new FileChooser();
        fc.setInitialFileName(p.getNom() + "_fiche.pdf");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        Path f = Optional.ofNullable(fc.showSaveDialog(stage)).map(java.io.File::toPath).orElse(null);
        if (f != null) {
            runAsync(() -> { PDF.generateFiche(f, p); return null; },
                    v -> alertInfo("Fiche PDF exportée"));
        }
    }

    private void exportHistorique(Stage stage) {
        FileChooser fc = new FileChooser();
        fc.setInitialFileName("Historique_global_prestataires.pdf");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        Path f = Optional.ofNullable(fc.showSaveDialog(stage)).map(java.io.File::toPath).orElse(null);
        if (f != null) {
            runAsync(() -> { PDF.generateHistorique(f, dao); return null; },
                    v -> alertInfo("Historique PDF exporté"));
        }
    }

    private void editDialog(Prestataire src) {
        Dialog<Prestataire> d = new Dialog<>(); ThemeManager.apply(d);
        d.setTitle(src == null ? "Nouveau Prestataire" : "Modifier Prestataire");
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        GridPane gp = new GridPane(); gp.setHgap(8); gp.setVgap(8); gp.setPadding(new Insets(12));
        String[] lab = {"Nom","Société","Téléphone","Email","Note (0-100)","Facturation","Date contrat (dd/MM/yyyy)"};
        TextField[] f = new TextField[lab.length];
        Pattern mailRx = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$");

        for (int i = 0; i < lab.length; i++) {
            gp.add(new Label(lab[i] + " :"), 0, i);
            f[i] = new TextField();
            switch (i) {
                case 0 -> f[i].setText(src == null ? "" : src.getNom());
                case 1 -> f[i].setText(src == null ? "" : src.getSociete());
                case 2 -> f[i].setText(src == null ? "" : src.getTelephone());
                case 3 -> f[i].setText(src == null ? "" : src.getEmail());
                case 4 -> f[i].setText(src == null ? "0" : String.valueOf(src.getNote()));
                case 5 -> f[i].setText(src == null ? "" : src.getFacturation());
                case 6 -> f[i].setText(src == null ? DATE_FR.format(LocalDate.now()) : src.getDateContrat());
            }
            gp.add(f[i], 1, i);
        }
        d.getDialogPane().setContent(gp);

        Button ok = (Button) d.getDialogPane().lookupButton(ButtonType.OK);
        ok.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
            try {
                String nom = f[0].getText().trim();
                if (nom.isEmpty()) throw new IllegalArgumentException("Nom obligatoire.");
                if (nom.contains("\n") || nom.contains("\"")) throw new IllegalArgumentException("Caractère interdit dans le nom");
                checkUserInput(nom, "nom");
                if (!f[3].getText().isBlank() && !mailRx.matcher(f[3].getText()).matches())
                    throw new IllegalArgumentException("Email invalide.");
                for (int i : new int[]{1,2,3,5,6}) checkUserInput(f[i].getText(), lab[i].toLowerCase());
                int note = Integer.parseInt(f[4].getText());
                if (note < 0 || note > 100) throw new IllegalArgumentException("Note 0‑100.");
                String dt = f[6].getText().trim();
                if (dt.isEmpty()) throw new IllegalArgumentException("Date obligatoire.");
                LocalDate.parse(dt, DATE_FR);
            } catch (Exception e) {
                alert(e.getMessage()); ev.consume();
            }
        });

        d.setResultConverter(bt -> bt == ButtonType.OK ?
                new Prestataire(src == null ? 0 : src.getId(),
                        f[0].getText(), f[1].getText(), f[2].getText(),
                        f[3].getText(), Integer.parseInt(f[4].getText()),
                        f[5].getText(), f[6].getText()) : null);
        d.showAndWait().ifPresent(p -> runAsync(() -> {
            if (src == null) dao.add(p.copyWithoutId()); else dao.update(p);
        }, () -> refresh(search.getText())));
    }

    private void addServiceDialog() {
        Prestataire p = table.getSelectionModel().getSelectedItem();
        if (p == null) { alert("Sélectionnez un prestataire."); return; }
        TextInputDialog td = new TextInputDialog(); ThemeManager.apply(td);
        td.setTitle("Nouveau service"); td.setHeaderText("Description du service");
        td.showAndWait().ifPresent(desc -> {
            if (!desc.isBlank()) {
                try { checkUserInput(desc, "description"); }
                catch (Exception e) { alert(e.getMessage()); return; }
                runAsync(() -> dao.addService(p.getId(), desc), null);
            }
        });
    }

    private void showHistoryDialog() {
        Prestataire p = table.getSelectionModel().getSelectedItem();
        if (p == null) { alert("Sélectionnez un prestataire."); return; }
        Stage w = new Stage();
        w.setTitle("Historique — " + p.getNom());
        Label title = new Label("Historique — " + p.getNom()); title.getStyleClass().add("title");
        VBox vb = new VBox(6, title); vb.setPadding(new Insets(12));
        vb.getChildren().add(new Label("Chargement..."));
        Scene sc = new Scene(new ScrollPane(vb), 400, 400); ThemeManager.apply(sc);
        w.setScene(sc); w.initModality(Modality.WINDOW_MODAL); w.show();
        runAsync(() -> dao.services(p.getId()), list -> {
            vb.getChildren().setAll(title);
            list.forEach(sr -> vb.getChildren().add(new Label(sr.date() + " — " + sr.desc())));
        });
    }

    private void showFacturesDialog() {
        Prestataire p = table.getSelectionModel().getSelectedItem();
        if (p == null) { alert("Sélectionnez un prestataire."); return; }

        Stage w = new Stage(); w.setTitle("Factures — " + p.getNom());
        Label title = new Label("Factures — " + p.getNom()); title.getStyleClass().add("title");

        TableView<Facture> tv = new TableView<>(); tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        Label total = new Label(); total.setStyle("-fx-font-weight:bold");

        TableColumn<Facture, Boolean> cPay = new TableColumn<>("Réglée");
        cPay.setCellValueFactory(new PropertyValueFactory<>("paye"));
        cPay.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Boolean v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) {
                    setText(null); getStyleClass().removeAll("cell-paid","cell-unpaid");
                } else {
                    setText(v ? "✓" : "✗");
                    getStyleClass().removeAll("cell-paid","cell-unpaid");
                    getStyleClass().add(v ? "cell-paid" : "cell-unpaid");
                }
            }
        });

        TableColumn<Facture, String> cEch = new TableColumn<>("Échéance"); cEch.setCellValueFactory(new PropertyValueFactory<>("echeanceFr"));
        TableColumn<Facture, String> cDes = new TableColumn<>("Description"); cDes.setCellValueFactory(new PropertyValueFactory<>("description"));
        TableColumn<Facture, BigDecimal> cHt = new TableColumn<>("HT"); cHt.setCellValueFactory(new PropertyValueFactory<>("montantHt"));
        TableColumn<Facture, BigDecimal> cPct = new TableColumn<>("TVA %"); cPct.setCellValueFactory(new PropertyValueFactory<>("tvaPct"));
        TableColumn<Facture, BigDecimal> cTtc = new TableColumn<>("TTC"); cTtc.setCellValueFactory(new PropertyValueFactory<>("montantTtc"));
        TableColumn<Facture, String> cPayDt = new TableColumn<>("Date paiement"); cPayDt.setCellValueFactory(new PropertyValueFactory<>("datePaiementFr"));
        tv.getColumns().addAll(cEch, cDes, cHt, cPct, cTtc, cPay, cPayDt);

        tv.setRowFactory(tb -> {
            TableRow<Facture> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount()==2 && !row.isEmpty()) {
                    Facture f = row.getItem();
                    runAsync(() -> dao.setFacturePayee(f.getId(), !f.isPaye()),
                            () -> refreshFactures(p, tv, total));
                }
            });
            return row;
        });

        Button bAdd = new Button("Nouvelle facture");
        Button bMail = new Button("Rappel e‑mail");
        Button bToggle = new Button("Marquer réglée / attente");
        Button bClose = new Button("Fermer");

        bAdd.setOnAction(e -> addFactureDialog(p, tv, total));
        bMail.setOnAction(e -> {
            Facture f = tv.getSelectionModel().getSelectedItem();
            if (f == null) { alert("Sélectionnez une facture."); return; }
            if (f.isPaye()) { alert("La facture est déjà réglée."); return; }
            afficherDialogRappel(p, f);
        });
        bToggle.setOnAction(e -> {
            Facture f = tv.getSelectionModel().getSelectedItem();
            if (f != null) runAsync(() -> dao.setFacturePayee(f.getId(), !f.isPaye()),
                    () -> refreshFactures(p, tv, total));
        });
        bClose.setOnAction(e -> w.close());

        HBox btns = new HBox(10, bAdd, bMail, bToggle, bClose); btns.setPadding(new Insets(10));
        VBox root = new VBox(10, title, tv, total, btns); root.setPadding(new Insets(12));
        Scene sc = new Scene(root, 600, 400); ThemeManager.apply(sc);
        w.setScene(sc); w.initModality(Modality.WINDOW_MODAL); w.show();

        refreshFactures(p, tv, total);
    }

    private void refreshFactures(Prestataire p, TableView<Facture> tv, Label lbl) {
        runAsync(() -> dao.factures(p.getId(), null), list -> {
            tv.setItems(FXCollections.observableArrayList(list));
            BigDecimal sum = list.stream().map(Facture::getMontantTtc).reduce(BigDecimal.ZERO, BigDecimal::add);
            lbl.setText(String.format("Total TTC : %.2f €", sum));
            refresh(search.getText());
        });
    }

    private void addFactureDialog(Prestataire p, TableView<Facture> tv, Label lbl) {
        Dialog<Facture> d = new Dialog<>(); ThemeManager.apply(d);
        d.setTitle("Nouvelle facture – " + p.getNom());
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        GridPane gp = new GridPane(); gp.setHgap(8); gp.setVgap(8); gp.setPadding(new Insets(12));
        DatePicker dp = new DatePicker(LocalDate.now());
        TextField tfDes = new TextField();
        TextField tfHt = new TextField("0");
        TextField tfPct = new TextField("20");
        Label lTtc = new Label();

        DoubleBinding ttc = Bindings.createDoubleBinding(() -> {
            try { return Double.parseDouble(tfHt.getText())*(1+Double.parseDouble(tfPct.getText())/100); }
            catch(Exception e){ return 0.0; }
        }, tfHt.textProperty(), tfPct.textProperty());
        lTtc.textProperty().bind(Bindings.format("%.2f", ttc));

        gp.addRow(0,new Label("Échéance :"), dp);
        gp.addRow(1,new Label("Description :"), tfDes);
        gp.addRow(2,new Label("Montant HT (€) :"), tfHt);
        gp.addRow(3,new Label("TVA (%) :"), tfPct);
        gp.addRow(4,new Label("TTC (€) :"), lTtc);

        d.getDialogPane().setContent(gp);

        Button ok = (Button) d.getDialogPane().lookupButton(ButtonType.OK);
        ok.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
            try {
                checkUserInput(tfDes.getText(), "description");
                new BigDecimal(tfHt.getText()); new BigDecimal(tfPct.getText());
            } catch(Exception e){ alert(e.getMessage()); ev.consume(); }
        });

        d.setResultConverter(bt -> bt == ButtonType.OK ? buildFacture(p, dp.getValue(), tfDes.getText(), tfHt.getText(), tfPct.getText()) : null);
        d.showAndWait().ifPresent(f -> runAsync(() -> dao.addFacture(f), () -> refreshFactures(p, tv, lbl)));
    }

    private static Facture buildFacture(Prestataire p, LocalDate d, String desc, String sHt, String sPct) {
        BigDecimal ht = new BigDecimal(sHt); BigDecimal pct = new BigDecimal(sPct);
        BigDecimal tva = ht.multiply(pct).divide(BigDecimal.valueOf(100));
        BigDecimal ttc = ht.add(tva);
        return new Facture(0, p.getId(), desc, d, ht, pct, tva, ttc, false, null, false);
    }

    private void afficherDialogRappel(Prestataire pr, Facture f) {
        Dialog<Void> dlg = new Dialog<>(); ThemeManager.apply(dlg);
        dlg.setTitle("Rappel – " + pr.getNom());

        GridPane gp = new GridPane(); gp.setHgap(8); gp.setVgap(10); gp.setPadding(new Insets(12));
        TextField tfDest = new TextField(pr.getEmail());
        TextField tfSubj = new TextField("Rappel de paiement – facture du " + f.getEcheanceFr());
        TextArea taBody = new TextArea("""
        Bonjour %NOM%,

        Nous n'avons pas encore reçu le règlement de votre facture de %MONTANT% € (échéance %ECHEANCE%).

        Merci de procéder au paiement ou de nous tenir informés.

        Cordialement.
        """.replace("%NOM%", pr.getNom())
                .replace("%MONTANT%", String.format("%.2f", f.getMontantTtc()))
                .replace("%ECHEANCE%", f.getEcheanceFr()));
        taBody.setPrefRowCount(8);

        DatePicker dp = new DatePicker(f.getEcheance());
        CheckBox cbNow = new CheckBox("Envoyer immédiatement"); cbNow.setSelected(true);
        cbNow.selectedProperty().addListener((o,b,b2)->dp.setDisable(b2));

        gp.addRow(0,new Label("Destinataire :"), tfDest);
        gp.addRow(1,new Label("Sujet :"), tfSubj);
        gp.addRow(2,new Label("Message :"), taBody);
        gp.addRow(3, cbNow, new Label("… sinon à la date :")); gp.add(dp,2,3);

        dlg.getDialogPane().setContent(gp);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Button ok = (Button) dlg.getDialogPane().lookupButton(ButtonType.OK);
        ok.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
            try { checkUserInput(tfDest.getText(), "destinataire"); checkUserInput(tfSubj.getText(), "sujet"); }
            catch(Exception e){ alert(e.getMessage()); ev.consume(); }
        });

        dlg.setResultConverter(bt -> {
            if (bt == ButtonType.OK) {
                MailPrefs cfg = mailPrefsDao.load();
                if (cbNow.isSelected()) {
                    runAsync(() -> {
                        Map<String,String> v = Mailer.vars(pr,f);
                        Mailer.send(mailPrefsDao,cfg,tfDest.getText(),Mailer.subjToPresta(cfg,v),taBody.getText());
                        return null;
                    }, v -> alertInfo("E‑mail envoyé."));
                } else {
                    LocalDateTime when = dp.getValue().atTime(8,0);
                    Rappel r = new Rappel(0,f.getId(),tfDest.getText(),tfSubj.getText(),taBody.getText(),when,false);
                    runAsync(() -> { dao.addRappel(r); return null; },
                            v -> alertInfo("Rappel enregistré pour le " + when.toLocalDate()));
                }
            }
            return null;
        });
        dlg.showAndWait();
    }

    private boolean confirm(String m) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, m, ButtonType.YES, ButtonType.NO);
        ThemeManager.apply(a); return a.showAndWait().orElse(ButtonType.NO) == ButtonType.YES;
    }

    private void alert(String m) {
        Alert a = new Alert(Alert.AlertType.ERROR, m, ButtonType.OK);
        ThemeManager.apply(a); a.showAndWait();
    }

    private void alertInfo(String m) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, m, ButtonType.OK);
        ThemeManager.apply(a); a.showAndWait();
    }

    private static void checkUserInput(String v, String champ) {
        if (v == null) return;
        if (v.contains("\n") || v.contains("\"")) throw new IllegalArgumentException("Caractère interdit dans le " + champ);
        for (int i = 0; i < v.length(); i++) if (v.charAt(i) < 32) throw new IllegalArgumentException("Caractère interdit dans le " + champ);
    }

    public void shutdownExecutor() { executor.shutdownNow(); }
}

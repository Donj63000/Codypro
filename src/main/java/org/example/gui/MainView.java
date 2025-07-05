package org.example.gui;

import javafx.application.Platform;
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

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

public class MainView {

    /* ====================================================================== */
    /* ==========================   CHAMPS & INIT   ========================= */
    /* ====================================================================== */

    private static final DateTimeFormatter DATE_FR =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final BorderPane root = new BorderPane();
    private final DB          dao;
    private final MailPrefsDAO mailPrefsDao;

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor(r -> new Thread(r, "gui-bg"));

    private final TableView<Prestataire> table = new TableView<>();
    private final TextField              search = new TextField();
    private final Label[]                detailLabels = new Label[7];
    private final ProgressBar            noteBar = new ProgressBar(0);

    /* ---------------------------------------------------------------------- */
    /**  Constructeur principal : on reçoit *déjà* le MailPrefsDAO. */
    public MainView(Stage stage, DB dao, MailPrefsDAO mailPrefsDao) {
        this.dao          = dao;
        this.mailPrefsDao = mailPrefsDao;
        buildLayout(stage);
        refresh("");
        stage.setOnCloseRequest(e -> executor.shutdown());
    }

    /** Constructeur « legacy » pour les anciens appels/tests. */
    public MainView(Stage stage, DB dao) {
        this(stage, dao, new MailPrefsDAO(dao.getConnection()));
    }

    /* ====================================================================== */
    /* ============================   VUE ROOT   ============================ */
    /* ====================================================================== */

    public Parent getRoot() { return root; }

    /* Asynchronisme utilitaire ------------------------------------------------ */
    private <T> void runAsync(java.util.concurrent.Callable<T> work,
                              java.util.function.Consumer<T> ui) {

        javafx.concurrent.Task<T> task = new javafx.concurrent.Task<>() {
            @Override protected T call() throws Exception { return work.call(); }
        };
        task.setOnSucceeded(ev ->
                Platform.runLater(() -> ui.accept(task.getValue())));
        task.setOnFailed(ev -> {
            Throwable ex = task.getException();
            if (ex != null) ex.printStackTrace();
            Platform.runLater(() -> alert(ex == null ? "Erreur" : ex.getMessage()));
        });
        executor.submit(task);
    }
    private void runAsync(Runnable work, Runnable ui) {
        runAsync(() -> { work.run(); return null; },
                v -> { if (ui != null) ui.run(); });
    }

    /* ---------------------------------------------------------------------- */
    /* -----------------------------  LAYOUT  ------------------------------- */
    /* ---------------------------------------------------------------------- */

    private void buildLayout(Stage stage) {

        /* ---- barre de recherche ---- */
        HBox topBar = new HBox(10, new Label("Recherche : "), search);
        topBar.setPadding(new Insets(10));
        search.textProperty().addListener((o,oldV,newV) -> refresh(newV));

        /* ---- tableau et détails ---- */
        createTable();
        VBox right = buildDetailPane();
        right.setPrefWidth(320);

        /* ---- barre du bas ---- */
        HBox bottom = buildBottomBar(stage);

        root.setTop(topBar);
        root.setCenter(table);
        root.setRight(right);
        root.setBottom(bottom);
    }

    /* ---------- Table prestataires ---------- */
    private void createTable() {
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        String[] cols  = {"Nom","Société","Téléphone","Email","Note","Date contrat"};
        String[] props = {"nom","societe","telephone","email","note","dateContrat"};
        for (int i = 0; i < cols.length; i++) {
            TableColumn<Prestataire, ?> c = new TableColumn<>(cols[i]);
            c.setCellValueFactory(new PropertyValueFactory<>(props[i]));
            table.getColumns().add(c);
        }
        /* indicateur impayés */
        TableColumn<Prestataire,Boolean> cOk = new TableColumn<>("Toutes factures payées");
        cOk.setCellValueFactory(d -> new ReadOnlyBooleanWrapper(d.getValue().getImpayes()==0));
        cOk.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Boolean val, boolean empty) {
                super.updateItem(val, empty);
                if (empty || val==null) {
                    setText(null); getStyleClass().removeAll("cell-paid","cell-unpaid");
                } else {
                    setText(val ? "\u2714" : "\u2717");
                    getStyleClass().removeAll("cell-paid","cell-unpaid");
                    getStyleClass().add(val ? "cell-paid" : "cell-unpaid");
                }
            }
        });
        table.getColumns().add(cOk);

        table.getSelectionModel()
                .selectedItemProperty()
                .addListener((o,oldV,newV) -> updateDetails(newV));

        table.setPrefWidth(580);
    }

    /* ---------- Panneau de détails ---------- */
    private VBox buildDetailPane() {

        VBox v = new VBox(10);
        v.setId("detail-pane");
        v.setPadding(new Insets(10));

        String[] lab = {"Nom","Société","Téléphone","Email",
                "Note","Facturation","Date contrat"};
        for (int i=0;i<lab.length;i++){
            Label key = new Label(lab[i]+" :");
            key.setStyle("-fx-font-weight:bold");
            detailLabels[i] = new Label();
            v.getChildren().add(new HBox(6, key, detailLabels[i]));
        }
        noteBar.setPrefWidth(200);
        v.getChildren().add(noteBar);
        return v;
    }

    /* ---------- Barre de boutons ---------- */
    private HBox buildBottomBar(Stage stage) {

        Button bAdd   = new Button("Nouveau");
        Button bEdit  = new Button("Modifier");
        Button bDel   = new Button("Supprimer");
        Button bSvc   = new Button("Ajout service");
        Button bHist  = new Button("Historique");
        Button bFact  = new Button("Factures");
        Button bPDF   = new Button("Fiche PDF");
        Button bPDFAll= new Button("PDF global");
        Button bMailPrefs = new Button("Mail…");

        bAdd .getStyleClass().add("accent");
        bFact.getStyleClass().add("accent");

        /* actions ---------------------------------------------------------- */
        bAdd .setOnAction(e -> editDialog(null));
        bEdit.setOnAction(e -> editDialog(table.getSelectionModel().getSelectedItem()));
        bDel .setOnAction(e -> {
            Prestataire p = table.getSelectionModel().getSelectedItem();
            if (p!=null && confirm("Supprimer "+p.getNom()+" ?"))
                runAsync(() -> dao.delete(p.getId()),
                        () -> refresh(search.getText()));
        });

        bSvc .setOnAction(e -> addServiceDialog());
        bHist.setOnAction(e -> showHistoryDialog());
        bFact.setOnAction(e -> showFacturesDialog());

        bPDF .setOnAction(e -> exportFiche(stage));
        bPDFAll.setOnAction(e -> exportHistorique(stage));

        bMailPrefs.setOnAction(e -> MailPrefsDialog.open(stage, mailPrefsDao));

        HBox hb = new HBox(16,bAdd,bEdit,bDel,bSvc,bHist,bFact,bPDF,bPDFAll,bMailPrefs);
        hb.setPadding(new Insets(10));
        hb.setAlignment(Pos.CENTER_LEFT);
        return hb;
    }

    /* ====================================================================== */
    /* ====================   FONCTIONS D’AIDE / UI   ======================= */
    /* ====================================================================== */

    private void refresh(String filtre){
        runAsync(() -> dao.list(filtre),
                list -> { table.setItems(FXCollections.observableArrayList(list));
                    updateDetails(null); });
    }

    private void updateDetails(Prestataire p){
        if(p==null){
            Arrays.stream(detailLabels).forEach(l -> l.setText(""));
            noteBar.setProgress(0); return;
        }
        detailLabels[0].setText(p.getNom());
        detailLabels[1].setText(p.getSociete());
        detailLabels[2].setText(p.getTelephone());
        detailLabels[3].setText(p.getEmail());
        detailLabels[4].setText(p.getNote()+" %");
        detailLabels[5].setText(p.getFacturation());
        detailLabels[6].setText(p.getDateContrat());
        noteBar.setProgress(p.getNote()/100.0);
    }

    /* ------------------------------------------------------------------ */
    private void exportFiche(Stage stage){
        Prestataire p = table.getSelectionModel().getSelectedItem();
        if(p==null) return;
        FileChooser fc = new FileChooser();
        fc.setInitialFileName(p.getNom() + "_fiche.pdf");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF","*.pdf"));
        Path f = Optional.ofNullable(fc.showSaveDialog(stage))
                .map(java.io.File::toPath).orElse(null);
        if(f!=null)
            runAsync(() -> { PDF.generateFiche(f,p); return null; },
                    v -> info("Fiche PDF exportée."));
    }
    private void exportHistorique(Stage stage){
        FileChooser fc = new FileChooser();
        fc.setInitialFileName("Historique_global_prestataires.pdf");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF","*.pdf"));
        Path f = Optional.ofNullable(fc.showSaveDialog(stage))
                .map(java.io.File::toPath).orElse(null);
        if(f!=null)
            runAsync(() -> { PDF.generateHistorique(f,dao); return null; },
                    v -> info("Historique PDF exporté."));
    }

    /* ====================================================================== */
    /* =======================  DIALOGUES MÉTIERS  ========================= */
    /* ====================================================================== */

    /* ---------- CRUD prestataire ---------- */
    private void editDialog(Prestataire src){
        Dialog<Prestataire> d = new Dialog<>();
        d.setTitle(src==null ? "Nouveau Prestataire"
                : "Modifier Prestataire");
        d.getDialogPane().getButtonTypes()
                .addAll(ButtonType.OK, ButtonType.CANCEL);

        GridPane gp = new GridPane(); gp.setHgap(8); gp.setVgap(8);

        String[] lab = {"Nom","Société","Téléphone","Email",
                "Note (0-100)","Facturation","Date contrat (dd/MM/yyyy)"};
        TextField[] f = new TextField[lab.length];
        Pattern mailRegex = Pattern.compile("[^@]+@[^@]+\\.[^@]+");

        for(int i=0;i<lab.length;i++){
            gp.add(new Label(lab[i]+" :"),0,i);
            f[i]=new TextField();
            switch(i){
                case 0->f[i].setText(src==null?"":src.getNom());
                case 1->f[i].setText(src==null?"":src.getSociete());
                case 2->f[i].setText(src==null?"":src.getTelephone());
                case 3->f[i].setText(src==null?"":src.getEmail());
                case 4->f[i].setText(src==null?"0":String.valueOf(src.getNote()));
                case 5->f[i].setText(src==null?"":src.getFacturation());
                case 6->f[i].setText(src==null?DATE_FR.format(LocalDate.now())
                        :src.getDateContrat());
            }
            gp.add(f[i],1,i);
        }
        d.getDialogPane().setContent(gp);

        /* validation ---------------------------------------------------- */
        ((Button)d.getDialogPane().lookupButton(ButtonType.OK))
                .addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
                    try{
                        if(f[0].getText().trim().isEmpty())
                            throw new IllegalArgumentException("Nom obligatoire.");
                        if(!f[3].getText().isBlank() &&
                                !mailRegex.matcher(f[3].getText()).matches())
                            throw new IllegalArgumentException("Email invalide.");
                        int note = Integer.parseInt(f[4].getText());
                        if(note<0||note>100) throw new IllegalArgumentException("Note 0‑100.");
                        if(f[6].getText().trim().isEmpty())
                            throw new IllegalArgumentException("Date obligatoire.");
                        LocalDate.parse(f[6].getText(), DATE_FR);
                    }catch(Exception e){ alert(e.getMessage()); ev.consume(); }
                });

        d.setResultConverter(bt -> {
            if(bt!=ButtonType.OK) return null;
            return new Prestataire(src==null?0:src.getId(),
                    f[0].getText(), f[1].getText(), f[2].getText(),
                    f[3].getText(), Integer.parseInt(f[4].getText()),
                    f[5].getText(), f[6].getText());
        });

        d.showAndWait().ifPresent(p ->
                runAsync(() -> { if(src==null) dao.add(p.copyWithoutId()); else dao.update(p); },
                        () -> refresh(search.getText())));
    }

    /* ---------- Ajout service ---------- */
    private void addServiceDialog(){
        Prestataire p = table.getSelectionModel().getSelectedItem();
        if(p==null){ alert("Sélectionnez un prestataire."); return; }
        TextInputDialog td = new TextInputDialog();
        td.setTitle("Nouveau service");
        td.setHeaderText("Description du service");
        td.showAndWait()
                .filter(desc -> !desc.isBlank())
                .ifPresent(desc -> runAsync(() -> dao.addService(p.getId(),desc), null));
    }

    /* ---------- Historique ---------- */
    private void showHistoryDialog(){
        Prestataire p = table.getSelectionModel().getSelectedItem();
        if(p==null){ alert("Sélectionnez un prestataire."); return; }

        Stage win = new Stage(); win.setTitle("Historique — "+p.getNom());

        Label title = new Label("Historique — "+p.getNom());
        title.getStyleClass().add("title");

        VBox vb = new VBox(6,title);
        vb.setPadding(new Insets(10));
        vb.getChildren().add(new Label("Chargement…"));

        Scene sc = new Scene(new ScrollPane(vb),400,400);
        sc.getStylesheets().add(getClass().getResource("/css/dark.css").toExternalForm());
        win.setScene(sc); win.initModality(Modality.WINDOW_MODAL); win.show();

        runAsync(() -> dao.services(p.getId()),
                list -> { vb.getChildren().setAll(title);
                    list.forEach(sr ->
                            vb.getChildren().add(new Label(sr.date()+" — "+sr.desc())));});
    }

    /* ---------- Factures ---------- */
    private void showFacturesDialog(){
        Prestataire p = table.getSelectionModel().getSelectedItem();
        if(p==null){ alert("Sélectionnez un prestataire."); return; }

        Stage win = new Stage(); win.setTitle("Factures — "+p.getNom());
        Label title = new Label("Factures — "+p.getNom());
        title.getStyleClass().add("title");

        TableView<Facture> tv = new TableView<>();
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<Facture,Boolean> cPaye = new TableColumn<>("Réglée");
        cPaye.setCellValueFactory(new PropertyValueFactory<>("paye"));
        cPaye.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Boolean val, boolean empty){
                super.updateItem(val,empty);
                if(empty||val==null){ setText(null); getStyleClass().removeAll("cell-paid","cell-unpaid"); }
                else{
                    setText(val?"\u2714":"\u2717");
                    getStyleClass().removeAll("cell-paid","cell-unpaid");
                    getStyleClass().add(val?"cell-paid":"cell-unpaid");
                }
            }});

        tv.getColumns().addAll(
                col("Échéance"     ,"echeanceFr"),
                col("Description"  ,"description"),
                col("Montant"      ,"montant"),
                cPaye,
                col("Date paiement","datePaiementFr")
        );
        tv.setRowFactory(tb -> {
            TableRow<Facture> row = new TableRow<>();
            row.setOnMouseClicked(ev -> {
                if(ev.getClickCount()==2 && !row.isEmpty()){
                    Facture f = row.getItem();
                    runAsync(() -> dao.setFacturePayee(f.getId(), !f.isPaye()),
                            () -> refreshFactures(p,tv));
                }
            });
            return row;
        });

        Button bAdd  = new Button("Nouvelle facture");
        Button bMail = new Button("Rappel e-mail");
        Button bTog  = new Button("Marquer réglée / attente");
        Button bClose= new Button("Fermer");

        bAdd .setOnAction(e -> addFactureDialog(p,tv));
        bMail.setOnAction(e -> {
            Facture f = tv.getSelectionModel().getSelectedItem();
            if(f==null){ alert("Sélectionnez une facture."); return; }
            if(f.isPaye()){ alert("La facture est déjà réglée."); return; }
            afficherDialogRappel(p,f);
        });
        bTog .setOnAction(e -> {
            Facture f = tv.getSelectionModel().getSelectedItem();
            if(f!=null)
                runAsync(() -> dao.setFacturePayee(f.getId(), !f.isPaye()),
                        () -> refreshFactures(p,tv));
        });
        bClose.setOnAction(e -> win.close());

        HBox btns = new HBox(10,bAdd,bMail,bTog,bClose);
        btns.setPadding(new Insets(10));

        VBox root = new VBox(10,title,tv,btns);
        root.setPadding(new Insets(10));

        Scene sc = new Scene(root,600,400);
        sc.getStylesheets().add(getClass().getResource("/css/dark.css").toExternalForm());
        win.setScene(sc); win.initModality(Modality.WINDOW_MODAL); win.show();

        refreshFactures(p,tv);
    }
    private void refreshFactures(Prestataire p, TableView<Facture> tv){
        runAsync(() -> dao.factures(p.getId(),null),
                list -> { tv.setItems(FXCollections.observableArrayList(list));
                    refresh(search.getText()); });
    }
    private TableColumn<Facture,?> col(String t,String prop){
        TableColumn<Facture,?> c = new TableColumn<>(t);
        c.setCellValueFactory(new PropertyValueFactory<>(prop));
        return c;
    }

    /* ---------- Ajout facture ---------- */
    private void addFactureDialog(Prestataire p, TableView<Facture> tv){
        Dialog<Facture> d = new Dialog<>();
        d.setTitle("Nouvelle facture – "+p.getNom());
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK,ButtonType.CANCEL);

        GridPane gp = new GridPane(); gp.setHgap(8); gp.setVgap(8);
        DatePicker dpEch = new DatePicker(LocalDate.now());
        TextField tfDesc = new TextField();
        TextField tfMont = new TextField("0");

        gp.addRow(0,new Label("Échéance :"), dpEch);
        gp.addRow(1,new Label("Description :"), tfDesc);
        gp.addRow(2,new Label("Montant HT (€) :"), tfMont);
        d.getDialogPane().setContent(gp);

        d.setResultConverter(bt -> {
            if(bt!=ButtonType.OK) return null;
            return new Facture(0,p.getId(), tfDesc.getText(), dpEch.getValue(),
                    Double.parseDouble(tfMont.getText()), false, null,false);
        });
        d.showAndWait()
                .ifPresent(f -> runAsync(() -> dao.addFacture(f),
                        () -> refreshFactures(p,tv)));
    }

    /* ---------- Rappel mail ---------- */
    private void afficherDialogRappel(Prestataire pr, Facture f){
        Dialog<Void> dlg = new Dialog<>();
        dlg.setTitle("Rappel – "+pr.getNom());

        GridPane gp = new GridPane(); gp.setHgap(8); gp.setVgap(10);
        TextField tfDest  = new TextField(pr.getEmail());
        TextField tfSujet = new TextField("Rappel de paiement – facture du "+f.getEcheanceFr());
        TextArea taCorps  = new TextArea("""
                Bonjour %NOM%,

                Nous n'avons pas encore reçu le règlement de votre facture de %MONTANT% € (échéance %ECHEANCE%).

                Merci de procéder au paiement ou de nous tenir informés.

                Cordialement.
                """.replace("%NOM%",pr.getNom())
                .replace("%MONTANT%",String.format("%.2f",f.getMontant()))
                .replace("%ECHEANCE%",f.getEcheanceFr()));
        taCorps.setPrefRowCount(8);

        DatePicker dpDate = new DatePicker(f.getEcheance());
        CheckBox cbNow = new CheckBox("Envoyer immédiatement");
        cbNow.setSelected(true);
        cbNow.selectedProperty().addListener((o,b,b2)-> dpDate.setDisable(b2));

        gp.addRow(0, new Label("Destinataire :"), tfDest);
        gp.addRow(1, new Label("Sujet :"), tfSujet);
        gp.addRow(2, new Label("Message :"), taCorps);
        gp.addRow(3, cbNow, new Label("… sinon à la date :")); gp.add(dpDate,2,3);

        dlg.getDialogPane().setContent(gp);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK,ButtonType.CANCEL);

        dlg.setResultConverter(bt -> {
            if(bt!=ButtonType.OK) return null;
            MailPrefs cfg = mailPrefsDao.load();
            if(cbNow.isSelected()){
                runAsync(() -> {
                    Map<String,String> v = Mailer.vars(pr,f);
                    Mailer.send(cfg, tfDest.getText(),
                            Mailer.subjToPresta(cfg,v),
                            taCorps.getText());
                    return null;
                }, v -> info("E‑mail envoyé."));
            }else{
                LocalDateTime when = dpDate.getValue().atTime(8,0);
                Rappel r = new Rappel(0,f.getId(), tfDest.getText(),
                        tfSujet.getText(), taCorps.getText(),
                        when,false);
                runAsync(() -> dao.addRappel(r),
                        () -> info("Rappel enregistré pour le "+when.toLocalDate()));
            }
            return null;
        });
        dlg.showAndWait();
    }

    /* ====================================================================== */
    /* ============================  UTILITÉS  ============================== */
    /* ====================================================================== */

    private boolean confirm(String msg){
        return new Alert(Alert.AlertType.CONFIRMATION,msg,
                ButtonType.YES,ButtonType.NO)
                .showAndWait().orElse(ButtonType.NO)==ButtonType.YES;
    }
    private void alert(String msg){
        new Alert(Alert.AlertType.ERROR,msg,ButtonType.OK).showAndWait();
    }
    private void info(String msg){
        new Alert(Alert.AlertType.INFORMATION,msg,ButtonType.OK).showAndWait();
    }

    public void shutdownExecutor(){ executor.shutdownNow(); }
}

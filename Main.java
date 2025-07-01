package com.rochias.prestamgr;

import javafx.application.Application;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.FileOutputStream;
import java.nio.file.Path;
import java.sql.*;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.github.librepdf.openpdf.text.*;
import com.github.librepdf.openpdf.text.pdf.PdfWriter;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

/**
 * ============================================================================
 *  Gestion des Prestataires + Drop Calculator
 *  --------------------------------------------------------------------------
 *  - CRUD SQLite
 *  - Exports PDF (fiche & historique)
 *  - Envoi Mail (SMTP/SSL)
 *  - JavaFX GUI (TableView + dialogs)
 *  - Drop Calculator (probas cumulées)
 * ============================================================================
 *  Auteur  : Dév interne – Rochias
 *  Contact : valentin.gidon@rochias.fr
 * ============================================================================
 */
public class Main extends Application {

    /*------------------------------------------------------------------------*
     *  SECTION 1 — CONFIGURATION                                             *
     *------------------------------------------------------------------------*/
    private static final String DB_FILE = "prestataires.db";
    private static final String MAIL_USER = Optional.ofNullable(System.getenv("MAIL_USER"))
                                                   .orElse("votre_mail@example.com");
    private static final String MAIL_PWD  = Optional.ofNullable(System.getenv("MAIL_PWD"))
                                                   .orElse("votre_mdp");

    private static final DateTimeFormatter DATE_FR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /*------------------------------------------------------------------------*
     *  SECTION 2 — LANCEMENT                                                 *
     *------------------------------------------------------------------------*/
    public static void main(String[] args) { launch(args); }

    @Override
    public void start(Stage primaryStage) {
        DB dao = new DB(DB_FILE);
        GUI gui = new GUI(primaryStage, dao);
        primaryStage.setTitle("Gestion des Prestataires — Rochias");
        primaryStage.setScene(new Scene(gui.root, 920, 600));
        primaryStage.show();
    }

    /*======================================================================*/
    /*=====================    CLASSE  DB / DAO    =========================*/
    /*======================================================================*/
    static class DB {
        private final Connection conn;

        DB(String path) {
            try {
                conn = DriverManager.getConnection("jdbc:sqlite:" + path);
                Statement st = conn.createStatement();
                st.execute("PRAGMA foreign_keys = 1");
                // --- schéma
                conn.createStatement().executeUpdate("""
                    CREATE TABLE IF NOT EXISTS prestataires (
                        id INTEGER PRIMARY KEY,
                        nom TEXT UNIQUE NOT NULL COLLATE NOCASE,
                        societe TEXT,
                        telephone TEXT,
                        email TEXT,
                        note INTEGER CHECK(note BETWEEN 0 AND 100),
                        facturation TEXT,
                        date_contrat TEXT
                    );
                    """);
                conn.createStatement().executeUpdate("""
                    CREATE TABLE IF NOT EXISTS services (
                        id INTEGER PRIMARY KEY,
                        prestataire_id INTEGER REFERENCES prestataires(id) ON DELETE CASCADE,
                        description TEXT,
                        date TEXT
                    );
                    """);
            } catch (Exception e) { throw new RuntimeException(e); }
        }

        /*--------------------  CRUD Prestataires  ------------------------*/
        List<Prestataire> list(String filtre) {
            String sql = """
                SELECT * FROM prestataires
                WHERE nom LIKE ? OR societe LIKE ?
                ORDER BY nom
            """;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                String like = "%" + filtre + "%";
                ps.setString(1, like);
                ps.setString(2, like);
                ResultSet rs = ps.executeQuery();
                List<Prestataire> l = new ArrayList<>();
                while (rs.next()) l.add(rowToPrestataire(rs));
                return l;
            } catch (SQLException e) { throw new RuntimeException(e); }
        }

        void add(Prestataire p) {
            String sql = "INSERT INTO prestataires(nom,societe,telephone,email,note,facturation,date_contrat) VALUES(?,?,?,?,?,?,?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                fill(ps, p); ps.executeUpdate();
            } catch (SQLException e) { throw new RuntimeException(e); }
        }

        void update(Prestataire p) {
            String sql = """
                UPDATE prestataires SET
                nom=?,societe=?,telephone=?,email=?,note=?,facturation=?,date_contrat=? WHERE id=?
            """;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                fill(ps, p); ps.setInt(8, p.getId()); ps.executeUpdate();
            } catch (SQLException e) { throw new RuntimeException(e); }
        }

        void delete(int pid) {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM prestataires WHERE id=?")) {
                ps.setInt(1, pid); ps.executeUpdate();
            } catch (SQLException e) { throw new RuntimeException(e); }
        }

        /*--------------------  CRUD Services  ---------------------------*/
        void addService(int pid, String desc) {
            String sql = "INSERT INTO services(prestataire_id,description,date) VALUES(?,?,?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, pid);
                ps.setString(2, desc);
                ps.setString(3, DATE_FR.format(LocalDate.now()));
                ps.executeUpdate();
            } catch (SQLException e) { throw new RuntimeException(e); }
        }

        List<ServiceRow> services(int pid) {
            String sql = "SELECT description,date FROM services WHERE prestataire_id=? ORDER BY date";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, pid);
                ResultSet rs = ps.executeQuery();
                List<ServiceRow> out = new ArrayList<>();
                while (rs.next()) out.add(new ServiceRow(rs.getString("description"), rs.getString("date")));
                return out;
            } catch (SQLException e) { throw new RuntimeException(e); }
        }

        /*--------------------  Utilitaires ------------------------------*/
        private static Prestataire rowToPrestataire(ResultSet rs) throws SQLException {
            return new Prestataire(
                    rs.getInt("id"), rs.getString("nom"), rs.getString("societe"),
                    rs.getString("telephone"), rs.getString("email"),
                    rs.getInt("note"), rs.getString("facturation"), rs.getString("date_contrat"));
        }
        private static void fill(PreparedStatement ps, Prestataire p) throws SQLException {
            ps.setString(1, p.getNom());
            ps.setString(2, p.getSociete());
            ps.setString(3, p.getTelephone());
            ps.setString(4, p.getEmail());
            ps.setInt(5, p.getNote());
            ps.setString(6, p.getFacturation());
            ps.setString(7, p.getDateContrat());
        }
    }

    /*======================================================================*/
    /*=====================      MODELES POJO       ========================*/
    /*======================================================================*/
    record ServiceRow(String desc, String date) { }

    static class Prestataire {
        private final IntegerProperty id = new SimpleIntegerProperty();
        private final StringProperty nom = new SimpleStringProperty();
        private final StringProperty societe = new SimpleStringProperty();
        private final StringProperty telephone = new SimpleStringProperty();
        private final StringProperty email = new SimpleStringProperty();
        private final IntegerProperty note = new SimpleIntegerProperty();
        private final StringProperty facturation = new SimpleStringProperty();
        private final StringProperty dateContrat = new SimpleStringProperty();

        // ---- ctor
        Prestataire(int id, String n, String s, String tel, String mail, int note, String fact, String date) {
            this.id.set(id); nom.set(n); societe.set(s); telephone.set(tel);
            email.set(mail); this.note.set(note); facturation.set(fact); dateContrat.set(date);
        }

        // ---- getters/setters simplifiés
        int getId(){return id.get();}
        String getNom(){return nom.get();}
        void setNom(String s){nom.set(s);}
        String getSociete(){return societe.get();}
        String getTelephone(){return telephone.get();}
        String getEmail(){return email.get();}
        int getNote(){return note.get();}
        String getFacturation(){return facturation.get();}
        String getDateContrat(){return dateContrat.get();}

        /* util clone sans ID (formulaire create) */
        Prestataire copyWithoutId(){ return new Prestataire(0,getNom(),getSociete(),getTelephone(),getEmail(),getNote(),getFacturation(),getDateContrat()); }
    }

    /*======================================================================*/
    /*=====================       SECTION GUI        =======================*/
    /*======================================================================*/
    static class GUI {
        final BorderPane root = new BorderPane();
        private final DB dao;

        // compos UI
        private final TableView<Prestataire> table = new TableView<>();
        private final TextField search = new TextField();
        private final Label[] detailLabels = new Label[7];
        private final ProgressBar noteBar = new ProgressBar(0);

        // DropCalc fields
        private final TextField baseRateField = new TextField("0.05");
        private final TextField ppField = new TextField("100,120,80");
        private final TextField targetField = new TextField("0.95");
        private final Label fightsOut = new Label();

        GUI(Stage stage, DB dao) {
            this.dao = dao;
            buildLayout(stage);
            refresh("");
        }

        /*----------------------  Layout principal  ---------------------*/
        private void buildLayout(Stage stage) {
            /* -------- TOP BAR ---------- */
            HBox topBar = new HBox(10, new Label("Recherche : "), search);
            topBar.setPadding(new Insets(10));
            search.textProperty().addListener((obs, o, n) -> refresh(n));

            /* -------- CENTRE  ---------- */
            createTable(stage);

            /* -------- PANEL DROITE ----- */
            VBox right = buildDetailPane(stage);
            right.setPrefWidth(320);

            /* -------- BOTTOM BAR ------- */
            HBox bottom = buildBottomBar(stage);

            /* -------- ROOT ------------- */
            root.setTop(topBar);
            root.setCenter(table);
            root.setRight(right);
            root.setBottom(bottom);
        }

        /*----------------------  TableView & data  ---------------------*/
        private void createTable(Stage stage) {
            table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
            String[] cols = {"Nom","Société","Téléphone","Email","Note","Facturation","Date contrat"};
            String[] props = {"nom","societe","telephone","email","note","facturation","dateContrat"};
            for (int i=0;i<cols.length;i++){
                TableColumn<Prestataire,?> c = new TableColumn<>(cols[i]);
                c.setCellValueFactory(new PropertyValueFactory<>(props[i]));
                table.getColumns().add(c);
            }
            table.getSelectionModel().selectedItemProperty().addListener((obs,o,n)->updateDetails(n));
            table.setPrefWidth(580);
        }

        private void refresh(String filtre) {
            table.setItems(FXCollections.observableArrayList(dao.list(filtre)));
            updateDetails(null);
        }

        /*----------------------  Détails Prestataire  ------------------*/
        private VBox buildDetailPane(Stage stage) {
            VBox v = new VBox(8);
            v.setPadding(new Insets(10));

            String[] lab = {"Nom","Société","Téléphone","Email","Note","Facturation","Date contrat"};
            for (int i=0;i<lab.length;i++){
                Label key = new Label(lab[i]+" :");
                key.setStyle("-fx-font-weight:bold");
                detailLabels[i] = new Label();
                HBox line = new HBox(6, key, detailLabels[i]);
                v.getChildren().add(line);
            }
            noteBar.setPrefWidth(200);
            v.getChildren().add(noteBar);

            v.getChildren().add(new Separator());
            v.getChildren().add(buildDropCalcPane());
            return v;
        }

        private void updateDetails(Prestataire p){
            if(p==null){
                Arrays.stream(detailLabels).forEach(l->l.setText(""));
                noteBar.setProgress(0);
                return;
            }
            detailLabels[0].setText(p.getNom());
            detailLabels[1].setText(p.getSociete());
            detailLabels[2].setText(p.getTelephone());
            detailLabels[3].setText(p.getEmail());
            detailLabels[4].setText(p.getNote()+" %");
            detailLabels[5].setText(p.getFacturation());
            detailLabels[6].setText(p.getDateContrat());
            noteBar.setProgress(p.getNote()/100.0);
        }

        /*----------------------  Bottom Bar (buttons) ------------------*/
        private HBox buildBottomBar(Stage stage){
            Button bAdd = new Button("Nouveau");
            Button bEdit= new Button("Modifier");
            Button bDel = new Button("Supprimer");
            Button bService = new Button("Ajout service");
            Button bHist = new Button("Historique");
            Button bPDF = new Button("Fiche PDF");
            Button bPDFAll = new Button("PDF global");

            bAdd.setOnAction(_->editDialog(null));
            bEdit.setOnAction(_->editDialog(table.getSelectionModel().getSelectedItem()));
            bDel.setOnAction(_->{
                Prestataire p = table.getSelectionModel().getSelectedItem();
                if(p!=null && confirm("Supprimer "+p.getNom()+" ?")){
                    dao.delete(p.getId()); refresh(search.getText());
                }
            });
            bService.setOnAction(_->addServiceDialog());
            bHist.setOnAction(_->showHistoryDialog());
            bPDF.setOnAction(_->{
                Prestataire p = table.getSelectionModel().getSelectedItem();
                if(p!=null) PDF.fiche(stage, p);
            });
            bPDFAll.setOnAction(_->PDF.historiqueGlobal(stage, dao));

            HBox hb = new HBox(8,bAdd,bEdit,bDel,bService,bHist,bPDF,bPDFAll);
            hb.setPadding(new Insets(10));
            return hb;
        }

        /*==================================================================*
         *           DIALOGUE : Création + édition Prestataire              *
         *==================================================================*/
        private void editDialog(Prestataire src){
            Dialog<Prestataire> d = new Dialog<>();
            d.setTitle(src==null?"Nouveau Prestataire":"Modifier Prestataire");
            d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

            GridPane gp = new GridPane(); gp.setHgap(8); gp.setVgap(8);
            String[] lab = {"Nom","Société","Téléphone","Email","Note (0-100)","Facturation","Date contrat (dd/MM/yyyy)"};
            TextField[] fields = new TextField[lab.length];
            Pattern mailRegex = Pattern.compile("[^@]+@[^@]+\\.[^@]+");

            for(int i=0;i<lab.length;i++){
                gp.add(new Label(lab[i]+" :"),0,i);
                fields[i] = new TextField(src==null?"":switch(i){
                    case 0 -> ""; case 1 -> ""; case 2 -> ""; case 3 -> ""; case 4 -> "0";
                    case 5 -> ""; default -> DATE_FR.format(LocalDate.now());
                });
                gp.add(fields[i],1,i);
            }
            if(src!=null){
                fields[0].setText(src.getNom());
                fields[1].setText(src.getSociete());
                fields[2].setText(src.getTelephone());
                fields[3].setText(src.getEmail());
                fields[4].setText(""+src.getNote());
                fields[5].setText(src.getFacturation());
                fields[6].setText(src.getDateContrat());
            }
            d.getDialogPane().setContent(gp);

            /* ---- validation ---- */
            Button ok = (Button)d.getDialogPane().lookupButton(ButtonType.OK);
            ok.addEventFilter(javafx.event.ActionEvent.ACTION, ev->{
                try{
                    String nom = fields[0].getText().trim();
                    if(nom.isEmpty()) throw new IllegalArgumentException("Nom obligatoire.");
                    if(!fields[3].getText().isBlank() && !mailRegex.matcher(fields[3].getText()).matches())
                        throw new IllegalArgumentException("Email invalide.");
                    int note = Integer.parseInt(fields[4].getText());
                    if(note<0||note>100) throw new IllegalArgumentException("Note 0-100.");
                    // date : simple vérif
                    LocalDate.parse(fields[6].getText(), DATE_FR);
                }catch(Exception e){
                    alert(e.getMessage()); ev.consume();
                }
            });

            d.setResultConverter(bt->{
                if(bt==ButtonType.OK){
                    return new Prestataire(
                            src==null?0:src.getId(),
                            fields[0].getText(), fields[1].getText(),
                            fields[2].getText(), fields[3].getText(),
                            Integer.parseInt(fields[4].getText()),
                            fields[5].getText(), fields[6].getText()
                    );
                }
                return null;
            });
            Optional<Prestataire> res = d.showAndWait();
            res.ifPresent(p->{
                if(src==null) dao.add(p.copyWithoutId());
                else dao.update(p);
                refresh(search.getText());
            });
        }

        /*=====================  Dialogues secondaires  ===================*/
        private void addServiceDialog(){
            Prestataire p = table.getSelectionModel().getSelectedItem();
            if(p==null){ alert("Sélectionnez un prestataire."); return; }
            TextInputDialog td = new TextInputDialog();
            td.setTitle("Nouveau service");
            td.setHeaderText("Description du service");
            td.showAndWait().ifPresent(desc->{
                if(!desc.isBlank()) dao.addService(p.getId(), desc);
            });
        }

        private void showHistoryDialog(){
            Prestataire p = table.getSelectionModel().getSelectedItem();
            if(p==null){ alert("Sélectionnez un prestataire."); return; }
            Stage win = new Stage();
            win.setTitle("Historique — "+p.getNom());
            VBox vb = new VBox(6); vb.setPadding(new Insets(10));
            dao.services(p.getId()).forEach(sr->vb.getChildren()
                    .add(new Label(sr.date()+" — "+sr.desc())));
            win.setScene(new Scene(new ScrollPane(vb),400,400));
            win.initModality(Modality.WINDOW_MODAL);
            win.show();
        }

        /*=========================  Drop Calc  ===========================*/
        private VBox buildDropCalcPane(){
            // Restricteurs :
            UnaryOperator<TextFormatter.Change> numFilter = c->{
                if(c.getControlNewText().matches("[0-9.,\\s-]*")) return c; return null;
            };
            baseRateField.setTextFormatter(new TextFormatter<>(numFilter));
            ppField.setTextFormatter(new TextFormatter<>(numFilter));
            targetField.setTextFormatter(new TextFormatter<>(numFilter));

            Button compute = new Button("Calculer");
            compute.setOnAction(_->computeDrop());

            GridPane gp = new GridPane(); gp.setHgap(8); gp.setVgap(4);
            gp.addRow(0,new Label("Taux base :"),baseRateField);
            gp.addRow(1,new Label("PP (%) :"),ppField);
            gp.addRow(2,new Label("Cible cumulée :"),targetField);
            gp.addRow(3,compute,fightsOut);
            return new VBox(new Label("—  Drop Calculator  —"),gp);
        }

        private void computeDrop(){
            try{
                double base = Double.parseDouble(baseRateField.getText().replace(',','.'));
                double[] pps = Arrays.stream(ppField.getText().split("[,\\s]+"))
                                     .filter(s->!s.isBlank())
                                     .mapToDouble(Double::parseDouble).toArray();
                double target = Double.parseDouble(targetField.getText().replace(',','.'));

                double pFight = Stats.groupProb(base, pps);
                int fights   = Stats.fightsNeeded(target, pFight);
                DecimalFormat df = new DecimalFormat("#.####");

                fightsOut.setText("P/fight = "+df.format(pFight*100)+" %\nCombats ≈ "+fights);
            }catch(Exception e){ alert("Entrées numériques invalides."); }
        }

        /*=========================  Utils GUI  ==========================*/
        private boolean confirm(String msg){ return new Alert(Alert.AlertType.CONFIRMATION,msg,ButtonType.YES,ButtonType.NO).showAndWait().orElse(ButtonType.NO)==ButtonType.YES; }
        private void alert(String msg){ new Alert(Alert.AlertType.ERROR,msg,ButtonType.OK).showAndWait(); }
    }

    /*======================================================================*/
    /*=====================          STATS          ========================*/
    /*======================================================================*/
    static class Stats {
        static double indivProb(double base, double pp){ return Math.max(0, Math.min(1, base*pp/100.0)); }
        static double groupProb(double base, double... pps){
            double invProd = 1.0;
            for (double pp:pps) invProd *= 1 - indivProb(base, pp);
            return 1 - invProd;
        }
        static int fightsNeeded(double target, double pFight){
            if(pFight<=0) return Integer.MAX_VALUE;
            return (int)Math.ceil(Math.log(1-target)/Math.log(1-pFight));
        }
    }

    /*======================================================================*/
    /*=====================          PDF            ========================*/
    /*======================================================================*/
    static class PDF {
        static void fiche(Stage owner, Prestataire p){
            FileChooser fc = new FileChooser();
            fc.setInitialFileName(p.getNom()+"_fiche.pdf");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF","*.pdf"));
            Path file = Optional.ofNullable(fc.showSaveDialog(owner)).map(java.io.File::toPath).orElse(null);
            if(file==null) return;

            try(Document doc = new Document()){
                PdfWriter.getInstance(doc, new FileOutputStream(file.toFile()));
                doc.open();
                doc.add(new Paragraph("Fiche Prestataire — "+p.getNom(), FontFactory.getFont(FontFactory.HELVETICA_BOLD,18)));
                doc.add(new Paragraph("\n"));
                doc.add(new Paragraph("Société      : "+p.getSociete()));
                doc.add(new Paragraph("Téléphone   : "+p.getTelephone()));
                doc.add(new Paragraph("Email       : "+p.getEmail()));
                doc.add(new Paragraph("Note        : "+p.getNote()+" %"));
                doc.add(new Paragraph("Facturation : "+p.getFacturation()));
                doc.add(new Paragraph("Date contrat: "+p.getDateContrat()));
                doc.close();
                info("Fiche PDF exportée.");
            }catch(Exception e){ error(e.getMessage()); }
        }

        static void historiqueGlobal(Stage owner, DB dao){
            FileChooser fc = new FileChooser();
            fc.setInitialFileName("Historique_global_prestataires.pdf");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF","*.pdf"));
            Path file = Optional.ofNullable(fc.showSaveDialog(owner)).map(java.io.File::toPath).orElse(null);
            if(file==null) return;

            try(Document doc = new Document()){
                PdfWriter.getInstance(doc, new FileOutputStream(file.toFile()));
                doc.open();
                doc.add(new Paragraph("Historique des contrats et services", FontFactory.getFont(FontFactory.HELVETICA_BOLD,18)));
                doc.add(new Paragraph("\n"));
                dao.list("").forEach(p-> dao.services(p.getId()).forEach(sr->{
                    try{ doc.add(new Paragraph(sr.date()+" — "+p.getNom()+" ("+p.getSociete()+") : "+sr.desc())); }
                    catch(DocumentException e){throw new RuntimeException(e);}
                }));
                doc.close();
                info("Historique PDF exporté.");
            }catch(Exception e){ error(e.getMessage()); }
        }

        private static void info(String m){ new Alert(Alert.AlertType.INFORMATION,m,ButtonType.OK).showAndWait(); }
        private static void error(String m){ new Alert(Alert.AlertType.ERROR,m,ButtonType.OK).showAndWait(); }
    }

    /*======================================================================*/
    /*=====================      ENVOI  MAIL        ========================*/
    /*======================================================================*/
    static class Mailer {
        static void send(String dest, String subject, String body) throws MessagingException {
            Properties props = new Properties();
            props.put("mail.smtp.auth","true");
            props.put("mail.smtp.ssl.enable","true");
            props.put("mail.smtp.host","smtp.gmail.com");
            props.put("mail.smtp.port","465");

            Session sess = Session.getInstance(props,new Authenticator(){
                @Override protected PasswordAuthentication getPasswordAuthentication(){
                    return new PasswordAuthentication(MAIL_USER, MAIL_PWD);
                }
            });
            Message msg = new MimeMessage(sess);
            msg.setFrom(new InternetAddress(MAIL_USER));
            msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(dest,false));
            msg.setSubject(subject);
            msg.setText(body);
            Transport.send(msg);
        }
    }
}

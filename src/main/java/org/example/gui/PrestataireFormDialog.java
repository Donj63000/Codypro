package org.example.gui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import org.example.dao.DB;
import org.example.model.Prestataire;

import java.util.regex.Pattern;

public final class PrestataireFormDialog extends Dialog<Prestataire> {
    private final DB dao;
    private final Prestataire baseRef;
    private final Runnable onServicesChanged;
    private final TextField tfNom = new TextField();
    private final TextField tfSoc = new TextField();
    private final TextField tfTel = new TextField();
    private final TextField tfMail = new TextField();
    private final Slider    slNote = new Slider(0, 100, 70);
    private final Label     lbNote = new Label("70");
    private final DatePicker dpContrat = new DatePicker();
    private final TextField tfServiceType = new TextField();
    private final TextArea  taServiceNotes = new TextArea();
    private final Button    btnServices = new Button("Configurer services");
    private final Label err = new Label();

    public PrestataireFormDialog(DB dao, Prestataire base, Runnable onServicesChanged) {
        this.dao = dao;
        this.baseRef = base;
        this.onServicesChanged = onServicesChanged != null ? onServicesChanged : () -> {};
        setTitle(base == null ? "Nouveau prestataire" : "Modifier le prestataire");
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        GridPane g = new GridPane(); g.setHgap(8); g.setVgap(8); g.setPadding(new Insets(12));
        int r=0;
        g.add(new Label("Nom *"), 0, r); g.add(tfNom, 1, r++);
        g.add(new Label("Societe"), 0, r); g.add(tfSoc, 1, r++);
        g.add(new Label("Telephone"), 0, r); g.add(tfTel, 1, r++);
        g.add(new Label("Email"), 0, r); g.add(tfMail, 1, r++);
        g.add(new Label("Note"), 0, r);
        slNote.setShowTickMarks(true); slNote.setMajorTickUnit(20);
        slNote.valueProperty().addListener((o,ov,nv)-> lbNote.setText(Integer.toString(nv.intValue())));
        g.add(new javafx.scene.layout.HBox(8, slNote, lbNote), 1, r++);
        g.add(new Label("Contrat (date)"), 0, r); g.add(dpContrat, 1, r++);
        g.add(new Label("Type de service *"), 0, r); g.add(tfServiceType, 1, r++);
        taServiceNotes.setPrefRowCount(3); taServiceNotes.setWrapText(true);
        g.add(new Label("Description"), 0, r); g.add(taServiceNotes, 1, r++);

        HBox serviceBar = new HBox(8, btnServices);
        serviceBar.setAlignment(Pos.CENTER_RIGHT);
        g.add(serviceBar, 0, r, 2, 1);
        r++;

        err.getStyleClass().add("error");
        g.add(err, 0, r, 2, 1);
        getDialogPane().setContent(g);

        if (base != null) {
            tfNom.setText(n(base.getNom())); tfSoc.setText(n(base.getSociete()));
            tfTel.setText(n(base.getTelephone())); tfMail.setText(n(base.getEmail()));
            slNote.setValue(base.getNote()); lbNote.setText(Integer.toString(base.getNote()));
            tfServiceType.setText(n(base.getFacturation()));
            taServiceNotes.setText(n(base.getServiceNotes()));
            var dc = base.getDateContrat();
            if (dc != null && !dc.isBlank()) {
                try {
                    var fmt = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
                    var ld = java.time.LocalDate.parse(dc, fmt);
                    dpContrat.setValue(ld);
                } catch (Exception ignore) {}
            }
        }
        configureServicesButton();

        Node ok = getDialogPane().lookupButton(ButtonType.OK);
        ok.addEventFilter(javafx.event.ActionEvent.ACTION, evt -> {
            String msg = validate(tfNom.getText(), tfMail.getText(), tfTel.getText(), tfServiceType.getText());
            if (msg != null) { err.setText(msg); evt.consume(); }
        });

        setResultConverter(bt -> {
            if (bt != ButtonType.OK) return null;
            var nom = tfNom.getText().trim();
            var soc = z(tfSoc.getText());
            var tel = z(tfTel.getText());
            var mail = z(tfMail.getText());
            var note = (int) slNote.getValue();
            var serviceType = tfServiceType.getText() == null ? "" : tfServiceType.getText().trim();
            var serviceDesc = z(taServiceNotes.getText());
            String dateContrat = null;
            if (dpContrat.getValue()!=null) {
                var ld = dpContrat.getValue();
                var fmt = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
                dateContrat = ld.format(fmt);
            }
            int id = base != null ? base.getId() : 0;
            return new Prestataire(id, nom, soc, tel, mail, note, serviceType, serviceDesc, dateContrat);
        });
    }

    private void configureServicesButton() {
        boolean hasPersistedPrestataire = baseRef != null && baseRef.getId() > 0;
        btnServices.setDisable(!hasPersistedPrestataire || dao == null);
        btnServices.setOnAction(e -> {
            if (!hasPersistedPrestataire || dao == null) return;
            ServicesManagerDialog dlg = new ServicesManagerDialog(dao, baseRef.getId());
            ThemeManager.apply(dlg);
            dlg.showAndWait();
            onServicesChanged.run();
        });
    }

    private static String n(String s){ return s==null?"":s; }
    private static String z(String s){ if (s==null) return null; s=s.trim(); return s.isEmpty()?null:s; }

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern PHONE = Pattern.compile("^[0-9+().\\s-]{6,}$");

    private static String validate(String nom, String mail, String tel, String serviceType){
        if (nom==null || nom.isBlank()) return "Le nom est obligatoire.";
        if (mail!=null && !mail.isBlank() && !EMAIL.matcher(mail).matches()) return "Email invalide.";
        if (tel!=null && !tel.isBlank() && !PHONE.matcher(tel).matches()) return "Telephone invalide.";
        if (serviceType==null || serviceType.isBlank()) return "Le type de service est obligatoire.";
        return null;
    }
}

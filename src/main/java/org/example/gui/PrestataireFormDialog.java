package org.example.gui;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import org.example.model.Prestataire;

import java.time.ZoneId;
import java.util.regex.Pattern;

public final class PrestataireFormDialog extends Dialog<Prestataire> {
    private final TextField tfNom = new TextField();
    private final TextField tfSoc = new TextField();
    private final TextField tfTel = new TextField();
    private final TextField tfMail = new TextField();
    private final Slider    slNote = new Slider(0, 100, 70);
    private final Label     lbNote = new Label("70");
    private final DatePicker dpContrat = new DatePicker();
    private final Label err = new Label();

    public PrestataireFormDialog(Prestataire base) {
        setTitle(base == null ? "Nouveau prestataire" : "Modifier le prestataire");
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        GridPane g = new GridPane(); g.setHgap(8); g.setVgap(8); g.setPadding(new Insets(12));
        int r=0;
        g.add(new Label("Nom *"), 0, r); g.add(tfNom, 1, r++);
        g.add(new Label("Société"), 0, r); g.add(tfSoc, 1, r++);
        g.add(new Label("Téléphone"), 0, r); g.add(tfTel, 1, r++);
        g.add(new Label("Email"), 0, r); g.add(tfMail, 1, r++);
        g.add(new Label("Note"), 0, r);
        slNote.setShowTickMarks(true); slNote.setMajorTickUnit(20);
        slNote.valueProperty().addListener((o,ov,nv)-> lbNote.setText(Integer.toString(nv.intValue())));
        g.add(new javafx.scene.layout.HBox(8, slNote, lbNote), 1, r++);
        g.add(new Label("Contrat (date)"), 0, r); g.add(dpContrat, 1, r++);
        err.getStyleClass().add("error"); g.add(err, 0, r, 2, 1);
        getDialogPane().setContent(g);

        if (base != null) {
            tfNom.setText(n(base.getNom())); tfSoc.setText(n(base.getSociete()));
            tfTel.setText(n(base.getTelephone())); tfMail.setText(n(base.getEmail()));
            slNote.setValue(base.getNote()); lbNote.setText(Integer.toString(base.getNote()));
            var dc = base.getDateContrat();
            if (dc != null && !dc.isBlank()) {
                try {
                    var fmt = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
                    var ld = java.time.LocalDate.parse(dc, fmt);
                    dpContrat.setValue(ld);
                } catch (Exception ignore) {}
            }
        }

        Node ok = getDialogPane().lookupButton(ButtonType.OK);
        ok.addEventFilter(javafx.event.ActionEvent.ACTION, evt -> {
            String msg = validate(tfNom.getText(), tfMail.getText(), tfTel.getText());
            if (msg != null) { err.setText(msg); evt.consume(); }
        });

        setResultConverter(bt -> {
            if (bt != ButtonType.OK) return null;
            var nom = tfNom.getText().trim();
            var soc = z(tfSoc.getText());
            var tel = z(tfTel.getText());
            var mail = z(tfMail.getText());
            var note = (int) slNote.getValue();
            var fact = (base != null ? base.getFacturation() : "");
            String dateContrat = null;
            if (dpContrat.getValue()!=null) {
                var ld = dpContrat.getValue();
                var fmt = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
                dateContrat = ld.format(fmt);
            }
            int id = base != null ? base.getId() : 0;
            return new Prestataire(id, nom, soc, tel, mail, note, fact, dateContrat);
        });
    }

    private static String n(String s){ return s==null?"":s; }
    private static String z(String s){ if (s==null) return null; s=s.trim(); return s.isEmpty()?null:s; }

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern PHONE = Pattern.compile("^[0-9+().\\s-]{6,}$");

    private static String validate(String nom, String mail, String tel){
        if (nom==null || nom.isBlank()) return "Le nom est obligatoire.";
        if (mail!=null && !mail.isBlank() && !EMAIL.matcher(mail).matches()) return "Email invalide.";
        if (tel!=null && !tel.isBlank() && !PHONE.matcher(tel).matches()) return "Téléphone invalide.";
        return null;
    }
}

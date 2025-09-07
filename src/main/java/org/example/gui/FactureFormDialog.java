package org.example.gui;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import org.example.model.Facture;

import java.math.BigDecimal;
import java.time.ZoneId;

public final class FactureFormDialog extends Dialog<Facture> {
    private final TextField tfDesc = new TextField();
    private final DatePicker dpEch = new DatePicker();
    private final TextField tfHt  = new TextField();
    private final Spinner<Double> spTva = new Spinner<>(0.0, 100.0, 20.0, 0.5);
    private final CheckBox cbPayee = new CheckBox("Facture payée");
    private final DatePicker dpPay = new DatePicker();
    private final Label err = new Label();

    public FactureFormDialog(Facture base) {
        setTitle(base==null ? "Nouvelle facture" : "Modifier la facture");
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        GridPane g = new GridPane(); g.setHgap(8); g.setVgap(8); g.setPadding(new Insets(12));
        int r=0;
        g.add(new Label("Description *"),0,r); g.add(tfDesc,1,r++);
        g.add(new Label("Échéance"),0,r); g.add(dpEch,1,r++);
        g.add(new Label("Montant HT (€) *"),0,r); g.add(tfHt,1,r++);
        g.add(new Label("TVA (%)"),0,r); g.add(spTva,1,r++);
        g.add(cbPayee,1,r++); g.add(new Label("Paiement le"),0,r); g.add(dpPay,1,r++);
        err.getStyleClass().add("error"); g.add(err,0,r,2,1);
        dpPay.setDisable(true); cbPayee.selectedProperty().addListener((o,ov,nv)-> dpPay.setDisable(!nv));
        getDialogPane().setContent(g);

        if (base!=null){
            tfDesc.setText(n(base.getDescription()));
            if (base.getEcheance()!=null){ dpEch.setValue(base.getEcheance()); }
            tfHt.setText(base.getMontantHt()==null ? "" : base.getMontantHt().toPlainString());
            spTva.getValueFactory().setValue(base.getTvaPct()==null ? 20.0 : base.getTvaPct().doubleValue());
            cbPayee.setSelected(base.isPaye());
            if (base.getDatePaiement()!=null){ dpPay.setValue(base.getDatePaiement()); dpPay.setDisable(false); }
        }

        Node ok = getDialogPane().lookupButton(ButtonType.OK);
        ok.addEventFilter(javafx.event.ActionEvent.ACTION, evt -> {
            if (tfDesc.getText().trim().isEmpty()){ err.setText("La description est obligatoire."); evt.consume(); return; }
            try { new BigDecimal(tfHt.getText().trim().replace(',', '.')); }
            catch(Exception ex){ err.setText("Montant HT invalide."); evt.consume(); }
        });

        setResultConverter(bt -> {
            if (bt != ButtonType.OK) return null;
            var desc = tfDesc.getText().trim();
            var ech  = dpEch.getValue();
            var ht   = new BigDecimal(tfHt.getText().trim().replace(',', '.'));
            var tva  = BigDecimal.valueOf(spTva.getValue());
            var mtva = Facture.calcTva(ht, tva);
            var ttc  = Facture.calcTtc(ht, tva);
            boolean payee = cbPayee.isSelected();
            var datePay = payee ? dpPay.getValue() : null;
            int id = base != null ? base.getId() : 0;
            int pid = base != null ? base.getPrestataireId() : 0;
            boolean preavis = base != null && base.isPreavisEnvoye();
            return new Facture(id, pid, desc, ech, ht, tva, mtva, ttc, payee, datePay, preavis);
        });
    }

    private static String n(String s){ return s==null?"":s; }
}

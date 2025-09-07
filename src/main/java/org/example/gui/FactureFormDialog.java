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
            if (base.getEcheanceTs()!=null){
                var ld = java.time.Instant.ofEpochMilli(base.getEcheanceTs()).atZone(ZoneId.systemDefault()).toLocalDate();
                dpEch.setValue(ld);
            }
            tfHt.setText(base.getMontantHt()==null ? "" : base.getMontantHt().toPlainString());
            spTva.getValueFactory().setValue(base.getTvaPct()==null ? 20.0 : base.getTvaPct().doubleValue());
            cbPayee.setSelected(base.isPaye());
            if (base.getDatePaiementTs()!=null){
                var ld = java.time.Instant.ofEpochMilli(base.getDatePaiementTs()).atZone(ZoneId.systemDefault()).toLocalDate();
                dpPay.setValue(ld); dpPay.setDisable(false);
            }
        }

        Node ok = getDialogPane().lookupButton(ButtonType.OK);
        ok.addEventFilter(javafx.event.ActionEvent.ACTION, evt -> {
            if (tfDesc.getText().trim().isEmpty()){ err.setText("La description est obligatoire."); evt.consume(); return; }
            try { new BigDecimal(tfHt.getText().trim().replace(',', '.')); }
            catch(Exception ex){ err.setText("Montant HT invalide."); evt.consume(); }
        });

        setResultConverter(bt -> {
            if (bt != ButtonType.OK) return null;
            Facture f = (base!=null ? base : new Facture());
            f.setDescription(tfDesc.getText().trim());
            if (dpEch.getValue()!=null){
                var ld = dpEch.getValue();
                var fmt = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
                f.setEcheance(ld.format(fmt));
                f.setEcheanceTs(ld.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli());
            } else { f.setEcheance(null); f.setEcheanceTs(null); }
            var ht  = new BigDecimal(tfHt.getText().trim().replace(',', '.'));
            var tva = BigDecimal.valueOf(spTva.getValue());
            var mtva = ht.multiply(tva).divide(BigDecimal.valueOf(100));
            var ttc  = ht.add(mtva);
            f.setMontantHt(ht); f.setTvaPct(tva); f.setMontantTva(mtva); f.setMontantTtc(ttc);
            f.setDevise("EUR");
            f.setPaye(cbPayee.isSelected());
            if (cbPayee.isSelected() && dpPay.getValue()!=null){
                var ld = dpPay.getValue();
                var fmt = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
                f.setDatePaiement(ld.format(fmt));
                f.setDatePaiementTs(ld.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli());
            } else { f.setDatePaiement(null); f.setDatePaiementTs(null); }
            return f;
        });
    }

    private static String n(String s){ return s==null?"":s; }
}

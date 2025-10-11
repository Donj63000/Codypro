package org.example.gui;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import org.example.model.Facture;

import java.math.BigDecimal;

public final class FactureFormDialog extends Dialog<Facture> {
    private final TextField tfDesc = new TextField();
    private final DatePicker dpEch = new DatePicker();
    private final TextField tfHt  = new TextField();
    private final Spinner<Double> spTva = new Spinner<>(0.0, 100.0, 20.0, 0.5);
    private final CheckBox cbPayee = new CheckBox("Facture payee");
    private final DatePicker dpPay = new DatePicker();
    private final Label err = new Label();

    public FactureFormDialog(Facture base) {
        setTitle(base == null ? "Nouvelle facture" : "Modifier la facture");
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        spTva.setEditable(true);
        dpEch.setPromptText("JJ/MM/AAAA");
        dpPay.setPromptText("JJ/MM/AAAA");

        GridPane g = new GridPane();
        g.setHgap(8);
        g.setVgap(8);
        g.setPadding(new Insets(12));

        int r = 0;
        g.add(new Label("Description *"), 0, r); g.add(tfDesc, 1, r++);
        g.add(new Label("Echeance"), 0, r);    g.add(dpEch, 1, r++);
        g.add(new Label("Montant HT (EUR) *"), 0, r); g.add(tfHt, 1, r++);
        g.add(new Label("TVA (%)"), 0, r);     g.add(spTva, 1, r++);
        g.add(cbPayee, 1, r++);
        g.add(new Label("Paiement le"), 0, r); g.add(dpPay, 1, r++);
        err.getStyleClass().add("error");
        g.add(err, 0, r, 2, 1);

        dpPay.setDisable(true);
        cbPayee.selectedProperty().addListener((obs, old, now) -> dpPay.setDisable(!now));

        getDialogPane().setContent(g);

        if (base != null) {
            tfDesc.setText(nonNull(base.getDescription()));
            if (base.getEcheance() != null) dpEch.setValue(base.getEcheance());
            if (base.getMontantHt() != null) tfHt.setText(base.getMontantHt().toPlainString());
            if (base.getTvaPct() != null) spTva.getValueFactory().setValue(base.getTvaPct().doubleValue());
            cbPayee.setSelected(base.isPaye());
            if (base.getDatePaiement() != null) {
                dpPay.setValue(base.getDatePaiement());
                dpPay.setDisable(false);
            }
        }

        Node ok = getDialogPane().lookupButton(ButtonType.OK);
        ok.addEventFilter(javafx.event.ActionEvent.ACTION, evt -> {
            String desc = tfDesc.getText().trim();
            if (desc.isEmpty()) {
                err.setText("La description est obligatoire.");
                evt.consume();
                return;
            }
            try {
                new BigDecimal(tfHt.getText().trim().replace(',', '.'));
            } catch (Exception ex) {
                err.setText("Montant HT invalide.");
                evt.consume();
            }
        });

        setResultConverter(btn -> {
            if (btn != ButtonType.OK) return null;
            BigDecimal ht = new BigDecimal(tfHt.getText().trim().replace(',', '.'));
            BigDecimal tva = BigDecimal.valueOf(spTva.getValue());
            BigDecimal mtva = Facture.calcTva(ht, tva);
            BigDecimal ttc = Facture.calcTtc(ht, tva);
            boolean payee = cbPayee.isSelected();
            return new Facture(
                    base != null ? base.getId() : 0,
                    base != null ? base.getPrestataireId() : 0,
                    tfDesc.getText().trim(),
                    dpEch.getValue(),
                    ht,
                    tva,
                    mtva,
                    ttc,
                    payee,
                    payee ? dpPay.getValue() : null,
                    base != null && base.isPreavisEnvoye()
            );
        });
    }

    private static String nonNull(String value) {
        return value == null ? "" : value;
    }
}
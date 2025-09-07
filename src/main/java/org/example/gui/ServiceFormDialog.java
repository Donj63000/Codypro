package org.example.gui;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import org.example.model.ServiceRow;

import java.time.ZoneId;

public final class ServiceFormDialog extends Dialog<ServiceRow> {
    private final DatePicker dpDate = new DatePicker();
    private final TextArea   taDesc = new TextArea();
    private final Label      err = new Label();

    public ServiceFormDialog(ServiceRow base) {
        setTitle(base==null ? "Nouveau service" : "Modifier le service");
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        GridPane g = new GridPane(); g.setHgap(8); g.setVgap(8); g.setPadding(new Insets(12));
        g.add(new Label("Date"), 0, 0); g.add(dpDate, 1, 0);
        g.add(new Label("Description *"), 0, 1); taDesc.setPrefRowCount(5); g.add(taDesc, 1, 1);
        err.getStyleClass().add("error"); g.add(err, 0, 2, 2, 1);
        getDialogPane().setContent(g);

        if (base != null) {
            taDesc.setText(base.desc());
            var d = base.date();
            if (d != null && !d.isBlank()) {
                try {
                    var fmt = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
                    var ld = java.time.LocalDate.parse(d, fmt);
                    dpDate.setValue(ld);
                } catch (Exception ignore) {}
            }
        }

        Node ok = getDialogPane().lookupButton(ButtonType.OK);
        ok.addEventFilter(javafx.event.ActionEvent.ACTION, evt -> {
            if (taDesc.getText()==null || taDesc.getText().trim().isEmpty()){
                err.setText("La description est obligatoire."); evt.consume(); return;
            }
            if (dpDate.getValue()==null) {
                err.setText("La date est obligatoire."); evt.consume();
            }
        });

        setResultConverter(bt -> {
            if (bt != ButtonType.OK) return null;
            String desc = taDesc.getText().trim();
            var ld = dpDate.getValue();
            var fmt = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
            String dateStr = ld.format(fmt);
            return new ServiceRow(desc, dateStr);
        });
    }
}

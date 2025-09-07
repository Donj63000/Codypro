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
            taDesc.setText(base.getDescription());
            if (base.getDateTs()!=null) {
                var ld = java.time.Instant.ofEpochMilli(base.getDateTs()).atZone(ZoneId.systemDefault()).toLocalDate();
                dpDate.setValue(ld);
            }
        }

        Node ok = getDialogPane().lookupButton(ButtonType.OK);
        ok.addEventFilter(javafx.event.ActionEvent.ACTION, evt -> {
            if (taDesc.getText()==null || taDesc.getText().trim().isEmpty()){
                err.setText("La description est obligatoire."); evt.consume();
            }
        });

        setResultConverter(bt -> {
            if (bt != ButtonType.OK) return null;
            ServiceRow s = (base != null ? base : new ServiceRow());
            s.setDescription(taDesc.getText().trim());
            if (dpDate.getValue()!=null){
                var ld = dpDate.getValue();
                var fmt = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
                s.setDate(ld.format(fmt));
                s.setDateTs(ld.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli());
            } else { s.setDate(null); s.setDateTs(null); }
            return s;
        });
    }
}

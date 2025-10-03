package org.example.gui;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.collections.FXCollections;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import org.example.model.ServiceRow;
import org.example.model.ServiceStatus;

import javafx.util.StringConverter;

public final class ServiceFormDialog extends Dialog<ServiceRow> {
    private final DatePicker dpDate = new DatePicker();
    private final TextArea   taDesc = new TextArea();
    private final ComboBox<ServiceStatus> cbStatus = new ComboBox<>();
    private final Label      err = new Label();

    public ServiceFormDialog(ServiceRow base) {
        setTitle(base==null ? "Nouveau service" : "Modifier le service");
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        GridPane g = new GridPane(); g.setHgap(8); g.setVgap(8); g.setPadding(new Insets(12));
        g.add(new Label("Date"), 0, 0); g.add(dpDate, 1, 0);
        g.add(new Label("Statut"), 0, 1); g.add(cbStatus, 1, 1);
        g.add(new Label("Description *"), 0, 2); taDesc.setPrefRowCount(5); g.add(taDesc, 1, 2);
        err.getStyleClass().add("error"); g.add(err, 0, 3, 2, 1);
        getDialogPane().setContent(g);

        cbStatus.setItems(FXCollections.observableArrayList(ServiceStatus.values()));
        cbStatus.setConverter(new StringConverter<>() {
            @Override public String toString(ServiceStatus status) { return status == null ? "" : status.label(); }
            @Override public ServiceStatus fromString(String string) { return ServiceStatus.from(string); }
        });

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
            cbStatus.setValue(base.status());
        }
        if (cbStatus.getValue() == null) cbStatus.setValue(ServiceStatus.EN_ATTENTE);

        Node ok = getDialogPane().lookupButton(ButtonType.OK);
        ok.addEventFilter(javafx.event.ActionEvent.ACTION, evt -> {
            if (taDesc.getText()==null || taDesc.getText().trim().isEmpty()){
                err.setText("La description est obligatoire."); evt.consume(); return;
            }
            if (dpDate.getValue()==null) {
                err.setText("La date est obligatoire."); evt.consume();
            }
            if (cbStatus.getValue() == null) {
                cbStatus.setValue(ServiceStatus.EN_ATTENTE);
            }
        });

        setResultConverter(bt -> {
            if (bt != ButtonType.OK) return null;
            String desc = taDesc.getText().trim();
            var ld = dpDate.getValue();
            var fmt = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
            String dateStr = ld.format(fmt);
            ServiceStatus status = cbStatus.getValue() == null ? ServiceStatus.EN_ATTENTE : cbStatus.getValue();
            Integer id = base != null ? base.id() : null;
            return new ServiceRow(id, desc, dateStr, status);
        });
    }
}

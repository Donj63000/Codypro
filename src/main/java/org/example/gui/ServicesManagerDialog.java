package org.example.gui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Callback;
import org.example.dao.DB;
import org.example.model.ServiceRow;
import org.example.model.ServiceStatus;

import java.util.Objects;

final class ServicesManagerDialog extends Dialog<Void> {
    private final DB dao;
    private final int prestataireId;
    private final TableView<ServiceRow> table = new TableView<>();
    private final ObservableList<ServiceRow> items = FXCollections.observableArrayList();

    ServicesManagerDialog(DB dao, int prestataireId) {
        this.dao = Objects.requireNonNull(dao, "dao");
        this.prestataireId = prestataireId;

        setTitle("Services du prestataire");
        getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        buildTable();
        VBox content = new VBox(10, buildToolbar(), table);
        content.setPadding(new Insets(12));
        VBox.setVgrow(table, Priority.ALWAYS);
        getDialogPane().setContent(content);

        loadServices();

        setResultConverter(btn -> null);
    }

    private void buildTable() {
        table.setItems(items);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("Aucun service"));
        table.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);

        TableColumn<ServiceRow, String> cDate = new TableColumn<>("Date");
        cDate.setMinWidth(100);
        cDate.setCellValueFactory(cd -> new javafx.beans.property.ReadOnlyStringWrapper(cd.getValue().date()));

        TableColumn<ServiceRow, ServiceStatus> cStatus = new TableColumn<>("Statut");
        cStatus.setMinWidth(120);
        cStatus.setCellValueFactory(cd -> new javafx.beans.property.ReadOnlyObjectWrapper<>(cd.getValue().status()));
        cStatus.setCellFactory(statusCellFactory());

        TableColumn<ServiceRow, String> cDesc = new TableColumn<>("Description");
        cDesc.setCellValueFactory(cd -> new javafx.beans.property.ReadOnlyStringWrapper(cd.getValue().desc()));

        table.getColumns().setAll(cDate, cStatus, cDesc);

        table.setRowFactory(tv -> {
            TableRow<ServiceRow> row = new TableRow<>();
            ContextMenu menu = new ContextMenu();
            for (ServiceStatus status : ServiceStatus.values()) {
                MenuItem item = new MenuItem(status.label());
                item.setOnAction(e -> {
                    ServiceRow current = row.getItem();
                    if (current != null) updateStatus(current, status);
                });
                menu.getItems().add(item);
            }
            row.contextMenuProperty().bind(javafx.beans.binding.Bindings.when(row.emptyProperty()).then((ContextMenu) null).otherwise(menu));
            return row;
        });
    }

    private Callback<TableColumn<ServiceRow, ServiceStatus>, TableCell<ServiceRow, ServiceStatus>> statusCellFactory() {
        return col -> new TableCell<>() {
            private final Label pill = new Label();
            {
                pill.getStyleClass().add("status-pill");
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            }

            @Override
            protected void updateItem(ServiceStatus status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setGraphic(null);
                } else {
                    pill.setText(status.label());
                    pill.getStyleClass().setAll("status-pill", "status-pill--" + status.cssClassSuffix());
                    setGraphic(pill);
                }
            }
        };
    }

    private HBox buildToolbar() {
        Button btnAdd = new Button("Ajouter");
        Button btnEdit = new Button("Modifier");
        Button btnDelete = new Button("Supprimer");

        btnAdd.setOnAction(e -> onAdd());
        btnEdit.setOnAction(e -> onEdit());
        btnDelete.setOnAction(e -> onDelete());

        btnEdit.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        btnDelete.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());

        HBox bar = new HBox(8, btnAdd, btnEdit, btnDelete);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private void loadServices() {
        loadServices(null);
    }

    private void loadServices(Integer selectId) {
        try {
            items.setAll(dao.services(prestataireId));
            if (selectId != null) selectRowById(selectId);
        } catch (Exception ex) {
            showError(ex);
        }
    }

    private void onAdd() {
        ServiceFormDialog dlg = new ServiceFormDialog(null);
        ThemeManager.apply(dlg);
        dlg.showAndWait().ifPresent(service -> {
            try {
                int id = dao.insertService(prestataireId, service);
                loadServices(id);
            } catch (Exception ex) {
                showError(ex);
            }
        });
    }

    private void onEdit() {
        ServiceRow selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        ServiceFormDialog dlg = new ServiceFormDialog(selected);
        ThemeManager.apply(dlg);
        dlg.showAndWait().ifPresent(result -> {
            ServiceRow updated = new ServiceRow(selected.id(), result.desc(), result.date(), result.status());
            try {
                dao.updateService(updated);
                loadServices(updated.id());
            } catch (Exception ex) {
                showError(ex);
            }
        });
    }

    private void onDelete() {
        ServiceRow selected = table.getSelectionModel().getSelectedItem();
        if (selected == null || selected.id() == null) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Supprimer ce service ?", ButtonType.YES, ButtonType.NO);
        ThemeManager.apply(confirm);
        if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) return;
        try {
            dao.deleteService(selected.id());
            loadServices();
        } catch (Exception ex) {
            showError(ex);
        }
    }

    private void updateStatus(ServiceRow row, ServiceStatus status) {
        if (row.id() == null || status == null || status == row.status()) return;
        try {
            dao.updateServiceStatus(row.id(), status);
            loadServices(row.id());
        } catch (Exception ex) {
            showError(ex);
        }
    }

    private ServiceRow selectRowById(Integer id) {
        if (id == null) return null;
        for (ServiceRow row : items) {
            if (id.equals(row.id())) {
                table.getSelectionModel().select(row);
                table.scrollTo(row);
                return row;
            }
        }
        return null;
    }

    private void showError(Exception ex) {
        Alert alert = new Alert(Alert.AlertType.ERROR, ex.getMessage() == null ? ex.toString() : ex.getMessage(), ButtonType.OK);
        ThemeManager.apply(alert);
        alert.showAndWait();
    }
}

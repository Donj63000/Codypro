package org.example.gui;

import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.example.dao.DB;
import org.example.model.Facture;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

final class FacturesManagerDialog extends Dialog<Void> {

    private final DB dao;
    private final int prestataireId;
    private final String prestataireNom;
    private final TableView<Facture> table = new TableView<>();
    private final ObservableList<Facture> items = FXCollections.observableArrayList();
    private final ToggleGroup filterGroup = new ToggleGroup();
    private final Label summaryTotalTtc = new Label();
    private final Label summaryDue = new Label();
    private final Label summaryCount = new Label();
    private final java.text.NumberFormat money = java.text.NumberFormat.getCurrencyInstance(Locale.FRANCE);
    private boolean mutated;

    FacturesManagerDialog(DB dao, int prestataireId, String prestataireNom) {
        this.dao = Objects.requireNonNull(dao, "dao");
        this.prestataireId = prestataireId;
        this.prestataireNom = prestataireNom == null ? "" : prestataireNom;

        setTitle("Factures du prestataire");
        getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        buildTable();
        BorderPane toolbar = buildToolbar();
        HBox summary = buildSummaryBar();

        VBox content = new VBox(12, toolbar, table, summary);
        content.setPadding(new Insets(12));
        VBox.setVgrow(table, Priority.ALWAYS);
        getDialogPane().setContent(content);

        loadFactures(null);

        setResultConverter(btn -> null);
    }

    boolean hasMutations() {
        return mutated;
    }

    private void buildTable() {
        table.setItems(items);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("Aucune facture"));
        table.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);

        TableColumn<Facture, String> cDesc = new TableColumn<>("Description");
        cDesc.setCellValueFactory(cd -> cd.getValue().descriptionProperty());
        cDesc.setMinWidth(160);

        TableColumn<Facture, String> cEch = new TableColumn<>("Echeance");
        cEch.setMinWidth(100);
        cEch.setCellValueFactory(cd -> new javafx.beans.property.ReadOnlyStringWrapper(cd.getValue().getEcheanceFr()));

        TableColumn<Facture, String> cHt = new TableColumn<>("Montant HT");
        cHt.setMinWidth(100);
        cHt.setCellValueFactory(cd -> new javafx.beans.property.ReadOnlyStringWrapper(formatMoney(cd.getValue().getMontantHt())));

        TableColumn<Facture, String> cTtc = new TableColumn<>("Montant TTC");
        cTtc.setMinWidth(110);
        cTtc.setCellValueFactory(cd -> new javafx.beans.property.ReadOnlyStringWrapper(formatMoney(cd.getValue().getMontantTtc())));

        TableColumn<Facture, String> cPayee = new TableColumn<>("Payee");
        cPayee.setMinWidth(70);
        cPayee.setMaxWidth(80);
        cPayee.setCellValueFactory(cd -> new javafx.beans.property.ReadOnlyStringWrapper(cd.getValue().isPaye() ? "Oui" : "Non"));

        TableColumn<Facture, String> cPayDate = new TableColumn<>("Paiement le");
        cPayDate.setMinWidth(110);
        cPayDate.setCellValueFactory(cd -> new javafx.beans.property.ReadOnlyStringWrapper(cd.getValue().getDatePaiementFr()));

        table.getColumns().setAll(cDesc, cEch, cHt, cTtc, cPayee, cPayDate);

        table.setRowFactory(tv -> {
            TableRow<Facture> row = new TableRow<>();
            row.setOnMouseClicked(ev -> {
                if (ev.getClickCount() == 2 && !row.isEmpty()) {
                    onEdit();
                }
            });
            return row;
        });
    }

    private BorderPane buildToolbar() {
        Button btnAdd = new Button("Ajouter");
        Button btnEdit = new Button("Modifier");
        Button btnDelete = new Button("Supprimer");
        Button btnMarkPaid = new Button("Marquer payee");
        Button btnMarkUnpaid = new Button("Marquer impayee");

        btnAdd.setOnAction(e -> onAdd());
        btnEdit.setOnAction(e -> onEdit());
        btnDelete.setOnAction(e -> onDelete());
        btnMarkPaid.setOnAction(e -> onTogglePaid(true));
        btnMarkUnpaid.setOnAction(e -> onTogglePaid(false));

        btnEdit.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        btnDelete.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        btnMarkPaid.disableProperty().bind(Bindings.createBooleanBinding(
                () -> {
                    Facture f = table.getSelectionModel().getSelectedItem();
                    return f == null || f.isPaye();
                },
                table.getSelectionModel().selectedItemProperty()));
        btnMarkUnpaid.disableProperty().bind(Bindings.createBooleanBinding(
                () -> {
                    Facture f = table.getSelectionModel().getSelectedItem();
                    return f == null || !f.isPaye();
                },
                table.getSelectionModel().selectedItemProperty()));

        HBox actions = new HBox(8, btnAdd, btnEdit, btnDelete, new Separator(Orientation.VERTICAL), btnMarkPaid, btnMarkUnpaid);
        actions.setAlignment(Pos.CENTER_LEFT);

        ToggleButton all = buildFilterButton("Toutes", null);
        ToggleButton due = buildFilterButton("Impayees", Boolean.FALSE);
        ToggleButton paid = buildFilterButton("Payees", Boolean.TRUE);
        filterGroup.selectToggle(all);
        filterGroup.selectedToggleProperty().addListener((obs, old, sel) -> loadFactures(null));

        HBox filters = new HBox(6, new Label("Filtrer :"), all, due, paid);
        filters.setAlignment(Pos.CENTER_RIGHT);

        BorderPane bar = new BorderPane();
        bar.setLeft(actions);
        bar.setRight(filters);
        bar.setPadding(new Insets(0, 0, 4, 0));
        return bar;
    }

    private HBox buildSummaryBar() {
        Label name = new Label("Prestataire : " + prestataireNom);
        name.getStyleClass().add("summary-value");
        summaryCount.getStyleClass().add("summary-value");
        summaryTotalTtc.getStyleClass().add("summary-value");
        summaryDue.getStyleClass().add("summary-value");

        HBox box = new HBox(16, name, summaryCount, summaryTotalTtc, summaryDue);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private ToggleButton buildFilterButton(String label, Boolean value) {
        ToggleButton btn = new ToggleButton(label);
        btn.setUserData(value);
        btn.setToggleGroup(filterGroup);
        btn.setMaxWidth(Double.MAX_VALUE);
        return btn;
    }

    private void onAdd() {
        FactureFormDialog dlg = new FactureFormDialog(null);
        ThemeManager.apply(dlg);
        dlg.showAndWait().ifPresent(result -> {
            try {
                int id = dao.insertFacture(prestataireId, result);
                mutated = true;
                loadFactures(id);
            } catch (Exception ex) {
                showError(ex);
            }
        });
    }

    private void onEdit() {
        Facture selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        FactureFormDialog dlg = new FactureFormDialog(selected);
        ThemeManager.apply(dlg);
        dlg.showAndWait().ifPresent(result -> {
            try {
                dao.updateFacture(result);
                mutated = true;
                loadFactures(result.getId());
            } catch (Exception ex) {
                showError(ex);
            }
        });
    }

    private void onDelete() {
        Facture selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Supprimer cette facture ?", ButtonType.YES, ButtonType.NO);
        ThemeManager.apply(confirm);
        if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) return;
        try {
            dao.deleteFacture(selected.getId());
            mutated = true;
            loadFactures(null);
        } catch (Exception ex) {
            showError(ex);
        }
    }

    private void onTogglePaid(boolean payee) {
        Facture selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        LocalDate datePaiement = payee ? selected.getDatePaiement() : null;
        if (payee) {
            Dialog<LocalDate> dlg = new Dialog<>();
            dlg.setTitle("Date de paiement");
            dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
            DatePicker picker = new DatePicker(datePaiement != null ? datePaiement : LocalDate.now());
            VBox box = new VBox(8, new Label("Date de paiement"), picker);
            box.setPadding(new Insets(12));
            dlg.getDialogPane().setContent(box);
            ThemeManager.apply(dlg);
            dlg.setResultConverter(btn -> btn == ButtonType.OK ? picker.getValue() : null);
            datePaiement = dlg.showAndWait().orElse(null);
            if (datePaiement == null) return;
        }

        Facture updated = new Facture(
                selected.getId(),
                selected.getPrestataireId(),
                selected.getDescription(),
                selected.getEcheance(),
                selected.getMontantHt(),
                selected.getTvaPct(),
                selected.getMontantTva(),
                selected.getMontantTtc(),
                payee,
                datePaiement,
                selected.isPreavisEnvoye()
        );
        try {
            dao.updateFacture(updated);
            mutated = true;
            loadFactures(updated.getId());
        } catch (Exception ex) {
            showError(ex);
        }
    }

    private void loadFactures(Integer selectId) {
        try {
            Boolean filter = null;
            Toggle selectedToggle = filterGroup.getSelectedToggle();
            if (selectedToggle != null) {
                filter = (Boolean) selectedToggle.getUserData();
            }
            List<Facture> factures = dao.factures(prestataireId, filter);
            items.setAll(factures);
            if (selectId != null) {
                selectById(selectId);
            }
            updateSummary();
        } catch (Exception ex) {
            showError(ex);
        }
    }

    private void selectById(Integer id) {
        if (id == null) return;
        for (Facture f : items) {
            if (f.getId() == id) {
                table.getSelectionModel().select(f);
                table.scrollTo(f);
                return;
            }
        }
    }

    private void updateSummary() {
        summaryCount.setText("Factures : " + items.size());

        BigDecimal totalTtc = BigDecimal.ZERO;
        BigDecimal unpaid = BigDecimal.ZERO;
        int unpaidCount = 0;
        for (Facture f : items) {
            if (f.getMontantTtc() != null) {
                totalTtc = totalTtc.add(f.getMontantTtc());
                if (!f.isPaye()) {
                    unpaid = unpaid.add(f.getMontantTtc());
                    unpaidCount++;
                }
            }
        }
        summaryTotalTtc.setText("Total TTC : " + formatMoney(totalTtc));
        summaryDue.setText("Impayes : " + formatMoney(unpaid) + " (" + unpaidCount + ")");
    }

    private String formatMoney(BigDecimal value) {
        if (value == null) return money.format(0);
        return money.format(value.doubleValue());
    }

    private void showError(Exception ex) {
        Alert alert = new Alert(Alert.AlertType.ERROR, ex.getMessage() == null ? ex.toString() : ex.getMessage(), ButtonType.OK);
        ThemeManager.apply(alert);
        alert.showAndWait();
    }
}

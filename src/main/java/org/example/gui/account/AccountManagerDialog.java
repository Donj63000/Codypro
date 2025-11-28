package org.example.gui.account;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.example.gui.ThemeManager;
import org.example.security.AuthService;

import java.util.Arrays;
import java.util.Optional;

public final class AccountManagerDialog extends Stage {

    private final AuthService authService;
    private final AuthService.Session currentSession;
    private final Runnable onRefresh;

    private final ObservableList<AuthService.UserSummary> users = FXCollections.observableArrayList();
    private final TableView<AuthService.UserSummary> table = new TableView<>();

    private AccountManagerDialog(Stage owner,
                                 AuthService authService,
                                 AuthService.Session currentSession,
                                 Runnable onRefresh) {
        this.authService = authService;
        this.currentSession = currentSession;
        this.onRefresh = onRefresh == null ? () -> {} : onRefresh;
        initOwner(owner);
        initModality(Modality.WINDOW_MODAL);
        setTitle("Configuration des comptes");

        BorderPane root = new BorderPane();
        root.getStyleClass().add("account-manager-dialog");
        root.setPadding(new Insets(20));

        Label header = new Label("Gestion des comptes");
        header.getStyleClass().add("dialog-title");

        Label subtitle = new Label("Créez, renommez ou supprimez les identifiants instantanément.");
        subtitle.getStyleClass().add("dialog-subtitle");

        VBox headerBox = new VBox(6, header, subtitle);
        headerBox.setAlignment(Pos.CENTER_LEFT);
        headerBox.getStyleClass().add("dialog-header");
        headerBox.setPadding(new Insets(0, 0, 14, 0));
        root.setTop(headerBox);

        table.setItems(users);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("Aucun compte défini"));
        table.getStyleClass().add("modern-table");

        TableColumn<AuthService.UserSummary, String> colUser = new TableColumn<>("Utilisateur");
        colUser.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().username()));

        TableColumn<AuthService.UserSummary, String> colCreated = new TableColumn<>("Créé le");
        colCreated.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
                Optional.ofNullable(data.getValue().createdAt()).orElse("")));
        colCreated.setMinWidth(140);

        table.getColumns().setAll(colUser, colCreated);
        root.setCenter(table);

        Button btnAdd = new Button("Créer");
        Button btnRename = new Button("Renommer");
        Button btnPwd = new Button("Changer mot de passe");
        Button btnDelete = new Button("Supprimer");
        Button btnClose = new Button("Fermer");

        btnAdd.setOnAction(e -> onCreate());
        btnRename.setOnAction(e -> onRename());
        btnPwd.setOnAction(e -> onChangePassword());
        btnDelete.setOnAction(e -> onDelete());
        btnClose.setOnAction(e -> close());
        btnRename.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        btnPwd.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        btnDelete.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());

        btnAdd.getStyleClass().addAll("accent");
        btnRename.getStyleClass().addAll("outline");
        btnPwd.getStyleClass().addAll("ghost");
        btnDelete.getStyleClass().addAll("danger");

        HBox actions = new HBox(10, btnAdd, btnRename, btnPwd, btnDelete);
        actions.setAlignment(Pos.CENTER_LEFT);

        HBox footer = new HBox(16, actions, btnClose);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setPadding(new Insets(16, 0, 0, 0));
        footer.getStyleClass().add("dialog-footer");

        root.setBottom(footer);

        setScene(new Scene(root, 520, 340));
        ThemeManager.apply(getScene());
        refreshUsers();
    }

    public static void open(Stage owner,
                            AuthService authService,
                            AuthService.Session currentSession,
                            Runnable onRefresh) {
        AccountManagerDialog dialog = new AccountManagerDialog(owner, authService, currentSession, onRefresh);
        dialog.show();
    }

    private void refreshUsers() {
        try {
            users.setAll(authService.listUsers());
        } catch (Exception ex) {
            showError("Impossible de charger les comptes: " + ex.getMessage());
        }
    }

    private void onCreate() {
        Optional<Credentials> result = promptCreation();
        if (result.isEmpty()) return;

        Credentials cred = result.get();
        try {
            authService.register(cred.username(), cred.password());
            showInfo("Compte créé", "Le compte \"" + cred.username() + "\" a été ajouté.");
            refreshUsers();
            onRefresh.run();
        } catch (Exception ex) {
            showError(ex.getMessage() != null ? ex.getMessage() : "Impossible de créer le compte.");
        } finally {
            cred.dispose();
        }
    }

    private void onRename() {
        AuthService.UserSummary selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("Veuillez sélectionner un compte à renommer.");
            return;
        }
        if (selected.id() == currentSession.userId()) {
            showWarning("Vous ne pouvez pas renommer le compte actuellement connecté.");
            return;
        }
        TextInputDialog dialog = new TextInputDialog(selected.username());
        dialog.setTitle("Renommer le compte");
        dialog.setHeaderText("Nouveau nom pour \"" + selected.username() + "\"");
        dialog.setContentText("Nom d'utilisateur :");
        dialog.initOwner(getOwner());
        ThemeManager.apply(dialog);

        Optional<String> result = dialog.showAndWait();
        if (result.isEmpty()) return;

        String newName = result.get();
        try {
            authService.updateUsername(selected.id(), newName);
            showInfo("Compte renommé", "Le compte a été renommé en \"" + newName + "\".");
            refreshUsers();
            onRefresh.run();
        } catch (Exception ex) {
            showError(ex.getMessage() != null ? ex.getMessage() : "Impossible de renommer le compte.");
        }
    }

    private void onChangePassword() {
        AuthService.UserSummary selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("Veuillez sélectionner un compte.");
            return;
        }
        Optional<PasswordChange> result = promptPasswordChange(selected.username());
        if (result.isEmpty()) return;
        PasswordChange change = result.get();
        try {
            authService.changePassword(selected.id(), change.oldPwd(), change.newPwd());
            showInfo("Mot de passe modifié", "Le mot de passe du compte \"" + selected.username() + "\" a été mis à jour.");
            onRefresh.run();
        } catch (Exception ex) {
            showError(ex.getMessage() != null ? ex.getMessage() : "Échec du changement de mot de passe.");
        } finally {
            change.dispose();
        }
    }

    private void onDelete() {
        AuthService.UserSummary selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("Veuillez sélectionner un compte.");
            return;
        }
        if (selected.id() == currentSession.userId()) {
            showWarning("Vous ne pouvez pas supprimer la session actuellement connectée.");
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirmation");
        confirm.setHeaderText("Supprimer le compte \"" + selected.username() + "\" ?");
        confirm.setContentText("Cette action est définitive. Les données chiffrées associées seront supprimées.");
        confirm.initOwner(getOwner());

        Optional<ButtonType> res = confirm.showAndWait();
        if (res.isEmpty() || res.get() != ButtonType.OK) return;

        try {
            authService.deleteUser(selected.id(), true);
            showInfo("Compte supprimé", "Le compte a été supprimé.");
            refreshUsers();
            onRefresh.run();
        } catch (Exception ex) {
            showError(ex.getMessage() != null ? ex.getMessage() : "Impossible de supprimer le compte.");
        }
    }

    private Optional<Credentials> promptCreation() {
        Dialog<Credentials> dialog = new Dialog<>();
        dialog.setTitle("Créer un compte");
        dialog.setHeaderText("Ajouter un nouvel utilisateur");
        dialog.initOwner(getOwner());

        ButtonType createType = new ButtonType("Créer", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createType, ButtonType.CANCEL);

        TextField tfUser = new TextField();
        PasswordField pfPwd = new PasswordField();
        PasswordField pfConfirm = new PasswordField();

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 10, 10, 10));

        grid.addRow(0, new Label("Nom d'utilisateur"), tfUser);
        grid.addRow(1, new Label("Mot de passe"), pfPwd);
        grid.addRow(2, new Label("Confirmation"), pfConfirm);

        dialog.getDialogPane().setContent(grid);
        ThemeManager.apply(dialog);

        dialog.setResultConverter(button -> {
            if (button == createType) {
                String username = tfUser.getText() == null ? "" : tfUser.getText().trim();
                String pwd = pfPwd.getText();
                String confirm = pfConfirm.getText();

                if (username.isEmpty()) {
                    showWarning("Le nom d'utilisateur ne peut pas être vide.");
                    return null;
                }
                if (pwd == null || pwd.isBlank()) {
                    showWarning("Le mot de passe ne peut pas être vide.");
                    return null;
                }
                if (pwd.length() < AuthService.minimumPasswordLength()) {
                    showWarning("Le mot de passe doit contenir au moins " + AuthService.minimumPasswordLength() + " caractères.");
                    return null;
                }
                if (pwd.chars().allMatch(Character::isWhitespace)) {
                    showWarning("Le mot de passe ne peut pas être uniquement composé d'espaces.");
                    return null;
                }
                if (!pwd.equals(confirm)) {
                    showWarning("Les mots de passe ne correspondent pas.");
                    return null;
                }
                return new Credentials(username, pwd.toCharArray());
            }
            return null;
        });

        Optional<Credentials> out = dialog.showAndWait();
        pfPwd.clear();
        pfConfirm.clear();
        return out;
    }

    private Optional<PasswordChange> promptPasswordChange(String username) {
        Dialog<PasswordChange> dialog = new Dialog<>();
        dialog.setTitle("Changer le mot de passe");
        dialog.setHeaderText("Mettre à jour \"" + username + "\"");
        dialog.initOwner(getOwner());

        ButtonType apply = new ButtonType("Modifier", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(apply, ButtonType.CANCEL);

        PasswordField pfOld = new PasswordField();
        PasswordField pfNew = new PasswordField();
        PasswordField pfConfirm = new PasswordField();

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 10, 10, 10));

        grid.addRow(0, new Label("Mot de passe actuel"), pfOld);
        grid.addRow(1, new Label("Nouveau mot de passe"), pfNew);
        grid.addRow(2, new Label("Confirmation"), pfConfirm);

        dialog.getDialogPane().setContent(grid);
        ThemeManager.apply(dialog);

        dialog.setResultConverter(button -> {
            if (button == apply) {
                String old = pfOld.getText();
                String fresh = pfNew.getText();
                String confirm = pfConfirm.getText();

                if (old == null || old.isBlank()) {
                    showWarning("Le mot de passe actuel est requis.");
                    return null;
                }
                if (fresh == null || fresh.isBlank()) {
                    showWarning("Le nouveau mot de passe est requis.");
                    return null;
                }
                if (fresh.length() < AuthService.minimumPasswordLength()) {
                    showWarning("Le nouveau mot de passe doit contenir au moins " + AuthService.minimumPasswordLength() + " caractères.");
                    return null;
                }
                if (fresh.chars().allMatch(Character::isWhitespace)) {
                    showWarning("Le nouveau mot de passe ne peut pas être uniquement composé d'espaces.");
                    return null;
                }
                if (!fresh.equals(confirm)) {
                    showWarning("La confirmation ne correspond pas.");
                    return null;
                }
                if (fresh.equals(old)) {
                    showWarning("Le nouveau mot de passe doit être différent de l'actuel.");
                    return null;
                }
                return new PasswordChange(old.toCharArray(), fresh.toCharArray());
            }
            return null;
        });

        Optional<PasswordChange> out = dialog.showAndWait();
        pfOld.clear();
        pfNew.clear();
        pfConfirm.clear();
        return out;
    }

    private void showWarning(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING, message, ButtonType.OK);
        alert.initOwner(getOwner());
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.initOwner(getOwner());
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        alert.initOwner(getOwner());
        alert.setHeaderText(title);
        alert.showAndWait();
    }

    private record Credentials(String username, char[] password) {
        void dispose() {
            if (password != null) {
                Arrays.fill(password, '\0');
            }
        }
    }

    private record PasswordChange(char[] oldPwd, char[] newPwd) {
        void dispose() {
            if (oldPwd != null) Arrays.fill(oldPwd, '\0');
            if (newPwd != null) Arrays.fill(newPwd, '\0');
        }
    }
}

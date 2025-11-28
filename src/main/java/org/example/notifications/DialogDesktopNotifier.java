package org.example.notifications;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import org.example.gui.ThemeManager;

/**
 * Fallback notifier that surfaces notifications inside the JavaFX app when the system tray is unavailable.
 */
public final class DialogDesktopNotifier implements DesktopNotifier {

    @Override
    public void notify(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION, message == null ? "" : message, ButtonType.OK);
            alert.setHeaderText(title == null ? "Notification" : title);
            ThemeManager.apply(alert);
            alert.show();
        });
    }
}


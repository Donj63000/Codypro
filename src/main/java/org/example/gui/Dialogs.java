package org.example.gui;

import javafx.scene.control.Alert;
import javafx.scene.control.DialogPane;
import javafx.scene.control.TextArea;
import javafx.stage.Window;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Objects;

/**
 * Centralised helpers to display themed dialogs with optional exception details.
 */
public final class Dialogs {

    private Dialogs() {
    }

    public static void info(Window owner, String title, String message) {
        show(Alert.AlertType.INFORMATION, owner, defaultTitle(title, "Information"), message, null);
    }

    public static void warning(Window owner, String title, String message) {
        show(Alert.AlertType.WARNING, owner, defaultTitle(title, "Attention"), message, null);
    }

    public static void error(Window owner, String message) {
        show(Alert.AlertType.ERROR, owner, "Erreur", message, null);
    }

    public static void error(Window owner, String message, Throwable cause) {
        show(Alert.AlertType.ERROR, owner, "Erreur", message, cause);
    }

    public static void error(Window owner, Throwable cause) {
        String message = cause == null ? "Une erreur est survenue." : Objects.toString(cause.getMessage(), "Une erreur est survenue.");
        show(Alert.AlertType.ERROR, owner, "Erreur", message, cause);
    }

    private static void show(Alert.AlertType type,
                             Window owner,
                             String title,
                             String message,
                             Throwable cause) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message == null || message.isBlank() ? null : message.trim());
        if (owner != null) {
            alert.initOwner(owner);
        }
        ThemeManager.apply(alert);
        if (cause != null) {
            DialogPane pane = alert.getDialogPane();
            TextArea details = new TextArea(stackTrace(cause));
            details.setEditable(false);
            details.setWrapText(false);
            details.setMaxWidth(Double.MAX_VALUE);
            details.setMaxHeight(Double.MAX_VALUE);
            pane.setExpandableContent(details);
            pane.setExpanded(false);
        }
        alert.showAndWait();
    }

    private static String stackTrace(Throwable cause) {
        StringWriter sw = new StringWriter();
        cause.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    private static String defaultTitle(String candidate, String fallback) {
        return candidate == null || candidate.isBlank() ? fallback : candidate;
    }
}

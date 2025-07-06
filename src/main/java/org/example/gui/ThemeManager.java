package org.example.gui;

import javafx.scene.Scene;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;

/** Utility class for applying the global CSS theme. */
public final class ThemeManager {
    private static final String THEME =
            ThemeManager.class.getResource("/css/dark.css").toExternalForm();

    private ThemeManager() {}

    /** Apply the application stylesheet to the given scene. */
    public static void apply(Scene scene) {
        if (scene != null && !scene.getStylesheets().contains(THEME)) {
            scene.getStylesheets().add(THEME);
        }
    }

    /** Apply the application stylesheet to the given dialog. */
    public static void apply(Dialog<?> dialog) {
        DialogPane pane = dialog.getDialogPane();
        if (pane != null && !pane.getStylesheets().contains(THEME)) {
            pane.getStylesheets().add(THEME);
        }
    }
}

package org.example.gui;

import javafx.scene.Scene;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import java.util.List;

public final class ThemeManager {
    private static final String CSS = ThemeManager.class
            .getResource("/css/dark.css")
            .toExternalForm();

    private ThemeManager() {}

    public static void apply(Scene scene) {
        add(scene == null ? null : scene.getStylesheets());
    }

    public static void apply(Dialog<?> dialog) {
        DialogPane pane = dialog == null ? null : dialog.getDialogPane();
        add(pane == null ? null : pane.getStylesheets());
    }

    private static void add(List<String> list) {
        if (list != null && !list.contains(CSS)) list.add(CSS);
    }
}

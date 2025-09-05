package org.example.gui;

import javafx.scene.Scene;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import java.util.List;

public final class ThemeManager {
    public enum Theme { DARK, LIGHT }

    private static final String DARK_URL = ThemeManager.class.getResource("/css/dark.css").toExternalForm();
    private static final String LIGHT_URL = ThemeManager.class.getResource("/css/light.css").toExternalForm();

    private static Theme current = Theme.DARK;

    private ThemeManager() {}

    public static boolean isDark() { return current == Theme.DARK; }

    public static void setTheme(Theme theme) { current = theme == null ? Theme.DARK : theme; }

    public static void toggle(Scene scene) {
        current = isDark() ? Theme.LIGHT : Theme.DARK;
        reapply(scene);
    }

    public static void apply(Scene scene) {
        add(scene == null ? null : scene.getStylesheets());
    }

    public static void apply(Dialog<?> dialog) {
        DialogPane pane = dialog == null ? null : dialog.getDialogPane();
        add(pane == null ? null : pane.getStylesheets());
    }

    private static void add(List<String> list) {
        if (list == null) return;
        list.remove(DARK_URL); list.remove(LIGHT_URL);
        list.add(isDark() ? DARK_URL : LIGHT_URL);
    }

    private static void reapply(Scene scene) { apply(scene); }
}

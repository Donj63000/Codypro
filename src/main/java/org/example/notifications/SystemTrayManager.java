package org.example.notifications;

import java.awt.EventQueue;
import java.awt.Menu;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import javafx.application.Platform;
import javafx.stage.Stage;

/**
 * Binds the JavaFX stage with the system tray icon: shows the window, offers snooze, handles quit.
 */
public final class SystemTrayManager {

    private final Stage stage;
    private final SystemTrayNotifier notifier;
    private final SnoozeHandler snoozeHandler;
    private final Runnable onQuit;

    private volatile Menu snoozeMenu;

    public interface SnoozeHandler {
        void snooze(Duration duration);
    }

    public SystemTrayManager(Stage stage,
                             SystemTrayNotifier notifier,
                             SnoozeHandler snoozeHandler,
                             Runnable onQuit) {
        this.stage = Objects.requireNonNull(stage, "stage");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.snoozeHandler = Objects.requireNonNull(snoozeHandler, "snoozeHandler");
        this.onQuit = Objects.requireNonNull(onQuit, "onQuit");
    }

    public void install(int defaultSnoozeMinutes) {
        PopupMenu menu = new PopupMenu();

        MenuItem showItem = new MenuItem("Afficher CodyPrestataires");
        showItem.addActionListener(e -> Platform.runLater(() -> {
            if (!stage.isShowing()) {
                stage.show();
            }
            stage.toFront();
        }));
        menu.add(showItem);

        Menu snooze = new Menu("Reporter...");
        this.snoozeMenu = snooze;
        menu.add(snooze);
        rebuildSnoozeMenu(defaultSnoozeMinutes);

        MenuItem quitItem = new MenuItem("Quitter");
        quitItem.addActionListener(e -> Platform.runLater(onQuit));
        menu.addSeparator();
        menu.add(quitItem);

        notifier.trayIcon().setPopupMenu(menu);
        notifier.trayIcon().addActionListener(e -> Platform.runLater(() -> {
            if (!stage.isShowing()) {
                stage.show();
            }
            stage.toFront();
        }));
    }

    public void updateSnoozeMinutes(int defaultSnoozeMinutes) {
        EventQueue.invokeLater(() -> rebuildSnoozeMenu(defaultSnoozeMinutes));
    }

    private void rebuildSnoozeMenu(int defaultSnoozeMinutes) {
        Menu menu = snoozeMenu;
        if (menu == null) {
            return;
        }
        menu.removeAll();
        Set<Integer> minutesSet = new LinkedHashSet<>();
        if (defaultSnoozeMinutes > 0) {
            minutesSet.add(defaultSnoozeMinutes);
        }
        minutesSet.add(15);
        minutesSet.add(30);
        minutesSet.add(60);
        minutesSet.add(120);
        for (Integer minutes : minutesSet) {
            String label = minutes.equals(defaultSnoozeMinutes)
                    ? "Par défaut (" + minutes + " min)"
                    : minutes + " min";
            MenuItem item = new MenuItem(label);
            item.addActionListener(e -> snoozeHandler.snooze(Duration.ofMinutes(minutes)));
            menu.add(item);
        }
        menu.addSeparator();
        MenuItem clearItem = new MenuItem("Réactiver immédiatement");
        clearItem.addActionListener(e -> snoozeHandler.snooze(Duration.ZERO));
        menu.add(clearItem);
    }
}


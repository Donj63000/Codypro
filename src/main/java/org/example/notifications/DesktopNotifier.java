package org.example.notifications;

/**
 * Abstraction representing a component able to surface desktop notifications.
 */
public interface DesktopNotifier {

    /**
     * Displays a notification payload to the user.
     *
     * @param title   short headline for the notification
     * @param message detailed body content
     */
    void notify(String title, String message);
}


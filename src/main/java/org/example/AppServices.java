package org.example;

import java.util.Objects;
import java.util.Optional;
import org.example.notifications.NotificationService;
import org.example.notifications.SystemTrayManager;

/**
 * Simple static registry to share long-lived application services.
 */
public final class AppServices {

    private static volatile NotificationService notificationService;
    private static volatile SystemTrayManager trayManager;

    private AppServices() {
    }

    public static void registerNotificationService(NotificationService service) {
        notificationService = Objects.requireNonNull(service, "service");
    }

    public static boolean hasNotificationService() {
        return notificationService != null;
    }

    public static NotificationService notificationService() {
        NotificationService service = notificationService;
        if (service == null) {
            throw new IllegalStateException("NotificationService is not initialised yet.");
        }
        return service;
    }

    public static Optional<NotificationService> notificationServiceOptional() {
        return Optional.ofNullable(notificationService);
    }

    public static void clearNotificationService() {
        notificationService = null;
    }

    public static void registerTrayManager(SystemTrayManager manager) {
        trayManager = manager;
    }

    public static Optional<SystemTrayManager> trayManagerOptional() {
        return Optional.ofNullable(trayManager);
    }

    public static void clearTrayManager() {
        trayManager = null;
    }
}


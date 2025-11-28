package org.example.notifications;

import java.awt.AWTException;
import java.awt.Image;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.awt.image.MemoryImageSource;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import javax.imageio.ImageIO;

/**
 * Desktop notifier backed by {@link SystemTray}. Displays operating system popups.
 */
public final class SystemTrayNotifier implements DesktopNotifier {

    private static final String TOOLTIP = "CodyPrestataires";

    private final SystemTray tray;
    private final TrayIcon trayIcon;

    public SystemTrayNotifier() {
        if (!SystemTray.isSupported()) {
            throw new IllegalStateException("System tray not supported on this platform.");
        }
        this.tray = SystemTray.getSystemTray();
        Image image = loadIcon();
        this.trayIcon = new TrayIcon(image, TOOLTIP);
        trayIcon.setImageAutoSize(true);
        installIntoTray();
    }

    private void installIntoTray() {
        for (TrayIcon existing : tray.getTrayIcons()) {
            if (Objects.equals(existing.getToolTip(), TOOLTIP)) {
                tray.remove(existing);
            }
        }
        try {
            tray.add(trayIcon);
        } catch (AWTException e) {
            throw new RuntimeException("Unable to add application icon to the system tray.", e);
        }
    }

    private Image loadIcon() {
        Image fromResources = readPng("/tray-icon.png");
        if (fromResources == null) {
            fromResources = readPng("/img.png");
        }
        if (fromResources != null) {
            return fromResources;
        }
        return fallbackPng();
    }

    private int trayIconSize() {
        try {
            return (int) tray.getTrayIconSize().getWidth();
        } catch (Exception e) {
            return 16;
        }
    }

    public TrayIcon trayIcon() {
        return trayIcon;
    }

    public void dispose() {
        tray.remove(trayIcon);
    }

    @Override
    public void notify(String title, String message) {
        String safeTitle = title == null ? "Notification" : title;
        String safeMessage = message == null ? "" : message;
        trayIcon.displayMessage(safeTitle, safeMessage, TrayIcon.MessageType.INFO);
    }

    private Image readPng(String resourcePath) {
        try (InputStream in = getClass().getResourceAsStream(resourcePath)) {
            if (in == null) {
                return null;
            }
            return ImageIO.read(in);
        } catch (IOException ex) {
            return null;
        }
    }

    private Image fallbackPng() {
        byte[] pngBytes = decodeBase64("iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAACklEQVR4nGNgAAIAAAUAAQ0KLbQAAAAASUVORK5CYII=");
        if (pngBytes != null) {
            return Toolkit.getDefaultToolkit().createImage(pngBytes);
        }
        int size = (int) tray.getTrayIconSize().getWidth();
        int[] pixels = new int[size * size];
        return Toolkit.getDefaultToolkit().createImage(new MemoryImageSource(size, size, pixels, 0, size));
    }

    private byte[] decodeBase64(String data) {
        try {
            return java.util.Base64.getDecoder().decode(data);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}

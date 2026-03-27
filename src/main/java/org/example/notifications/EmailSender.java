package org.example.notifications;

import org.example.model.NotificationSettings;

public interface EmailSender {
    void send(NotificationSettings settings, EmailMessage message) throws Exception;
}

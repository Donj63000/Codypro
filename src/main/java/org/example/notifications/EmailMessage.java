package org.example.notifications;

public record EmailMessage(
        String to,
        String from,
        String subject,
        String body
) {
}

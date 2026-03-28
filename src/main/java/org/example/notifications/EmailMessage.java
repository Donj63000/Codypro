package org.example.notifications;

public record EmailMessage(
        String to,
        String from,
        String fromName,
        String replyTo,
        String subject,
        String body
) {
}

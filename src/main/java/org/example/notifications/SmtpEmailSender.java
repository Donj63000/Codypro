package org.example.notifications;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.example.model.NotificationSettings;
import org.example.model.SmtpSecurity;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Objects;
import java.util.Properties;

public final class SmtpEmailSender implements EmailSender {

    @Override
    public void send(NotificationSettings settings, EmailMessage message) throws Exception {
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(message, "message");

        Properties props = new Properties();
        props.put("mail.smtp.host", settings.smtpHost());
        props.put("mail.smtp.port", Integer.toString(settings.smtpPort()));
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");

        boolean auth = settings.smtpUsername() != null && !settings.smtpUsername().isBlank();
        props.put("mail.smtp.auth", auth ? "true" : "false");

        SmtpSecurity security = settings.smtpSecurity() == null ? SmtpSecurity.STARTTLS : settings.smtpSecurity();
        if (security == SmtpSecurity.STARTTLS) {
            props.put("mail.smtp.starttls.enable", "true");
        } else if (security == SmtpSecurity.SSL) {
            props.put("mail.smtp.ssl.enable", "true");
        }

        Authenticator authenticator = null;
        if (auth) {
            String pwd = settings.smtpPassword() == null ? "" : settings.smtpPassword();
            authenticator = new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(settings.smtpUsername(), pwd);
                }
            };
        }

        Session session = Session.getInstance(props, authenticator);
        MimeMessage mime = new MimeMessage(session);

        InternetAddress from = new InternetAddress(message.from(), false);
        from.validate();
        String fromName = message.fromName() == null ? "" : message.fromName().strip();
        if (!fromName.isBlank()) {
            from.setPersonal(fromName, StandardCharsets.UTF_8.name());
        }
        mime.setFrom(from);

        if (message.replyTo() != null && !message.replyTo().isBlank()) {
            InternetAddress replyTo = new InternetAddress(message.replyTo(), false);
            replyTo.validate();
            mime.setReplyTo(new InternetAddress[]{replyTo});
        }

        InternetAddress[] recipients = InternetAddress.parse(message.to(), false);
        for (InternetAddress recipient : recipients) {
            recipient.validate();
        }
        mime.setRecipients(Message.RecipientType.TO, recipients);

        String subject = message.subject() == null ? "" : message.subject();
        String body = message.body() == null ? "" : message.body();
        mime.setSubject(subject, StandardCharsets.UTF_8.name());
        mime.setText(body, StandardCharsets.UTF_8.name());
        mime.setSentDate(new Date());
        Transport.send(mime);
    }
}

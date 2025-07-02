package org.example.mail;

import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import java.util.Optional;
import java.util.Properties;

public class Mailer {
    private static final String MAIL_USER = Optional.ofNullable(System.getenv("MAIL_USER"))
            .orElse("votre_mail@example.com");
    private static final String MAIL_PWD = Optional.ofNullable(System.getenv("MAIL_PWD"))
            .orElse("votre_mdp");

    public static void send(String dest, String subject, String body) throws MessagingException {
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.ssl.enable", "true");
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.port", "465");

        Session sess = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(MAIL_USER, MAIL_PWD);
            }
        });
        Message msg = new MimeMessage(sess);
        msg.setFrom(new InternetAddress(MAIL_USER));
        msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(dest, false));
        msg.setSubject(subject);
        msg.setText(body);
        Transport.send(msg);
    }
}

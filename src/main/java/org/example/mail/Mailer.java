package org.example.mail;

import jakarta.mail.*;
import jakarta.mail.internet.*;

import org.example.model.Facture;
import org.example.model.Prestataire;

import org.example.mail.OAuthService;
import org.example.mail.OAuthServiceFactory;
import org.example.mail.GoogleAuthService;

import java.util.*;

/**
 * Utility class for sending e-mails using the preferences stored in
 * {@link MailPrefs}. The previous implementation relied on environment
 * variables; this version lets the caller provide all configuration values
 * explicitly.
 */
public final class Mailer {
    private Mailer() {}

    /** Create a mail {@link Session} using the provided configuration. */
    private static Session makeSession(MailPrefs cfg) {
        Properties p = new Properties();
        p.put("mail.smtp.auth", "true");
        if (cfg.ssl()) {
            p.put("mail.smtp.ssl.enable", "true");   // SMTPS port 465
        } else {
            p.put("mail.smtp.starttls.enable", "true"); // ← AJOUT
        }
        p.put("mail.smtp.host", cfg.host());
        p.put("mail.smtp.port", String.valueOf(cfg.port()));
        return Session.getInstance(p, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(cfg.user(), cfg.pwd());
            }
        });
    }

    /** Create an OAuth {@link Session} using XOAUTH2. */
    private static Session makeSessionOAuth(MailPrefs cfg, String token) {
        Properties p = new Properties();
        p.put("mail.smtp.auth", "true");
        if (cfg.ssl()) {
            p.put("mail.smtp.ssl.enable", "true");
        } else {
            p.put("mail.smtp.starttls.enable", "true");
        }
        p.put("mail.smtp.host", cfg.host());
        p.put("mail.smtp.port", String.valueOf(cfg.port()));
        p.put("mail.smtp.sasl.enable", "true");
        p.put("mail.smtp.sasl.mechanisms", "XOAUTH2");
        p.put("mail.smtp.auth.mechanisms", "XOAUTH2");
        p.put("mail.smtp.auth.login.disable", "true");
        p.put("mail.smtp.auth.plain.disable", "true");
        return Session.getInstance(p, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(cfg.user(), token);
            }
        });
    }

    /**
     * Send an e-mail using the given configuration.
     *
     * @param cfg     mail server preferences
     * @param to      recipient address
     * @param subject mail subject
     * @param body    mail body (plain text)
     */
    public static void send(MailPrefs cfg, String to, String subject, String body)
            throws MessagingException {
        Session s;
        String provider = cfg.provider() == null ? "" : cfg.provider().toLowerCase();
        switch (provider) {
            case "gmail" -> {
                OAuthService svc = new GoogleAuthService(cfg);
                String token = svc.getAccessToken();
                s = makeSessionOAuth(cfg, token);
            }
            default -> {
                OAuthService svc = OAuthServiceFactory.create(cfg);
                if (svc != null) {
                    String token = svc.getAccessToken();
                    s = makeSessionOAuth(cfg, token);
                } else {
                    s = makeSession(cfg);
                }
            }
        }
        Message m = new MimeMessage(s);
        m.setFrom(new InternetAddress(cfg.from()));
        m.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to, false));
        m.setSubject(subject);
        m.setText(body);
        Transport.send(m);
    }

    /* ===== helpers de templating ===== */
    private static String inject(String tpl, Map<String, String> vars) {
        String out = tpl;
        for (var e : vars.entrySet()) out = out.replace(e.getKey(), e.getValue());
        return out;
    }

    /** Build a map of variables for template injection. */
    public static Map<String, String> vars(Prestataire pr, Facture f) {
        return Map.of(
                "%NOM%", pr.getNom(),
                "%EMAIL%", pr.getEmail(),
                "%MONTANT%", String.format("%.2f", f.getMontant()),
                "%ECHEANCE%", f.getEcheanceFr(),
                "%ID%", String.valueOf(f.getId())
        );
    }

    public static String subjToPresta(MailPrefs cfg, Map<String, String> v) {
        return inject(cfg.subjPresta(), v);
    }

    public static String bodyToPresta(MailPrefs cfg, Map<String, String> v) {
        return inject(cfg.bodyPresta(), v);
    }

    public static String subjToSelf(MailPrefs cfg, Map<String, String> v) {
        return inject(cfg.subjSelf(), v);
    }

    public static String bodyToSelf(MailPrefs cfg, Map<String, String> v) {
        return inject(cfg.bodySelf(), v);
    }
}

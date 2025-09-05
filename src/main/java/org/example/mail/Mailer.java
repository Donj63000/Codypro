package org.example.mail;

import jakarta.mail.*;
import jakarta.mail.internet.*;
import org.example.dao.MailPrefsDAO;
import org.example.model.Facture;
import org.example.model.Prestataire;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class Mailer {
    private Mailer() {}

    private static final Map<String, OAuthService> SERVICES = new ConcurrentHashMap<>();

    private static Properties baseProps(MailPrefs cfg) {
        Properties p = new Properties();
        p.put("mail.smtp.auth", "true");
        if (cfg.ssl()) {
            p.put("mail.smtp.ssl.enable", "true");
        } else {
            p.put("mail.smtp.starttls.enable", "true");
        }
        p.put("mail.smtp.host", cfg.host());
        p.put("mail.smtp.port", String.valueOf(cfg.port()));
        return p;
    }

    private static Session makeSession(MailPrefs cfg) {
        Properties p = baseProps(cfg);
        return Session.getInstance(p, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(cfg.user(), cfg.pwd());
            }
        });
    }

    private static Session makeSessionOAuth(MailPrefs cfg, String token) {
        Properties p = baseProps(cfg);
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

    public static void send(MailPrefsDAO dao, MailPrefs cfg,
                            String to, String subject, String body)
            throws MessagingException {

        String provider = Optional.ofNullable(cfg.provider()).orElse("").toLowerCase();

        OAuthService svc = SERVICES.computeIfAbsent(provider, p -> {
            if ("gmail".equals(p)) return new GoogleAuthService(dao);
            return OAuthServiceFactory.create(cfg);
        });

        if ("gmail".equals(provider) && dao != null && svc instanceof GoogleAuthService gs && !gs.hasDao()) {
            svc = new GoogleAuthService(dao, gs.prefs());
            SERVICES.put(provider, svc);
        }

        Session session = (svc == null)
                ? makeSession(cfg)
                : makeSessionOAuth(cfg, svc.getAccessToken());

        Message msg = new MimeMessage(session);
        msg.setFrom(new InternetAddress(cfg.from()));
        msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to, false));
        msg.setSubject(subject);
        msg.setText(body);
        Transport.send(msg);
    }

    /**
     * Envoi d'un MimeMessage déjà construit via la configuration (avec OAuth si requis).
     */
    public static void send(MailPrefsDAO dao, MailPrefs cfg, MimeMessage source) throws MessagingException {
        if (cfg == null) throw new MessagingException("Configuration mail manquante");
        String provider = Optional.ofNullable(cfg.provider()).orElse("").toLowerCase();

        OAuthService svc = SERVICES.computeIfAbsent(provider, p -> {
            if ("gmail".equals(p)) return new GoogleAuthService(dao);
            return OAuthServiceFactory.create(cfg);
        });

        if ("gmail".equals(provider) && dao != null && svc instanceof GoogleAuthService gs && !gs.hasDao()) {
            svc = new GoogleAuthService(dao, gs.prefs());
            SERVICES.put(provider, svc);
        }

        Session session = (svc == null)
                ? makeSession(cfg)
                : makeSessionOAuth(cfg, svc.getAccessToken());

        // Reconstruit le message avec la bonne Session pour conserver pièces jointes/headers
        try {
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            source.writeTo(bos);
            MimeMessage msg = new MimeMessage(session, new java.io.ByteArrayInputStream(bos.toByteArray()));
            if (msg.getFrom() == null || msg.getFrom().length == 0) msg.setFrom(new InternetAddress(cfg.from()));
            Transport.send(msg);
        } catch (java.io.IOException e) {
            throw new MessagingException("Erreur lors de la copie du message", e);
        }
    }

    private static String inject(String tpl, Map<String, String> vars) {
        String out = tpl;
        for (var e : vars.entrySet()) out = out.replace(e.getKey(), e.getValue());
        return out;
    }

    public static Map<String, String> vars(Prestataire pr, Facture f) {
        return Map.of(
                "%NOM%", pr.getNom(),
                "%EMAIL%", pr.getEmail(),
                "%MONTANT%", String.format("%.2f", f.getMontantTtc()),
                "%ECHEANCE%", f.getEcheanceFr(),
                "%ID%", String.valueOf(f.getId())
        );
    }

    public static String subjToPresta(MailPrefs cfg, Map<String, String> v) { return inject(cfg.subjPresta(), v); }
    public static String bodyToPresta(MailPrefs cfg, Map<String, String> v) { return inject(cfg.bodyPresta(), v); }
    public static String subjToSelf  (MailPrefs cfg, Map<String, String> v) { return inject(cfg.subjSelf(),   v); }
    public static String bodyToSelf  (MailPrefs cfg, Map<String, String> v) { return inject(cfg.bodySelf(),   v); }
}

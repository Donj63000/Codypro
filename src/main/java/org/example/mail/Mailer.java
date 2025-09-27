package org.example.mail;

import jakarta.mail.*;
import jakarta.mail.internet.*;
import org.example.dao.MailPrefsDAO;
import org.example.model.Facture;
import org.example.model.Prestataire;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

public final class Mailer {
    private Mailer() {}
    private static final Logger log = LoggerFactory.getLogger(Mailer.class);

    private static final ConcurrentHashMap<ServiceKey, OAuthHolder> SERVICES = new ConcurrentHashMap<>();

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

    private static Session sessionFor(MailPrefsDAO dao, MailPrefs cfg) {
        OAuthService svc = resolveOAuthService(dao, cfg);
        return (svc == null) ? makeSession(cfg) : makeSessionOAuth(cfg, svc.getAccessToken());
    }

    public static void send(MailPrefsDAO dao, MailPrefs cfg,
                            String to, String subject, String body)
            throws MessagingException {

        String provider = normalizeProvider(cfg.provider());

        Session session = sessionFor(dao, cfg);

        Message msg = new MimeMessage(session);
        msg.setFrom(new InternetAddress(cfg.from()));
        msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to, false));
        msg.setSubject(subject);
        msg.setText(body);
        Transport.send(msg);
        log.debug("[Mailer] Sent mail to {} via {} subject=\"{}\"", to, provider, subject);
    }

    /**
     * Envoi d'un MimeMessage d?j?? construit via la configuration (avec OAuth si requis).
     */
    public static void send(MailPrefsDAO dao, MailPrefs cfg, MimeMessage source) throws MessagingException {
        if (cfg == null) throw new MessagingException("Configuration mail manquante");
        String provider = normalizeProvider(cfg.provider());

        Session session = sessionFor(dao, cfg);

        // Reconstruit le message avec la bonne Session pour conserver pi??ces jointes/headers
        try {
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            source.writeTo(bos);
            MimeMessage msg = new MimeMessage(session, new java.io.ByteArrayInputStream(bos.toByteArray()));
            if (msg.getFrom() == null || msg.getFrom().length == 0) msg.setFrom(new InternetAddress(cfg.from()));
            Transport.send(msg);
            Address[] tos = msg.getRecipients(Message.RecipientType.TO);
            String dest = tos == null ? "<none>" : java.util.Arrays.toString(tos);
            log.debug("[Mailer] Relayed MimeMessage to {} via {} subject=\"{}\"", dest, provider, msg.getSubject());
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
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.FRANCE);
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);
        return Map.of(
                "%NOM%", pr.getNom(),
                "%EMAIL%", pr.getEmail(),
                "%MONTANT%", nf.format(f.getMontantTtc()),
                "%ECHEANCE%", f.getEcheanceFr(),
                "%ID%", String.valueOf(f.getId())
        );
    }

    public static String subjToPresta(MailPrefs cfg, Map<String, String> v) { return inject(cfg.subjPresta(), v); }
    public static String bodyToPresta(MailPrefs cfg, Map<String, String> v) { return inject(cfg.bodyPresta(), v); }
    public static String subjToSelf  (MailPrefs cfg, Map<String, String> v) { return inject(cfg.subjSelf(),   v); }
    public static String bodyToSelf  (MailPrefs cfg, Map<String, String> v) { return inject(cfg.bodySelf(),   v); }

    private static OAuthService resolveOAuthService(MailPrefsDAO dao, MailPrefs cfg) {
        ServiceKey key = ServiceKey.from(cfg);
        if (!key.isOAuthProvider()) {
            return null;
        }
        OAuthHolder holder = SERVICES.compute(key, (k, existing) -> {
            if (existing == null) {
                return OAuthHolder.maybeCreate(dao, cfg);
            }
            if (existing.needsDaoInjection(dao)) {
                return OAuthHolder.maybeCreate(dao, cfg);
            }
            if (!existing.matches(cfg)) {
                return OAuthHolder.maybeCreate(dao, cfg);
            }
            return existing;
        });
        if (holder == null) {
            SERVICES.remove(key);
            return null;
        }
        return holder.service();
    }

    private static OAuthService instantiateService(MailPrefsDAO dao, MailPrefs cfg) {
        String provider = normalizeProvider(cfg.provider());
        return switch (provider) {
            case "gmail" -> dao != null ? new GoogleAuthService(dao, cfg) : new GoogleAuthService(cfg);
            case "outlook" -> dao != null ? new MicrosoftAuthService(dao, cfg) : new MicrosoftAuthService(cfg);
            default -> null;
        };
    }

    private static MailPrefs currentPrefs(OAuthService svc) {
        if (svc instanceof GoogleAuthService g) return g.prefs();
        if (svc instanceof MicrosoftAuthService m) return m.prefs();
        return null;
    }

    private static String normalizeProvider(String provider) {
        return provider == null ? "" : provider.trim().toLowerCase(Locale.ROOT);
    }

    private record OAuthHolder(OAuthService service) {
        static OAuthHolder maybeCreate(MailPrefsDAO dao, MailPrefs cfg) {
            OAuthService svc = instantiateService(dao, cfg);
            return svc == null ? null : new OAuthHolder(svc);
        }

        boolean matches(MailPrefs cfg) {
            MailPrefs current = currentPrefs(service);
            return current != null && current.equals(cfg);
        }

        boolean needsDaoInjection(MailPrefsDAO dao) {
            if (dao == null) return false;
            if (service instanceof GoogleAuthService g) return !g.hasDao();
            if (service instanceof MicrosoftAuthService m) return !m.hasDao();
            return false;
        }
    }

    private record ServiceKey(String provider, String user, String client) {
        static ServiceKey from(MailPrefs cfg) {
            String provider = normalizeProvider(cfg.provider());
            String user = cfg.user() == null ? "" : cfg.user().trim().toLowerCase(Locale.ROOT);
            String client = cfg.oauthClient() == null ? "" : cfg.oauthClient().trim();
            return new ServiceKey(provider, user, client);
        }

        boolean isOAuthProvider() {
            return "gmail".equals(provider) || "outlook".equals(provider);
        }
    }
}

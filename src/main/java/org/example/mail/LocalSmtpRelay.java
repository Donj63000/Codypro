package org.example.mail;

import org.example.dao.MailPrefsDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.subethamail.smtp.MessageContext;
import org.subethamail.smtp.MessageHandler;
import org.subethamail.smtp.MessageHandlerFactory;
import org.subethamail.smtp.server.SMTPServer;

import jakarta.mail.internet.MimeMessage;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Petit serveur SMTP local (par défaut sur 2525) qui relaie les messages
 * via la configuration MailPrefs (classique ou OAuth selon provider).
 */
public final class LocalSmtpRelay {
    private static final Logger log = LoggerFactory.getLogger(LocalSmtpRelay.class);
    private final SMTPServer server;
    private final MailPrefsDAO dao;
    private final int port;

    public LocalSmtpRelay(MailPrefsDAO dao, int port) {
        this.dao = dao;
        this.port = port;
        this.server = new SMTPServer(new RelayFactory());
        this.server.setPort(port);
        this.server.setSoftwareName("Prestataires Local SMTP Relay");
    }

    public void start() { server.start(); log.info("[SMTP-Relay] started on localhost:{}", port); }
    public void stop()  { server.stop();  log.info("[SMTP-Relay] stopped"); }

    private final class RelayFactory implements MessageHandlerFactory {
        @Override public MessageHandler create(MessageContext ctx) {
            return new Handler(ctx);
        }
    }

    private final class Handler implements MessageHandler {
        private final MessageContext ctx;
        private final List<String> recipients = new ArrayList<>();
        private String from;

        Handler(MessageContext ctx) { this.ctx = ctx; }

        @Override public void from(String from) { this.from = from; }
        @Override public void recipient(String recipient) { recipients.add(recipient); }

        @Override public void data(InputStream data) {
            try {
                // Parse le message reçu
                MimeMessage msg = new MimeMessage((jakarta.mail.Session) null, data);
                // Charge la config et relaie avec Mailer
                MailPrefs cfg = dao.load();
                if (cfg == null) throw new IllegalStateException("MailPrefs non configuré");
                // Si From absent, utilise celui de la config
                if (msg.getFrom() == null || msg.getFrom().length == 0) {
                    msg.setFrom(cfg.from());
                }
                String host = cfg.host() == null ? "" : cfg.host().toLowerCase();
                if ("localhost".equals(host) || "127.0.0.1".equals(host)) {
                    // Evite une boucle si la conf pointe vers ce même relais. On sauvegarde le message.
                    dumpToOutbox(msg);
                    org.slf4j.LoggerFactory.getLogger(LocalSmtpRelay.class).info("[SMTP-Relay] Message reçu et archivé (pas de relais car host=localhost)");
                    System.out.println("[SMTP-Relay] Message reçu et archivé (pas de relais car host=localhost).");
                } else {
                    Mailer.send(dao, cfg, msg);
                    org.slf4j.LoggerFactory.getLogger(LocalSmtpRelay.class).info("[SMTP-Relay] Message relayé: from={}, rcpt={}, via={}", from, recipients, cfg.provider());
                    System.out.println("[SMTP-Relay] Message relayé: from=" + from + ", rcpt=" + recipients + ", via=" + cfg.provider());
                }
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(LocalSmtpRelay.class).error("[SMTP-Relay] Error while relaying: {}", e.getMessage(), e);
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }

        @Override public void done() {}
    }

    private static void dumpToOutbox(MimeMessage msg) {
        try {
            java.nio.file.Path dir = java.nio.file.Path.of(System.getProperty("user.home"), ".prestataires", "outbox");
            java.nio.file.Files.createDirectories(dir);
            String name = java.time.LocalDateTime.now().toString().replace(':','-') + ".eml";
            java.nio.file.Path file = dir.resolve(name);
            try (java.io.OutputStream os = java.nio.file.Files.newOutputStream(file)) { msg.writeTo(os); }
        } catch (Exception ignore) { }
    }
}

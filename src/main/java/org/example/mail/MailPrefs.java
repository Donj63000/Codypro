package org.example.mail;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import javax.crypto.SecretKey;
import org.example.util.TokenCrypto;

public record MailPrefs(
        String host,
        int    port,
        boolean ssl,
        String user,
        String pwd,
        String provider,
        String oauthClient,
        String oauthRefresh,
        long   oauthExpiry,
        String from,
        String copyToSelf,
        int    delayHours,
        String style,
        String subjPresta,
        String bodyPresta,
        String subjSelf,
        String bodySelf) {

    public static final Map<String, String[]> TEMPLATE_SETS = Map.of(
            "fr", new String[]{
                    "Rappel de paiement – échéance %ECHEANCE%",
                    """
                    Bonjour %NOM%,

                    Nous n'avons pas encore reçu votre règlement de %MONTANT% €
                    (échéance %ECHEANCE%).

                    Merci de régulariser au plus vite.

                    Cordialement.
                    """,
                    "Préavis facture %ID% – %NOM%",
                    "Le prestataire %NOM% (%EMAIL%) n'a pas réglé la facture %ID% "
                            + "(échéance %ECHEANCE%, montant %MONTANT% €)."
            },
            "en", new String[]{
                    "Payment reminder – due %ECHEANCE%",
                    """
                    Hello %NOM%,

                    We have not yet received your payment of %MONTANT% €
                    (due %ECHEANCE%).

                    Please settle as soon as possible.

                    Regards.
                    """,
                    "Pre‑notice invoice %ID% – %NOM%",
                    "Provider %NOM% (%EMAIL%) has not paid invoice %ID% "
                            + "(due %ECHEANCE%, amount %MONTANT% €)."
            }
    );

    public static final String DEFAULT_STYLE = "fr";
    private static final int   DEFAULT_DELAY = 48;

    public MailPrefs {
        host         = trim(host);
        port         = port > 0 ? port : 465;
        user         = trim(user);
        pwd          = pwd == null ? "" : pwd;
        provider     = trim(provider).toLowerCase();
        oauthClient  = trim(oauthClient);
        oauthRefresh = trim(oauthRefresh);
        oauthExpiry  = Math.max(oauthExpiry, 0L);
        from         = trim(from);
        copyToSelf   = trim(copyToSelf);
        delayHours   = delayHours > 0 ? delayHours : DEFAULT_DELAY;
        style        = normalizeStyle(style);

        String[] tpl = TEMPLATE_SETS.get(style);
        subjPresta = blank(subjPresta) ? tpl[0] : subjPresta;
        bodyPresta = blank(bodyPresta) ? tpl[1] : bodyPresta;
        subjSelf   = blank(subjSelf)   ? tpl[2] : subjSelf;
        bodySelf   = blank(bodySelf)   ? tpl[3] : bodySelf;
    }

    private static String trim(String v)       { return v == null ? "" : v.trim(); }
    private static boolean blank(String v)     { return v == null || v.isBlank(); }
    private static String normalizeStyle(String s) {
        String st = trim(s).toLowerCase();
        return TEMPLATE_SETS.containsKey(st) ? st : DEFAULT_STYLE;
    }

    public static MailPrefs defaultValues() {
        String[] t = TEMPLATE_SETS.get(DEFAULT_STYLE);
        return new MailPrefs(
                "smtp.gmail.com", 465, true,
                "", "", "", "", "", 0L,
                "mon_mail@exemple.com", "",
                DEFAULT_DELAY,
                DEFAULT_STYLE,
                t[0], t[1], t[2], t[3]
        );
    }

    public static MailPrefs fromPreset(SmtpPreset p) {
        MailPrefs d = defaultValues();
        return new MailPrefs(
                p.host(), p.port(), p.ssl(),
                "", "",
                p.provider(), "", "", 0L,
                "", "",
                d.delayHours(),
                d.style(),
                d.subjPresta(), d.bodyPresta(),
                d.subjSelf(),   d.bodySelf()
        );
    }

    public static MailPrefs fromRS(ResultSet rs, SecretKey key) throws SQLException {
        return new MailPrefs(
                rs.getString("host"),
                rs.getInt("port"),
                rs.getInt("ssl") != 0,
                rs.getString("user"),
                TokenCrypto.decrypt(rs.getString("pwd"), key),
                rs.getString("provider"),
                TokenCrypto.decrypt(rs.getString("oauth_client"),  key),
                TokenCrypto.decrypt(rs.getString("oauth_refresh"), key),
                rs.getLong("oauth_expiry"),
                rs.getString("from_addr"),
                rs.getString("copy_to_self"),
                rs.getInt("delay_hours"),
                rs.getString("style"),
                rs.getString("subj_tpl_presta"),
                rs.getString("body_tpl_presta"),
                rs.getString("subj_tpl_self"),
                rs.getString("body_tpl_self")
        );
    }

    public void bind(PreparedStatement ps, SecretKey key) throws SQLException {
        ps.setString(1, host());
        ps.setInt   (2, port());
        ps.setInt   (3, ssl() ? 1 : 0);
        ps.setString(4, user());
        ps.setString(5, TokenCrypto.encrypt(pwd(), key));
        ps.setString(6, provider());
        ps.setString(7, TokenCrypto.encrypt(oauthClient(),  key));
        ps.setString(8, TokenCrypto.encrypt(oauthRefresh(), key));
        ps.setLong  (9, oauthExpiry());
        ps.setString(10, from());
        ps.setString(11, copyToSelf());
        ps.setInt   (12, delayHours());
        ps.setString(13, style());
        ps.setString(14, subjPresta());
        ps.setString(15, bodyPresta());
        ps.setString(16, subjSelf());
        ps.setString(17, bodySelf());
    }
}


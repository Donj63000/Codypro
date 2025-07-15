package org.example.mail;

import java.sql.*;
import java.util.Map;
import javax.crypto.SecretKey;
import org.example.util.TokenCrypto;

public record MailPrefs(
        String host, int port, boolean ssl,
        String user, String pwd,
        String provider, String oauthClient,
        String oauthRefresh, long oauthExpiry,
        String from, String copyToSelf,
        int delayHours,
        String style,
        String subjPresta, String bodyPresta,
        String subjSelf, String bodySelf) {

    /** Built-in template packs indexed by style/language. */
    public static final Map<String, String[]> TEMPLATE_SETS = Map.of(
            "fr", new String[]{
                    "Rappel de paiement – échéance %ECHEANCE%",
                    """
                    Bonjour %NOM%,

                    Nous n'avons pas encore reçu votre règlement de %MONTANT% €
                    (échéance %ECHEANCE%).

                    Merci de régulariser au plus vite.

                    Cordialement.
                    """,
                    "🛈 Pré‑avis facture %ID% – %NOM%",
                    "Le prestataire %NOM% (%EMAIL%) n'a pas réglé la facture %ID% " +
                            "(échéance %ECHEANCE%, montant %MONTANT% €)."
            },
            "en", new String[]{
                    "Payment reminder – due %ECHEANCE%",
                    """
                    Hello %NOM%,

                    We have not yet received your payment of %MONTANT%€
                    (due %ECHEANCE%).

                    Please settle as soon as possible.

                    Regards.
                    """,
                    "Notice invoice %ID% – %NOM%",
                    "Provider %NOM% (%EMAIL%) has not paid invoice %ID% " +
                            "(due %ECHEANCE%, amount %MONTANT%€)."
            }
    );

    /** Default style used when none is specified. */
    public static final String DEFAULT_STYLE = "fr";

    /* =========  utilitaires  ========= */
    public static MailPrefs defaultValues() {
        String[] tpl = TEMPLATE_SETS.get(DEFAULT_STYLE);
        return new MailPrefs("smtp.gmail.com", 465, true,
                "", "",
                "", "", "", 0L,
                "mon_mail@exemple.com", "",
                48,
                DEFAULT_STYLE,
                tpl[0], tpl[1], tpl[2], tpl[3]
        );
    }

    /**
     * Create a new configuration from an SMTP preset. Message templates and
     * delay use the default values while all user related fields are empty.
     */
    public static MailPrefs fromPreset(SmtpPreset pre) {
        MailPrefs def = defaultValues();
        return new MailPrefs(
                pre.host(), pre.port(), pre.ssl(),
                "", "",
                pre.provider(), "", "", 0L,
                "", "",
                def.delayHours(),
                def.style(),
                def.subjPresta(), def.bodyPresta(),
                def.subjSelf(), def.bodySelf()
        );
    }

    public static MailPrefs fromRS(ResultSet rs, SecretKey key) throws SQLException {
        String provider = rs.getString("provider");
        if(provider == null) provider = "";
        String oauthClient = rs.getString("oauth_client");
        if (oauthClient == null) oauthClient = "";
        else oauthClient = TokenCrypto.decrypt(oauthClient, key);
        String oauthRefresh = rs.getString("oauth_refresh");
        if (oauthRefresh == null) oauthRefresh = "";
        else oauthRefresh = TokenCrypto.decrypt(oauthRefresh, key);
        long expiry = 0L;
        try {
            expiry = rs.getLong("oauth_expiry");
            if(rs.wasNull()) expiry = 0L;
        } catch(SQLException ignore) {}
        String style = rs.getString("style");
        if(style == null || style.isEmpty()) style = DEFAULT_STYLE;
        String copy = rs.getString("copy_to_self");
        if (copy == null) copy = "";
        return new MailPrefs(
            rs.getString("host"),
            rs.getInt("port"),
            rs.getInt("ssl") != 0,
            rs.getString("user"),
            rs.getString("pwd"),
            provider,
            oauthClient,
            oauthRefresh,
            expiry,
            rs.getString("from_addr"),
            copy,
            rs.getInt("delay_hours"),
            style,
            rs.getString("subj_tpl_presta"),
            rs.getString("body_tpl_presta"),
            rs.getString("subj_tpl_self"),
            rs.getString("body_tpl_self")
        );
    }

    public void bind(PreparedStatement ps, SecretKey key) throws SQLException {
        ps.setString(1, host());
        ps.setInt(2, port());
        ps.setInt(3, ssl() ? 1 : 0);
        ps.setString(4, user());
        ps.setString(5, pwd());
        ps.setString(6, provider());
        ps.setString(7, TokenCrypto.encrypt(oauthClient(), key));
        ps.setString(8, TokenCrypto.encrypt(oauthRefresh(), key));
        ps.setLong(9, oauthExpiry());
        ps.setString(10, from());
        ps.setString(11, copyToSelf() == null ? "" : copyToSelf());
        ps.setInt(12, delayHours());
        ps.setString(13, style());
        ps.setString(14, subjPresta());
        ps.setString(15, bodyPresta());
        ps.setString(16, subjSelf());
        ps.setString(17, bodySelf());
    }
}

package org.example.mail;

import java.sql.*;

public record MailPrefs(
        String host,int port,boolean ssl,
        String user,String pwd,
        String provider,String oauthClient,
        String oauthRefresh,long oauthExpiry,
        String from,String copyToSelf,
        int delayHours,
        String subjPresta,String bodyPresta,
        String subjSelf,String bodySelf){

    /* =========  utilitaires  ========= */
    public static MailPrefs defaultValues(){
        return new MailPrefs("smtp.gmail.com",465,true,
                "", "",
                "", "", "", 0L,
                "mon_mail@exemple.com", "",
                48,
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
        );
    }

    public static MailPrefs fromRS(ResultSet rs) throws SQLException {
        String provider = rs.getString("provider");
        if(provider == null) provider = "";
        String oauthClient = rs.getString("oauth_client");
        if(oauthClient == null) oauthClient = "";
        String oauthRefresh = rs.getString("oauth_refresh");
        if(oauthRefresh == null) oauthRefresh = "";
        long expiry = 0L;
        try {
            expiry = rs.getLong("oauth_expiry");
            if(rs.wasNull()) expiry = 0L;
        } catch(SQLException ignore) {}
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
            rs.getString("copy_to_self"),
            rs.getInt("delay_hours"),
            rs.getString("subj_tpl_presta"),
            rs.getString("body_tpl_presta"),
            rs.getString("subj_tpl_self"),
            rs.getString("body_tpl_self")
        );
    }

    public void bind(PreparedStatement ps) throws SQLException {
        ps.setString(1, host());
        ps.setInt(2, port());
        ps.setInt(3, ssl() ? 1 : 0);
        ps.setString(4, user());
        ps.setString(5, pwd());
        ps.setString(6, provider());
        ps.setString(7, oauthClient());
        ps.setString(8, oauthRefresh());
        ps.setLong(9, oauthExpiry());
        ps.setString(10, from());
        ps.setString(11, copyToSelf());
        ps.setInt(12, delayHours());
        ps.setString(13, subjPresta());
        ps.setString(14, bodyPresta());
        ps.setString(15, subjSelf());
        ps.setString(16, bodySelf());
    }
}

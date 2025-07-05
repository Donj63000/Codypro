package org.example.mail;

import java.sql.*;

public record MailPrefs(
        String host,int port,boolean ssl,
        String user,String pwd,
        String from,String copyToSelf,
        int delayHours,
        String subjPresta,String bodyPresta,
        String subjSelf,String bodySelf){

    /* =========  utilitaires  ========= */
    public static MailPrefs defaultValues(){
        return new MailPrefs("smtp.gmail.com",465,true,
                "", "", "mon_mail@exemple.com", "",
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
        return new MailPrefs(
            rs.getString("host"),
            rs.getInt("port"),
            rs.getInt("ssl") != 0,
            rs.getString("user"),
            rs.getString("pwd"),
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
        ps.setString(6, from());
        ps.setString(7, copyToSelf());
        ps.setInt(8, delayHours());
        ps.setString(9, subjPresta());
        ps.setString(10, bodyPresta());
        ps.setString(11, subjSelf());
        ps.setString(12, bodySelf());
    }
}

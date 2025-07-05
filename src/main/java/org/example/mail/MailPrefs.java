package org.example.mail;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public record MailPrefs(
    String host,
    int port,
    boolean ssl,
    String user,
    String pwd,
    String fromAddr,
    String copyToSelf,
    int delayHours,
    String subjTplPresta,
    String bodyTplPresta,
    String subjTplSelf,
    String bodyTplSelf
) {
    public static MailPrefs defaultValues() {
        return new MailPrefs(
            "smtp.gmail.com",
            465,
            true,
            "",
            "",
            "",
            "",
            48,
            "",
            "",
            "",
            ""
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
        ps.setString(1, host);
        ps.setInt(2, port);
        ps.setInt(3, ssl ? 1 : 0);
        ps.setString(4, user);
        ps.setString(5, pwd);
        ps.setString(6, fromAddr);
        ps.setString(7, copyToSelf);
        ps.setInt(8, delayHours);
        ps.setString(9, subjTplPresta);
        ps.setString(10, bodyTplPresta);
        ps.setString(11, subjTplSelf);
        ps.setString(12, bodyTplSelf);
    }
}

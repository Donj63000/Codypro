package org.example.dao;

import org.example.mail.MailPrefs;
import org.example.mail.SmtpPreset;
import javax.crypto.SecretKey;

import java.sql.*;

public class MailPrefsDAO {
    private final ConnectionProvider ds;
    private final SecretKey key;
    public MailPrefsDAO(ConnectionProvider ds, SecretKey key){
        this.ds = ds;
        this.key = key;
    }

    public MailPrefs load(){
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()){
            ResultSet rs = st.executeQuery("SELECT * FROM mail_prefs WHERE id=1");
            if(!rs.next()) return MailPrefs.fromPreset(SmtpPreset.PRESETS[0]);
            return MailPrefs.fromRS(rs, key);
        } catch(SQLException e){ throw new RuntimeException(e);}    }
    public void save(MailPrefs p){
        String sql = """
            INSERT INTO mail_prefs(id,host,port,ssl,user,pwd,provider,oauth_client,oauth_refresh,oauth_expiry,
                                   from_addr,copy_to_self,delay_hours,style,subj_tpl_presta,body_tpl_presta,
                                   subj_tpl_self,body_tpl_self)
            VALUES(1,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            ON CONFLICT(id) DO UPDATE SET
              host=excluded.host,port=excluded.port,ssl=excluded.ssl,
              user=excluded.user,pwd=excluded.pwd,
              provider=excluded.provider,oauth_client=excluded.oauth_client,
              oauth_refresh=excluded.oauth_refresh,oauth_expiry=excluded.oauth_expiry,
              from_addr=excluded.from_addr,
              copy_to_self=excluded.copy_to_self,delay_hours=excluded.delay_hours,style=excluded.style,
              subj_tpl_presta=excluded.subj_tpl_presta,body_tpl_presta=excluded.body_tpl_presta,
              subj_tpl_self=excluded.subj_tpl_self,body_tpl_self=excluded.body_tpl_self
        """;
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)){
            p.bind(ps, key);
            ps.executeUpdate();
        }catch(SQLException e){ throw new RuntimeException(e);}    }

    /** Clear stored OAuth tokens so that the next send triggers a new login. */
    public void invalidateOAuth() {
        MailPrefs p = load();
        MailPrefs cleared = new MailPrefs(
                p.host(), p.port(), p.ssl(),
                p.user(), p.pwd(),
                p.provider(), p.oauthClient(),
                "", 0L,
                p.from(), p.copyToSelf(), p.delayHours(),
                p.style(),
                p.subjPresta(), p.bodyPresta(),
                p.subjSelf(), p.bodySelf()
        );
        save(cleared);
    }
}

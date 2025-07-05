package org.example.dao;

import org.example.mail.MailPrefs;

import java.sql.*;

public class MailPrefsDAO {
    private final Connection c;
    public MailPrefsDAO(Connection c){ this.c = c; }

    public MailPrefs load(){
        try (Statement st = c.createStatement()){
            ResultSet rs = st.executeQuery("SELECT * FROM mail_prefs WHERE id=1");
            if(!rs.next()) return MailPrefs.defaultValues();
            return MailPrefs.fromRS(rs);
        } catch(SQLException e){ throw new RuntimeException(e);}    }
    public void save(MailPrefs p){
        String sql = """
            INSERT INTO mail_prefs(id,host,port,ssl,user,pwd,from_addr,copy_to_self,
                                   delay_hours,subj_tpl_presta,body_tpl_presta,
                                   subj_tpl_self,body_tpl_self)
            VALUES(1,?,?,?,?,?,?,?,?,?,?,?,?)
            ON CONFLICT(id) DO UPDATE SET
              host=excluded.host,port=excluded.port,ssl=excluded.ssl,
              user=excluded.user,pwd=excluded.pwd,from_addr=excluded.from_addr,
              copy_to_self=excluded.copy_to_self,delay_hours=excluded.delay_hours,
              subj_tpl_presta=excluded.subj_tpl_presta,body_tpl_presta=excluded.body_tpl_presta,
              subj_tpl_self=excluded.subj_tpl_self,body_tpl_self=excluded.body_tpl_self
        """;
        try (PreparedStatement ps = c.prepareStatement(sql)){
            p.bind(ps);
            ps.executeUpdate();
        }catch(SQLException e){ throw new RuntimeException(e);}    }
}

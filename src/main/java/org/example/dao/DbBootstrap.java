package org.example.dao;

import java.sql.*;

public final class DbBootstrap {
    private DbBootstrap() {}

    public static void ensureSchema(DB dao, UserDB userDb) {
        try (Connection c = userDb.getConnection(); Statement st = c.createStatement()) {
            st.execute("PRAGMA foreign_keys=ON");
            st.execute("PRAGMA busy_timeout=5000");

            st.execute("""
                CREATE TABLE IF NOT EXISTS prestataires(
                  id INTEGER PRIMARY KEY,
                  nom TEXT NOT NULL UNIQUE,
                  societe TEXT, telephone TEXT, email TEXT,
                  note INTEGER DEFAULT 0,
                  facturation TEXT,
                  date_contrat TEXT, date_contrat_ts INTEGER
                )
            """);
            st.execute("""
                CREATE TABLE IF NOT EXISTS services(
                  id INTEGER PRIMARY KEY,
                  prestataire_id INTEGER NOT NULL,
                  description TEXT,
                  date TEXT, date_ts INTEGER,
                  FOREIGN KEY(prestataire_id) REFERENCES prestataires(id) ON DELETE CASCADE
                )
            """);
            st.execute("""
                CREATE TABLE IF NOT EXISTS factures(
                  id INTEGER PRIMARY KEY,
                  prestataire_id INTEGER NOT NULL,
                  description TEXT,
                  echeance TEXT, echeance_ts INTEGER,
                  montant_ht REAL, tva_pct REAL DEFAULT 20,
                  montant_tva REAL, montant_ttc REAL,
                  devise TEXT DEFAULT 'EUR',
                  paye INTEGER DEFAULT 0,
                  date_paiement TEXT, date_paiement_ts INTEGER,
                  preavis_envoye INTEGER DEFAULT 0,
                  FOREIGN KEY(prestataire_id) REFERENCES prestataires(id) ON DELETE CASCADE
                )
            """);
            st.execute("""
                CREATE TABLE IF NOT EXISTS mail_prefs(
                  id INTEGER PRIMARY KEY CHECK (id=1),
                  host TEXT, port INTEGER, ssl INTEGER,
                  user TEXT, pwd TEXT,
                  provider TEXT,
                  oauth_client TEXT, oauth_refresh TEXT, oauth_expiry INTEGER,
                  from_addr TEXT,
                  copy_to_self TEXT,
                  delay_hours INTEGER,
                  style TEXT,
                  subj_tpl_presta TEXT, body_tpl_presta TEXT,
                  subj_tpl_self TEXT,  body_tpl_self TEXT
                )
            """);

            // Migration SQLite-safe pour NOT NULL/DEFAULT '' de copy_to_self
            try {
                st.executeUpdate("UPDATE mail_prefs SET copy_to_self='' WHERE copy_to_self IS NULL");
                st.execute("""
                    CREATE TABLE IF NOT EXISTS mail_prefs_new(
                      id INTEGER PRIMARY KEY CHECK (id=1),
                      host TEXT, port INTEGER, ssl INTEGER,
                      user TEXT, pwd TEXT,
                      provider TEXT,
                      oauth_client TEXT, oauth_refresh TEXT, oauth_expiry INTEGER,
                      from_addr TEXT,
                      copy_to_self TEXT NOT NULL DEFAULT '',
                      delay_hours INTEGER,
                      style TEXT,
                      subj_tpl_presta TEXT, body_tpl_presta TEXT,
                      subj_tpl_self TEXT,  body_tpl_self TEXT
                    )
                """);
                st.execute("""
                    INSERT OR REPLACE INTO mail_prefs_new
                    SELECT id, host, port, ssl, user, pwd, provider,
                           oauth_client, oauth_refresh, oauth_expiry,
                           from_addr, COALESCE(copy_to_self,''),
                           delay_hours, style,
                           subj_tpl_presta, body_tpl_presta,
                           subj_tpl_self, body_tpl_self
                    FROM mail_prefs
                """);
                st.execute("DROP TABLE mail_prefs");
                st.execute("ALTER TABLE mail_prefs_new RENAME TO mail_prefs");
            } catch (SQLException ignore) {}

            // Index
            dao.ensureIndexes(c);

        } catch (SQLException e) {
            throw new RuntimeException("Init DB/migrations : " + e.getMessage(), e);
        }
    }
}

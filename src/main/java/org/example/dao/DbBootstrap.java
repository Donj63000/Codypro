package org.example.dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.sql.*;

public final class DbBootstrap {
    private static final Logger log = LoggerFactory.getLogger(DbBootstrap.class);
    private DbBootstrap() {}

    public static void ensureSchema(DB dao, UserDB userDb) {
        Connection c;
        try {
            c = userDb.getConnection();
        } catch (SQLException e) {
            throw new RuntimeException("Init DB/migrations : " + e.getMessage(), e);
        }

        try (Statement st = c.createStatement()) {
            log.debug("[DbBootstrap] Ensuring schema and migrations");
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
                CREATE TABLE IF NOT EXISTS notification_settings(
                  id INTEGER PRIMARY KEY CHECK(id=1),
                  lead_days INTEGER NOT NULL DEFAULT 3,
                  reminder_hour INTEGER NOT NULL DEFAULT 9,
                  reminder_minute INTEGER NOT NULL DEFAULT 0,
                  repeat_every_hours INTEGER NOT NULL DEFAULT 4,
                  highlight_overdue INTEGER NOT NULL DEFAULT 1,
                  desktop_popup INTEGER NOT NULL DEFAULT 1,
                  snooze_minutes INTEGER NOT NULL DEFAULT 30,
                  subject_template TEXT NOT NULL DEFAULT 'Facture {{prestataire}} : échéance le {{echeance}}',
                  body_template TEXT NOT NULL DEFAULT 'La facture {{facture}} d''un montant de {{montant}} pour {{prestataire}} arrive {{delai}}.\nStatut : {{statut}}.'
                )
            """);
            st.execute("""
                INSERT INTO notification_settings (id)
                SELECT 1 WHERE NOT EXISTS (SELECT 1 FROM notification_settings WHERE id=1)
            """);
            // Index
            dao.ensureIndexes(c);
            log.debug("[DbBootstrap] Schema ensured; indexes created");

        } catch (SQLException e) {
            throw new RuntimeException("Init DB/migrations : " + e.getMessage(), e);
        }
    }
}

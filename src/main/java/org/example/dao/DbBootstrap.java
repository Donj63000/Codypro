package org.example.dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class DbBootstrap {
    private static final Logger log = LoggerFactory.getLogger(DbBootstrap.class);

    private DbBootstrap() {
    }

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
                  societe TEXT,
                  telephone TEXT,
                  email TEXT,
                  note INTEGER DEFAULT 0,
                  facturation TEXT,
                  service_notes TEXT,
                  date_contrat TEXT,
                  date_contrat_ts INTEGER
                )
            """);
            st.execute("""
                CREATE TABLE IF NOT EXISTS services(
                  id INTEGER PRIMARY KEY,
                  prestataire_id INTEGER NOT NULL,
                  description TEXT,
                  date TEXT,
                  date_ts INTEGER,
                  status TEXT NOT NULL DEFAULT 'EN_ATTENTE',
                  FOREIGN KEY(prestataire_id) REFERENCES prestataires(id) ON DELETE CASCADE
                )
            """);
            st.execute("""
                CREATE TABLE IF NOT EXISTS factures(
                  id INTEGER PRIMARY KEY,
                  prestataire_id INTEGER NOT NULL,
                  description TEXT,
                  echeance TEXT,
                  echeance_ts INTEGER,
                  montant_ht REAL,
                  tva_pct REAL DEFAULT 20,
                  montant_tva REAL,
                  montant_ttc REAL,
                  devise TEXT DEFAULT 'EUR',
                  paye INTEGER DEFAULT 0,
                  date_paiement TEXT,
                  date_paiement_ts INTEGER,
                  preavis_envoye INTEGER DEFAULT 0,
                  FOREIGN KEY(prestataire_id) REFERENCES prestataires(id) ON DELETE CASCADE
                )
            """);
            st.execute("""
                CREATE TABLE IF NOT EXISTS rappels(
                  id INTEGER PRIMARY KEY,
                  job_key TEXT,
                  type TEXT NOT NULL DEFAULT 'MANAGER_PRE',
                  facture_id INTEGER NOT NULL,
                  prestataire_id INTEGER,
                  dest TEXT NOT NULL,
                  sujet TEXT NOT NULL,
                  corps TEXT NOT NULL,
                  date_envoi TEXT NOT NULL,
                  date_envoi_ts INTEGER NOT NULL,
                  envoye INTEGER NOT NULL DEFAULT 0,
                  statut TEXT NOT NULL DEFAULT 'PENDING',
                  attempt_count INTEGER NOT NULL DEFAULT 0,
                  last_error TEXT NOT NULL DEFAULT '',
                  sent_at TEXT,
                  sent_at_ts INTEGER,
                  FOREIGN KEY(facture_id) REFERENCES factures(id) ON DELETE CASCADE
                )
            """);

            DB.ensureNotificationSettingsSchema(c);
            DB.ensureRappelsSchema(c);
            dao.ensureIndexes(c);
            log.debug("[DbBootstrap] Schema ensured; indexes created");
        } catch (SQLException e) {
            throw new RuntimeException("Init DB/migrations : " + e.getMessage(), e);
        }
    }
}

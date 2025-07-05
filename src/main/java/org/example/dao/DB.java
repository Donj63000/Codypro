package org.example.dao;

import org.example.model.Prestataire;
import org.example.model.ServiceRow;
import org.example.model.Facture;
import org.example.model.Rappel;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

public class DB implements AutoCloseable {
    private static final DateTimeFormatter DATE_FR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_DB = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private final Connection conn;

    public DB(String path) {
        try {
            conn = DriverManager.getConnection("jdbc:sqlite:" + path);
            try (Statement st = conn.createStatement()) {
                st.execute("PRAGMA foreign_keys = 1");
            }
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS prestataires (
                            id INTEGER PRIMARY KEY,
                            nom TEXT UNIQUE NOT NULL COLLATE NOCASE,
                            societe TEXT,
                            telephone TEXT,
                            email TEXT,
                            note INTEGER CHECK(note BETWEEN 0 AND 100),
                            facturation TEXT,
                            date_contrat TEXT
                        );
                        """);
            }
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS services (
                            id INTEGER PRIMARY KEY,
                            prestataire_id INTEGER REFERENCES prestataires(id) ON DELETE CASCADE,
                            description TEXT,
                            date TEXT
                        );
                        """);
            }
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS factures (
                            id INTEGER PRIMARY KEY,
                            prestataire_id INTEGER NOT NULL
                                REFERENCES prestataires(id) ON DELETE CASCADE,
                            description TEXT,
                            echeance TEXT NOT NULL,
                            montant_ht REAL NOT NULL,
                            paye INTEGER NOT NULL DEFAULT 0,
                            date_paiement TEXT
                        );
                        """);
            }
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS rappels (
                            id           INTEGER PRIMARY KEY,
                            facture_id   INTEGER NOT NULL REFERENCES factures(id) ON DELETE CASCADE,
                            dest         TEXT      NOT NULL,
                            sujet        TEXT      NOT NULL,
                            corps        TEXT      NOT NULL,
                            date_envoi   TEXT      NOT NULL,   -- ISO yyyy-MM-dd HH:mm
                            envoye       INTEGER   NOT NULL DEFAULT 0
                        );
                        """);
                st.executeUpdate("""
                        CREATE INDEX IF NOT EXISTS idx_rappels_date
                        ON rappels(envoye,date_envoi);
                        """);
            }
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("""
                        CREATE INDEX IF NOT EXISTS idx_factures_prestataire
                        ON factures(prestataire_id, paye);
                        """);
            }
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS mail_prefs (
                            id               INTEGER PRIMARY KEY CHECK(id=1),
                            host             TEXT  NOT NULL,
                            port             INTEGER NOT NULL,
                            ssl              INTEGER NOT NULL DEFAULT 1,
                            user             TEXT,
                            pwd              TEXT,
                            from_addr        TEXT  NOT NULL,
                            copy_to_self     TEXT,
                            delay_hours      INTEGER NOT NULL DEFAULT 48,
                            subj_tpl_presta  TEXT  NOT NULL,
                            body_tpl_presta  TEXT  NOT NULL,
                            subj_tpl_self    TEXT  NOT NULL,
                            body_tpl_self    TEXT  NOT NULL
                        );
                        """);
            }
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("""
                    ALTER TABLE factures
                    ADD COLUMN preavis_envoye INTEGER NOT NULL DEFAULT 0
                """);
            } catch (SQLException ignore) {}
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        try {
            conn.close();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public java.sql.Connection getConnection(){
        return conn;
    }

    public List<Prestataire> list(String filtre) {
        String sql = """
                SELECT  p.*, 
                       (SELECT COUNT(*) FROM factures f
                        WHERE f.prestataire_id = p.id AND f.paye = 0) AS nb_impayes
                FROM    prestataires p
                WHERE   p.nom LIKE ? OR p.societe LIKE ?
                ORDER BY p.nom
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            String like = "%" + filtre + "%";
            ps.setString(1, like);
            ps.setString(2, like);
            ResultSet rs = ps.executeQuery();
            List<Prestataire> l = new ArrayList<>();
            while (rs.next()) l.add(rowToPrestataire(rs));
            return l;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void add(Prestataire p) {
        String sql = "INSERT INTO prestataires(nom,societe,telephone,email,note,facturation,date_contrat) VALUES(?,?,?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            fill(ps, p);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void update(Prestataire p) {
        String sql = """
                UPDATE prestataires SET
                nom=?,societe=?,telephone=?,email=?,note=?,facturation=?,date_contrat=? WHERE id=?
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            fill(ps, p);
            ps.setInt(8, p.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void delete(int pid) {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM prestataires WHERE id=?")) {
            ps.setInt(1, pid);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Prestataire findPrestataire(int pid){
        String sql = "SELECT * FROM prestataires WHERE id=?";
        try(PreparedStatement ps = conn.prepareStatement(sql)){
            ps.setInt(1, pid);
            ResultSet rs = ps.executeQuery();
            if(!rs.next()) return null;
            return rowToPrestataire(rs);
        }catch(SQLException e){ throw new RuntimeException(e);}    }

    public void addService(int pid, String desc) {
        String sql = "INSERT INTO services(prestataire_id,description,date) VALUES(?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, pid);
            ps.setString(2, desc);
            ps.setString(3, DATE_DB.format(LocalDate.now()));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static LocalDate parseAny(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return LocalDate.parse(raw, DATE_DB);
        } catch (DateTimeParseException ex) {
            return LocalDate.parse(raw, DATE_FR);
        }
    }

    public List<ServiceRow> services(int pid) {
        String sql = "SELECT description,date FROM services WHERE prestataire_id=? ORDER BY date";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, pid);
            ResultSet rs = ps.executeQuery();
            List<ServiceRow> out = new ArrayList<>();
            while (rs.next()) {
                LocalDate d = parseAny(rs.getString("date"));
                out.add(new ServiceRow(
                        rs.getString("description"),
                        d == null ? "" : DATE_FR.format(d)));
            }
            return out;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /* =========================== Factures =========================== */

    public void addFacture(Facture f) {
        String sql = "INSERT INTO factures(prestataire_id,description,echeance,montant_ht,paye,date_paiement) VALUES(?,?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, f.getPrestataireId());
            ps.setString(2, f.getDescription());
            ps.setString(3, f.getEcheance().format(DATE_DB));
            ps.setDouble(4, f.getMontant());
            ps.setInt(5, f.isPaye() ? 1 : 0);
            LocalDate dp = f.getDatePaiement();
            if (dp == null) ps.setNull(6, Types.VARCHAR);
            else ps.setString(6, dp.format(DATE_DB));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void setFacturePayee(int id, boolean payee) {
        String sql = "UPDATE factures SET paye=?,date_paiement=? WHERE id=?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, payee ? 1 : 0);
            if (payee) ps.setString(2, LocalDate.now().format(DATE_DB));
            else ps.setNull(2, Types.VARCHAR);
            ps.setInt(3, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Facture> factures(int pid, Boolean payee) {
        String sql = "SELECT * FROM factures WHERE prestataire_id=? " + (payee == null ? "" : "AND paye=? ") + "ORDER BY echeance";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            ps.setInt(idx++, pid);
            if (payee != null) ps.setInt(idx++, payee ? 1 : 0);
            ResultSet rs = ps.executeQuery();
            List<Facture> out = new ArrayList<>();
            while (rs.next()) out.add(rowToFacture(rs));
            return out;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static Facture rowToFacture(ResultSet rs) throws SQLException {
        LocalDate ech = parseAny(rs.getString("echeance"));
        LocalDate dp = parseAny(rs.getString("date_paiement"));
        int preavis = 0;
        try {
            preavis = rs.getInt("preavis_envoye");
        } catch (SQLException ignore) {}
        return new Facture(
                rs.getInt("id"),
                rs.getInt("prestataire_id"),
                rs.getString("description"),
                ech,
                rs.getDouble("montant_ht"),
                rs.getInt("paye") != 0,
                dp,
                preavis != 0
        );
    }

    public List<Facture> facturesImpayeesAvant(LocalDateTime lim){
        String sql = """
            SELECT * FROM factures
            WHERE paye=0 AND preavis_envoye=0 AND
                  echeance <= ?""";
        try(PreparedStatement ps = conn.prepareStatement(sql)){
            ps.setString(1, lim.toLocalDate().toString());
            ResultSet rs = ps.executeQuery();
            List<Facture> l = new ArrayList<>();
            while(rs.next()) l.add(rowToFacture(rs));
            return l;
        }catch(SQLException e){ throw new RuntimeException(e);}  }

    public void marquerPreavisEnvoye(int fid){
        try(PreparedStatement ps = conn.prepareStatement(
            "UPDATE factures SET preavis_envoye=1 WHERE id=?")){
            ps.setInt(1,fid); ps.executeUpdate();
        }catch(SQLException e){ throw new RuntimeException(e);} }

    /* =========================== Rappels =========================== */
    public void addRappel(Rappel r){
        String sql = """
            INSERT INTO rappels(facture_id,dest,sujet,corps,date_envoi)
            VALUES(?,?,?,?,?)
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)){
            ps.setInt   (1, r.factureId());
            ps.setString(2, r.dest());
            ps.setString(3, r.sujet());
            ps.setString(4, r.corps());
            ps.setString(5, r.dateEnvoi().toString());
            ps.executeUpdate();
        }catch(SQLException e){ throw new RuntimeException(e); }
    }

    public List<Rappel> rappelsÀEnvoyer(){
        String sql = "SELECT * FROM rappels WHERE envoye=0 AND date_envoi<=?";
        try(PreparedStatement ps = conn.prepareStatement(sql)){
            ps.setString(1, LocalDateTime.now().toString());
            ResultSet rs = ps.executeQuery();
            List<Rappel> l = new ArrayList<>();
            while(rs.next())
                l.add(new Rappel(
                    rs.getInt("id"),
                    rs.getInt("facture_id"),
                    rs.getString("dest"),
                    rs.getString("sujet"),
                    rs.getString("corps"),
                    LocalDateTime.parse(rs.getString("date_envoi")),
                    rs.getInt("envoye")!=0
                ));
            return l;
        }catch(SQLException e){ throw new RuntimeException(e); }
    }

    public void markRappelEnvoyé(int id){
        try(PreparedStatement ps = conn.prepareStatement(
            "UPDATE rappels SET envoye=1 WHERE id=?")){
            ps.setInt(1,id); ps.executeUpdate();
        }catch(SQLException e){ throw new RuntimeException(e); }
    }

    private static Prestataire rowToPrestataire(ResultSet rs) throws SQLException {
        LocalDate d = parseAny(rs.getString("date_contrat"));
        String date = d == null ? "" : DATE_FR.format(d);
        int imp = 0;
        try {
            imp = rs.getInt("nb_impayes");
        } catch (SQLException ignore) {}
        Prestataire pr = new Prestataire(
                rs.getInt("id"),
                rs.getString("nom"),
                rs.getString("societe"),
                rs.getString("telephone"),
                rs.getString("email"),
                rs.getInt("note"),
                rs.getString("facturation"),
                date);
        pr.setImpayes(imp);
        return pr;
    }

    private static void fill(PreparedStatement ps, Prestataire p) throws SQLException {
        ps.setString(1, p.getNom());
        ps.setString(2, p.getSociete());
        ps.setString(3, p.getTelephone());
        ps.setString(4, p.getEmail());
        ps.setInt(5, p.getNote());
        ps.setString(6, p.getFacturation());
        String raw = p.getDateContrat().trim();
        if (raw.isEmpty()) ps.setNull(7, Types.VARCHAR);
        else ps.setString(7, LocalDate.parse(raw, DATE_FR).format(DATE_DB));
    }
}

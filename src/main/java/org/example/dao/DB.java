package org.example.dao;

import org.example.model.Prestataire;
import org.example.model.ServiceRow;

import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class DB {
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
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void close() {
        try {
            conn.close();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Prestataire> list(String filtre) {
        String sql = """
                SELECT * FROM prestataires
                WHERE nom LIKE ? OR societe LIKE ?
                ORDER BY nom
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

    public List<ServiceRow> services(int pid) {
        String sql = "SELECT description,date FROM services WHERE prestataire_id=? ORDER BY date";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, pid);
            ResultSet rs = ps.executeQuery();
            List<ServiceRow> out = new ArrayList<>();
            while (rs.next()) {
                String raw = rs.getString("date");
                String date = DATE_FR.format(LocalDate.parse(raw, DATE_DB));
                out.add(new ServiceRow(rs.getString("description"), date));
            }
            return out;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static Prestataire rowToPrestataire(ResultSet rs) throws SQLException {
        String raw = rs.getString("date_contrat");
        String date = raw == null || raw.isBlank()
                ? ""
                : DATE_FR.format(LocalDate.parse(raw, DATE_DB));
        return new Prestataire(
                rs.getInt("id"),
                rs.getString("nom"),
                rs.getString("societe"),
                rs.getString("telephone"),
                rs.getString("email"),
                rs.getInt("note"),
                rs.getString("facturation"),
                date);
    }

    private static void fill(PreparedStatement ps, Prestataire p) throws SQLException {
        ps.setString(1, p.getNom());
        ps.setString(2, p.getSociete());
        ps.setString(3, p.getTelephone());
        ps.setString(4, p.getEmail());
        ps.setInt(5, p.getNote());
        ps.setString(6, p.getFacturation());
        ps.setString(7, LocalDate.parse(p.getDateContrat(), DATE_FR).format(DATE_DB));
    }
}

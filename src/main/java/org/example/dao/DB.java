package org.example.dao;

import org.example.model.Facture;
import org.example.model.Prestataire;
import org.example.model.Rappel;
import org.example.model.ServiceRow;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteOpenMode;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class DB implements ConnectionProvider {

    private static final DateTimeFormatter DATE_FR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_DB = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final HikariDataSource ds;
    private final ConnectionProvider delegate;
    private Connection singleConn;
    private Connection proxyConn;

    public static Connection newConnection(String path) throws SQLException {
        SQLiteConfig cfg = new SQLiteConfig();
        cfg.setBusyTimeout(5000);
        cfg.setSharedCache(true);
        cfg.setSynchronous(SQLiteConfig.SynchronousMode.FULL);
        cfg.setJournalMode(SQLiteConfig.JournalMode.WAL);
        cfg.enforceForeignKeys(true);
        cfg.setOpenMode(SQLiteOpenMode.FULLMUTEX);
        return DriverManager.getConnection("jdbc:sqlite:" + path, cfg.toProperties());
    }

    public DB(ConnectionProvider provider) {
        this.ds = null;
        this.delegate = provider;
        try {
            singleConn = provider.getConnection();
            try (Statement st = singleConn.createStatement()) {
                st.execute("PRAGMA foreign_keys = 1");
            }
            initSchema(singleConn);
            proxyConn = wrap(singleConn);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public DB(String path) {
        SQLiteConfig sc = new SQLiteConfig();
        sc.setBusyTimeout(5000);
        sc.setSharedCache(true);
        sc.setSynchronous(SQLiteConfig.SynchronousMode.FULL);
        sc.setJournalMode(SQLiteConfig.JournalMode.WAL);
        sc.enforceForeignKeys(true);
        sc.setOpenMode(SQLiteOpenMode.FULLMUTEX);

        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl("jdbc:sqlite:" + path);
        hc.setDataSourceProperties(sc.toProperties());
        this.ds = new HikariDataSource(hc);
        this.delegate = ds::getConnection;

        try (Connection c = ds.getConnection();
             Statement st = c.createStatement()) {
            st.execute("PRAGMA foreign_keys = 1");
            initSchema(c);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Connection getConnection() throws SQLException {
        return ds == null ? proxyConn : ds.getConnection();
    }

    @Override
    public void close() {
        if (ds != null) ds.close();
        else {
            try {
                singleConn.close();
            } catch (Exception ignore) {
            }
        }
    }

    private static Connection wrap(Connection c) {
        InvocationHandler h = (Object p, Method m, Object[] a) -> {
            if ("close".equals(m.getName())) return null;
            try {
                return m.invoke(c, a);
            } catch (InvocationTargetException ex) {
                throw ex.getTargetException();
            }
        };
        return (Connection) Proxy.newProxyInstance(DB.class.getClassLoader(), new Class<?>[]{Connection.class}, h);
    }

    private static void initSchema(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS prestataires(
                        id INTEGER PRIMARY KEY,
                        nom TEXT UNIQUE NOT NULL COLLATE NOCASE,
                        societe TEXT,
                        telephone TEXT,
                        email TEXT,
                        note INTEGER CHECK(note BETWEEN 0 AND 100),
                        facturation TEXT,
                        date_contrat TEXT,
                        date_contrat_ts INTEGER
                    );""");
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS services(
                        id INTEGER PRIMARY KEY,
                        prestataire_id INTEGER REFERENCES prestataires(id) ON DELETE CASCADE,
                        description TEXT,
                        date TEXT,
                        date_ts INTEGER
                    );""");
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS factures(
                        id INTEGER PRIMARY KEY,
                        prestataire_id INTEGER NOT NULL REFERENCES prestataires(id) ON DELETE CASCADE,
                        description TEXT,
                        echeance TEXT NOT NULL,
                        echeance_ts INTEGER NOT NULL,
                        montant_ht REAL NOT NULL,
                        tva_pct REAL NOT NULL DEFAULT 20,
                        montant_tva REAL NOT NULL,
                        montant_ttc REAL NOT NULL,
                        devise TEXT DEFAULT 'EUR',
                        paye INTEGER NOT NULL DEFAULT 0,
                        date_paiement TEXT,
                        date_paiement_ts INTEGER,
                        preavis_envoye INTEGER NOT NULL DEFAULT 0
                    );""");
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS rappels(
                        id INTEGER PRIMARY KEY,
                        facture_id INTEGER NOT NULL REFERENCES factures(id) ON DELETE CASCADE,
                        dest TEXT NOT NULL,
                        sujet TEXT NOT NULL,
                        corps TEXT NOT NULL,
                        date_envoi TEXT NOT NULL,
                        date_envoi_ts INTEGER NOT NULL,
                        envoye INTEGER NOT NULL DEFAULT 0
                    );""");
            st.executeUpdate("""
                    CREATE INDEX IF NOT EXISTS idx_rappels_date ON rappels(envoye,date_envoi_ts);""");
            st.executeUpdate("""
                    CREATE INDEX IF NOT EXISTS idx_factures_prestataire ON factures(prestataire_id,paye);""");
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS mail_prefs(
                        id INTEGER PRIMARY KEY CHECK(id=1),
                        host TEXT NOT NULL,
                        port INTEGER NOT NULL,
                        ssl INTEGER NOT NULL DEFAULT 1,
                        user TEXT,
                        pwd TEXT,
                        provider TEXT,
                        oauth_client TEXT,
                        oauth_refresh TEXT,
                        oauth_expiry INTEGER,
                        from_addr TEXT NOT NULL,
                        copy_to_self TEXT NOT NULL DEFAULT '',
                        delay_hours INTEGER NOT NULL DEFAULT 48,
                        style TEXT NOT NULL DEFAULT 'fr',
                        subj_tpl_presta TEXT NOT NULL,
                        body_tpl_presta TEXT NOT NULL,
                        subj_tpl_self TEXT NOT NULL,
                        body_tpl_self TEXT NOT NULL
                    );""");
        }
        upgradeCopyToSelf(c);
        addMissingColumns(c);
    }

    public List<Prestataire> list(String filter) {
        String sql = """
                SELECT p.*,
                       (SELECT COUNT(*) FROM factures f WHERE f.prestataire_id=p.id AND f.paye=0) AS nb_impayes
                FROM prestataires p
                WHERE p.nom LIKE ? OR p.societe LIKE ?
                ORDER BY p.nom""";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            String like = '%' + filter + '%';
            ps.setString(1, like);
            ps.setString(2, like);
            ResultSet rs = ps.executeQuery();
            List<Prestataire> out = new ArrayList<>();
            while (rs.next()) out.add(toPrestataire(rs));
            return out;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void add(Prestataire p) {
        String sql = """
                INSERT INTO prestataires(nom,societe,telephone,email,note,facturation,date_contrat,date_contrat_ts)
                VALUES(?,?,?,?,?,?,?,?)""";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindPrestataire(ps, p);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void update(Prestataire p) {
        String sql = """
                UPDATE prestataires SET
                nom=?,societe=?,telephone=?,email=?,note=?,facturation=?,date_contrat=?,date_contrat_ts=?
                WHERE id=?""";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindPrestataire(ps, p);
            ps.setInt(9, p.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void delete(int id) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM prestataires WHERE id=?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Prestataire findPrestataire(int id) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM prestataires WHERE id=?")) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? toPrestataire(rs) : null;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void addService(int pid, String desc) {
        String sql = "INSERT INTO services(prestataire_id,description,date,date_ts) VALUES(?,?,?,?)";
        LocalDate now = LocalDate.now();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, pid);
            ps.setString(2, desc);
            ps.setString(3, DATE_DB.format(now));
            ps.setLong(4, now.atStartOfDay().toEpochSecond(ZoneOffset.UTC));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<ServiceRow> services(int pid) {
        String sql = "SELECT description,date,date_ts FROM services WHERE prestataire_id=? ORDER BY date_ts";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, pid);
            ResultSet rs = ps.executeQuery();
            List<ServiceRow> out = new ArrayList<>();
            while (rs.next()) {
                LocalDate d;
                long ts = rs.getLong("date_ts");
                if (rs.wasNull()) d = parseDate(rs.getString("date"));
                else d = LocalDateTime.ofEpochSecond(ts, 0, ZoneOffset.UTC).toLocalDate();
                out.add(new ServiceRow(rs.getString("description"), d == null ? "" : DATE_FR.format(d)));
            }
            return out;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void addFacture(Facture f) {
        String sql = """
                INSERT INTO factures(prestataire_id,description,echeance,echeance_ts,
                                     montant_ht,tva_pct,montant_tva,montant_ttc,devise,
                                     paye,date_paiement,date_paiement_ts)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?)""";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, f.getPrestataireId());
            ps.setString(2, f.getDescription());
            ps.setString(3, f.getEcheance().format(DATE_DB));
            ps.setLong(4, f.getEcheance().atStartOfDay().toEpochSecond(ZoneOffset.UTC));
            ps.setBigDecimal(5, f.getMontantHt());
            ps.setBigDecimal(6, f.getTvaPct());
            ps.setBigDecimal(7, f.getMontantTva());
            ps.setBigDecimal(8, f.getMontantTtc());
            ps.setString(9, "EUR");
            ps.setInt(10, f.isPaye() ? 1 : 0);
            LocalDate dp = f.getDatePaiement();
            if (dp == null) {
                ps.setNull(11, Types.VARCHAR);
                ps.setNull(12, Types.INTEGER);
            } else {
                ps.setString(11, dp.format(DATE_DB));
                ps.setLong(12, dp.atStartOfDay().toEpochSecond(ZoneOffset.UTC));
            }
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void setFacturePayee(int id, boolean payee) {
        String sql = "UPDATE factures SET paye=?,date_paiement=?,date_paiement_ts=? WHERE id=?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, payee ? 1 : 0);
            if (payee) {
                LocalDate now = LocalDate.now();
                ps.setString(2, now.format(DATE_DB));
                ps.setLong(3, now.atStartOfDay().toEpochSecond(ZoneOffset.UTC));
            } else {
                ps.setNull(2, Types.VARCHAR);
                ps.setNull(3, Types.INTEGER);
            }
            ps.setInt(4, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Facture> factures(int pid, Boolean payee) {
        String sql = "SELECT * FROM factures WHERE prestataire_id=? " + (payee == null ? "" : "AND paye=? ") + "ORDER BY echeance_ts";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, pid);
            if (payee != null) ps.setInt(2, payee ? 1 : 0);
            ResultSet rs = ps.executeQuery();
            List<Facture> list = new ArrayList<>();
            while (rs.next()) list.add(toFacture(rs));
            return list;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Facture> facturesImpayeesAvant(LocalDateTime limit) {
        String sql = "SELECT * FROM factures WHERE paye=0 AND preavis_envoye=0 AND echeance_ts<=?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, limit.toEpochSecond(ZoneOffset.UTC));
            ResultSet rs = ps.executeQuery();
            List<Facture> list = new ArrayList<>();
            while (rs.next()) list.add(toFacture(rs));
            return list;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void marquerPreavisEnvoye(int id) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE factures SET preavis_envoye=1 WHERE id=?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void addRappel(Rappel r) {
        String sql = "INSERT INTO rappels(facture_id,dest,sujet,corps,date_envoi,date_envoi_ts) VALUES(?,?,?,?,?,?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, r.factureId());
            ps.setString(2, r.dest());
            ps.setString(3, r.sujet());
            ps.setString(4, r.corps());
            ps.setString(5, r.dateEnvoi().toString());
            ps.setLong(6, r.dateEnvoi().toEpochSecond(ZoneOffset.UTC));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Rappel> rappelsÀEnvoyer() {
        String sql = "SELECT * FROM rappels WHERE envoye=0 AND date_envoi_ts<=?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, LocalDateTime.now().toEpochSecond(ZoneOffset.UTC));
            ResultSet rs = ps.executeQuery();
            List<Rappel> list = new ArrayList<>();
            while (rs.next()) {
                list.add(new Rappel(
                        rs.getInt("id"),
                        rs.getInt("facture_id"),
                        rs.getString("dest"),
                        rs.getString("sujet"),
                        rs.getString("corps"),
                        LocalDateTime.ofEpochSecond(rs.getLong("date_envoi_ts"), 0, ZoneOffset.UTC),
                        rs.getInt("envoye") != 0));
            }
            return list;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void markRappelEnvoyé(int id) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE rappels SET envoye=1 WHERE id=?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static Prestataire toPrestataire(ResultSet rs) throws SQLException {
        LocalDate d;
        long ts = rs.getLong("date_contrat_ts");
        if (rs.wasNull()) d = parseDate(rs.getString("date_contrat"));
        else d = LocalDateTime.ofEpochSecond(ts, 0, ZoneOffset.UTC).toLocalDate();
        String date = d == null ? "" : DATE_FR.format(d);
        int imp = 0;
        try {
            imp = rs.getInt("nb_impayes");
        } catch (SQLException ignore) {
        }
        Prestataire p = new Prestataire(
                rs.getInt("id"),
                rs.getString("nom"),
                rs.getString("societe"),
                rs.getString("telephone"),
                rs.getString("email"),
                rs.getInt("note"),
                rs.getString("facturation"),
                date);
        p.setImpayes(imp);
        return p;
    }

    private static Facture toFacture(ResultSet rs) throws SQLException {
        LocalDate ech;
        long ts = rs.getLong("echeance_ts");
        if (rs.wasNull()) ech = parseDate(rs.getString("echeance"));
        else ech = LocalDateTime.ofEpochSecond(ts, 0, ZoneOffset.UTC).toLocalDate();

        LocalDate dp;
        long ts2 = rs.getLong("date_paiement_ts");
        if (rs.wasNull()) dp = parseDate(rs.getString("date_paiement"));
        else dp = LocalDateTime.ofEpochSecond(ts2, 0, ZoneOffset.UTC).toLocalDate();

        int prev = 0;
        try {
            prev = rs.getInt("preavis_envoye");
        } catch (SQLException ignore) {
        }

        return new Facture(
                rs.getInt("id"),
                rs.getInt("prestataire_id"),
                rs.getString("description"),
                ech,
                rs.getBigDecimal("montant_ht"),
                rs.getBigDecimal("tva_pct"),
                rs.getBigDecimal("montant_tva"),
                rs.getBigDecimal("montant_ttc"),
                rs.getInt("paye") != 0,
                dp,
                prev != 0);
    }

    private static void bindPrestataire(PreparedStatement ps, Prestataire p) throws SQLException {
        ps.setString(1, p.getNom());
        ps.setString(2, p.getSociete());
        ps.setString(3, p.getTelephone());
        ps.setString(4, p.getEmail());
        ps.setInt(5, p.getNote());
        ps.setString(6, p.getFacturation());
        String raw = Objects.toString(p.getDateContrat(), "").trim();
        if (raw.isEmpty()) {
            ps.setNull(7, Types.VARCHAR);
            ps.setNull(8, Types.INTEGER);
        } else {
            LocalDate d = LocalDate.parse(raw, DATE_FR);
            ps.setString(7, d.format(DATE_DB));
            ps.setLong(8, d.atStartOfDay().toEpochSecond(ZoneOffset.UTC));
        }
    }

    private static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return LocalDate.parse(raw, DATE_DB);
        } catch (DateTimeParseException ex) {
            return LocalDate.parse(raw, DATE_FR);
        }
    }

    private static void addMissingColumns(Connection c) throws SQLException {
        ensureTs(c, "prestataires", "date_contrat_ts", "date_contrat");
        ensureTs(c, "services", "date_ts", "date");
        ensureTs(c, "factures", "echeance_ts", "echeance");
        ensureTs(c, "factures", "date_paiement_ts", "date_paiement");
        ensureTs(c, "rappels", "date_envoi_ts", "date_envoi");
        ensureMoney(c);
    }

    private static void upgradeCopyToSelf(Connection c) {
        try (Statement st = c.createStatement()) {
            st.executeUpdate("UPDATE mail_prefs SET copy_to_self='' WHERE copy_to_self IS NULL");
            st.executeUpdate("ALTER TABLE mail_prefs ALTER COLUMN copy_to_self SET DEFAULT ''");
            st.executeUpdate("ALTER TABLE mail_prefs ALTER COLUMN copy_to_self SET NOT NULL");
        } catch (SQLException ignore) {
        }
    }

    private static void ensureTs(Connection c, String table, String col, String from) throws SQLException {
        if (!hasColumn(c, table, col)) {
            try (Statement st = c.createStatement()) {
                st.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + col + " INTEGER");
                st.executeUpdate("UPDATE " + table + " SET " + col + "=strftime('%s'," + from + ") WHERE " + from + " IS NOT NULL");
            }
        }
    }

    private static boolean hasColumn(Connection c, String table, String col) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("PRAGMA table_info(" + table + ")");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) if (col.equalsIgnoreCase(rs.getString("name"))) return true;
        }
        return false;
    }

    private static void ensureColumn(Connection c, String table, String col, String def) throws SQLException {
        if (!hasColumn(c, table, col)) {
            try (Statement st = c.createStatement()) {
                st.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + col + " " + def);
            }
        }
    }

    private static void ensureMoney(Connection c) throws SQLException {
        ensureColumn(c, "factures", "tva_pct", "REAL NOT NULL DEFAULT 20");
        ensureColumn(c, "factures", "montant_tva", "REAL NOT NULL DEFAULT 0");
        ensureColumn(c, "factures", "montant_ttc", "REAL NOT NULL DEFAULT 0");
        ensureColumn(c, "factures", "devise", "TEXT DEFAULT 'EUR'");
        try (Statement st = c.createStatement()) {
            st.executeUpdate("UPDATE factures SET tva_pct=20 WHERE tva_pct IS NULL");
            st.executeUpdate("UPDATE factures SET montant_tva=montant_ht*tva_pct/100 WHERE montant_tva=0");
            st.executeUpdate("UPDATE factures SET montant_ttc=montant_ht+montant_tva WHERE montant_ttc=0");
            st.executeUpdate("UPDATE factures SET devise='EUR' WHERE devise IS NULL");
        }
    }
}

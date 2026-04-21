package com.hrreporting.db;

import com.hrreporting.model.FaitRH;

import java.sql.*;
import java.util.*;

/**
 * DWRepository — Couche d'accès aux données du Data Warehouse H2.
 * Toutes les méthodes KPI acceptent des filtres annee/departement.
 * "Toutes" / "Tous" = pas de filtre appliqué.
 */
public class DWRepository {

    // ═══════════════════════════════════════════════════════════════════
    // INSERTIONS — DIMENSIONS
    // ═══════════════════════════════════════════════════════════════════

    public static int upsertDepartement(String nomDept, String localisation, String responsable) throws SQLException {
        Connection conn = DatabaseManager.getConnection();
        PreparedStatement check = conn.prepareStatement(
                "SELECT dept_id FROM dim_departement WHERE nom_dept = ?");
        check.setString(1, nomDept);
        ResultSet rs = check.executeQuery();
        if (rs.next()) return rs.getInt("dept_id");

        PreparedStatement insert = conn.prepareStatement(
                "INSERT INTO dim_departement (nom_dept, localisation, responsable) VALUES (?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS);
        insert.setString(1, nomDept);
        insert.setString(2, localisation);
        insert.setString(3, responsable);
        insert.executeUpdate();
        ResultSet keys = insert.getGeneratedKeys();
        keys.next();
        return keys.getInt(1);
    }

    public static int upsertTemps(int annee, int semestre, int trimestre, int mois) throws SQLException {
        Connection conn = DatabaseManager.getConnection();
        PreparedStatement check = conn.prepareStatement(
                "SELECT temps_id FROM dim_temps WHERE annee = ? AND semestre = ? AND mois = ?");
        check.setInt(1, annee);
        check.setInt(2, semestre);
        check.setInt(3, mois);
        ResultSet rs = check.executeQuery();
        if (rs.next()) return rs.getInt("temps_id");

        PreparedStatement insert = conn.prepareStatement(
                "INSERT INTO dim_temps (annee, semestre, trimestre, mois) VALUES (?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS);
        insert.setInt(1, annee);
        insert.setInt(2, semestre);
        insert.setInt(3, trimestre);
        insert.setInt(4, mois);
        insert.executeUpdate();
        ResultSet keys = insert.getGeneratedKeys();
        keys.next();
        return keys.getInt(1);
    }

    public static int upsertPoste(String titre, String niveau, String sourceRecrutement) throws SQLException {
        Connection conn = DatabaseManager.getConnection();
        PreparedStatement check = conn.prepareStatement(
                "SELECT poste_id FROM dim_poste WHERE titre = ?");
        check.setString(1, titre);
        ResultSet rs = check.executeQuery();
        if (rs.next()) return rs.getInt("poste_id");

        PreparedStatement insert = conn.prepareStatement(
                "INSERT INTO dim_poste (titre, niveau, source_recrutement) VALUES (?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS);
        insert.setString(1, titre);
        insert.setString(2, niveau);
        insert.setString(3, sourceRecrutement);
        insert.executeUpdate();
        ResultSet keys = insert.getGeneratedKeys();
        keys.next();
        return keys.getInt(1);
    }

    public static int upsertFormation(String intitule, int dureeJours, double coutUsd) throws SQLException {
        Connection conn = DatabaseManager.getConnection();
        PreparedStatement check = conn.prepareStatement(
                "SELECT formation_id FROM dim_formation WHERE intitule = ?");
        check.setString(1, intitule);
        ResultSet rs = check.executeQuery();
        if (rs.next()) return rs.getInt("formation_id");

        PreparedStatement insert = conn.prepareStatement(
                "INSERT INTO dim_formation (intitule, duree_jours, cout_usd) VALUES (?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS);
        insert.setString(1, intitule);
        insert.setInt(2, dureeJours);
        insert.setDouble(3, coutUsd);
        insert.executeUpdate();
        ResultSet keys = insert.getGeneratedKeys();
        keys.next();
        return keys.getInt(1);
    }

    public static void upsertEmploye(String employeId, String nom, int age, String genre,
                                     int anciennete, String statut, String motifDepart) throws SQLException {
        Connection conn = DatabaseManager.getConnection();
        PreparedStatement check = conn.prepareStatement(
                "SELECT employe_id FROM dim_employe WHERE employe_id = ?");
        check.setString(1, employeId);
        ResultSet rs = check.executeQuery();
        if (rs.next()) return;

        PreparedStatement insert = conn.prepareStatement(
                "INSERT INTO dim_employe (employe_id, nom, age, genre, anciennete_ans, statut, motif_depart) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?)");
        insert.setString(1, employeId);
        insert.setString(2, nom);
        insert.setInt(3, age);
        insert.setString(4, genre);
        insert.setInt(5, anciennete);
        insert.setString(6, statut);
        insert.setString(7, motifDepart);
        insert.executeUpdate();
    }

    // ═══════════════════════════════════════════════════════════════════
    // INSERTION — TABLE DE FAITS
    // ═══════════════════════════════════════════════════════════════════

    public static void insertFait(FaitRH fait) throws SQLException {
        Connection conn = DatabaseManager.getConnection();
        PreparedStatement ps = conn.prepareStatement("""
            INSERT INTO fait_rh (
                employe_id, dept_id, temps_id, poste_id, formation_id,
                salaire_mensuel, attrition, score_performance, satisfaction_employe,
                nb_absences, heures_sup, score_evaluation, objectifs_atteints_pct,
                cout_formation, nb_formations, duree_avant_depart, promotion_recommandee,
                annee_depart, annee_embauche
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """);
        bindFait(ps, fait);
        ps.executeUpdate();
    }

    public static void insertFaitsBatch(List<FaitRH> faits) throws SQLException {
        Connection conn = DatabaseManager.getConnection();
        conn.setAutoCommit(false);
        PreparedStatement ps = conn.prepareStatement("""
            INSERT INTO fait_rh (
                employe_id, dept_id, temps_id, poste_id, formation_id,
                salaire_mensuel, attrition, score_performance, satisfaction_employe,
                nb_absences, heures_sup, score_evaluation, objectifs_atteints_pct,
                cout_formation, nb_formations, duree_avant_depart, promotion_recommandee,
                annee_depart, annee_embauche
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """);
        for (FaitRH fait : faits) {
            bindFait(ps, fait);
            ps.addBatch();
        }
        ps.executeBatch();
        conn.commit();
        conn.setAutoCommit(true);
        System.out.println("[DB] " + faits.size() + " faits insérés en batch.");
    }

    private static void bindFait(PreparedStatement ps, FaitRH fait) throws SQLException {
        ps.setString(1, fait.getEmployeId());
        ps.setInt(2, fait.getDeptId());
        ps.setInt(3, fait.getTempsId());
        ps.setInt(4, fait.getPosteId());
        setNullableInt(ps, 5, fait.getFormationId());
        setNullableDouble(ps, 6, fait.getSalaireMensuel());
        ps.setInt(7, fait.getAttrition());
        setNullableInt(ps, 8, fait.getScorePerformance());
        setNullableInt(ps, 9, fait.getSatisfactionEmploye());
        setNullableInt(ps, 10, fait.getNbAbsences());
        ps.setInt(11, fait.getHeuresSup());
        setNullableInt(ps, 12, fait.getScoreEvaluation());
        setNullableInt(ps, 13, fait.getObjectifsAtteintsP());
        setNullableDouble(ps, 14, fait.getCoutFormation());
        setNullableInt(ps, 15, fait.getNbFormations());
        setNullableInt(ps, 16, fait.getDureeAvantDepart());
        setNullableInt(ps, 17, fait.getPromotionRecommandee());
        setNullableInt(ps, 18, fait.getAnneeDepart());
        setNullableInt(ps, 19, fait.getAnneeEmbauche());
    }

    // ═══════════════════════════════════════════════════════════════════
    // REQUÊTES KPI — AVEC FILTRES
    // ═══════════════════════════════════════════════════════════════════

    /** Effectifs : nombre d'employés par département */
    public static Map<String, Integer> getEffectifParDept(String annee, String dept) throws SQLException {
        String sql = """
            SELECT d.nom_dept, COUNT(DISTINCT f.employe_id) AS nb
            FROM fait_rh f
            JOIN dim_departement d ON f.dept_id = d.dept_id
            JOIN dim_temps t ON f.temps_id = t.temps_id
            WHERE 1=1
            """ + filtreActifAnnee(annee) + filtreDept(dept) + """
            GROUP BY d.nom_dept ORDER BY nb DESC
        """;
        return queryStringInt(sql);
    }

    /** Taux d'attrition (%) par département */
    public static Map<String, Double> getTauxAttritionParDept(String annee, String dept) throws SQLException {
        String sql = """
        SELECT d.nom_dept,
               ROUND(
                 COUNT(CASE WHEN f.annee_depart = {AN} THEN 1 END) * 100.0
                 / NULLIF(COUNT(CASE WHEN f.annee_embauche <= {AN}
                                    AND (f.annee_depart IS NULL OR f.annee_depart >= {AN})
                               THEN 1 END), 0)
               , 1) AS taux
        FROM fait_rh f
        JOIN dim_departement d ON f.dept_id = d.dept_id
        WHERE 1=1
        """;
        // Pour "Toutes" : fallback sur l'ancienne logique globale
        if (annee == null || annee.equals("Toutes")) {
            sql = """
            SELECT d.nom_dept,
                   ROUND(SUM(f.attrition) * 100.0 / NULLIF(COUNT(*), 0), 1) AS taux
            FROM fait_rh f
            JOIN dim_departement d ON f.dept_id = d.dept_id
            WHERE 1=1
            """ + filtreDept(dept) + """
            GROUP BY d.nom_dept ORDER BY taux DESC
        """;
        } else {
            String an = annee.replaceAll("[^0-9]", "");
            sql = sql.replace("{AN}", an).replace("{AN}", an) + filtreDept(dept) + """
            GROUP BY d.nom_dept ORDER BY taux DESC
        """;
        }
        return queryStringDouble(sql, "nom_dept", "taux");
    }

    /** Salaire mensuel moyen par département */
    public static Map<String, Double> getSalaireMoyenParDept(String annee, String dept) throws SQLException {
        String sql = """
            SELECT d.nom_dept,
                   ROUND(AVG(f.salaire_mensuel), 2) AS salaire_moyen
            FROM fait_rh f
            JOIN dim_departement d ON f.dept_id = d.dept_id
            JOIN dim_temps t ON f.temps_id = t.temps_id
            WHERE f.salaire_mensuel IS NOT NULL AND f.salaire_mensuel > 0
            """ + filtreActifAnnee(annee) + filtreDept(dept) + """
            GROUP BY d.nom_dept ORDER BY salaire_moyen DESC
        """;
        return queryStringDouble(sql, "nom_dept", "salaire_moyen");
    }

    /** Salaire moyen par genre */
    public static Map<String, Double> getSalaireMoyenParGenre(String annee, String dept) throws SQLException {
        String sql = """
            SELECT e.genre,
                   ROUND(AVG(f.salaire_mensuel), 2) AS salaire_moyen
            FROM fait_rh f
            JOIN dim_employe e ON f.employe_id = e.employe_id
            JOIN dim_departement d ON f.dept_id = d.dept_id
            JOIN dim_temps t ON f.temps_id = t.temps_id
            WHERE f.salaire_mensuel IS NOT NULL AND f.salaire_mensuel > 0
              AND e.genre IS NOT NULL
            """ + filtreActifAnnee(annee) + filtreDept(dept) + """
            GROUP BY e.genre
        """;
        return queryStringDouble(sql, "genre", "salaire_moyen");
    }

    /** Score performance moyen par département */
    public static Map<String, Double> getScorePerfMoyenParDept(String annee, String dept) throws SQLException {
        String sql = """
            SELECT d.nom_dept,
                   ROUND(AVG(f.score_performance), 2) AS score_moyen
            FROM fait_rh f
            JOIN dim_departement d ON f.dept_id = d.dept_id
            JOIN dim_temps t ON f.temps_id = t.temps_id
            WHERE f.score_performance IS NOT NULL AND f.score_performance > 0
            """ + filtreActifAnnee(annee) + filtreDept(dept) + """
            GROUP BY d.nom_dept ORDER BY score_moyen DESC
        """;
        return queryStringDouble(sql, "nom_dept", "score_moyen");
    }

    /** Satisfaction moyenne par département */
    public static Map<String, Double> getSatisfactionParDept(String annee, String dept) throws SQLException {
        String sql = """
            SELECT d.nom_dept,
                   ROUND(AVG(f.satisfaction_employe), 2) AS satisfaction
            FROM fait_rh f
            JOIN dim_departement d ON f.dept_id = d.dept_id
            JOIN dim_temps t ON f.temps_id = t.temps_id
            WHERE f.satisfaction_employe IS NOT NULL AND f.satisfaction_employe > 0
            """ + filtreActifAnnee(annee) + filtreDept(dept) + """
            GROUP BY d.nom_dept ORDER BY satisfaction DESC
        """;
        return queryStringDouble(sql, "nom_dept", "satisfaction");
    }

    /** Absentéisme moyen par département */
    public static Map<String, Double> getAbsenteismeParDept(String annee, String dept) throws SQLException {
        String sql = """
            SELECT d.nom_dept,
                   ROUND(AVG(f.nb_absences), 1) AS moy_absences
            FROM fait_rh f
            JOIN dim_departement d ON f.dept_id = d.dept_id
            JOIN dim_temps t ON f.temps_id = t.temps_id
            WHERE f.nb_absences IS NOT NULL AND f.nb_absences >= 0
            """ + filtreActifAnnee(annee) + filtreDept(dept) + """
            GROUP BY d.nom_dept ORDER BY moy_absences DESC
        """;
        return queryStringDouble(sql, "nom_dept", "moy_absences");
    }

    /** Coût total formations par département */
    public static Map<String, Double> getCoutFormationParDept(String annee, String dept) throws SQLException {
        String sql = """
            SELECT d.nom_dept,
                   ROUND(SUM(f.cout_formation), 2) AS cout_total
            FROM fait_rh f
            JOIN dim_departement d ON f.dept_id = d.dept_id
            JOIN dim_temps t ON f.temps_id = t.temps_id
            WHERE f.cout_formation IS NOT NULL AND f.cout_formation > 0
            """ + filtreAnnee(annee) + filtreDept(dept) + """
            GROUP BY d.nom_dept ORDER BY cout_total DESC
        """;
        return queryStringDouble(sql, "nom_dept", "cout_total");
    }

    /** Répartition genre */
    public static Map<String, Integer> getRepartitionGenre(String annee, String dept) throws SQLException {
        String sql = """
            SELECT e.genre, COUNT(DISTINCT f.employe_id) AS nb
            FROM fait_rh f
            JOIN dim_employe e ON f.employe_id = e.employe_id
            JOIN dim_departement d ON f.dept_id = d.dept_id
            JOIN dim_temps t ON f.temps_id = t.temps_id
            WHERE e.genre IS NOT NULL
            """ + filtreActifAnnee(annee) + filtreDept(dept) + """
            GROUP BY e.genre
        """;
        return queryStringInt(sql);
    }

    /** Motifs de départ */
    public static Map<String, Integer> getMotifsDepart(String annee, String dept) throws SQLException {
        String sql = """
            SELECT e.motif_depart, COUNT(*) AS nb
            FROM fait_rh f
            JOIN dim_employe e ON f.employe_id = e.employe_id
            JOIN dim_departement d ON f.dept_id = d.dept_id
            JOIN dim_temps t ON f.temps_id = t.temps_id
            WHERE f.attrition = 1 AND e.motif_depart IS NOT NULL
            """ + filtreAnneeDepart(annee) + filtreDept(dept) + """
            GROUP BY e.motif_depart ORDER BY nb DESC
        """;
        return queryStringInt(sql);
    }

    /** Candidats à la promotion par département */
    public static Map<String, Integer> getCandidatsPromotion(String annee, String dept) throws SQLException {
        String sql = """
            SELECT d.nom_dept, COUNT(*) AS nb
            FROM fait_rh f
            JOIN dim_departement d ON f.dept_id = d.dept_id
            JOIN dim_temps t ON f.temps_id = t.temps_id
            WHERE f.promotion_recommandee = 1
              AND f.score_performance >= 3
              AND f.score_evaluation >= 4
              AND f.objectifs_atteints_pct >= 80
            """ + filtreAnnee(annee) + filtreDept(dept) + """
            GROUP BY d.nom_dept ORDER BY nb DESC
        """;
        return queryStringInt(sql);
    }

    // ═══════════════════════════════════════════════════════════════════
    // COMPATIBILITÉ ASCENDANTE — sans filtres
    // ═══════════════════════════════════════════════════════════════════

    public static Map<String, Integer> getEffectifParDept()           throws SQLException { return getEffectifParDept("Toutes", "Tous"); }
    public static Map<String, Double>  getTauxAttritionParDept()      throws SQLException { return getTauxAttritionParDept("Toutes", "Tous"); }
    public static Map<String, Double>  getSalaireMoyenParDept()       throws SQLException { return getSalaireMoyenParDept("Toutes", "Tous"); }
    public static Map<String, Double>  getSalaireMoyenParGenre()      throws SQLException { return getSalaireMoyenParGenre("Toutes", "Tous"); }
    public static Map<String, Double>  getScorePerfMoyenParDept()     throws SQLException { return getScorePerfMoyenParDept("Toutes", "Tous"); }
    public static Map<String, Double>  getSatisfactionParDept()       throws SQLException { return getSatisfactionParDept("Toutes", "Tous"); }
    public static Map<String, Double>  getAbsenteismeParDept()        throws SQLException { return getAbsenteismeParDept("Toutes", "Tous"); }
    public static Map<String, Double>  getCoutFormationParDept()      throws SQLException { return getCoutFormationParDept("Toutes", "Tous"); }
    public static Map<String, Integer> getRepartitionGenre()          throws SQLException { return getRepartitionGenre("Toutes", "Tous"); }
    public static Map<String, Integer> getMotifsDepart()              throws SQLException { return getMotifsDepart("Toutes", "Tous"); }
    public static Map<String, Integer> getCandidatsPromotion()        throws SQLException { return getCandidatsPromotion("Toutes", "Tous"); }

    // ═══════════════════════════════════════════════════════════════════
    // KPI SCALAIRES — AVEC FILTRES
    // ═══════════════════════════════════════════════════════════════════

    /** Taux d'attrition global (%) */
    public static double getTauxAttritionGlobal(String annee, String dept) throws SQLException {
        String sql;
        if (annee == null || annee.equals("Toutes")) {
            sql = """
            SELECT ROUND(SUM(f.attrition) * 100.0 / NULLIF(COUNT(*), 0), 1)
            FROM fait_rh f
            JOIN dim_departement d ON f.dept_id = d.dept_id
            WHERE 1=1
            """ + filtreDept(dept);
        } else {
            String an = annee.replaceAll("[^0-9]", "");
            sql = "SELECT ROUND(" +
                    "  COUNT(CASE WHEN f.annee_depart = " + an + " THEN 1 END) * 100.0" +
                    "  / NULLIF(COUNT(CASE WHEN f.annee_embauche <= " + an +
                    "    AND (f.annee_depart IS NULL OR f.annee_depart >= " + an + ")" +
                    "    THEN 1 END), 0), 1)" +
                    " FROM fait_rh f" +
                    " JOIN dim_departement d ON f.dept_id = d.dept_id" +
                    " WHERE 1=1" + filtreDept(dept);
        }
        ResultSet rs = DatabaseManager.getConnection().createStatement().executeQuery(sql);
        return rs.next() ? rs.getDouble(1) : 0;
    }

    /** Effectif total */
    public static int getEffectifTotal(String annee, String dept) throws SQLException {
        String sql;
        if (annee == null || annee.equals("Toutes")) {
            sql = """
            SELECT COUNT(DISTINCT f.employe_id)
            FROM fait_rh f
            JOIN dim_departement d ON f.dept_id = d.dept_id
            WHERE 1=1
            """ + filtreDept(dept);
        } else {
            String an = annee.replaceAll("[^0-9]", "");
            sql = "SELECT COUNT(DISTINCT f.employe_id)" +
                    " FROM fait_rh f" +
                    " JOIN dim_departement d ON f.dept_id = d.dept_id" +
                    " WHERE f.annee_embauche <= " + an +
                    " AND (f.annee_depart IS NULL OR f.annee_depart >= " + an + ")" +
                    filtreDept(dept);
        }
        ResultSet rs = DatabaseManager.getConnection().createStatement().executeQuery(sql);
        return rs.next() ? rs.getInt(1) : 0;
    }

    /** Salaire mensuel moyen global */
    public static double getSalaireMoyenGlobal(String annee, String dept) throws SQLException {
        String sql = """
            SELECT ROUND(AVG(f.salaire_mensuel), 2)
            FROM fait_rh f
            JOIN dim_departement d ON f.dept_id = d.dept_id
            JOIN dim_temps t ON f.temps_id = t.temps_id
            WHERE f.salaire_mensuel IS NOT NULL AND f.salaire_mensuel > 0
            """ + filtreActifAnnee(annee) + filtreDept(dept);
        ResultSet rs = DatabaseManager.getConnection().createStatement().executeQuery(sql);
        return rs.next() ? rs.getDouble(1) : 0;
    }

    /** Satisfaction moyenne globale */
    public static double getSatisfactionMoyenneGlobale(String annee, String dept) throws SQLException {
        String sql = """
            SELECT ROUND(AVG(f.satisfaction_employe), 2)
            FROM fait_rh f
            JOIN dim_departement d ON f.dept_id = d.dept_id
            JOIN dim_temps t ON f.temps_id = t.temps_id
            WHERE f.satisfaction_employe IS NOT NULL AND f.satisfaction_employe > 0
            """ + filtreActifAnnee(annee) + filtreDept(dept);
        ResultSet rs = DatabaseManager.getConnection().createStatement().executeQuery(sql);
        return rs.next() ? rs.getDouble(1) : 0;
    }

    /** Delta salaire N vs N-1 (%) — dynamique */
    public static double getDeltaSalaireAnnuel(String annee) throws SQLException {
        if (annee == null || annee.equals("Toutes")) return 0;
        int an = Integer.parseInt(annee);
        String sql = """
            SELECT t.annee, ROUND(AVG(f.salaire_mensuel), 2)
            FROM fait_rh f
            JOIN dim_temps t ON f.temps_id = t.temps_id
            WHERE f.salaire_mensuel > 0 AND t.annee IN (?, ?)
            GROUP BY t.annee ORDER BY t.annee
        """;
        PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql);
        ps.setInt(1, an - 1);
        ps.setInt(2, an);
        ResultSet rs = ps.executeQuery();
        double salN1 = 0, salN = 0;
        while (rs.next()) {
            if (rs.getInt(1) == an - 1) salN1 = rs.getDouble(2);
            if (rs.getInt(1) == an)     salN  = rs.getDouble(2);
        }
        if (salN1 == 0) return 0;
        return Math.round((salN - salN1) / salN1 * 1000.0) / 10.0;
    }

    // ═══════════════════════════════════════════════════════════════════
    // HELPERS FILTRES
    // ═══════════════════════════════════════════════════════════════════

    private static String filtreAnnee(String annee) {
        return (annee == null || annee.equals("Toutes")) ? ""
                : " AND t.annee = " + annee.replaceAll("[^0-9]", "");
    }

    /** Filtre sur l'année réelle de départ — pour attrition, motifs, durée avant départ */
    private static String filtreAnneeDepart(String annee) {
        return (annee == null || annee.equals("Toutes")) ? ""
                : " AND f.annee_depart = " + annee.replaceAll("[^0-9]", "");
    }

    private static String filtreDept(String dept) {
        return (dept == null || dept.equals("Tous")) ? ""
                : " AND d.nom_dept = '" + dept.replace("'", "''") + "'";
    }

    /** Filtre employés actifs pendant l'année X : embauchés avant X et pas encore partis */
    private static String filtreActifAnnee(String annee) {
        if (annee == null || annee.equals("Toutes")) return "";
        String an = annee.replaceAll("[^0-9]", "");
        return " AND f.annee_embauche <= " + an +
                " AND (f.annee_depart IS NULL OR f.annee_depart >= " + an + ")";
    }

    // ═══════════════════════════════════════════════════════════════════
    // UTILITAIRES PRIVÉS
    // ═══════════════════════════════════════════════════════════════════

    public static boolean isDatabasePopulated() throws SQLException {
        ResultSet rs = DatabaseManager.getConnection().createStatement()
                .executeQuery("SELECT COUNT(*) FROM fait_rh");
        return rs.next() && rs.getInt(1) > 0;
    }

    private static Map<String, Integer> queryStringInt(String sql) throws SQLException {
        Map<String, Integer> result = new LinkedHashMap<>();
        ResultSet rs = DatabaseManager.getConnection().createStatement().executeQuery(sql);
        while (rs.next()) result.put(rs.getString(1), rs.getInt(2));
        return result;
    }

    private static Map<String, Double> queryStringDouble(String sql, String keyCol, String valCol) throws SQLException {
        Map<String, Double> result = new LinkedHashMap<>();
        ResultSet rs = DatabaseManager.getConnection().createStatement().executeQuery(sql);
        while (rs.next()) result.put(rs.getString(keyCol), rs.getDouble(valCol));
        return result;
    }

    private static void setNullableInt(PreparedStatement ps, int idx, int val) throws SQLException {
        if (val == -1) ps.setNull(idx, Types.INTEGER);
        else ps.setInt(idx, val);
    }

    private static void setNullableDouble(PreparedStatement ps, int idx, double val) throws SQLException {
        if (val == -1) ps.setNull(idx, Types.DECIMAL);
        else ps.setDouble(idx, val);
    }
}
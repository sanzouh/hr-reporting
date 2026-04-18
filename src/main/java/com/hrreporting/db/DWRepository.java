package com.hrreporting.db;

import com.hrreporting.model.FaitRH;

import java.sql.*;
import java.util.*;

/**
 * DWRepository — Couche d'accès aux données du Data Warehouse H2.
 * Responsabilités
 * — Insertion des dimensions (upsert via MERGE)
 * — Insertion des faits (fait_rh)
 * — Requêtes d'agrégation pour les KPI du dashboard.
 * Toutes les méthodes travaillent avec la connexion fournie par DatabaseManager.
 */
public class DWRepository {

    // ═══════════════════════════════════════════════════════════════════
    // INSERTIONS — DIMENSIONS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Insère un département s'il n'existe pas déjà.
     * @return dept_id généré ou existant
     */
    public static int upsertDepartement(String nomDept, String localisation, String responsable) throws SQLException {
        Connection conn = DatabaseManager.getConnection();

        PreparedStatement check = conn.prepareStatement(
                "SELECT dept_id FROM dim_departement WHERE nom_dept = ?"
        );
        check.setString(1, nomDept);
        ResultSet rs = check.executeQuery();
        if (rs.next()) return rs.getInt("dept_id");

        PreparedStatement insert = conn.prepareStatement(
                "INSERT INTO dim_departement (nom_dept, localisation, responsable) VALUES (?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS
        );
        insert.setString(1, nomDept);
        insert.setString(2, localisation);
        insert.setString(3, responsable);
        insert.executeUpdate();
        ResultSet keys = insert.getGeneratedKeys();
        keys.next();
        return keys.getInt(1);
    }

    /**
     * Insère une période temps s'elle n'existe pas déjà.
     * @return temps_id généré ou existant
     */
    public static int upsertTemps(int annee, int semestre, int trimestre, int mois) throws SQLException {
        Connection conn = DatabaseManager.getConnection();

        PreparedStatement check = conn.prepareStatement(
                "SELECT temps_id FROM dim_temps WHERE annee = ? AND semestre = ? AND mois = ?"
        );
        check.setInt(1, annee);
        check.setInt(2, semestre);
        check.setInt(3, mois);
        ResultSet rs = check.executeQuery();
        if (rs.next()) return rs.getInt("temps_id");

        PreparedStatement insert = conn.prepareStatement(
                "INSERT INTO dim_temps (annee, semestre, trimestre, mois) VALUES (?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS
        );
        insert.setInt(1, annee);
        insert.setInt(2, semestre);
        insert.setInt(3, trimestre);
        insert.setInt(4, mois);
        insert.executeUpdate();
        ResultSet keys = insert.getGeneratedKeys();
        keys.next();
        return keys.getInt(1);
    }

    /**
     * Insère un poste s'il n'existe pas déjà.
     * @return poste_id généré ou existant
     */
    public static int upsertPoste(String titre, String niveau, String sourceRecrutement) throws SQLException {
        Connection conn = DatabaseManager.getConnection();

        PreparedStatement check = conn.prepareStatement(
                "SELECT poste_id FROM dim_poste WHERE titre = ?"
        );
        check.setString(1, titre);
        ResultSet rs = check.executeQuery();
        if (rs.next()) return rs.getInt("poste_id");

        PreparedStatement insert = conn.prepareStatement(
                "INSERT INTO dim_poste (titre, niveau, source_recrutement) VALUES (?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS
        );
        insert.setString(1, titre);
        insert.setString(2, niveau);
        insert.setString(3, sourceRecrutement);
        insert.executeUpdate();
        ResultSet keys = insert.getGeneratedKeys();
        keys.next();
        return keys.getInt(1);
    }

    /**
     * Insère une formation si elle n'existe pas déjà.
     * @return formation_id généré ou existant
     */
    public static int upsertFormation(String intitule, int dureeJours, double coutUsd) throws SQLException {
        Connection conn = DatabaseManager.getConnection();

        PreparedStatement check = conn.prepareStatement(
                "SELECT formation_id FROM dim_formation WHERE intitule = ?"
        );
        check.setString(1, intitule);
        ResultSet rs = check.executeQuery();
        if (rs.next()) return rs.getInt("formation_id");

        PreparedStatement insert = conn.prepareStatement(
                "INSERT INTO dim_formation (intitule, duree_jours, cout_usd) VALUES (?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS
        );
        insert.setString(1, intitule);
        insert.setInt(2, dureeJours);
        insert.setDouble(3, coutUsd);
        insert.executeUpdate();
        ResultSet keys = insert.getGeneratedKeys();
        keys.next();
        return keys.getInt(1);
    }

    /**
     * Insère un employé s'il n'existe pas déjà.
     */
    public static void upsertEmploye(String employeId, String nom, int age, String genre,
                                     int anciennete, String statut, String motifDepart) throws SQLException {
        Connection conn = DatabaseManager.getConnection();

        PreparedStatement check = conn.prepareStatement(
                "SELECT employe_id FROM dim_employe WHERE employe_id = ?"
        );
        check.setString(1, employeId);
        ResultSet rs = check.executeQuery();
        if (rs.next()) return;

        PreparedStatement insert = conn.prepareStatement(
                "INSERT INTO dim_employe (employe_id, nom, age, genre, anciennete_ans, statut, motif_depart) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?)"
        );
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

    /**
     * Insère une ligne dans la table de faits fait_rh.
     */
    public static void insertFait(FaitRH fait) throws SQLException {
        Connection conn = DatabaseManager.getConnection();

        PreparedStatement ps = conn.prepareStatement("""
            INSERT INTO fait_rh (
                employe_id, dept_id, temps_id, poste_id, formation_id,
                salaire_mensuel, attrition, score_performance, satisfaction_employe,
                nb_absences, heures_sup, score_evaluation, objectifs_atteints_pct,
                cout_formation, nb_formations, duree_avant_depart
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """);

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

        ps.executeUpdate();
    }

    /**
     * Insertion en batch pour de meilleures performances.
     */
    public static void insertFaitsBatch(List<FaitRH> faits) throws SQLException {
        Connection conn = DatabaseManager.getConnection();
        conn.setAutoCommit(false);

        PreparedStatement ps = conn.prepareStatement("""
            INSERT INTO fait_rh (
                employe_id, dept_id, temps_id, poste_id, formation_id,
                salaire_mensuel, attrition, score_performance, satisfaction_employe,
                nb_absences, heures_sup, score_evaluation, objectifs_atteints_pct,
                cout_formation, nb_formations, duree_avant_depart
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """);

        for (FaitRH fait : faits) {
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
            ps.addBatch();
        }

        ps.executeBatch();
        conn.commit();
        conn.setAutoCommit(true);
        System.out.println("[DB] " + faits.size() + " faits insérés en batch.");
    }

    // ═══════════════════════════════════════════════════════════════════
    // REQUÊTES KPI — DASHBOARD
    // ═══════════════════════════════════════════════════════════════════

    /** KPI Effectifs : nombre d'employés par département */
    public static Map<String, Integer> getEffectifParDept() throws SQLException {
        String sql = """
            SELECT d.nom_dept, COUNT(DISTINCT f.employe_id) AS nb
            FROM fait_rh f
            JOIN dim_departement d ON f.dept_id = d.dept_id
            GROUP BY d.nom_dept
            ORDER BY nb DESC
        """;
        return queryStringInt(sql);
    }

    /** KPI Turnover : taux d'attrition (%) par département */
    public static Map<String, Double> getTauxAttritionParDept() throws SQLException {
        String sql = """
            SELECT d.nom_dept,
                   ROUND(SUM(f.attrition) * 100.0 / COUNT(*), 1) AS taux
            FROM fait_rh f
            JOIN dim_departement d ON f.dept_id = d.dept_id
            GROUP BY d.nom_dept
            ORDER BY taux DESC
        """;
        return queryStringDouble(sql, "nom_dept", "taux");
    }

    /** KPI Salaire : salaire mensuel moyen par département */
    public static Map<String, Double> getSalaireMoyenParDept() throws SQLException {
        String sql = """
            SELECT d.nom_dept,
                   ROUND(AVG(f.salaire_mensuel), 2) AS salaire_moyen
            FROM fait_rh f
            JOIN dim_departement d ON f.dept_id = d.dept_id
            WHERE f.salaire_mensuel IS NOT NULL AND f.salaire_mensuel > 0
            GROUP BY d.nom_dept
            ORDER BY salaire_moyen DESC
        """;
        return queryStringDouble(sql, "nom_dept", "salaire_moyen");
    }

    /** KPI Salaire : salaire moyen par genre (écart H/F) */
    public static Map<String, Double> getSalaireMoyenParGenre() throws SQLException {
        String sql = """
            SELECT e.genre,
                   ROUND(AVG(f.salaire_mensuel), 2) AS salaire_moyen
            FROM fait_rh f
            JOIN dim_employe e ON f.employe_id = e.employe_id
            WHERE f.salaire_mensuel IS NOT NULL AND f.salaire_mensuel > 0
              AND e.genre IS NOT NULL
            GROUP BY e.genre
        """;
        return queryStringDouble(sql, "genre", "salaire_moyen");
    }

    /** KPI Performance : score moyen par département */
    public static Map<String, Double> getScorePerfMoyenParDept() throws SQLException {
        String sql = """
            SELECT d.nom_dept,
                   ROUND(AVG(f.score_performance), 2) AS score_moyen
            FROM fait_rh f
            JOIN dim_departement d ON f.dept_id = d.dept_id
            WHERE f.score_performance IS NOT NULL AND f.score_performance > 0
            GROUP BY d.nom_dept
            ORDER BY score_moyen DESC
        """;
        return queryStringDouble(sql, "nom_dept", "score_moyen");
    }

    /** KPI Satisfaction : satisfaction moyenne par département */
    public static Map<String, Double> getSatisfactionParDept() throws SQLException {
        String sql = """
            SELECT d.nom_dept,
                   ROUND(AVG(f.satisfaction_employe), 2) AS satisfaction
            FROM fait_rh f
            JOIN dim_departement d ON f.dept_id = d.dept_id
            WHERE f.satisfaction_employe IS NOT NULL AND f.satisfaction_employe > 0
            GROUP BY d.nom_dept
            ORDER BY satisfaction DESC
        """;
        return queryStringDouble(sql, "nom_dept", "satisfaction");
    }

    /** KPI Absentéisme : nombre moyen de jours d'absence par département */
    public static Map<String, Double> getAbsenteismeParDept() throws SQLException {
        String sql = """
            SELECT d.nom_dept,
                   ROUND(AVG(f.nb_absences), 1) AS moy_absences
            FROM fait_rh f
            JOIN dim_departement d ON f.dept_id = d.dept_id
            WHERE f.nb_absences IS NOT NULL AND f.nb_absences >= 0
            GROUP BY d.nom_dept
            ORDER BY moy_absences DESC
        """;
        return queryStringDouble(sql, "nom_dept", "moy_absences");
    }

    /** KPI Formation : coût total des formations par département */
    public static Map<String, Double> getCoutFormationParDept() throws SQLException {
        String sql = """
            SELECT d.nom_dept,
                   ROUND(SUM(f.cout_formation), 2) AS cout_total
            FROM fait_rh f
            JOIN dim_departement d ON f.dept_id = d.dept_id
            WHERE f.cout_formation IS NOT NULL AND f.cout_formation > 0
            GROUP BY d.nom_dept
            ORDER BY cout_total DESC
        """;
        return queryStringDouble(sql, "nom_dept", "cout_total");
    }

    /** KPI Répartition genre : nombre d'employés par genre */
    public static Map<String, Integer> getRepartitionGenre() throws SQLException {
        String sql = """
            SELECT e.genre, COUNT(DISTINCT f.employe_id) AS nb
            FROM fait_rh f
            JOIN dim_employe e ON f.employe_id = e.employe_id
            WHERE e.genre IS NOT NULL
            GROUP BY e.genre
        """;
        return queryStringInt(sql);
    }

    /** KPI Motifs de départ */
    public static Map<String, Integer> getMotifsDepart() throws SQLException {
        String sql = """
            SELECT e.motif_depart, COUNT(*) AS nb
            FROM fait_rh f
            JOIN dim_employe e ON f.employe_id = e.employe_id
            WHERE f.attrition = 1 AND e.motif_depart IS NOT NULL
            GROUP BY e.motif_depart
            ORDER BY nb DESC
        """;
        return queryStringInt(sql);
    }

    // ═══════════════════════════════════════════════════════════════════
    // UTILITAIRES PRIVÉS
    // ═══════════════════════════════════════════════════════════════════

    private static Map<String, Integer> queryStringInt(String sql) throws SQLException {
        Map<String, Integer> result = new LinkedHashMap<>();
        ResultSet rs = DatabaseManager.getConnection().createStatement().executeQuery(sql);
        ResultSetMetaData meta = rs.getMetaData();
        while (rs.next()) {
            result.put(rs.getString(1), rs.getInt(2));
        }
        return result;
    }

    private static Map<String, Double> queryStringDouble(String sql, String keyCol, String valCol) throws SQLException {
        Map<String, Double> result = new LinkedHashMap<>();
        ResultSet rs = DatabaseManager.getConnection().createStatement().executeQuery(sql);
        while (rs.next()) {
            result.put(rs.getString(keyCol), rs.getDouble(valCol));
        }
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
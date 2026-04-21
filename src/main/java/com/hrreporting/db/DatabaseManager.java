package com.hrreporting.db;

import java.sql.*;

// DatabaseManager — Connexion H2 + création du schéma étoile (5 dims + fait_rh)

public class DatabaseManager {

    private static final String DB_URL = "jdbc:h2:./database/hr_reporting_dw;AUTO_SERVER=TRUE";
    private static final String USER   = "san";
    private static final String PASS   = "";

    private static Connection connection;

    public static Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection(DB_URL, USER, PASS);
        }
        return connection;
    }

    public static boolean isDatabasePopulated() throws SQLException {
        ResultSet rs = getConnection().createStatement()
                .executeQuery("SELECT COUNT(*) FROM fait_rh");
        return rs.next() && rs.getInt(1) > 0;
    }

    public static void initialize() throws SQLException {
        Connection conn = getConnection();
        Statement stmt  = conn.createStatement();

        // ── Dimensions ────────────────────────────────────────────────

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS dim_departement (
                dept_id     INT AUTO_INCREMENT PRIMARY KEY,
                nom_dept    VARCHAR(100) NOT NULL UNIQUE,
                localisation VARCHAR(100),
                responsable  VARCHAR(100)
            )
        """);

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS dim_temps (
                temps_id    INT AUTO_INCREMENT PRIMARY KEY,
                annee       INT NOT NULL,
                semestre    INT,
                trimestre   INT,
                mois        INT,
                UNIQUE (annee, semestre, mois)
            )
        """);

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS dim_poste (
                poste_id            INT AUTO_INCREMENT PRIMARY KEY,
                titre               VARCHAR(150) NOT NULL,
                niveau              VARCHAR(50),
                source_recrutement  VARCHAR(100)
            )
        """);

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS dim_formation (
                formation_id    INT AUTO_INCREMENT PRIMARY KEY,
                intitule        VARCHAR(200) NOT NULL,
                duree_jours     INT,
                cout_usd        DECIMAL(10,2)
            )
        """);

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS dim_employe (
                employe_id      VARCHAR(20)  PRIMARY KEY,
                nom             VARCHAR(150),
                age             INT,
                genre           VARCHAR(10),
                anciennete_ans  INT,
                statut          VARCHAR(20),
                motif_depart    VARCHAR(150)
            )
        """);

        // ── Table de faits ────────────────────────────────────────────

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS fait_rh (
                fait_id                 INT AUTO_INCREMENT PRIMARY KEY,
                employe_id              VARCHAR(20),
                dept_id                 INT,
                temps_id                INT,
                poste_id                INT,
                formation_id            INT,

                salaire_mensuel         DECIMAL(10,2),
                attrition               TINYINT,
                score_performance       INT,
                satisfaction_employe    INT,
                nb_absences             INT,
                heures_sup              TINYINT,
                score_evaluation        INT,
                objectifs_atteints_pct  INT,
                cout_formation          DECIMAL(10,2),
                nb_formations           INT,
                duree_avant_depart      INT,
                promotion_recommandee   TINYINT,
                annee_depart            INT,
                annee_embauche          INT,

                FOREIGN KEY (employe_id) REFERENCES dim_employe(employe_id),
                FOREIGN KEY (dept_id)    REFERENCES dim_departement(dept_id),
                FOREIGN KEY (temps_id)   REFERENCES dim_temps(temps_id),
                FOREIGN KEY (poste_id)   REFERENCES dim_poste(poste_id),
                FOREIGN KEY (formation_id) REFERENCES dim_formation(formation_id)
            )
        """);

        stmt.close();
        System.out.println("[DB] Schéma H2 initialisé.");
    }

    public static void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                System.out.println("[DB] Connexion H2 fermée.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
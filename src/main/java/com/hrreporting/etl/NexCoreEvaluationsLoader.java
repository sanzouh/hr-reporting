package com.hrreporting.etl;

import com.hrreporting.db.DWRepository;
import com.hrreporting.model.FaitRH;

import java.io.File;
import java.sql.*;
import java.util.*;

/**
 * NexCoreEvaluationsLoader — Loader ETL pour Evaluations_Semestral.db (SQLite).
 *
 * Source     : Evaluations_Semestral.db (SQLite, ~8 000 lignes)
 * Table      : evaluations
 * Colonnes   : id, emp_id, Department, Semester, EvaluationScore,
 *              ObjectivesAchieved_Pct, PromotionRecommended, Site
 *
 * Spécificités :
 *   - emp_id = matricule numérique pur
 *   - Department = codes courts (RD, SALES, IT, OPS, HR, ADMIN, MGMT)
 *   - Semester = "S1-2022" / "S2-2023"
 *   - PromotionRecommended : Oui/Non/Yes/NO/oui/non → normaliserBoolean()
 *   - Nécessite le driver SQLite (org.xerial:sqlite-jdbc ou java.sql avec SQLite)
 *
 * Note : H2 est utilisé pour le DW, SQLite uniquement pour lire la source.
 */
public class NexCoreEvaluationsLoader {

    private static final String FILE_PATH =
            "src/main/resources/data/Evaluations_Semestral.db";

    private static final Map<String, String> DEPT_MAP = Map.of(
            "RD",    "R&D",
            "SALES", "Sales",
            "IT",    "IT",
            "OPS",   "Operations",
            "HR",    "HR",
            "ADMIN", "Admin",
            "MGMT",  "Management"
    );

    public static List<FaitRH> load() throws SQLException {
        List<FaitRH> faits = new ArrayList<>();
        int lignesLues = 0, lignesIgnorees = 0;

        // Chargement du driver SQLite
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            System.err.println("[EVAL] Driver SQLite non trouvé : " + e.getMessage());
            System.err.println("[EVAL] Ajoutez org.xerial:sqlite-jdbc dans pom.xml");
            return faits;
        }

        String dbUrl = "jdbc:sqlite:" + new File(FILE_PATH).getAbsolutePath();

        try (Connection conn = DriverManager.getConnection(dbUrl);
             Statement stmt  = conn.createStatement();
             ResultSet rs    = stmt.executeQuery("SELECT * FROM evaluations")) {

            while (rs.next()) {
                lignesLues++;
                try {
                    String matricule  = String.valueOf(rs.getLong("emp_id"));
                    String deptCode   = rs.getString("Department");
                    String semester   = rs.getString("Semester");
                    int    score      = rs.getInt("EvaluationScore");
                    int    objectives = rs.getInt("ObjectivesAchieved_Pct");
                    String promoRaw   = rs.getString("PromotionRecommended");
                    String site       = rs.getString("Site");

                    if (matricule.isBlank() || semester == null) {
                        lignesIgnorees++;
                        continue;
                    }

                    String dept     = DEPT_MAP.getOrDefault(deptCode.trim().toUpperCase(), deptCode.trim());
                    int    promoted = ETLUtils.normaliserBoolean(promoRaw);
                    int    annee    = parseSemestreAnnee(semester);
                    int    semNum   = parseSemestreNum(semester);
                    int    trimestre= semNum == 1 ? 1 : 3;
                    int    mois     = semNum == 1 ? 3 : 9;

                    DWRepository.upsertEmploye(matricule, "", -1, "N/A", -1, "N/A", "N/A");

                    String deptFinal = dept.isBlank() || dept.equals("N/A") ? "Non défini" : dept;
                    int deptId  = DWRepository.upsertDepartement(deptFinal, site, "N/A");
                    int tempsId = DWRepository.upsertTemps(annee, semNum, trimestre, mois);
                    int posteId = DWRepository.upsertPoste("N/A", "N/A", "N/A");

                    FaitRH fait = FaitRH.builder()
                            .employeId(matricule)
                            .deptId(deptId)
                            .tempsId(tempsId)
                            .posteId(posteId)
                            .scoreEvaluation(score > 0 ? score : -1)
                            .objectifsAtteintsP(objectives >= 0 ? objectives : -1)
                            .promotionRecommandee(promoted)
                            .build();

                    faits.add(fait);

                } catch (Exception e) {
                    lignesIgnorees++;
                    System.err.println("[EVAL] Ligne " + lignesLues + " ignorée : " + e.getMessage());
                }
            }
        }

        System.out.println("[EVAL] Chargement terminé — " + faits.size() +
                " faits construits, " + lignesIgnorees + " ignorées.");
        return faits;
    }

    private static int parseSemestreAnnee(String raw) {
        if (raw == null) return 2022;
        for (String part : raw.split("[-]")) {
            try {
                int v = Integer.parseInt(part.trim());
                if (v > 2000) return v;
            } catch (NumberFormatException ignored) {}
        }
        return 2022;
    }

    private static int parseSemestreNum(String raw) {
        if (raw == null) return 1;
        return raw.toUpperCase().contains("S2") ? 2 : 1;
    }
}
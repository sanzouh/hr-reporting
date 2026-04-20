package com.hrreporting.etl;

import com.hrreporting.db.DWRepository;
import com.hrreporting.model.FaitRH;

import java.io.File;
import java.sql.*;
import java.util.*;

/**
 * NexCoreEvaluationsLoader — Loader ETL pour Evaluations_Semestral.db (SQLite).
 * Retourne une Map<employe_id, FaitRH.Builder> pour consolidation dans ETLPipeline.
 * Agrégation : score_eval moyen, objectifs moyens, promotion = 1 si au moins une fois recommandé.
 */
public class NexCoreEvaluationsLoader {

    private static final String FILE_PATH = "src/main/resources/data/Evaluations_Semestral.db";

    private static final Map<String, String> DEPT_MAP = Map.of(
            "RD",    "R&D",
            "SALES", "Sales",
            "IT",    "IT",
            "OPS",   "Operations",
            "HR",    "HR",
            "ADMIN", "Admin",
            "MGMT",  "Management"
    );

    public static Map<String, FaitRH.Builder> loadAsMap() throws SQLException {
        Map<String, EvalAgg> aggregations = new LinkedHashMap<>();
        int lignesLues = 0, lignesIgnorees = 0;

        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            System.err.println("[EVAL] Driver SQLite non trouvé : " + e.getMessage());
            return new LinkedHashMap<>();
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

                    if (matricule.isBlank() || semester == null) { lignesIgnorees++; continue; }

                    String dept    = DEPT_MAP.getOrDefault(deptCode.trim().toUpperCase(), deptCode.trim());
                    int    promoted = ETLUtils.normaliserBoolean(promoRaw);
                    int    annee   = parseSemestreAnnee(semester);
                    int    semNum  = parseSemestreNum(semester);

                    aggregations.merge(matricule,
                            new EvalAgg(matricule, dept, score, objectives, promoted, annee, semNum, site),
                            (ex, nw) -> {
                                ex.scoreTotal      += nw.scoreTotal;
                                ex.objectifsTotal  += nw.objectifsTotal;
                                ex.count           += 1;
                                if (nw.promoted == 1) ex.promoted = 1; // 1 prend le dessus
                                return ex;
                            });

                } catch (Exception e) {
                    lignesIgnorees++;
                }
            }
        }

        Map<String, FaitRH.Builder> result = new LinkedHashMap<>();
        for (EvalAgg agg : aggregations.values()) {
            try {
                String deptFinal = agg.dept.isBlank() || agg.dept.equals("N/A") ? "Non défini" : agg.dept;
                int deptId  = DWRepository.upsertDepartement(deptFinal, agg.site, "N/A");
                int trimestre = agg.semNum == 1 ? 1 : 3;
                int mois      = agg.semNum == 1 ? 3 : 9;
                int tempsId = DWRepository.upsertTemps(agg.annee, agg.semNum, trimestre, mois);
                int posteId = DWRepository.upsertPoste("N/A", "N/A", "N/A");

                // Moyennes sur toutes les évaluations de l'employé
                int scoreEvalMoyen   = agg.count > 0 ? agg.scoreTotal / agg.count : -1;
                int objectifsMoyen   = agg.count > 0 ? agg.objectifsTotal / agg.count : -1;

                FaitRH.Builder builder = FaitRH.builder()
                        .employeId(agg.matricule)
                        .deptId(deptId)
                        .tempsId(tempsId)
                        .posteId(posteId)
                        .scoreEvaluation(scoreEvalMoyen)
                        .objectifsAtteintsP(objectifsMoyen)
                        .promotionRecommandee(agg.promoted);

                result.put(agg.matricule, builder);
            } catch (Exception e) {
                System.err.println("[EVAL] Erreur fait " + agg.matricule + " : " + e.getMessage());
            }
        }

        System.out.println("[EVAL] " + result.size() + " builders construits, " + lignesIgnorees + " ignorées sur " + lignesLues + " lues.");
        return result;
    }

    /** Compatibilité ascendante */
    public static List<FaitRH> load() throws SQLException {
        List<FaitRH> faits = new ArrayList<>();
        loadAsMap().values().forEach(b -> faits.add(b.build()));
        return faits;
    }

    private static int parseSemestreAnnee(String raw) {
        if (raw == null) return 2022;
        for (String part : raw.split("[-]")) {
            try { int v = Integer.parseInt(part.trim()); if (v > 2000) return v; }
            catch (NumberFormatException ignored) {}
        }
        return 2022;
    }

    private static int parseSemestreNum(String raw) {
        if (raw == null) return 1;
        return raw.toUpperCase().contains("S2") ? 2 : 1;
    }

    private static class EvalAgg {
        String matricule, dept, site;
        int    scoreTotal, objectifsTotal, count, promoted, annee, semNum;

        EvalAgg(String m, String d, int s, int o, int p, int a, int sn, String si) {
            this.matricule      = m; this.dept = d; this.site = si;
            this.scoreTotal     = s > 0 ? s : 0;
            this.objectifsTotal = o >= 0 ? o : 0;
            this.count          = 1;
            this.promoted       = p;
            this.annee          = a; this.semNum = sn;
        }
    }
}
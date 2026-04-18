package com.hrreporting.etl;

import com.hrreporting.db.DWRepository;
import com.hrreporting.model.FaitRH;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.*;

/**
 * IbmHrLoader — Loader ETL pour le dataset IBM HR Attrition.
 * Source : WA_Fn-UseC_-HR-Employee-Attrition.csv (1 470 lignes)
 * Opérations
 * – Extraction via OpenCSV
 * – Nettoyage : valeurs nulles, normalisation genre/département
 * – Transformation : salaire mensuel direct, attrition Yes/No → 0/1
 * – Chargement : alimentation des dimensions + construction des FaitRH.
 * Colonnes utilisées :
 *   EmployeeNumber, Age, Gender, Department, JobRole, MonthlyIncome,
 *   Attrition, JobSatisfaction, PerformanceRating, YearsAtCompany,
 *   OverTime, YearsSinceLastPromotion
 */
public class IbmHrLoader {

    private static final String FILE_PATH = "src/main/resources/data/WA_Fn-UseC_-HR-Employee-Attrition.csv";

    // Mapping normalisation département IBM → nom unifié
    private static final Map<String, String> DEPT_MAP = Map.of(
            "Research & Development", "R&D",
            "Sales",                  "Sales",
            "Human Resources",        "HR"
    );

    /**
     * Point d'entrée principal du loader.
     * Lit le CSV, nettoie, insère les dimensions et retourne les FaitRH.
     *
     * @return Liste de FaitRH prête pour insertFaitsBatch()
     */
    public static List<FaitRH> load() throws IOException, CsvValidationException, SQLException {
        List<FaitRH> faits = new ArrayList<>();
        int lignesLues = 0, lignesIgnorees = 0;

        try (CSVReader reader = new CSVReader(
                new InputStreamReader(
                        new FileInputStream(FILE_PATH), StandardCharsets.UTF_8))) {

            String[] headers = reader.readNext(); // ligne d'en-tête
            Map<String, Integer> idx = buildIndex(headers);

            String[] row;
            while ((row = reader.readNext()) != null) {
                lignesLues++;

                try {
                    // ── EXTRACTION ────────────────────────────────────
                    String employeId      = "EMP-" + get(row, idx, "EmployeeNumber").trim().replace("\uFEFF", "");
                    String deptRaw        = get(row, idx, "Department");
                    String jobRole        = get(row, idx, "JobRole");
                    String genre          = normaliserGenre(get(row, idx, "Gender"));
                    String attritionRaw   = get(row, idx, "Attrition");
                    String overtimeRaw    = get(row, idx, "OverTime");

                    // ── TRANSFORMATION ────────────────────────────────
                    String dept           = DEPT_MAP.getOrDefault(deptRaw.trim(), deptRaw.trim());
                    int    age            = parseIntSafe(get(row, idx, "Age"));
                    int    anciennete     = parseIntSafe(get(row, idx, "YearsAtCompany"));
                    double salaire        = parseDoubleSafe(get(row, idx, "MonthlyIncome"));
                    int    attrition      = "Yes".equalsIgnoreCase(attritionRaw.trim()) ? 1 : 0;
                    int    satisfaction   = parseIntSafe(get(row, idx, "JobSatisfaction"));
                    int    performance    = parseIntSafe(get(row, idx, "PerformanceRating"));
                    int    heuresSup      = "Yes".equalsIgnoreCase(overtimeRaw.trim()) ? 1 : 0;
                    String statut         = attrition == 1 ? "Parti" : "Actif";

                    // Durée avant départ : YearsAtCompany si parti, sinon -1
                    int dureeAvantDepart  = attrition == 1 ? anciennete * 365 : -1;

                    // ── VALIDATION ────────────────────────────────────
                    if (employeId.isBlank() || dept.isBlank()) {
                        lignesIgnorees++;
                        continue;
                    }

                    // ── CHARGEMENT DIMENSIONS ─────────────────────────
                    DWRepository.upsertEmploye(employeId, "", age, genre, anciennete, statut, "N/A");

                    int deptId    = DWRepository.upsertDepartement(dept, "N/A", "N/A");
                    int tempsId   = DWRepository.upsertTemps(2023, 1, 1, 1); // année de référence IBM
                    int posteId   = DWRepository.upsertPoste(jobRole, inferNiveau(jobRole), "N/A");

                    // ── CONSTRUCTION DU FAIT ──────────────────────────
                    FaitRH fait = FaitRH.builder()
                            .employeId(employeId)
                            .deptId(deptId)
                            .tempsId(tempsId)
                            .posteId(posteId)
                            .salaireMensuel(salaire)
                            .attrition(attrition)
                            .scorePerformance(performance)
                            .satisfactionEmploye(satisfaction)
                            .heuresSup(heuresSup)
                            .dureeAvantDepart(dureeAvantDepart)
                            .build();

                    faits.add(fait);

                } catch (Exception e) {
                    lignesIgnorees++;
                    System.err.println("[IBM] Ligne " + lignesLues + " ignorée : " + e.getMessage());
                }
            }
        }

        System.out.println("[IBM] Chargement terminé — " + faits.size() +
                " faits construits, " + lignesIgnorees + " lignes ignorées.");
        return faits;
    }

    // ═══════════════════════════════════════════════════════════════════
    // UTILITAIRES PRIVÉS
    // ═══════════════════════════════════════════════════════════════════

    /** Construit un index nom_colonne → position pour accès par nom */
    private static Map<String, Integer> buildIndex(String[] headers) {
        Map<String, Integer> idx = new HashMap<>();
        for (int i = 0; i < headers.length; i++) {
            idx.put(headers[i].trim().replace("\uFEFF", ""), i);
        }
        return idx;
    }

    /** Accès sécurisé à une cellule par nom de colonne */
    private static String get(String[] row, Map<String, Integer> idx, String col) {
        Integer i = idx.get(col);
        if (i == null || i >= row.length) return "";
        return row[i] == null ? "" : row[i].trim();
    }

    /** Normalise le genre vers M/F */
    private static String normaliserGenre(String raw) {
        if (raw == null) return "N/A";
        return switch (raw.trim().toLowerCase()) {
            case "male", "m"   -> "M";
            case "female", "f" -> "F";
            default            -> "N/A";
        };
    }

    /** Infère un niveau de poste à partir du titre */
    private static String inferNiveau(String jobRole) {
        if (jobRole == null) return "N/A";
        String r = jobRole.toLowerCase();
        if (r.contains("manager") || r.contains("director") || r.contains("executive")) return "Senior";
        if (r.contains("senior"))  return "Senior";
        if (r.contains("junior"))  return "Junior";
        return "Mid";
    }

    private static int parseIntSafe(String val) {
        try { return Integer.parseInt(val.trim()); }
        catch (Exception e) { return -1; }
    }

    private static double parseDoubleSafe(String val) {
        try { return Double.parseDouble(val.trim().replace(",", ".")); }
        catch (Exception e) { return -1; }
    }
}
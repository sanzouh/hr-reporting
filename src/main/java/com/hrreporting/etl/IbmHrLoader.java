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
            Map<String, Integer> idx = ETLUtils.buildIndex(headers);

            String[] row;
            while ((row = reader.readNext()) != null) {
                lignesLues++;

                try {
                    // ── EXTRACTION ────────────────────────────────────
                    String employeId      = "EMP-" + ETLUtils.clean(ETLUtils.get(row, idx, "EmployeeNumber"));
                    String deptRaw        = ETLUtils.get(row, idx, "Department");
                    String jobRole        = ETLUtils.get(row, idx, "JobRole");
                    String genre          = ETLUtils.normaliserGenre(ETLUtils.get(row, idx, "Gender"));
                    String attritionRaw   = ETLUtils.get(row, idx, "Attrition");
                    String overtimeRaw    = ETLUtils.get(row, idx, "OverTime");

                    // ── TRANSFORMATION ────────────────────────────────
                    String dept           = ETLUtils.normaliserDepartement(deptRaw);
                    int    age            = ETLUtils.parseInt(ETLUtils.get(row, idx, "Age"));
                    int    anciennete     = ETLUtils.parseInt(ETLUtils.get(row, idx, "YearsAtCompany"));
                    double salaire        = ETLUtils.parseMontant(ETLUtils.get(row, idx, "MonthlyIncome"));
                    int    attrition      = "Yes".equalsIgnoreCase(attritionRaw.trim()) ? 1 : 0;
                    int    satisfaction   = ETLUtils.parseInt(ETLUtils.get(row, idx, "JobSatisfaction"));
                    int    performance    =  ETLUtils.parseInt(ETLUtils.get(row, idx, "PerformanceRating"));
                    int    heuresSup      = "Yes".equalsIgnoreCase(overtimeRaw.trim()) ? 1 : 0;
                    String statut         = attrition == 1 ? "Parti" : "Actif";
                    String niveau    = ETLUtils.inferNiveau(jobRole);

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
                    int posteId   = DWRepository.upsertPoste(jobRole, niveau, "N/A");

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
}
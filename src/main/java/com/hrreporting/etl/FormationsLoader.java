package com.hrreporting.etl;

import com.hrreporting.db.DWRepository;
import com.hrreporting.model.FaitRH;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;

/**
 * FormationsLoader — Loader ETL pour formations.csv (source générée).
 * Source : formations.csv (~2 600 lignes)
 * Spécificités de nettoyage :
 * — Dates multi-formats : DD/MM/YYYY, MM-DD-YYYY, YYYY/MM/DD
 * — Coûts avec symboles : $500, 1 500, 750.00, 350
 * — Départements en casse variable : sales, SALES, RandD
 * — Valeurs nulles sur EmployeeID et Department (~2-5%)
 * — Duree_jours : parfois "N/A"
 * Colonnes utilisées :
 *   EmployeeID, Department, Formation, Duree_jours, Date, Cout_USD
 * Stratégie : une ligne formations = enrichissement d'un FaitRH existant.
 * Les données formations sont agrégées par employé (nb_formations, cout_total)
 * puis injectées dans le fait correspondant via formationId.
 */
public class FormationsLoader {

    private static final String FILE_PATH = "src/main/resources/data/formations.csv";

    /**
     * Charge et agrège les formations par employé.
     * @return Map employeId → FaitRH partiel (formationId, nbFormations, coutFormation)
     */
    public static List<FaitRH> load() throws IOException, CsvValidationException, SQLException {
        List<FaitRH> faits = new ArrayList<>();

        // Agrégation par employé : employeId → données agrégées
        Map<String, FormationAgregee> agregations = new LinkedHashMap<>();

        int lignesLues = 0, lignesIgnorees = 0;

        try (CSVReader reader = new CSVReader(
                new InputStreamReader(
                        new FileInputStream(FILE_PATH), StandardCharsets.UTF_8))) {

            String[] headers = reader.readNext();
            Map<String, Integer> idx = ETLUtils.buildIndex(headers);

            String[] row;
            while ((row = reader.readNext()) != null) {
                lignesLues++;

                try {
                    // ── EXTRACTION ────────────────────────────────────
                    String employeId   = ETLUtils.clean(ETLUtils.get(row, idx, "EmployeeID"));
                    String deptRaw     = ETLUtils.get(row, idx, "Department");
                    String formation   = ETLUtils.clean(ETLUtils.get(row, idx, "Formation"));
                    String dureeRaw    = ETLUtils.get(row, idx, "Duree_jours");
                    String dateRaw     = ETLUtils.get(row, idx, "Date");
                    String coutRaw     = ETLUtils.get(row, idx, "Cout_USD");

                    // ── VALIDATION ────────────────────────────────────
                    if (employeId.isBlank() || formation.isBlank()) {
                        lignesIgnorees++;
                        continue;
                    }

                    // ── TRANSFORMATION ────────────────────────────────
                    String dept      = ETLUtils.normaliserDepartement(deptRaw);
                    int    duree     = ETLUtils.parseInt(dureeRaw);
                    double cout      = ETLUtils.parseMontant(coutRaw);
                    LocalDate date   = ETLUtils.parseDate(dateRaw);

                    int annee     = date != null ? ETLUtils.annee(date)     : 2022;
                    int semestre  = date != null ? ETLUtils.semestre(date)  : 1;
                    int trimestre = date != null ? ETLUtils.trimestre(date) : 1;
                    int mois      = date != null ? ETLUtils.mois(date)      : 1;

                    // ── CHARGEMENT DIMENSION formation ────────────────
                    int formationId = DWRepository.upsertFormation(
                            formation,
                            duree > 0 ? duree : 1,
                            cout  > 0 ? cout  : 0
                    );

                    // ── AGRÉGATION PAR EMPLOYÉ ────────────────────────
                    agregations.merge(
                            employeId,
                            new FormationAgregee(employeId, dept, formationId, cout > 0 ? cout : 0, 1, annee, semestre, trimestre, mois),
                            (existing, newVal) -> {
                                existing.coutTotal    += newVal.coutTotal;
                                existing.nbFormations += 1;
                                return existing;
                            }
                    );

                } catch (Exception e) {
                    lignesIgnorees++;
                    System.err.println("[FORM] Ligne " + lignesLues + " ignorée : " + e.getMessage());
                }
            }
        }

        // ── CONSTRUCTION DES FAITS ────────────────────────────────────
        for (FormationAgregee agg : agregations.values()) {
            try {
                int deptId  = DWRepository.upsertDepartement(agg.dept, "N/A", "N/A");
                int tempsId = DWRepository.upsertTemps(agg.annee, agg.semestre, agg.trimestre, agg.mois);
                int posteId = DWRepository.upsertPoste("N/A", "N/A", "N/A");

                FaitRH fait = FaitRH.builder()
                        .employeId(agg.employeId)
                        .deptId(deptId)
                        .tempsId(tempsId)
                        .posteId(posteId)
                        .formationId(agg.formationId)
                        .coutFormation(agg.coutTotal)
                        .nbFormations(agg.nbFormations)
                        .build();

                faits.add(fait);

            } catch (Exception e) {
                System.err.println("[FORM] Erreur construction fait pour " + agg.employeId + " : " + e.getMessage());
            }
        }

        System.out.println("[FORM] Chargement terminé — " + faits.size() +
                " faits construits, " + lignesIgnorees + " lignes ignorées.");
        return faits;
    }

    // ═══════════════════════════════════════════════════════════════════
    // CLASSE INTERNE — AGRÉGATION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Accumulateur pour agréger les formations d'un même employé.
     */
    private static class FormationAgregee {
        String employeId;
        String dept;
        int    formationId;
        double coutTotal;
        int    nbFormations;
        int    annee, semestre, trimestre, mois;

        FormationAgregee(String employeId, String dept, int formationId,
                         double coutTotal, int nbFormations,
                         int annee, int semestre, int trimestre, int mois) {
            this.employeId   = employeId;
            this.dept        = dept;
            this.formationId = formationId;
            this.coutTotal   = coutTotal;
            this.nbFormations= nbFormations;
            this.annee       = annee;
            this.semestre    = semestre;
            this.trimestre   = trimestre;
            this.mois        = mois;
        }
    }
}
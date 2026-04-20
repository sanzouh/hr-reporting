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
 * NexCoreFormationsLoader — Loader ETL pour Formations_Learning.csv.
 *
 * Source     : Formations_Learning.csv (~2 000 lignes)
 * Colonnes   : employee_id, employee_department, training_name,
 *              training_duration_days, training_cost_usd,
 *              start_date, completion_date, completion_status, site
 *
 * Spécificités :
 *   - employee_id = matricule numérique pur (pas de préfixe ni underscore)
 *   - Dates en ISO YYYY-MM-DD
 *   - employee_department : codes mixtes (R&D, HR_Dept, IT, Admin, etc.)
 *   - Agrégation par employé : nb_formations, cout_total
 *   - Seules les formations "Completed" comptent dans le coût total
 */
public class NexCoreFormationsLoader {

    private static final String FILE_PATH =
            "src/main/resources/data/Formations_Learning.csv";

    private static final Map<String, String> DEPT_MAP = Map.of(
            "R&D",        "R&D",
            "Sales",      "Sales",
            "IT",         "IT",
            "Operations", "Operations",
            "HR_Dept",    "HR",
            "Admin",      "Admin",
            "Management", "Management"
    );

    public static List<FaitRH> load() throws IOException, CsvValidationException, SQLException {
        Map<String, FormationAgg> aggregations = new LinkedHashMap<>();

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
                    // employee_id peut avoir underscore → normaliser
                    String raw         = ETLUtils.clean(ETLUtils.get(row, idx, "employee_id"));
                    String matricule   = raw.replace("_", "");
                    String deptRaw     = ETLUtils.get(row, idx, "employee_department");
                    String formName    = ETLUtils.clean(ETLUtils.get(row, idx, "training_name"));
                    String durationRaw = ETLUtils.get(row, idx, "training_duration_days");
                    String costRaw     = ETLUtils.get(row, idx, "training_cost_usd");
                    String startRaw    = ETLUtils.get(row, idx, "start_date");
                    String statusRaw   = ETLUtils.get(row, idx, "completion_status");
                    String site        = ETLUtils.get(row, idx, "site");

                    if (matricule.isBlank() || formName.isBlank()) {
                        lignesIgnorees++;
                        continue;
                    }

                    String dept     = DEPT_MAP.getOrDefault(deptRaw.trim(), deptRaw.trim());
                    int    duration = ETLUtils.parseInt(durationRaw);
                    double cost     = ETLUtils.parseMontant(costRaw);
                    LocalDate start = ETLUtils.parseDate(startRaw);
                    boolean completed = "Completed".equalsIgnoreCase(statusRaw.trim());

                    int annee     = start != null ? ETLUtils.annee(start)     : 2022;
                    int semestre  = start != null ? ETLUtils.semestre(start)  : 1;
                    int trimestre = start != null ? ETLUtils.trimestre(start) : 1;
                    int mois      = start != null ? ETLUtils.mois(start)      : 1;

                    int formationId = DWRepository.upsertFormation(
                            formName,
                            duration > 0 ? duration : 1,
                            cost > 0 ? cost : 0
                    );

                    aggregations.merge(matricule,
                            new FormationAgg(matricule, dept, formationId,
                                    completed ? cost : 0, 1, annee, semestre, trimestre, mois, site),
                            (ex, nw) -> {
                                ex.coutTotal    += nw.coutTotal;
                                ex.nbFormations += 1;
                                return ex;
                            });

                } catch (Exception e) {
                    lignesIgnorees++;
                    System.err.println("[FORM] Ligne " + lignesLues + " ignorée : " + e.getMessage());
                }
            }
        }

        List<FaitRH> faits = new ArrayList<>();
        for (FormationAgg agg : aggregations.values()) {
            try {
                DWRepository.upsertEmploye(agg.matricule, "", -1, "N/A", -1, "N/A", "N/A");

                String deptFinal = agg.dept.isBlank() || agg.dept.equals("N/A")
                        ? "Non défini" : agg.dept;

                int deptId  = DWRepository.upsertDepartement(deptFinal, agg.site, "N/A");
                int tempsId = DWRepository.upsertTemps(agg.annee, agg.semestre, agg.trimestre, agg.mois);
                int posteId = DWRepository.upsertPoste("N/A", "N/A", "N/A");

                FaitRH fait = FaitRH.builder()
                        .employeId(agg.matricule)
                        .deptId(deptId)
                        .tempsId(tempsId)
                        .posteId(posteId)
                        .formationId(agg.formationId)
                        .coutFormation(agg.coutTotal)
                        .nbFormations(agg.nbFormations)
                        .build();

                faits.add(fait);
            } catch (Exception e) {
                System.err.println("[FORM] Erreur fait " + agg.matricule + " : " + e.getMessage());
            }
        }

        System.out.println("[FORM] Chargement terminé — " + faits.size() +
                " faits construits, " + lignesIgnorees + " ignorées.");
        return faits;
    }

    private static class FormationAgg {
        String matricule, dept, site;
        int    formationId, nbFormations;
        double coutTotal;
        int    annee, semestre, trimestre, mois;

        FormationAgg(String m, String d, int fid, double c, int n,
                     int a, int s, int t, int mo, String si) {
            this.matricule   = m; this.dept     = d; this.formationId = fid;
            this.coutTotal   = c; this.nbFormations = n;
            this.annee       = a; this.semestre = s; this.trimestre = t;
            this.mois        = mo; this.site     = si;
        }
    }
}
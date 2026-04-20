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
 * NexCoreJobHistoryLoader — Loader ETL pour JobHistory.csv.
 *
 * Source     : JobHistory.csv (~2 000 lignes)
 * Colonnes   : EmployeeID, ChangeDate, FromJobTitle, ToJobTitle,
 *              SalaryBefore, SalaryAfter, ChangeType, DepartmentCode, Site
 *
 * Spécificités :
 *   - EmployeeID = matricule numérique pur
 *   - Dates en DD/MM/YYYY
 *   - SalaryBefore peut être vide (première embauche)
 *   - ChangeType : Promotion, Lateral_Move, Hire, Demotion
 *   - Promotion → promotion_recommandee = 1 dans le fait
 *   - Salaire = SalaryAfter × 12 → mensuel via ETLUtils
 */
public class NexCoreJobHistoryLoader {

    private static final String FILE_PATH =
            "src/main/resources/data/JobHistory.csv";

    private static final Map<String, String> DEPT_MAP = Map.of(
            "RD",    "R&D",
            "SALES", "Sales",
            "IT",    "IT",
            "OPS",   "Operations",
            "HR",    "HR",
            "ADMIN", "Admin",
            "MGMT",  "Management"
    );

    public static List<FaitRH> load() throws IOException, CsvValidationException, SQLException {
        List<FaitRH> faits = new ArrayList<>();
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
                    String matricule    = ETLUtils.clean(ETLUtils.get(row, idx, "EmployeeID"));
                    String changeDateRaw= ETLUtils.get(row, idx, "ChangeDate");
                    String toTitle      = ETLUtils.get(row, idx, "ToJobTitle");
                    String salAfterRaw  = ETLUtils.get(row, idx, "SalaryAfter");
                    String changeType   = ETLUtils.get(row, idx, "ChangeType");
                    String deptCode     = ETLUtils.get(row, idx, "DepartmentCode");
                    String site         = ETLUtils.get(row, idx, "Site");

                    if (matricule.isBlank() || toTitle.isBlank()) {
                        lignesIgnorees++;
                        continue;
                    }

                    String dept         = DEPT_MAP.getOrDefault(deptCode.trim().toUpperCase(), deptCode.trim());
                    LocalDate changeDate= ETLUtils.parseDate(changeDateRaw);
                    double salAnnuel    = ETLUtils.parseMontant(salAfterRaw);
                    double salMensuel   = ETLUtils.annuelVersQuotidien(salAnnuel);

                    int annee     = changeDate != null ? ETLUtils.annee(changeDate)     : 2020;
                    int semestre  = changeDate != null ? ETLUtils.semestre(changeDate)  : 1;
                    int trimestre = changeDate != null ? ETLUtils.trimestre(changeDate) : 1;
                    int mois      = changeDate != null ? ETLUtils.mois(changeDate)      : 1;

                    // Promotion → promotion_recommandee = 1
                    int promoted = "Promotion".equalsIgnoreCase(changeType.trim()) ? 1 : 0;

                    // ── CHARGEMENT DIMENSIONS ─────────────────────────
                    DWRepository.upsertEmploye(matricule, "", -1, "N/A", -1, "N/A", "N/A");

                    int deptId  = DWRepository.upsertDepartement(dept, site, "N/A");
                    int tempsId = DWRepository.upsertTemps(annee, semestre, trimestre, mois);
                    int posteId = DWRepository.upsertPoste(
                            toTitle,
                            ETLUtils.inferNiveau(toTitle),
                            "N/A"
                    );

                    FaitRH fait = FaitRH.builder()
                            .employeId(matricule)
                            .deptId(deptId)
                            .tempsId(tempsId)
                            .posteId(posteId)
                            .salaireMensuel(salMensuel)
                            .promotionRecommandee(promoted)
                            .build();

                    faits.add(fait);

                } catch (Exception e) {
                    lignesIgnorees++;
                    System.err.println("[JH] Ligne " + lignesLues + " ignorée : " + e.getMessage());
                }
            }
        }

        System.out.println("[JH] Chargement terminé — " + faits.size() +
                " faits construits, " + lignesIgnorees + " ignorées.");
        return faits;
    }
}
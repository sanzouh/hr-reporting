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
 * NexCoreRHLoader — Loader ETL pour RH_Paie.csv (NexCore Technologies).
 *
 * Source     : RH_Paie.csv (1 000 lignes, UTF-8 BOM)
 * Colonnes   : EmployeeID, FirstName, LastName, DateOfBirth, Gender,
 *              DepartmentCode, JobTitle, AnnualSalary, HireDate,
 *              TerminationDate, EmploymentStatus, TermReason,
 *              EmployeeSatisfaction, PerformanceRating, Site
 *
 * Spécificités :
 *   - EmployeeID = matricule numérique pur (100001..101000)
 *   - Dates en DD/MM/YYYY
 *   - Salaire annuel → mensuel (÷ 12)
 *   - DepartmentCode : codes courts (RD, SALES, IT, OPS, HR, ADMIN, MGMT)
 *   - Site : Paris (siège) ou Milan (filiale)
 */
public class NexCoreRHLoader {

    private static final String FILE_PATH =
            "src/main/resources/data/RH_Paie.csv";

    // Mapping code court → nom normalisé DW
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
                    // ── EXTRACTION ────────────────────────────────────
                    String matricule   = ETLUtils.clean(ETLUtils.get(row, idx, "EmployeeID"));
                    String firstName   = ETLUtils.clean(ETLUtils.get(row, idx, "FirstName"));
                    String lastName    = ETLUtils.clean(ETLUtils.get(row, idx, "LastName"));
                    String dobRaw      = ETLUtils.get(row, idx, "DateOfBirth");
                    String genderRaw   = ETLUtils.get(row, idx, "Gender");
                    String deptCode    = ETLUtils.get(row, idx, "DepartmentCode");
                    String jobTitle    = ETLUtils.get(row, idx, "JobTitle");
                    String salaryRaw   = ETLUtils.get(row, idx, "AnnualSalary");
                    String hireRaw     = ETLUtils.get(row, idx, "HireDate");
                    String termRaw     = ETLUtils.get(row, idx, "TerminationDate");
                    String statusRaw   = ETLUtils.get(row, idx, "EmploymentStatus");
                    String termReason  = ETLUtils.get(row, idx, "TermReason");
                    String satisfRaw   = ETLUtils.get(row, idx, "EmployeeSatisfaction");
                    String perfRaw     = ETLUtils.get(row, idx, "PerformanceRating");
                    String site        = ETLUtils.get(row, idx, "Site");

                    // ── VALIDATION ────────────────────────────────────
                    if (matricule.isBlank() || deptCode.isBlank()) {
                        lignesIgnorees++;
                        continue;
                    }

                    // ── TRANSFORMATION ────────────────────────────────
                    String employeId    = matricule; // matricule numérique pur
                    String nom          = lastName + " " + firstName;
                    String genre        = ETLUtils.normaliserGenre(genderRaw);
                    String dept         = DEPT_MAP.getOrDefault(deptCode.trim().toUpperCase(), deptCode.trim());

                    LocalDate dob       = ETLUtils.parseDate(dobRaw);
                    LocalDate hireDate  = ETLUtils.parseDate(hireRaw);
                    LocalDate termDate  = ETLUtils.parseDate(termRaw);

                    int age = (dob != null)
                            ? (int) java.time.temporal.ChronoUnit.YEARS.between(dob, LocalDate.now())
                            : -1;

                    int anciennete = (hireDate != null)
                            ? (int) java.time.temporal.ChronoUnit.YEARS.between(hireDate, LocalDate.now())
                            : -1;

                    // Salaire annuel → mensuel
                    double salaireAnnuel  = ETLUtils.parseMontant(salaryRaw);
                    double salaireMensuel = ETLUtils.annuelVersQuotidien(salaireAnnuel);

                    int attrition = (termDate != null) ? 1 : 0;
                    int dureeAvantDepart = (attrition == 1)
                            ? ETLUtils.joursEntre(hireDate, termDate)
                            : -1;

                    String statut = attrition == 1 ? "Parti"
                            : statusRaw.equalsIgnoreCase("LOA") ? "Congé" : "Actif";

                    String motif = (termReason == null || termReason.isBlank())
                            ? "N/A" : ETLUtils.capitaliser(termReason);

                    int satisfaction = ETLUtils.parseInt(satisfRaw);
                    int performance  = ETLUtils.parseInt(perfRaw);

                    // Période de référence : année d'embauche
                    int annee     = hireDate != null ? ETLUtils.annee(hireDate)     : 2020;
                    int semestre  = hireDate != null ? ETLUtils.semestre(hireDate)  : 1;
                    int trimestre = hireDate != null ? ETLUtils.trimestre(hireDate) : 1;
                    int mois      = hireDate != null ? ETLUtils.mois(hireDate)      : 1;

                    // ── CHARGEMENT DIMENSIONS ─────────────────────────
                    DWRepository.upsertEmploye(
                            employeId, nom, age, genre, anciennete, statut, motif);

                    int deptId  = DWRepository.upsertDepartement(dept, site, "N/A");
                    int tempsId = DWRepository.upsertTemps(annee, semestre, trimestre, mois);
                    int posteId = DWRepository.upsertPoste(
                            jobTitle,
                            ETLUtils.inferNiveau(jobTitle),
                            "N/A"
                    );

                    // ── CONSTRUCTION DU FAIT ──────────────────────────
                    FaitRH fait = FaitRH.builder()
                            .employeId(employeId)
                            .deptId(deptId)
                            .tempsId(tempsId)
                            .posteId(posteId)
                            .salaireMensuel(salaireMensuel)
                            .attrition(attrition)
                            .scorePerformance(performance)
                            .satisfactionEmploye(satisfaction)
                            .dureeAvantDepart(dureeAvantDepart)
                            .build();

                    faits.add(fait);

                } catch (Exception e) {
                    lignesIgnorees++;
                    System.err.println("[RH] Ligne " + lignesLues + " ignorée : " + e.getMessage());
                }
            }
        }

        System.out.println("[RH] Chargement terminé — " + faits.size() +
                " faits construits, " + lignesIgnorees + " ignorées.");
        return faits;
    }
}
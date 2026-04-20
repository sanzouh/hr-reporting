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
 * Retourne une Map<employe_id, FaitRH.Builder> pour consolidation dans ETLPipeline.
 * Conserve le salaire le plus récent et promotion = 1 si au moins une promotion.
 */
public class NexCoreJobHistoryLoader {

    private static final String FILE_PATH = "src/main/resources/data/JobHistory.csv";

    private static final Map<String, String> DEPT_MAP = Map.of(
            "RD",    "R&D",
            "SALES", "Sales",
            "IT",    "IT",
            "OPS",   "Operations",
            "HR",    "HR",
            "ADMIN", "Admin",
            "MGMT",  "Management"
    );

    public static Map<String, FaitRH.Builder> loadAsMap()
            throws IOException, CsvValidationException, SQLException {

        // Agrégation : salaire le plus récent + promotion si au moins une
        Map<String, JobAgg> aggregations = new LinkedHashMap<>();
        int lignesLues = 0, lignesIgnorees = 0;

        try (CSVReader reader = new CSVReader(
                new InputStreamReader(new FileInputStream(FILE_PATH), StandardCharsets.UTF_8))) {

            String[] headers = reader.readNext();
            Map<String, Integer> idx = ETLUtils.buildIndex(headers);

            String[] row;
            while ((row = reader.readNext()) != null) {
                lignesLues++;
                try {
                    String matricule     = ETLUtils.clean(ETLUtils.get(row, idx, "EmployeeID"));
                    String changeDateRaw = ETLUtils.get(row, idx, "ChangeDate");
                    String toTitle       = ETLUtils.get(row, idx, "ToJobTitle");
                    String salAfterRaw   = ETLUtils.get(row, idx, "SalaryAfter");
                    String changeType    = ETLUtils.get(row, idx, "ChangeType");
                    String deptCode      = ETLUtils.get(row, idx, "DepartmentCode");
                    String site          = ETLUtils.get(row, idx, "Site");

                    if (matricule.isBlank() || toTitle.isBlank()) { lignesIgnorees++; continue; }

                    String    dept       = DEPT_MAP.getOrDefault(deptCode.trim().toUpperCase(), deptCode.trim());
                    LocalDate changeDate = ETLUtils.parseDate(changeDateRaw);
                    double    salMensuel = ETLUtils.annuelVersQuotidien(ETLUtils.parseMontant(salAfterRaw));
                    boolean   isPromo    = "Promotion".equalsIgnoreCase(changeType.trim());

                    aggregations.merge(matricule,
                            new JobAgg(matricule, dept, toTitle, salMensuel, isPromo, changeDate, site),
                            (ex, nw) -> {
                                // Garder le salaire le plus récent
                                if (nw.changeDate != null && (ex.changeDate == null || nw.changeDate.isAfter(ex.changeDate))) {
                                    ex.salMensuel  = nw.salMensuel;
                                    ex.titre       = nw.titre;
                                    ex.changeDate  = nw.changeDate;
                                }
                                // Promotion = 1 si au moins une promotion dans l'historique
                                if (nw.promoted) ex.promoted = true;
                                return ex;
                            });

                } catch (Exception e) {
                    lignesIgnorees++;
                    System.err.println("[JH] Ligne " + lignesLues + " ignorée : " + e.getMessage());
                }
            }
        }

        Map<String, FaitRH.Builder> result = new LinkedHashMap<>();
        for (JobAgg agg : aggregations.values()) {
            try {
                int deptId  = DWRepository.upsertDepartement(agg.dept, agg.site, "N/A");
                int annee   = agg.changeDate != null ? ETLUtils.annee(agg.changeDate)     : 2020;
                int sem     = agg.changeDate != null ? ETLUtils.semestre(agg.changeDate)  : 1;
                int trim    = agg.changeDate != null ? ETLUtils.trimestre(agg.changeDate) : 1;
                int mois    = agg.changeDate != null ? ETLUtils.mois(agg.changeDate)      : 1;
                int tempsId = DWRepository.upsertTemps(annee, sem, trim, mois);
                int posteId = DWRepository.upsertPoste(agg.titre, ETLUtils.inferNiveau(agg.titre), "N/A");

                FaitRH.Builder builder = FaitRH.builder()
                        .employeId(agg.matricule)
                        .deptId(deptId)
                        .tempsId(tempsId)
                        .posteId(posteId)
                        .salaireMensuel(agg.salMensuel)
                        .promotionRecommandee(agg.promoted ? 1 : 0);

                result.put(agg.matricule, builder);
            } catch (Exception e) {
                System.err.println("[JH] Erreur fait " + agg.matricule + " : " + e.getMessage());
            }
        }

        System.out.println("[JH] " + result.size() + " builders construits, " + lignesIgnorees + " ignorées.");
        return result;
    }

    /** Compatibilité ascendante */
    public static List<FaitRH> load() throws IOException, CsvValidationException, SQLException {
        List<FaitRH> faits = new ArrayList<>();
        loadAsMap().values().forEach(b -> faits.add(b.build()));
        return faits;
    }

    private static class JobAgg {
        String    matricule, dept, titre, site;
        double    salMensuel;
        boolean   promoted;
        LocalDate changeDate;

        JobAgg(String m, String d, String t, double s, boolean p, LocalDate cd, String si) {
            this.matricule = m; this.dept = d; this.titre = t;
            this.salMensuel = s; this.promoted = p; this.changeDate = cd; this.site = si;
        }
    }
}
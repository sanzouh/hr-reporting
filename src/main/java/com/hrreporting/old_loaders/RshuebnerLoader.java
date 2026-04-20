package com.hrreporting.old_loaders;

import com.hrreporting.db.DWRepository;
import com.hrreporting.etl.ETLUtils;
import com.hrreporting.model.FaitRH;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;

/**
 * RshuebnerLoader — Loader ETL pour le dataset HRDataset_v14 (rshuebner).
 * Source     : HRDataset_v14.csv (311 lignes)
 * Spécificités de nettoyage :
 *   - Salaire annuel → mensuel (÷ 12)
 *   - Dates mixtes : DateofHire, DateofTermination (MM/dd/yyyy, dd/MM/yyyy...)
 *   - Motifs de départ : TermReason (texte libre, N/A si encore actif)
 *   - Absences : colonne directe
 *   - Statut : EmploymentStatus (Active, Terminated, ...)
 * Colonnes utilisées :
 *   EmpID, Sex, Department, Position, Salary, Termd,
 *   DateofHire, DateofTermination, TermReason, EmploymentStatus,
 *   RecruitmentSource, PerformanceScore, EmpSatisfaction, Absences, DOB
 */
public class RshuebnerLoader {

    private static final String FILE_PATH = "src/main/resources/data/HRDataset_v14.csv";

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
                    String employeId        = "RSH-" + ETLUtils.clean(ETLUtils.get(row, idx, "EmpID"));
                    String deptRaw          = ETLUtils.get(row, idx, "Department");
                    String position         = ETLUtils.get(row, idx, "Position");
                    String sexRaw           = ETLUtils.get(row, idx, "Sex");
                    String salaryRaw        = ETLUtils.get(row, idx, "Salary");
                    String hireRaw          = ETLUtils.get(row, idx, "DateofHire");
                    String termRaw          = ETLUtils.get(row, idx, "DateofTermination");
                    String termReason       = ETLUtils.get(row, idx, "TermReason");
                    String empStatus        = ETLUtils.get(row, idx, "EmploymentStatus");
                    String recSource        = ETLUtils.get(row, idx, "RecruitmentSource");
                    String perfRaw          = ETLUtils.get(row, idx, "PerformanceScore");
                    String satisfRaw        = ETLUtils.get(row, idx, "EmpSatisfaction");
                    String absencesRaw      = ETLUtils.get(row, idx, "Absences");
                    String dobRaw           = ETLUtils.get(row, idx, "DOB");

                    // ── TRANSFORMATION ────────────────────────────────
                    String dept             = ETLUtils.normaliserDepartement(deptRaw);
                    String genre            = ETLUtils.normaliserGenre(sexRaw);

                    // Salaire annuel → mensuel
                    double salaireAnnuel    = ETLUtils.parseMontant(salaryRaw);
                    double salaireMensuel   = ETLUtils.annuelVersQuotidien(salaireAnnuel);

                    // Dates
                    LocalDate dateEmbauche  = ETLUtils.parseDate(hireRaw);
                    LocalDate dateDepart    = ETLUtils.parseDate(termRaw);
                    LocalDate dateNaissance = ETLUtils.parseDate(dobRaw);

                    // Âge calculé depuis DOB
                    int age = (dateNaissance != null)
                            ? (int) java.time.temporal.ChronoUnit.YEARS.between(dateNaissance, LocalDate.now())
                            : -1;

                    // Ancienneté en années depuis embauche
                    int anciennete = (dateEmbauche != null)
                            ? (int) java.time.temporal.ChronoUnit.YEARS.between(dateEmbauche, LocalDate.now())
                            : -1;

                    // Attrition : Termd = 1 si parti
                    int attrition = ETLUtils.parseInt(ETLUtils.get(row, idx, "Termd"));

                    // Durée avant départ en jours
                    int dureeAvantDepart = (attrition == 1)
                            ? ETLUtils.joursEntre(dateEmbauche, dateDepart)
                            : -1;

                    // Motif de départ normalisé
                    String motif = (termReason.isBlank()
                            || termReason.equalsIgnoreCase("N/A-StillEmployed"))
                            ? "N/A" : ETLUtils.capitaliser(termReason);

                    // Statut
                    String statut = empStatus.toLowerCase().contains("active") ? "Actif" : "Parti";

                    // Performance : texte → score numérique
                    int scorePerf = normaliserPerformanceScore(perfRaw);

                    // Satisfaction : déjà numérique (1-5)
                    int satisfaction = ETLUtils.parseInt(satisfRaw);

                    // Absences
                    int absences = ETLUtils.parseInt(absencesRaw);

                    // Période de référence : année d'embauche ou 2022 par défaut
                    int annee     = dateEmbauche != null ? ETLUtils.annee(dateEmbauche) : 2022;
                    int semestre  = dateEmbauche != null ? ETLUtils.semestre(dateEmbauche) : 1;
                    int trimestre = dateEmbauche != null ? ETLUtils.trimestre(dateEmbauche) : 1;
                    int mois      = dateEmbauche != null ? ETLUtils.mois(dateEmbauche) : 1;

                    // ── VALIDATION ────────────────────────────────────
                    if (employeId.equals("RSH-") || dept.isBlank()) {
                        lignesIgnorees++;
                        continue;
                    }

                    // ── CHARGEMENT DIMENSIONS ─────────────────────────
                    DWRepository.upsertEmploye(employeId, "", age, genre, anciennete, statut, motif);

                    int deptId  = DWRepository.upsertDepartement(dept, "N/A", "N/A");
                    int tempsId = DWRepository.upsertTemps(annee, semestre, trimestre, mois);
                    int posteId = DWRepository.upsertPoste(
                            position,
                            ETLUtils.inferNiveau(position),
                            ETLUtils.capitaliser(recSource)
                    );

                    // ── CONSTRUCTION DU FAIT ──────────────────────────
                    FaitRH fait = FaitRH.builder()
                            .employeId(employeId)
                            .deptId(deptId)
                            .tempsId(tempsId)
                            .posteId(posteId)
                            .salaireMensuel(salaireMensuel)
                            .attrition(attrition)
                            .scorePerformance(scorePerf)
                            .satisfactionEmploye(satisfaction)
                            .nbAbsences(absences)
                            .dureeAvantDepart(dureeAvantDepart)
                            .build();

                    faits.add(fait);

                } catch (Exception e) {
                    lignesIgnorees++;
                    System.err.println("[RSH] Ligne " + lignesLues + " ignorée : " + e.getMessage());
                }
            }
        }

        System.out.println("[RSH] Chargement terminé — " + faits.size() +
                " faits construits, " + lignesIgnorees + " lignes ignorées.");
        return faits;
    }

    // ═══════════════════════════════════════════════════════════════════
    // UTILITAIRES PRIVÉS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Convertit le PerformanceScore textuel (rshuebner) en score numérique 1-4.
     * Exceeds → 4, Fully Meets → 3, Needs Improvement → 2, PIP → 1
     */
    private static int normaliserPerformanceScore(String raw) {
        if (raw == null || raw.isBlank()) return -1;
        return switch (raw.trim().toLowerCase()) {
            case "exceeds"                        -> 4;
            case "fully meets"                    -> 3;
            case "needs improvement"              -> 2;
            case "pip"                            -> 1;
            default                               -> -1;
        };
    }
}
package com.hrreporting.etl;

import com.hrreporting.db.DWRepository;
import com.hrreporting.model.FaitRH;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * EvaluationsLoader — Loader ETL pour evaluations_semestrielles.xlsx (source générée).
 * Source : evaluations_semestrielles.xlsx (~1 800 lignes)
 * Spécificités de nettoyage :
 * — Dates multi-formats : DD-MM-YYYY, MM/DD/YYYY, DD MMM YYYY (cellules texte)
 * — Score : entier ou null
 * — Appreciation : casse variable (GOOD, good, Bon)
 * — Objectifs_atteints_pct : avec ou sans '%'
 * — Promotion_recommandee : Oui/oui/Yes/Non/No/NON
 * — Valeurs nulles sur plusieurs colonnes (~3-5%).
 * Colonnes utilisées :
 *   EmployeeID, Department, Semestre, Score, Appreciation,
 *   Manager, Objectifs_atteints_pct, Promotion_recommandee, Date_evaluation
 */
public class EvaluationsLoader {

    private static final String FILE_PATH = "src/main/resources/data/evaluations_semestrielles.xlsx";

    public static List<FaitRH> load() throws IOException, SQLException {
        List<FaitRH> faits = new ArrayList<>();
        int lignesLues = 0, lignesIgnorees = 0;

        try (Workbook workbook = new XSSFWorkbook(new FileInputStream(FILE_PATH))) {

            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            Map<String, Integer> idx = buildIndex(headerRow);

            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                lignesLues++;

                try {
                    // ── EXTRACTION ────────────────────────────────────
                    String employeId     = ETLUtils.clean(getCellString(row, idx, "EmployeeID"));
                    String deptRaw       = getCellString(row, idx, "Department");
                    String semestreRaw   = getCellString(row, idx, "Semestre");
                    String scoreRaw      = getCellString(row, idx, "Score");
                    String objectifsRaw  = getCellString(row, idx, "Objectifs_atteints_pct");
                    String promoRaw      = getCellString(row, idx, "Promotion_recommandee");
                    String dateRaw       = getCellString(row, idx, "Date_evaluation");

                    // ── VALIDATION ────────────────────────────────────
                    if (employeId.isBlank()) {
                        lignesIgnorees++;
                        continue;
                    }

                    // ── TRANSFORMATION ────────────────────────────────
                    String dept              = ETLUtils.normaliserDepartement(deptRaw);
                    int    score             = ETLUtils.parseInt(scoreRaw);
                    int    objectifs         = ETLUtils.parseInt(objectifsRaw);
                    int    promotionRecomm   = ETLUtils.normaliserBoolean(promoRaw);

                    // Parsing semestre : "S1-2022" ou "2022-S1"
                    int annee    = parseSemestreAnnee(semestreRaw);
                    int semestre = parseSemestreNum(semestreRaw);

                    // Date d'évaluation — peut être cellule date Excel ou texte
                    LocalDate dateEval = parseCellDate(row, idx, "Date_evaluation", dateRaw);
                    int trimestre = dateEval != null ? ETLUtils.trimestre(dateEval) : (semestre == 1 ? 1 : 3);
                    int mois      = dateEval != null ? ETLUtils.mois(dateEval)      : (semestre == 1 ? 3 : 9);

                    // ── CHARGEMENT DIMENSIONS ─────────────────────────
                    DWRepository.upsertEmploye(employeId, "", -1, "N/A", -1, "N/A", "N/A");

                    String deptFinal = dept.isBlank() || dept.equals("N/A") ? "Non défini" : dept;
                    int deptId = DWRepository.upsertDepartement(deptFinal, "N/A", "N/A");
                    int tempsId = DWRepository.upsertTemps(annee, semestre, trimestre, mois);
                    int posteId = DWRepository.upsertPoste("N/A", "N/A", "N/A");

                    // ── CONSTRUCTION DU FAIT ──────────────────────────
                    FaitRH fait = FaitRH.builder()
                            .employeId(employeId)
                            .deptId(deptId)
                            .tempsId(tempsId)
                            .posteId(posteId)
                            .scoreEvaluation(score)
                            .objectifsAtteintsP(objectifs)
                            .promotionRecommandee(promotionRecomm)
                            .build();

                    faits.add(fait);

                } catch (Exception e) {
                    lignesIgnorees++;
                    System.err.println("[EVAL] Ligne " + (r + 1) + " ignorée : " + e.getMessage());
                }
            }
        }

        System.out.println("[EVAL] Chargement terminé — " + faits.size() +
                " faits construits, " + lignesIgnorees + " lignes ignorées.");
        return faits;
    }

    // ═══════════════════════════════════════════════════════════════════
    // UTILITAIRES PRIVÉS — EXCEL
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Construit l'index nom_colonne → position depuis la ligne d'en-tête Excel.
     */
    private static Map<String, Integer> buildIndex(Row headerRow) {
        Map<String, Integer> idx = new HashMap<>();
        for (Cell cell : headerRow) {
            idx.put(ETLUtils.clean(cell.getStringCellValue()), cell.getColumnIndex());
        }
        return idx;
    }

    /**
     * Lit une cellule Excel comme String, quel que soit son type.
     * Gère : STRING, NUMERIC, BOOLEAN, BLANK, FORMULA.
     */
    private static String getCellString(Row row, Map<String, Integer> idx, String col) {
        Integer colIdx = idx.get(col);
        if (colIdx == null) return "";
        Cell cell = row.getCell(colIdx);
        if (cell == null) return "";

        return switch (cell.getCellType()) {
            case STRING  -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    // Retourne la date formatée comme texte pour parseDate()
                    LocalDate d = cell.getLocalDateTimeCellValue().toLocalDate();
                    yield d.toString(); // format ISO yyyy-MM-dd
                }
                double val = cell.getNumericCellValue();
                // Évite "4.0" pour les scores entiers
                yield val == Math.floor(val) ? String.valueOf((int) val) : String.valueOf(val);
            }
            case BOOLEAN -> cell.getBooleanCellValue() ? "1" : "0";
            case FORMULA -> {
                try { yield String.valueOf((int) cell.getNumericCellValue()); }
                catch (Exception e) { yield cell.getStringCellValue().trim(); }
            }
            default -> "";
        };
    }

    /**
     * Tente de lire une cellule date Excel nativement,
     * puis tombe en fallback sur ETLUtils.parseDate() si c'est du texte.
     */
    private static LocalDate parseCellDate(Row row, Map<String, Integer> idx,
                                           String col, String rawText) {
        Integer colIdx = idx.get(col);
        if (colIdx != null) {
            Cell cell = row.getCell(colIdx);
            if (cell != null && cell.getCellType() == CellType.NUMERIC
                    && DateUtil.isCellDateFormatted(cell)) {
                return cell.getLocalDateTimeCellValue().toLocalDate();
            }
        }
        return ETLUtils.parseDate(rawText);
    }

    // ═══════════════════════════════════════════════════════════════════
    // UTILITAIRES PRIVÉS — SEMESTRE
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Extrait l'année depuis un label semestre.
     * Formats attendus : "S1-2022", "S2-2021", "2023-S1"
     */
    private static int parseSemestreAnnee(String raw) {
        if (raw == null || raw.isBlank()) return 2022;
        for (String part : raw.split("-")) {
            try {
                int val = Integer.parseInt(part.trim());
                if (val > 2000) return val;
            } catch (NumberFormatException ignored) {}
        }
        return 2022;
    }

    /**
     * Extrait le numéro de semestre (1 ou 2) depuis un label semestre.
     */
    private static int parseSemestreNum(String raw) {
        if (raw == null || raw.isBlank()) return 1;
        if (raw.toUpperCase().contains("S2")) return 2;
        return 1;
    }
}
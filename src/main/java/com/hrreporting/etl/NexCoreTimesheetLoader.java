package com.hrreporting.etl;

import com.hrreporting.db.DWRepository;
import com.hrreporting.model.FaitRH;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.sql.SQLException;
import java.util.*;

/**
 * NexCoreTimesheetLoader — Loader ETL pour Timesheet_Operations.xlsx.
 *
 * Source     : Timesheet_Operations.xlsx (~47 000 lignes)
 * Colonnes   : Emp_Code, Department, Year, Month, OvertimeHours, AbsenceDays, Site
 *
 * Spécificités :
 *   - Emp_Code = matricule numérique pur (même que EmployeeID dans RH_Paie)
 *   - Department = noms longs (R&D, IT_Systems, Human_Resources, etc.)
 *   - Year/Month séparés (pas de date complète)
 *   - Agrégation annuelle : SUM absences et MAX overtime par employé/année
 */
public class NexCoreTimesheetLoader {

    private static final String FILE_PATH =
            "src/main/resources/data/Timesheet_Operations.xlsx";

    // Mapping noms longs → codes normalisés DW
    private static final Map<String, String> DEPT_MAP = Map.of(
            "R&D",             "R&D",
            "Sales",           "Sales",
            "IT_Systems",      "IT",
            "Operations",      "Operations",
            "Human_Resources", "HR",
            "Administration",  "Admin",
            "Management",      "Management"
    );

    public static List<FaitRH> load() throws IOException, SQLException {
        // Agrégation par (employeId, année) : total absences + max overtime
        Map<String, TimesheetAgg> aggregations = new LinkedHashMap<>();

        int lignesLues = 0, lignesIgnorees = 0;

        try (Workbook wb = new XSSFWorkbook(new FileInputStream(FILE_PATH))) {
            Sheet sheet = wb.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            Map<String, Integer> idx = buildExcelIndex(headerRow);

            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                lignesLues++;

                try {
                    String matricule = getCellString(row, idx, "Emp_Code");
                    String deptRaw   = getCellString(row, idx, "Department");
                    String yearRaw   = getCellString(row, idx, "Year");
                    String monthRaw  = getCellString(row, idx, "Month");
                    String otRaw     = getCellString(row, idx, "OvertimeHours");
                    String absRaw    = getCellString(row, idx, "AbsenceDays");
                    String site      = getCellString(row, idx, "Site");

                    if (matricule.isBlank()) { lignesIgnorees++; continue; }

                    int year  = ETLUtils.parseInt(yearRaw);
                    int month = ETLUtils.parseInt(monthRaw);
                    int ot    = ETLUtils.parseInt(otRaw);
                    int abs   = ETLUtils.parseInt(absRaw);
                    String dept = DEPT_MAP.getOrDefault(deptRaw.trim(), deptRaw.trim());

                    if (year < 0 || month < 0) { lignesIgnorees++; continue; }

                    String key = matricule + "_" + year;
                    aggregations.merge(key,
                            new TimesheetAgg(matricule, dept, year, month, ot, abs, site),
                            (ex, nw) -> {
                                ex.totalAbsences += nw.totalAbsences;
                                ex.maxOT         = Math.max(ex.maxOT, nw.maxOT);
                                return ex;
                            });

                } catch (Exception e) {
                    lignesIgnorees++;
                }
            }
        }

        // Construction des FaitRH depuis les agrégations
        List<FaitRH> faits = new ArrayList<>();
        for (TimesheetAgg agg : aggregations.values()) {
            try {
                DWRepository.upsertEmploye(agg.matricule, "", -1, "N/A", -1, "N/A", "N/A");

                int deptId  = DWRepository.upsertDepartement(agg.dept, agg.site, "N/A");
                int tempsId = DWRepository.upsertTemps(agg.year, agg.year <= 2020 ? 1 : 2, 1, agg.month);
                int posteId = DWRepository.upsertPoste("N/A", "N/A", "N/A");

                FaitRH fait = FaitRH.builder()
                        .employeId(agg.matricule)
                        .deptId(deptId)
                        .tempsId(tempsId)
                        .posteId(posteId)
                        .nbAbsences(agg.totalAbsences)
                        .heuresSup(agg.maxOT > 10 ? 1 : 0)
                        .build();

                faits.add(fait);
            } catch (Exception e) {
                System.err.println("[TS] Erreur " + agg.matricule + " : " + e.getMessage());
            }
        }

        System.out.println("[TS] Chargement terminé — " + faits.size() +
                " faits construits, " + lignesIgnorees + " ignorées sur " + lignesLues + " lues.");
        return faits;
    }

    // ── Classes internes ─────────────────────────────────────────────

    private static class TimesheetAgg {
        String matricule, dept, site;
        int year, month, totalAbsences, maxOT;

        TimesheetAgg(String m, String d, int y, int mo, int ot, int abs, String s) {
            this.matricule     = m;
            this.dept          = d;
            this.year          = y;
            this.month         = mo;
            this.maxOT         = ot;
            this.totalAbsences = abs;
            this.site          = s;
        }
    }

    // ── Utilitaires Excel ─────────────────────────────────────────────

    private static Map<String, Integer> buildExcelIndex(Row headerRow) {
        Map<String, Integer> idx = new HashMap<>();
        for (Cell cell : headerRow)
            idx.put(ETLUtils.clean(cell.getStringCellValue()), cell.getColumnIndex());
        return idx;
    }

    private static String getCellString(Row row, Map<String, Integer> idx, String col) {
        Integer ci = idx.get(col);
        if (ci == null) return "";
        Cell cell = row.getCell(ci);
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING  -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                double v = cell.getNumericCellValue();
                yield v == Math.floor(v) ? String.valueOf((int) v) : String.valueOf(v);
            }
            default -> "";
        };
    }
}
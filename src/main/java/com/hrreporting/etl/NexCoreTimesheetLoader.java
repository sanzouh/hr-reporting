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
 * Retourne une Map<employe_id, FaitRH.Builder> pour consolidation dans ETLPipeline.
 * Agrégation annuelle : SUM absences, MAX overtime par employé.
 */
public class NexCoreTimesheetLoader {

    private static final String FILE_PATH = "src/main/resources/data/Timesheet_Operations.xlsx";

    private static final Map<String, String> DEPT_MAP = Map.of(
            "R&D",             "R&D",
            "Sales",           "Sales",
            "IT_Systems",      "IT",
            "Operations",      "Operations",
            "Human_Resources", "HR",
            "Administration",  "Admin",
            "Management",      "Management"
    );

    public static Map<String, FaitRH.Builder> loadAsMap() throws IOException, SQLException {
        // Agrégation brute par employé (toutes années confondues)
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

                    aggregations.merge(matricule,
                            new TimesheetAgg(matricule, dept, year, month, ot, abs, site),
                            (ex, nw) -> {
                                ex.totalAbsences += nw.totalAbsences;
                                ex.maxOT          = Math.max(ex.maxOT, nw.maxOT);
                                return ex;
                            });
                } catch (Exception e) {
                    lignesIgnorees++;
                }
            }
        }

        // Construction des builders
        Map<String, FaitRH.Builder> result = new LinkedHashMap<>();
        for (TimesheetAgg agg : aggregations.values()) {
            try {
                int deptId  = DWRepository.upsertDepartement(agg.dept, agg.site, "N/A");
                int tempsId = DWRepository.upsertTemps(agg.year, agg.year <= 2020 ? 1 : 2, 1, agg.month);
                int posteId = DWRepository.upsertPoste("N/A", "N/A", "N/A");

                FaitRH.Builder builder = FaitRH.builder()
                        .employeId(agg.matricule)
                        .deptId(deptId)
                        .tempsId(tempsId)
                        .posteId(posteId)
                        .nbAbsences(agg.totalAbsences)
                        .heuresSup(agg.maxOT > 10 ? 1 : 0);

                result.put(agg.matricule, builder);
            } catch (Exception e) {
                System.err.println("[TS] Erreur " + agg.matricule + " : " + e.getMessage());
            }
        }

        System.out.println("[TS] " + result.size() + " builders construits, " + lignesIgnorees + " ignorées sur " + lignesLues + " lues.");
        return result;
    }

    /** Compatibilité ascendante */
    public static List<FaitRH> load() throws IOException, SQLException {
        List<FaitRH> faits = new ArrayList<>();
        loadAsMap().values().forEach(b -> faits.add(b.build()));
        return faits;
    }

    private static class TimesheetAgg {
        String matricule, dept, site;
        int year, month, totalAbsences, maxOT;

        TimesheetAgg(String m, String d, int y, int mo, int ot, int abs, String s) {
            this.matricule = m; this.dept = d; this.year = y;
            this.month = mo; this.maxOT = ot; this.totalAbsences = abs; this.site = s;
        }
    }

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
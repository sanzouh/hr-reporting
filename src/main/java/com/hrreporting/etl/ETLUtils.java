package com.hrreporting.etl;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * ETLUtils — Utilitaires partagés par tous les loaders ETL.
 * Centralise :
 * – Parsing de dates multi-formats
 * – Parsing de montants (coûts, salaires).
 * – Normalisation de chaînes (genre, département, booléens)
 * – Calculs dérivés (durée entre deux dates, semestre, trimestre).
 * Convention : toute valeur non parseable retourne -1 (int) ou -1.0 (double).
 */
public class ETLUtils {

    // Formats de dates rencontrés dans les 4 sources
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("MM-dd-yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("dd MMM yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yy"),
            DateTimeFormatter.ofPattern("M/d/yyyy"),   // couvre 7/5/2011, 1/7/2008
            //DateTimeFormatter.ofPattern("dd MMM yyyy") // couvre 26 Sep 2021 — attention : nécessite Locale.ENGLISH
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d MMM yyyy",  Locale.ENGLISH)
    );

    // ═══════════════════════════════════════════════════════════════════
    // PARSING DATES
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Tente de parser une date en essayant tous les formats connus.
     * @return LocalDate ou null si aucun format ne correspond
     */
    public static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank() || raw.equalsIgnoreCase("N/A")) return null;
        String cleaned = raw.trim();
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try {
                return LocalDate.parse(cleaned, fmt);
            } catch (DateTimeParseException ignored) {}
        }
        System.err.println("[ETLUtils] Date non parseable : '" + raw + "'");
        return null;
    }

    /**
     * Calcule le nombre de jours entre deux dates.
     * @return nombre de jours ou -1 si l'une des dates est null
     */
    public static int joursEntre(LocalDate debut, LocalDate fin) {
        if (debut == null || fin == null) return -1;
        return (int) ChronoUnit.DAYS.between(debut, fin);
    }

    /** Extrait l'année d'une date ou retourne -1 */
    public static int annee(LocalDate d) {
        return d == null ? -1 : d.getYear();
    }

    /** Calcule le semestre (1 ou 2) d'une date */
    public static int semestre(LocalDate d) {
        if (d == null) return -1;
        return d.getMonthValue() <= 6 ? 1 : 2;
    }

    /** Calcule le trimestre (1-4) d'une date */
    public static int trimestre(LocalDate d) {
        if (d == null) return -1;
        return (d.getMonthValue() - 1) / 3 + 1;
    }

    /** Retourne le mois (1-12) d'une date ou -1 */
    public static int mois(LocalDate d) {
        return d == null ? -1 : d.getMonthValue();
    }

    // ═══════════════════════════════════════════════════════════════════
    // PARSING MONTANTS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Parse un montant en supprimant les symboles courants : $, €, espaces, virgules.
     * Exemples : "$1,500", "1 000", "750.00", "350" → double
     * @return valeur double ou -1.0 si non parseable
     */
    public static double parseMontant(String raw) {
        if (raw == null || raw.isBlank()) return -1;
        String cleaned = raw.trim()
                .replace("$", "")
                .replace("€", "")
                .replace(" ", "")
                .replace(",", "");
        // Cas "750.00" ou "750"
        try { return Double.parseDouble(cleaned); }
        catch (NumberFormatException e) {
            System.err.println("[ETLUtils] Montant non parseable : '" + raw + "'");
            return -1;
        }
    }

    /**
     * Convertit un salaire annuel en mensuel (÷ 12).
     * Utilisé pour rshuebner dont le champ Salary est annuel.
     */
    public static double annuelVersQuotidien(double salaire) {
        return salaire <= 0 ? -1 : Math.round((salaire / 12.0) * 100.0) / 100.0;
    }

    // ═══════════════════════════════════════════════════════════════════
    // PARSING ENTIERS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Parse un entier de manière sécurisée.
     * Gère les cas "42%", "42.0", "N/A"
     * @return entier ou -1 si non parseable
     */
    public static int parseInt(String raw) {
        if (raw == null || raw.isBlank()) return -1;
        String cleaned = raw.trim().replace("%", "").replace(".0", "");
        try { return Integer.parseInt(cleaned); }
        catch (NumberFormatException e) {
            // Tenter via double (cas "42.5")
            try { return (int) Double.parseDouble(cleaned); }
            catch (NumberFormatException e2) { return -1; }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // NORMALISATION CHAÎNES
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Normalise le genre vers M / F / N/A.
     * Gère : Male, male, MALE, M, Female, female, F, Femme, Homme...
     */
    public static String normaliserGenre(String raw) {
        if (raw == null || raw.isBlank()) return "N/A";
        return switch (raw.trim().toLowerCase()) {
            case "male",   "m", "homme", "h" -> "M";
            case "female", "f", "femme"      -> "F";
            default                          -> "N/A";
        };
    }

    /**
     * Normalise un booléen textuel vers 0/1.
     * Gère : Yes/No, Oui/Non, oui/non, OUI/NON, 1/0, true/false
     * @return 1 = vrai, 0 = faux, -1 = inconnu
     */
    public static int normaliserBoolean(String raw) {
        if (raw == null || raw.isBlank()) return -1;
        return switch (raw.trim().toLowerCase()) {
            case "yes", "oui", "1", "true"  -> 1;
            case "no",  "non", "0", "false" -> 0;
            default                         -> -1;
        };
    }

    /**
     * Normalise un nom de département vers la nomenclature unifiée du DW.
     * Gère les casses, abréviations et variantes orthographiques.
     */
    public static String normaliserDepartement(String raw) {
        if (raw == null || raw.isBlank()) return "N/A";
        return switch (raw.trim().toLowerCase()) {
            case "research & development",
                 "r&d", "r and d", "rand d", "randd"-> "R&D";
            case "sales"                            -> "Sales";
            case "human resources", "hr"            -> "HR";
            case "it/is", "it", "software engineering" -> "IT";
            case "admin offices", "admin"           -> "Admin";
            case "production", "operations"         -> "Operations";
            case "executive office", "management"   -> "Management";
            default                                 -> raw.trim().isBlank() ? "Non défini" : capitaliser(raw.trim());
        };
    }

    /**
     * Infère le niveau hiérarchique d'un poste à partir de son intitulé.
     */
    public static String inferNiveau(String jobRole) {
        if (jobRole == null || jobRole.isBlank()) return "N/A";
        String r = jobRole.toLowerCase();
        if (r.contains("director") || r.contains("executive") || r.contains("vp")) return "Executive";
        if (r.contains("manager") || r.contains("senior") || r.contains("lead"))   return "Senior";
        if (r.contains("junior") || r.contains("assistant") || r.contains("i "))   return "Junior";
        return "Mid";
    }

    /**
     * Nettoie et normalise une chaîne : trim, suppression BOM UTF-8.
     */
    public static String clean(String raw) {
        if (raw == null) return "";
        return raw.trim().replace("\uFEFF", "");
    }

    /**
     * Met en majuscule la première lettre d'une chaîne.
     */
    public static String capitaliser(String s) {
        if (s == null || s.isBlank()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }

    // ═══════════════════════════════════════════════════════════════════
    // AUTRES
    // ═══════════════════════════════════════════════════════════════════

    /** Construit un index nom_colonne → position depuis un tableau de headers CSV */
    public static Map<String, Integer> buildIndex(String[] headers) {
        Map<String, Integer> idx = new HashMap<>();
        for (int i = 0; i < headers.length; i++) {
            idx.put(ETLUtils.clean(headers[i]), i);
        }
        return idx;
    }

    /** Accès sécurisé à une cellule CSV par nom de colonne */
    public static String get(String[] row, Map<String, Integer> idx, String col) {
        Integer i = idx.get(col);
        if (i == null || i >= row.length) return "";
        return row[i] == null ? "" : row[i].trim();
    }
}
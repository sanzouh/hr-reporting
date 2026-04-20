package com.hrreporting.ui;

import com.hrreporting.db.DatabaseManager;
import com.hrreporting.db.DWRepository;
import org.jfree.chart.*;
import org.jfree.chart.plot.*;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.general.DefaultPieDataset;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.sql.*;
import java.util.*;

/**
 * EffectifsPanel — Section Effectifs du dashboard RH.
 * Contenu :
 * – KPI cards : Total employés, % actifs, ancienneté moyenne, âge moyen
 * – Graphique 1 : Effectif par département (barres verticales)
 * – Graphique 2 : Répartition par genre (camembert)
 * – Graphique 3 : Pyramide des âges simplifiée (barres groupées par tranche)
 * – Graphique 4 : Ancienneté moyenne par département
 */
public class EffectifsPanel extends JPanel implements MainDashboard.Refreshable {

    private final MainDashboard dashboard;
    private String annee       = "Toutes";
    private String departement = "Tous";

    public EffectifsPanel(MainDashboard dashboard) {
        this.dashboard = dashboard;
        setBackground(MainDashboard.C_BG);
        setLayout(new BorderLayout());
        build();
    }

    private void build() {
        setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(8, 10, 8, 10);
        gbc.weightx = 1.0;
        gbc.gridx = 0;

        gbc.gridy = 0; gbc.weighty = 0.10;
        add(buildKpiRow(), gbc);

        gbc.gridy = 1; gbc.weighty = 0.50;
        add(buildChartsRow1(), gbc);

        gbc.gridy = 2; gbc.weighty = 0.40;
        add(buildChartsRow2(), gbc);
    }

    // ═══════════════════════════════════════════════════════════════════
    // KPI CARDS
    // ═══════════════════════════════════════════════════════════════════

    private JPanel buildKpiRow() {
        JPanel row = new JPanel(new GridLayout(1, 4, 12, 0));
        row.setBackground(MainDashboard.C_BG);

        try {
            String deptFilter  = buildDeptJoin();
            String anneeFilter = buildAnneeFilter();

            // Total employés
            ResultSet rs1 = query("SELECT COUNT(DISTINCT f.employe_id) FROM fait_rh f" +
                    " JOIN dim_departement d ON f.dept_id = d.dept_id" +
                    " JOIN dim_temps t ON f.temps_id = t.temps_id" +
                    " WHERE 1=1" + anneeFilter + deptFilter);
            int total = rs1.next() ? rs1.getInt(1) : 0;
            row.add(MainDashboard.buildKpiCard("Total employés",
                    String.format("%,d", total), null, null));

            // % actifs
            ResultSet rs2 = query("""
                SELECT ROUND(SUM(CASE WHEN e.statut = 'Actif' THEN 1 ELSE 0 END) * 100.0 / COUNT(*), 1)
                FROM fait_rh f
                JOIN dim_employe e ON f.employe_id = e.employe_id
                JOIN dim_departement d ON f.dept_id = d.dept_id
                JOIN dim_temps t ON f.temps_id = t.temps_id
                WHERE 1=1
                """ + anneeFilter + deptFilter);
            double pctActifs = rs2.next() ? rs2.getDouble(1) : 0;
            row.add(MainDashboard.buildKpiCard("Employés actifs",
                    String.format("%.1f%%", pctActifs),
                    pctActifs > 85 ? "Stable" : "Attention",
                    pctActifs > 85 ? MainDashboard.C_SUCCESS : MainDashboard.C_WARNING));

            // Âge moyen
            ResultSet rs3 = query("""
                SELECT ROUND(AVG(e.age), 1)
                FROM fait_rh f
                JOIN dim_employe e ON f.employe_id = e.employe_id
                JOIN dim_departement d ON f.dept_id = d.dept_id
                JOIN dim_temps t ON f.temps_id = t.temps_id
                WHERE e.age > 0
                """ + anneeFilter + deptFilter);
            double ageMoyen = rs3.next() ? rs3.getDouble(1) : 0;
            row.add(MainDashboard.buildKpiCard("Âge moyen",
                    String.format("%.0f ans", ageMoyen), null, null));

            // Ancienneté moyenne
            ResultSet rs4 = query("""
                SELECT ROUND(AVG(e.anciennete_ans), 1)
                FROM fait_rh f
                JOIN dim_employe e ON f.employe_id = e.employe_id
                JOIN dim_departement d ON f.dept_id = d.dept_id
                JOIN dim_temps t ON f.temps_id = t.temps_id
                WHERE e.anciennete_ans >= 0
                """ + anneeFilter + deptFilter);
            double ancMoyenne = rs4.next() ? rs4.getDouble(1) : 0;
            row.add(MainDashboard.buildKpiCard("Ancienneté moy.",
                    String.format("%.1f ans", ancMoyenne), null, null));

        } catch (Exception e) {
            System.err.println("[Effectifs] Erreur KPI : " + e.getMessage());
        }

        return row;
    }

    // ═══════════════════════════════════════════════════════════════════
    // GRAPHIQUES LIGNE 1 : Effectif/dept + Genre
    // ═══════════════════════════════════════════════════════════════════

    private JPanel buildChartsRow1() {
        JPanel row = new JPanel(new GridBagLayout());
        row.setBackground(MainDashboard.C_BG);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 1.0;
        gbc.insets = new Insets(0, 0, 0, 6);

        try {
            Map<String, Integer> effectifs = DWRepository.getEffectifParDept();
            DefaultCategoryDataset ds = new DefaultCategoryDataset();
            effectifs.forEach((dept, nb) -> ds.addValue(nb, "Employés", dept));
            JFreeChart chart = ChartFactory.createBarChart(
                    null, "Département", "Nombre", ds,
                    PlotOrientation.VERTICAL, false, true, false);
            styleBar(chart, MainDashboard.C_PRIMARY);

            gbc.gridx = 0; gbc.weightx = 0.60;
            row.add(MainDashboard.buildCard("Effectif par département",
                    new ChartPanel(chart)), gbc);

            Map<String, Integer> genre = DWRepository.getRepartitionGenre();
            DefaultPieDataset<String> dsPie = new DefaultPieDataset<>();
            genre.forEach(dsPie::setValue);
            JFreeChart chartPie = ChartFactory.createPieChart(null, dsPie, true, true, false);
            stylePie(chartPie);

            gbc.gridx = 1; gbc.weightx = 0.40; gbc.insets = new Insets(0, 6, 0, 0);
            row.add(MainDashboard.buildCard("Répartition par genre",
                    new ChartPanel(chartPie)), gbc);

        } catch (Exception e) {
            System.err.println("[Effectifs] Erreur graphiques ligne 1 : " + e.getMessage());
        }
        return row;
    }

    // ═══════════════════════════════════════════════════════════════════
    // GRAPHIQUES LIGNE 2 : Pyramide des âges + Ancienneté
    // ═══════════════════════════════════════════════════════════════════

    private JPanel buildChartsRow2() {
        JPanel row = new JPanel(new GridBagLayout());
        row.setBackground(MainDashboard.C_BG);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 1.0;
        gbc.insets = new Insets(0, 0, 0, 6);

        try {
            DefaultCategoryDataset dsPyramide = new DefaultCategoryDataset();
            String[] tranches    = {"< 25", "25-34", "35-44", "45-54", "55+"};
            String[] conditions  = {"e.age < 25", "e.age BETWEEN 25 AND 34",
                    "e.age BETWEEN 35 AND 44", "e.age BETWEEN 45 AND 54", "e.age >= 55"};
            for (int i = 0; i < tranches.length; i++) {
                ResultSet rsM = query("SELECT COUNT(*) FROM fait_rh f " +
                        "JOIN dim_employe e ON f.employe_id = e.employe_id " +
                        "WHERE e.genre = 'M' AND " + conditions[i]);
                ResultSet rsF = query("SELECT COUNT(*) FROM fait_rh f " +
                        "JOIN dim_employe e ON f.employe_id = e.employe_id " +
                        "WHERE e.genre = 'F' AND " + conditions[i]);
                dsPyramide.addValue(rsM.next() ? rsM.getInt(1) : 0, "Hommes", tranches[i]);
                dsPyramide.addValue(rsF.next() ? rsF.getInt(1) : 0, "Femmes", tranches[i]);
            }
            JFreeChart chartPyramide = ChartFactory.createBarChart(
                    null, "Tranche d'âge", "Nombre", dsPyramide,
                    PlotOrientation.HORIZONTAL, true, true, false);
            stylePyramide(chartPyramide);

            gbc.gridx = 0; gbc.weightx = 0.65;
            row.add(MainDashboard.buildCard("Pyramide des âges",
                    new ChartPanel(chartPyramide)), gbc);

            DefaultCategoryDataset dsAnc = new DefaultCategoryDataset();
            ResultSet rsAnc = query("""
            SELECT d.nom_dept, ROUND(AVG(e.anciennete_ans), 1)
            FROM fait_rh f
            JOIN dim_employe e ON f.employe_id = e.employe_id
            JOIN dim_departement d ON f.dept_id = d.dept_id
            WHERE e.anciennete_ans >= 0
            GROUP BY d.nom_dept ORDER BY AVG(e.anciennete_ans) DESC
        """);
            while (rsAnc.next())
                dsAnc.addValue(rsAnc.getDouble(2), "Ancienneté (ans)", rsAnc.getString(1));

            JFreeChart chartAnc = ChartFactory.createBarChart(
                    null, "Département", "Années", dsAnc,
                    PlotOrientation.VERTICAL, false, true, false);
            styleBar(chartAnc, MainDashboard.C_SUCCESS);

            gbc.gridx = 1; gbc.weightx = 0.35; gbc.insets = new Insets(0, 6, 0, 0);
            row.add(MainDashboard.buildCard("Ancienneté moyenne / département",
                    new ChartPanel(chartAnc)), gbc);

        } catch (Exception e) {
            System.err.println("[Effectifs] Erreur graphiques ligne 2 : " + e.getMessage());
        }
        return row;
    }

    // ═══════════════════════════════════════════════════════════════════
    // UTILITAIRES
    // ═══════════════════════════════════════════════════════════════════

    private ResultSet query(String sql) throws SQLException {
        return DatabaseManager.getConnection().createStatement().executeQuery(sql);
    }

    private String buildDeptJoin() {
        if (departement == null || departement.equals("Tous")) return "";
        return " AND d.nom_dept = '" + departement.replace("'", "''") + "'";
    }

    private String buildAnneeFilter() {
        if (annee == null || annee.equals("Toutes")) return "";
        return " AND t.annee = " + annee.replaceAll("[^0-9]", "");
    }

    // ═══════════════════════════════════════════════════════════════════
    // STYLE GRAPHIQUES
    // ═══════════════════════════════════════════════════════════════════

    private void styleBar(JFreeChart chart, Color color) {
        chart.setBackgroundPaint(MainDashboard.C_CARD);
        chart.setBorderVisible(false);
        CategoryPlot plot = chart.getCategoryPlot();
        plot.setBackgroundPaint(MainDashboard.C_CARD);
        plot.setOutlineVisible(false);
        plot.setRangeGridlinePaint(MainDashboard.C_BORDER);
        plot.setDomainGridlinesVisible(false);
        BarRenderer r = (BarRenderer) plot.getRenderer();
        r.setSeriesPaint(0, color);
        r.setDrawBarOutline(false);
        r.setShadowVisible(false);
        r.setMaximumBarWidth(0.5);
        plot.getDomainAxis().setTickLabelFont(new Font("Segoe UI", Font.PLAIN, 11));
        plot.getRangeAxis().setTickLabelFont(new Font("Segoe UI", Font.PLAIN, 11));
        plot.getDomainAxis().setAxisLineVisible(false);
        plot.getRangeAxis().setAxisLineVisible(false);
    }

    private void stylePyramide(JFreeChart chart) {
        chart.setBackgroundPaint(MainDashboard.C_CARD);
        chart.setBorderVisible(false);
        CategoryPlot plot = chart.getCategoryPlot();
        plot.setBackgroundPaint(MainDashboard.C_CARD);
        plot.setOutlineVisible(false);
        plot.setRangeGridlinePaint(MainDashboard.C_BORDER);
        BarRenderer r = (BarRenderer) plot.getRenderer();
        r.setSeriesPaint(0, MainDashboard.C_PRIMARY);
        r.setSeriesPaint(1, MainDashboard.C_DANGER);
        r.setDrawBarOutline(false);
        r.setShadowVisible(false);
        plot.getDomainAxis().setTickLabelFont(new Font("Segoe UI", Font.PLAIN, 11));
        plot.getRangeAxis().setTickLabelFont(new Font("Segoe UI", Font.PLAIN, 11));
        plot.getDomainAxis().setAxisLineVisible(false);
        plot.getRangeAxis().setAxisLineVisible(false);
    }

    private void stylePie(JFreeChart chart) {
        chart.setBackgroundPaint(MainDashboard.C_CARD);
        chart.setBorderVisible(false);
        PiePlot<?> plot = (PiePlot<?>) chart.getPlot();
        plot.setBackgroundPaint(MainDashboard.C_CARD);
        plot.setOutlineVisible(false);
        plot.setSectionPaint("M", MainDashboard.C_PRIMARY);
        plot.setSectionPaint("F", MainDashboard.C_DANGER);
        plot.setSectionPaint("N/A", MainDashboard.C_BORDER);
        plot.setLabelFont(new Font("Segoe UI", Font.PLAIN, 11));
        plot.setShadowPaint(null);
    }

    @Override
    public void refresh(String annee, String departement) {
        this.annee       = annee;
        this.departement = departement;
        removeAll();
        build();
        revalidate();
        repaint();
    }
}
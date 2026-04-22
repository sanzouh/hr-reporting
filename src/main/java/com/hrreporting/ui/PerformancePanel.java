package com.hrreporting.ui;

import com.hrreporting.db.DatabaseManager;
import com.hrreporting.db.DWRepository;
import org.jfree.chart.*;
import org.jfree.chart.plot.*;
import org.jfree.chart.renderer.category.*;
import org.jfree.data.category.DefaultCategoryDataset;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.sql.*;
import java.util.Map;

/**
 * PerformancePanel — Section Performance & Satisfaction.
 * Contenu :
 * – KPI cards : Score perf. moyen, satisfaction moyenne, % heures sup, % objectifs atteints
 * – Graphique 1 : Score performance moyen par département
 * – Graphique 2 : Satisfaction par département (courbe)
 * – Graphique 3 : Corrélation satisfaction ↔ turnover (barres groupées)
 * – Graphique 4 : Score évaluation semestrielle par département
 */
public class PerformancePanel extends JPanel implements MainDashboard.Refreshable {

    private final MainDashboard dashboard;
    private String annee       = "Toutes";
    private String departement = "Tous";

    public PerformancePanel(MainDashboard dashboard) {
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

        gbc.gridy = 0; gbc.weighty = 0.08;
        add(buildKpiRow(), gbc);

        gbc.gridy = 1; gbc.weighty = 0.46;
        add(buildChartsRow1(), gbc);

        gbc.gridy = 2; gbc.weighty = 0.46;
        add(buildChartsRow2(), gbc);
    }

    // ═══════════════════════════════════════════════════════════════════
    // KPI CARDS
    // ═══════════════════════════════════════════════════════════════════

    private JPanel buildKpiRow() {
        JPanel row = new JPanel(new GridLayout(1, 4, 12, 0));
        row.setBackground(MainDashboard.C_BG);

        try {
            String af = buildAnneeFilter();
            String df = buildDeptFilter();

            // Score performance moyen (1-4)
            ResultSet rs1 = query("SELECT ROUND(AVG(score_performance), 2) FROM fait_rh f " +
                    "JOIN dim_departement d ON f.dept_id = d.dept_id " +
                    "WHERE score_performance > 0" + af + df);
            double perf = rs1.next() ? rs1.getDouble(1) : 0;
            Color cPerf = perf >= 3.5 ? MainDashboard.C_SUCCESS : perf >= 2.5 ? MainDashboard.C_WARNING : MainDashboard.C_DANGER;
            row.add(MainDashboard.buildKpiCard("Score performance moy.",
                    String.format("%.2f / 4", perf),
                    perf >= 3.5 ? "Excellent" : perf >= 2.5 ? "Correct" : "Faible", cPerf));

            // Satisfaction moyenne (1-4)
            ResultSet rs2 = query("SELECT ROUND(AVG(satisfaction_employe), 2) FROM fait_rh f " +
                    "JOIN dim_departement d ON f.dept_id = d.dept_id " +
                    "WHERE satisfaction_employe > 0" + af + df);
            double satisf = rs2.next() ? rs2.getDouble(1) : 0;
            Color cSatisf = satisf >= 3 ? MainDashboard.C_SUCCESS : satisf >= 2 ? MainDashboard.C_WARNING : MainDashboard.C_DANGER;
            row.add(MainDashboard.buildKpiCard("Satisfaction moyenne",
                    String.format("%.2f / 4", satisf),
                    satisf >= 3 ? "Bon" : satisf >= 2 ? "Moyen" : "Faible", cSatisf));

            // % employés avec heures sup
            ResultSet rs3 = query("SELECT ROUND(SUM(heures_sup) * 100.0 / COUNT(*), 1) FROM fait_rh f " +
                    "JOIN dim_departement d ON f.dept_id = d.dept_id " +
                    "WHERE 1=1" + af + df);
            double pctHs = rs3.next() ? rs3.getDouble(1) : 0;
            row.add(MainDashboard.buildKpiCard("Heures supplémentaires",
                    String.format("%.1f%%", pctHs),
                    pctHs > 30 ? "Élevé" : "Normal",
                    pctHs > 30 ? MainDashboard.C_DANGER : MainDashboard.C_SUCCESS));

            // Score évaluation moyen (1-5)
            ResultSet rs4 = query("SELECT ROUND(AVG(score_evaluation), 2) FROM fait_rh f " +
                    "JOIN dim_departement d ON f.dept_id = d.dept_id " +
                    "WHERE score_evaluation > 0" + af + df);
            double eval = rs4.next() ? rs4.getDouble(1) : 0;
            row.add(MainDashboard.buildKpiCard("Score évaluation moy.",
                    String.format("%.2f / 5", eval), null, null));

        } catch (Exception e) {
            System.err.println("[Performance] Erreur KPI : " + e.getMessage());
        }

        return row;
    }

    // ═══════════════════════════════════════════════════════════════════
    // GRAPHIQUES LIGNE 1 : Performance/dept + Satisfaction/dept
    // ═══════════════════════════════════════════════════════════════════

    private JPanel buildChartsRow1() {
        JPanel row = new JPanel(new GridBagLayout());
        row.setBackground(MainDashboard.C_BG);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 1.0;

        try {
            Map<String, Double> perf = DWRepository.getScorePerfMoyenParDept(annee, departement);
            DefaultCategoryDataset dsPerf = new DefaultCategoryDataset();
            perf.forEach((dept, score) -> dsPerf.addValue(score, "Score", dept));
            JFreeChart chartPerf = ChartFactory.createBarChart(
                    null, "Département", "Score (1-4)", dsPerf,
                    PlotOrientation.VERTICAL, false, true, false);
            styleBar(chartPerf, MainDashboard.C_PRIMARY);

            gbc.gridx = 0; gbc.weightx = 0.60; gbc.insets = new Insets(0, 0, 0, 6);
            row.add(MainDashboard.buildCard("Score performance moyen / département",
                    new ChartPanel(chartPerf)), gbc);

            Map<String, Double> satisf = DWRepository.getSatisfactionParDept(annee, departement);
            DefaultCategoryDataset dsSatisf = new DefaultCategoryDataset();
            satisf.forEach((dept, s) -> dsSatisf.addValue(s, "Satisfaction", dept));
            JFreeChart chartSatisf = ChartFactory.createLineChart(
                    null, "Département", "Score (1-4)", dsSatisf,
                    PlotOrientation.VERTICAL, false, true, false);
            styleLine(chartSatisf);

            gbc.gridx = 1; gbc.weightx = 0.40; gbc.insets = new Insets(0, 6, 0, 0);
            row.add(MainDashboard.buildCard("Satisfaction employés / département",
                    new ChartPanel(chartSatisf)), gbc);

        } catch (Exception e) {
            System.err.println("[Performance] Erreur graphiques ligne 1 : " + e.getMessage());
        }
        return row;
    }

    // ═══════════════════════════════════════════════════════════════════
    // GRAPHIQUES LIGNE 2 : Satisfaction↔Turnover + Évaluation semestrielle
    // ═══════════════════════════════════════════════════════════════════

    private JPanel buildChartsRow2() {
        JPanel row = new JPanel(new GridBagLayout());
        row.setBackground(MainDashboard.C_BG);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 1.0;

        try {
            String af = buildAnneeFilter();
            String df = buildDeptFilter();

            DefaultCategoryDataset dsCorrel = new DefaultCategoryDataset();
            ResultSet rs = query(
                    "SELECT d.nom_dept," +
                            " ROUND(AVG(f.satisfaction_employe), 2) AS satisf," +
                            " ROUND(SUM(f.attrition) * 100.0 / COUNT(*), 1) AS taux_attr" +
                            " FROM fait_rh f" +
                            " JOIN dim_departement d ON f.dept_id = d.dept_id" +
                            " WHERE f.satisfaction_employe > 0" + af + df + " GROUP BY d.nom_dept");
            while (rs.next()) {
                dsCorrel.addValue(rs.getDouble("satisf"),    "Satisfaction (1-4)", rs.getString(1));
                dsCorrel.addValue(rs.getDouble("taux_attr"), "Attrition (%)",      rs.getString(1));
            }
            JFreeChart chartCorrel = ChartFactory.createBarChart(
                    null, "Département", "Valeur", dsCorrel,
                    PlotOrientation.VERTICAL, true, true, false);
            styleGroupedBar(chartCorrel);

            gbc.gridx = 0; gbc.weightx = 0.50; gbc.insets = new Insets(0, 0, 0, 6);
            row.add(MainDashboard.buildCard("Corrélation satisfaction ↔ turnover",
                    new ChartPanel(chartCorrel)), gbc);

            DefaultCategoryDataset dsEval = new DefaultCategoryDataset();
            ResultSet rsEval = query("""
                SELECT t.annee || '-S' || t.semestre AS periode,
                       ROUND(AVG(f.score_evaluation), 2)
                FROM fait_rh f
                JOIN dim_departement d ON f.dept_id = d.dept_id
                JOIN dim_temps t ON f.temps_id = t.temps_id
                WHERE f.score_evaluation > 0
                """ + df + " GROUP BY t.annee, t.semestre ORDER BY t.annee, t.semestre");
            while (rsEval.next())
                dsEval.addValue(rsEval.getDouble(2), "Score éval.", rsEval.getString(1));

            JFreeChart chartEval = ChartFactory.createLineChart(
                    null, "Période", "Score (1-5)", dsEval,
                    PlotOrientation.VERTICAL, false, true, false);
            styleLine(chartEval);

            gbc.gridx = 1; gbc.weightx = 0.50; gbc.insets = new Insets(0, 6, 0, 0);
            row.add(MainDashboard.buildCard("Évolution score évaluation semestrielle",
                    new ChartPanel(chartEval)), gbc);

        } catch (Exception e) {
            System.err.println("[Performance] Erreur graphiques ligne 2 : " + e.getMessage());
        }
        return row;
    }

    // ═══════════════════════════════════════════════════════════════════
    // STYLES
    // ═══════════════════════════════════════════════════════════════════

    private void styleBar(JFreeChart chart, Color color) {
        chart.setBackgroundPaint(MainDashboard.C_CARD);
        chart.setBorderVisible(false);
        CategoryPlot plot = chart.getCategoryPlot();
        plot.setBackgroundPaint(MainDashboard.C_CARD);
        plot.setOutlineVisible(false);
        plot.setRangeGridlinePaint(MainDashboard.C_BORDER);
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

    private void styleGroupedBar(JFreeChart chart) {
        chart.setBackgroundPaint(MainDashboard.C_CARD);
        chart.setBorderVisible(false);
        CategoryPlot plot = chart.getCategoryPlot();
        plot.setBackgroundPaint(MainDashboard.C_CARD);
        plot.setOutlineVisible(false);
        plot.setRangeGridlinePaint(MainDashboard.C_BORDER);
        BarRenderer r = (BarRenderer) plot.getRenderer();
        r.setSeriesPaint(0, MainDashboard.C_SUCCESS);
        r.setSeriesPaint(1, MainDashboard.C_DANGER);
        r.setDrawBarOutline(false);
        r.setShadowVisible(false);
        plot.getDomainAxis().setTickLabelFont(new Font("Segoe UI", Font.PLAIN, 11));
        plot.getRangeAxis().setTickLabelFont(new Font("Segoe UI", Font.PLAIN, 11));
        plot.getDomainAxis().setAxisLineVisible(false);
        plot.getRangeAxis().setAxisLineVisible(false);
    }

    private void styleLine(JFreeChart chart) {
        chart.setBackgroundPaint(MainDashboard.C_CARD);
        chart.setBorderVisible(false);
        CategoryPlot plot = chart.getCategoryPlot();
        plot.setBackgroundPaint(MainDashboard.C_CARD);
        plot.setOutlineVisible(false);
        plot.setRangeGridlinePaint(MainDashboard.C_BORDER);
        LineAndShapeRenderer r = (LineAndShapeRenderer) plot.getRenderer();
        r.setSeriesPaint(0, MainDashboard.C_PRIMARY_LT);
        r.setSeriesStroke(0, new BasicStroke(2.5f));
        r.setDefaultShapesVisible(true);
        plot.getDomainAxis().setTickLabelFont(new Font("Segoe UI", Font.PLAIN, 11));
        plot.getRangeAxis().setTickLabelFont(new Font("Segoe UI", Font.PLAIN, 11));
        plot.getDomainAxis().setAxisLineVisible(false);
        plot.getRangeAxis().setAxisLineVisible(false);
    }

    private ResultSet query(String sql) throws SQLException {
        return DatabaseManager.getConnection().createStatement().executeQuery(sql);
    }

    // ═══════════════════════════════════════════════════════════════════
    // HELPERS FILTRES SQL
    // ═══════════════════════════════════════════════════════════════════

    private String buildAnneeFilter() {
        if (annee == null || annee.equals("Toutes")) return "";
        String an = annee.replaceAll("[^0-9]", "");
        return " AND f.annee_embauche <= " + an +
                " AND (f.annee_depart IS NULL OR f.annee_depart >= " + an + ")";
    }

    private String buildDeptFilter() {
        return (departement == null || departement.equals("Tous")) ? ""
                : " AND d.nom_dept = '" + departement.replace("'", "''") + "'";
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
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
import java.util.Map;

/**
 * TurnoverPanel — Section Turnover & Rétention.
 * Contenu :
 * – KPI cards : Taux attrition global, nb départs, durée moy. avant départ, taux rétention
 * – Graphique 1 : Taux d'attrition par département (barres + seuil rouge)
 * – Graphique 2 : Motifs de départ (camembert)
 * – Graphique 3 : Durée moyenne avant départ par département
 * – Graphique 4 : Heures supplémentaires vs attrition
 */
public class TurnoverPanel extends JPanel implements MainDashboard.Refreshable {

    private final MainDashboard dashboard;

    public TurnoverPanel(MainDashboard dashboard) {
        this.dashboard = dashboard;
        setBackground(MainDashboard.C_BG);
        setLayout(new BorderLayout());
        build("Toutes", "Tous");
    }

    private void build(String annee, String departement) {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(MainDashboard.C_BG);
        content.setBorder(new EmptyBorder(20, 20, 20, 20));

        content.add(buildKpiRow());
        content.add(Box.createVerticalStrut(16));
        content.add(buildChartsRow1());
        content.add(Box.createVerticalStrut(16));
        content.add(buildChartsRow2());

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);
    }

    // ═══════════════════════════════════════════════════════════════════
    // KPI CARDS
    // ═══════════════════════════════════════════════════════════════════

    private JPanel buildKpiRow() {
        JPanel row = new JPanel(new GridLayout(1, 4, 12, 0));
        row.setBackground(MainDashboard.C_BG);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 130));

        try {
            // Taux attrition global
            ResultSet rs1 = query("SELECT ROUND(SUM(attrition) * 100.0 / COUNT(*), 1) FROM fait_rh");
            double taux = rs1.next() ? rs1.getDouble(1) : 0;
            Color cTaux = taux > 15 ? MainDashboard.C_DANGER : taux > 8 ? MainDashboard.C_WARNING : MainDashboard.C_SUCCESS;
            row.add(MainDashboard.buildKpiCard("Taux d'attrition",
                    String.format("%.1f%%", taux),
                    taux > 15 ? "▲ Critique" : taux > 8 ? "~ Modéré" : "✓ Stable", cTaux));

            // Nombre total de départs
            ResultSet rs2 = query("SELECT SUM(attrition) FROM fait_rh");
            int departs = rs2.next() ? rs2.getInt(1) : 0;
            row.add(MainDashboard.buildKpiCard("Départs totaux",
                    String.valueOf(departs), null, null));

            // Durée moyenne avant départ (jours → années)
            ResultSet rs3 = query("SELECT ROUND(AVG(duree_avant_depart) / 365.0, 1) FROM fait_rh WHERE duree_avant_depart > 0");
            double duree = rs3.next() ? rs3.getDouble(1) : 0;
            row.add(MainDashboard.buildKpiCard("Durée moy. avant départ",
                    String.format("%.1f ans", duree), null, null));

            // Taux de rétention
            double retention = 100.0 - taux;
            row.add(MainDashboard.buildKpiCard("Taux de rétention",
                    String.format("%.1f%%", retention),
                    retention >= 90 ? "✓ Excellent" : retention >= 80 ? "~ Correct" : "▼ À améliorer",
                    retention >= 90 ? MainDashboard.C_SUCCESS : retention >= 80 ? MainDashboard.C_WARNING : MainDashboard.C_DANGER));

        } catch (Exception e) {
            System.err.println("[Turnover] Erreur KPI : " + e.getMessage());
        }

        return row;
    }

    // ═══════════════════════════════════════════════════════════════════
    // GRAPHIQUES LIGNE 1 : Attrition/dept + Motifs
    // ═══════════════════════════════════════════════════════════════════

    private JPanel buildChartsRow1() {
        JPanel row = new JPanel(new GridLayout(1, 2, 12, 0));
        row.setBackground(MainDashboard.C_BG);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 280));

        try {
            // Attrition par département
            Map<String, Double> attrition = DWRepository.getTauxAttritionParDept();
            DefaultCategoryDataset ds = new DefaultCategoryDataset();
            attrition.forEach((dept, taux) -> ds.addValue(taux, "Attrition (%)", dept));
            JFreeChart chart = ChartFactory.createBarChart(
                    null, "Département", "Taux (%)", ds,
                    PlotOrientation.VERTICAL, false, true, false);
            styleAttritionChart(chart);
            row.add(MainDashboard.buildCard("Taux d'attrition par département", new ChartPanel(chart)));

            // Motifs de départ
            Map<String, Integer> motifs = DWRepository.getMotifsDepart();
            DefaultPieDataset<String> dsPie = new DefaultPieDataset<>();
            motifs.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(7)
                    .forEach(e -> dsPie.setValue(e.getKey(), e.getValue()));
            JFreeChart chartPie = ChartFactory.createPieChart(null, dsPie, true, true, false);
            stylePie(chartPie);
            row.add(MainDashboard.buildCard("Motifs de départ", new ChartPanel(chartPie)));

        } catch (Exception e) {
            System.err.println("[Turnover] Erreur graphiques ligne 1 : " + e.getMessage());
        }

        return row;
    }

    // ═══════════════════════════════════════════════════════════════════
    // GRAPHIQUES LIGNE 2 : Durée avant départ + Heures sup vs Attrition
    // ═══════════════════════════════════════════════════════════════════

    private JPanel buildChartsRow2() {
        JPanel row = new JPanel(new GridLayout(1, 2, 12, 0));
        row.setBackground(MainDashboard.C_BG);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 280));

        try {
            // Durée moyenne avant départ par département
            DefaultCategoryDataset dsDepart = new DefaultCategoryDataset();
            ResultSet rs = query("""
                SELECT d.nom_dept, ROUND(AVG(f.duree_avant_depart) / 365.0, 1)
                FROM fait_rh f
                JOIN dim_departement d ON f.dept_id = d.dept_id
                WHERE f.duree_avant_depart > 0
                GROUP BY d.nom_dept ORDER BY AVG(f.duree_avant_depart) ASC
            """);
            while (rs.next()) dsDepart.addValue(rs.getDouble(2), "Années", rs.getString(1));

            JFreeChart chartDepart = ChartFactory.createBarChart(
                    null, "Département", "Années", dsDepart,
                    PlotOrientation.HORIZONTAL, false, true, false);
            styleBar(chartDepart, MainDashboard.C_WARNING);
            row.add(MainDashboard.buildCard("Durée moyenne avant départ", new ChartPanel(chartDepart)));

            // Heures sup vs Attrition (barres groupées)
            DefaultCategoryDataset dsHs = new DefaultCategoryDataset();
            ResultSet rsHs = query("""
                SELECT d.nom_dept,
                       ROUND(SUM(CASE WHEN f.heures_sup = 1 AND f.attrition = 1 THEN 1 ELSE 0 END) * 100.0
                             / NULLIF(SUM(CASE WHEN f.heures_sup = 1 THEN 1 ELSE 0 END), 0), 1) AS attr_hs,
                       ROUND(SUM(CASE WHEN f.heures_sup = 0 AND f.attrition = 1 THEN 1 ELSE 0 END) * 100.0
                             / NULLIF(SUM(CASE WHEN f.heures_sup = 0 THEN 1 ELSE 0 END), 0), 1) AS attr_no_hs
                FROM fait_rh f
                JOIN dim_departement d ON f.dept_id = d.dept_id
                GROUP BY d.nom_dept
            """);
            while (rsHs.next()) {
                dsHs.addValue(rsHs.getDouble("attr_hs"),    "Avec heures sup",  rsHs.getString(1));
                dsHs.addValue(rsHs.getDouble("attr_no_hs"), "Sans heures sup",  rsHs.getString(1));
            }

            JFreeChart chartHs = ChartFactory.createBarChart(
                    null, "Département", "Attrition (%)", dsHs,
                    PlotOrientation.VERTICAL, true, true, false);
            styleGroupedBar(chartHs);
            row.add(MainDashboard.buildCard("Heures supplémentaires vs attrition", new ChartPanel(chartHs)));

        } catch (Exception e) {
            System.err.println("[Turnover] Erreur graphiques ligne 2 : " + e.getMessage());
        }

        return row;
    }

    // ═══════════════════════════════════════════════════════════════════
    // STYLES
    // ═══════════════════════════════════════════════════════════════════

    private void styleAttritionChart(JFreeChart chart) {
        chart.setBackgroundPaint(MainDashboard.C_CARD);
        chart.setBorderVisible(false);
        CategoryPlot plot = chart.getCategoryPlot();
        plot.setBackgroundPaint(MainDashboard.C_CARD);
        plot.setOutlineVisible(false);
        plot.setRangeGridlinePaint(MainDashboard.C_BORDER);
        BarRenderer r = (BarRenderer) plot.getRenderer();
        r.setDrawBarOutline(false);
        r.setShadowVisible(false);
        r.setMaximumBarWidth(0.5);
        // Colorer les barres selon le seuil
        for (int i = 0; i < chart.getCategoryPlot().getDataset().getColumnCount(); i++) {
            double val = chart.getCategoryPlot().getDataset().getValue(0, i).doubleValue();
            r.setSeriesPaint(0, val > 15 ? MainDashboard.C_DANGER :
                    val > 8 ? MainDashboard.C_WARNING : MainDashboard.C_SUCCESS);
        }
        plot.getDomainAxis().setTickLabelFont(new Font("Segoe UI", Font.PLAIN, 11));
        plot.getRangeAxis().setTickLabelFont(new Font("Segoe UI", Font.PLAIN, 11));
        plot.getDomainAxis().setAxisLineVisible(false);
        plot.getRangeAxis().setAxisLineVisible(false);
    }

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
        r.setSeriesPaint(0, MainDashboard.C_DANGER);
        r.setSeriesPaint(1, MainDashboard.C_PRIMARY);
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
        plot.setLabelFont(new Font("Segoe UI", Font.PLAIN, 10));
        plot.setShadowPaint(null);
    }

    private ResultSet query(String sql) throws SQLException {
        return DatabaseManager.getConnection().createStatement().executeQuery(sql);
    }

    @Override
    public void refresh(String annee, String departement) {
        removeAll();
        build(annee, departement);
        revalidate();
        repaint();
    }
}
package com.hrreporting.ui;

import com.hrreporting.db.DatabaseManager;
import com.hrreporting.db.DWRepository;
import org.jfree.chart.*;
import org.jfree.chart.plot.*;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.category.LineAndShapeRenderer;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.general.DefaultPieDataset;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.sql.*;
import java.util.*;

/**
 * PromotionsPanel — Section Promotions du dashboard RH.
 * Contenu :
 * – KPI cards : Nb candidats promus, % promotion, Score perf moyen, % objectifs atteints
 * – Graphique 1 : Candidats à la promotion par département (barres verticales)
 * – Graphique 2 : Score performance des promouvables vs reste (barres groupées)
 * – Graphique 3 : % objectifs atteints par département (ligne)
 * – Graphique 4 : Répartition promouvables par genre (camembert)
 * Filtres année/département appliqués sur tous les KPI et graphiques.
 */
public class PromotionsPanel extends JPanel implements MainDashboard.Refreshable {

    private final MainDashboard dashboard;

    public PromotionsPanel(MainDashboard dashboard) {
        this.dashboard = dashboard;
        setBackground(MainDashboard.C_BG);
        setLayout(new BorderLayout());
        build("Toutes", "Tous");
    }

    private void build(String annee, String departement) {
        setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(8, 10, 8, 10);
        gbc.weightx = 1.0;
        gbc.gridx = 0;

        gbc.gridy = 0; gbc.weighty = 0.08;
        add(buildKpiRow(annee, departement), gbc);

        gbc.gridy = 1; gbc.weighty = 0.46;
        add(buildChartsRow1(annee, departement), gbc);

        gbc.gridy = 2; gbc.weighty = 0.46;
        add(buildChartsRow2(annee, departement), gbc);
    }

    // ═══════════════════════════════════════════════════════════════════
    // KPI CARDS
    // ═══════════════════════════════════════════════════════════════════

    private JPanel buildKpiRow(String annee, String departement) {
        JPanel row = new JPanel(new GridLayout(1, 4, 12, 0));
        row.setBackground(MainDashboard.C_BG);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 130));

        try {
            String af = buildAnneeFilter(annee);
            String df = buildDeptFilter(departement);

            // Total candidats à la promotion
            Map<String, Integer> candidats = DWRepository.getCandidatsPromotion(annee, departement);
            int totalCandidats = candidats.values().stream().mapToInt(Integer::intValue).sum();
            row.add(MainDashboard.buildKpiCard("Candidats promotion",
                    String.format("%,d", totalCandidats), "Éligibles", MainDashboard.C_PRIMARY));

            // % de l'effectif total recommandé à la promotion
            int totalEffectif = DWRepository.getEffectifTotal(annee, departement);
            double pctPromo = totalEffectif > 0 ? totalCandidats * 100.0 / totalEffectif : 0;
            String badgePromo = pctPromo >= 10 ? "Actif" : pctPromo >= 5 ? "Modéré" : "Faible";
            Color  colorPromo = pctPromo >= 10 ? MainDashboard.C_SUCCESS
                    : pctPromo >= 5  ? MainDashboard.C_WARNING
                      : MainDashboard.C_DANGER;
            row.add(MainDashboard.buildKpiCard("% promouvables",
                    String.format("%.1f%%", pctPromo), badgePromo, colorPromo));

            // Score performance moyen des candidats
            ResultSet rsPerf = query("SELECT ROUND(AVG(f.score_performance), 2) FROM fait_rh f " +
                    "JOIN dim_departement d ON f.dept_id = d.dept_id " +
                    "JOIN dim_temps t ON f.temps_id = t.temps_id " +
                    "WHERE f.promotion_recommandee = 1 AND f.score_performance > 0" + af + df);
            double scoreMoyen = rsPerf.next() ? rsPerf.getDouble(1) : 0;
            row.add(MainDashboard.buildKpiCard("Score perf. moyen",
                    String.format("%.2f / 5", scoreMoyen), null, null));

            // % objectifs atteints moyen des candidats
            ResultSet rsObj = query("SELECT ROUND(AVG(f.objectifs_atteints_pct), 1) FROM fait_rh f " +
                    "JOIN dim_departement d ON f.dept_id = d.dept_id " +
                    "JOIN dim_temps t ON f.temps_id = t.temps_id " +
                    "WHERE f.promotion_recommandee = 1 AND f.objectifs_atteints_pct >= 0" + af + df);
            double objMoyen = rsObj.next() ? rsObj.getDouble(1) : 0;
            String badgeObj = objMoyen >= 80 ? "Excellent" : objMoyen >= 60 ? "Correct" : "Insuffisant";
            Color  colorObj  = objMoyen >= 80 ? MainDashboard.C_SUCCESS
                    : objMoyen >= 60 ? MainDashboard.C_WARNING
                      : MainDashboard.C_DANGER;
            row.add(MainDashboard.buildKpiCard("Objectifs atteints moy.",
                    String.format("%.1f%%", objMoyen), badgeObj, colorObj));

        } catch (Exception e) {
            System.err.println("[Promotions] Erreur KPI : " + e.getMessage());
        }

        return row;
    }

    // ═══════════════════════════════════════════════════════════════════
    // GRAPHIQUES LIGNE 1 : Candidats/dept + Perf promouvables vs autres
    // ═══════════════════════════════════════════════════════════════════

    private JPanel buildChartsRow1(String annee, String departement) {
        JPanel row = new JPanel(new GridBagLayout());
        row.setBackground(MainDashboard.C_BG);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 1.0;

        try {
            String af = buildAnneeFilter(annee);
            String df = buildDeptFilter(departement);

            Map<String, Integer> candidats = DWRepository.getCandidatsPromotion(annee, departement);
            DefaultCategoryDataset dsCand = new DefaultCategoryDataset();
            candidats.forEach((dept, nb) -> dsCand.addValue(nb, "Candidats", dept));
            JFreeChart chartCand = ChartFactory.createBarChart(
                    null, "Département", "Candidats", dsCand,
                    PlotOrientation.VERTICAL, false, true, false);
            styleBar(chartCand, MainDashboard.C_PRIMARY);

            gbc.gridx = 0; gbc.weightx = 0.55; gbc.insets = new Insets(0, 0, 0, 6);
            row.add(MainDashboard.buildCard("Candidats à la promotion / département",
                    new ChartPanel(chartCand)), gbc);

            DefaultCategoryDataset dsPerf = new DefaultCategoryDataset();
            ResultSet rsPerf = query("""
                SELECT d.nom_dept,
                       ROUND(AVG(CASE WHEN f.promotion_recommandee = 1 THEN f.score_performance END), 2) AS score_promo,
                       ROUND(AVG(CASE WHEN f.promotion_recommandee = 0 THEN f.score_performance END), 2) AS score_autres
                FROM fait_rh f
                JOIN dim_departement d ON f.dept_id = d.dept_id
                JOIN dim_temps t ON f.temps_id = t.temps_id
                WHERE f.score_performance IS NOT NULL AND f.score_performance > 0
                """ + af + df + " GROUP BY d.nom_dept ORDER BY d.nom_dept");
            while (rsPerf.next()) {
                String dept = rsPerf.getString("nom_dept");
                dsPerf.addValue(rsPerf.getDouble("score_promo"),  "Promouvables", dept);
                dsPerf.addValue(rsPerf.getDouble("score_autres"), "Autres",       dept);
            }
            JFreeChart chartPerf = ChartFactory.createBarChart(
                    null, "Département", "Score", dsPerf,
                    PlotOrientation.VERTICAL, true, true, false);
            stylePerfGrouped(chartPerf);

            gbc.gridx = 1; gbc.weightx = 0.45; gbc.insets = new Insets(0, 6, 0, 0);
            row.add(MainDashboard.buildCard("Score performance : promouvables vs autres",
                    new ChartPanel(chartPerf)), gbc);

        } catch (Exception e) {
            System.err.println("[Promotions] Erreur graphiques ligne 1 : " + e.getMessage());
        }
        return row;
    }

    // ═══════════════════════════════════════════════════════════════════
    // GRAPHIQUES LIGNE 2 : % objectifs/dept (ligne) + Genre promouvables (pie)
    // ═══════════════════════════════════════════════════════════════════

    private JPanel buildChartsRow2(String annee, String departement) {
        JPanel row = new JPanel(new GridBagLayout());
        row.setBackground(MainDashboard.C_BG);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 1.0;

        try {
            String af = buildAnneeFilter(annee);
            String df = buildDeptFilter(departement);

            DefaultCategoryDataset dsObj = new DefaultCategoryDataset();
            ResultSet rsObj = query("""
                SELECT d.nom_dept, ROUND(AVG(f.objectifs_atteints_pct), 1) AS moy_obj
                FROM fait_rh f
                JOIN dim_departement d ON f.dept_id = d.dept_id
                JOIN dim_temps t ON f.temps_id = t.temps_id
                WHERE f.objectifs_atteints_pct IS NOT NULL AND f.objectifs_atteints_pct >= 0
                """ + af + df + " GROUP BY d.nom_dept ORDER BY moy_obj DESC");
            while (rsObj.next())
                dsObj.addValue(rsObj.getDouble("moy_obj"), "% objectifs", rsObj.getString("nom_dept"));
            JFreeChart chartObj = ChartFactory.createLineChart(
                    null, "Département", "% objectifs atteints", dsObj,
                    PlotOrientation.VERTICAL, false, true, false);
            styleLine(chartObj, MainDashboard.C_SUCCESS);

            gbc.gridx = 0; gbc.weightx = 0.60; gbc.insets = new Insets(0, 0, 0, 6);
            row.add(MainDashboard.buildCard("% objectifs atteints / département",
                    new ChartPanel(chartObj)), gbc);

            DefaultPieDataset<String> dsGenre = new DefaultPieDataset<>();
            ResultSet rsGenre = query("""
                SELECT e.genre, COUNT(*) AS nb
                FROM fait_rh f
                JOIN dim_employe e ON f.employe_id = e.employe_id
                JOIN dim_departement d ON f.dept_id = d.dept_id
                JOIN dim_temps t ON f.temps_id = t.temps_id
                WHERE f.promotion_recommandee = 1 AND e.genre IS NOT NULL
                """ + af + df + " GROUP BY e.genre");
            while (rsGenre.next())
                dsGenre.setValue(rsGenre.getString("genre"), rsGenre.getInt("nb"));
            JFreeChart chartGenre = ChartFactory.createPieChart(
                    null, dsGenre, true, true, false);
            stylePie(chartGenre);

            gbc.gridx = 1; gbc.weightx = 0.40; gbc.insets = new Insets(0, 6, 0, 0);
            row.add(MainDashboard.buildCard("Genre des candidats à la promotion",
                    new ChartPanel(chartGenre)), gbc);

        } catch (Exception e) {
            System.err.println("[Promotions] Erreur graphiques ligne 2 : " + e.getMessage());
        }
        return row;
    }

    // ═══════════════════════════════════════════════════════════════════
    // UTILITAIRES
    // ═══════════════════════════════════════════════════════════════════

    private String buildAnneeFilter() {
        if (annee == null || annee.equals("Toutes")) return "";
        String an = annee.replaceAll("[^0-9]", "");
        return " AND f.annee_embauche <= " + an +
                " AND (f.annee_depart IS NULL OR f.annee_depart >= " + an + ")";
    }

    private String buildDeptFilter(String departement) {
        return (departement == null || departement.equals("Tous")) ? ""
                : " AND d.nom_dept = '" + departement.replace("'", "''") + "'";
    }

    private ResultSet query(String sql) throws SQLException {
        return DatabaseManager.getConnection().createStatement().executeQuery(sql);
    }

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

    private void stylePerfGrouped(JFreeChart chart) {
        chart.setBackgroundPaint(MainDashboard.C_CARD);
        chart.setBorderVisible(false);
        CategoryPlot plot = chart.getCategoryPlot();
        plot.setBackgroundPaint(MainDashboard.C_CARD);
        plot.setOutlineVisible(false);
        plot.setRangeGridlinePaint(MainDashboard.C_BORDER);
        plot.setDomainGridlinesVisible(false);
        BarRenderer r = (BarRenderer) plot.getRenderer();
        r.setSeriesPaint(0, MainDashboard.C_SUCCESS);
        r.setSeriesPaint(1, MainDashboard.C_BORDER);
        r.setDrawBarOutline(false);
        r.setShadowVisible(false);
        plot.getDomainAxis().setTickLabelFont(new Font("Segoe UI", Font.PLAIN, 11));
        plot.getRangeAxis().setTickLabelFont(new Font("Segoe UI", Font.PLAIN, 11));
        plot.getDomainAxis().setAxisLineVisible(false);
        plot.getRangeAxis().setAxisLineVisible(false);
        if (chart.getLegend() != null)
            chart.getLegend().setBackgroundPaint(MainDashboard.C_CARD);
    }

    private void styleLine(JFreeChart chart, Color color) {
        chart.setBackgroundPaint(MainDashboard.C_CARD);
        chart.setBorderVisible(false);
        CategoryPlot plot = chart.getCategoryPlot();
        plot.setBackgroundPaint(MainDashboard.C_CARD);
        plot.setOutlineVisible(false);
        plot.setRangeGridlinePaint(MainDashboard.C_BORDER);
        LineAndShapeRenderer r = (LineAndShapeRenderer) plot.getRenderer();
        r.setSeriesPaint(0, color);
        r.setSeriesStroke(0, new BasicStroke(2.5f));
        r.setDefaultShapesVisible(true);
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
        plot.setShadowPaint(null);
        plot.setLabelFont(new Font("Segoe UI", Font.PLAIN, 11));
        plot.setSectionPaint("M", MainDashboard.C_PRIMARY);
        plot.setSectionPaint("F", MainDashboard.C_DANGER);
        plot.setSectionPaint("N/A", MainDashboard.C_BORDER);
    }

    @Override
    public void refresh(String annee, String departement) {
        removeAll();
        build(annee, departement);
        revalidate();
        repaint();
    }
}
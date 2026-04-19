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
 * FormationPanel — Section Formation du dashboard RH.
 * Contenu :
 * – KPI cards : Nb formations, Coût total, Coût moyen/employé, % employés formés
 * – Graphique 1 : Coût total formation par département (barres verticales)
 * – Graphique 2 : Nombre de formations par intitulé (barres horizontales, top 8)
 * – Graphique 3 : Nb formations moyen par département (ligne)
 * – Graphique 4 : Répartition formations par durée (camembert)
 */
public class FormationPanel extends JPanel implements MainDashboard.Refreshable {

    private final MainDashboard dashboard;

    public FormationPanel(MainDashboard dashboard) {
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

        content.add(buildKpiRow(departement));
        content.add(Box.createVerticalStrut(16));
        content.add(buildChartsRow1(departement));
        content.add(Box.createVerticalStrut(16));
        content.add(buildChartsRow2(departement));

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);
    }

    // ═══════════════════════════════════════════════════════════════════
    // KPI CARDS
    // ═══════════════════════════════════════════════════════════════════

    private JPanel buildKpiRow(String departement) {
        JPanel row = new JPanel(new GridLayout(1, 4, 12, 0));
        row.setBackground(MainDashboard.C_BG);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 130));

        try {
            String joinDept = departement.equals("Tous") ? "" :
                    " JOIN dim_departement d ON f.dept_id = d.dept_id WHERE d.nom_dept = '" + departement + "'";
            String whereDept = departement.equals("Tous") ? " WHERE " : " AND ";

            // Nb total de formations enregistrées
            ResultSet rs1 = query("SELECT COALESCE(SUM(f.nb_formations), 0) FROM fait_rh f" + joinDept);
            int nbFormations = rs1.next() ? rs1.getInt(1) : 0;
            row.add(MainDashboard.buildKpiCard("Formations réalisées",
                    String.format("%,d", nbFormations), null, null));

            // Coût total
            Map<String, Double> couts = DWRepository.getCoutFormationParDept();
            double coutTotal = couts.values().stream().mapToDouble(Double::doubleValue).sum();
            row.add(MainDashboard.buildKpiCard("Coût total formation",
                    String.format("$%,.0f", coutTotal), null, MainDashboard.C_WARNING));

            // Coût moyen par employé formé
            ResultSet rs3 = query("""
                SELECT ROUND(AVG(f.cout_formation), 0)
                FROM fait_rh f
                WHERE f.cout_formation IS NOT NULL AND f.cout_formation > 0
                  AND f.nb_formations IS NOT NULL AND f.nb_formations > 0
            """);
            double coutMoyen = rs3.next() ? rs3.getDouble(1) : 0;
            row.add(MainDashboard.buildKpiCard("Coût moyen / employé",
                    String.format("$%,.0f", coutMoyen), null, null));

            // % employés ayant au moins une formation
            ResultSet rs4 = query("""
                SELECT
                    ROUND(
                        SUM(CASE WHEN f.nb_formations > 0 THEN 1 ELSE 0 END) * 100.0 / COUNT(*),
                        1
                    )
                FROM fait_rh f
            """);
            double pctFormes = rs4.next() ? rs4.getDouble(1) : 0;
            String badge = pctFormes >= 70 ? "✓ Bon" : pctFormes >= 40 ? "~ Moyen" : "▼ Faible";
            Color color  = pctFormes >= 70 ? MainDashboard.C_SUCCESS
                    : pctFormes >= 40 ? MainDashboard.C_WARNING
                      : MainDashboard.C_DANGER;
            row.add(MainDashboard.buildKpiCard("% employés formés",
                    String.format("%.1f%%", pctFormes), badge, color));

        } catch (Exception e) {
            System.err.println("[Formation] Erreur KPI : " + e.getMessage());
        }

        return row;
    }

    // ═══════════════════════════════════════════════════════════════════
    // GRAPHIQUES LIGNE 1 : Coût/dept + Top formations
    // ═══════════════════════════════════════════════════════════════════

    private JPanel buildChartsRow1(String departement) {
        JPanel row = new JPanel(new GridLayout(1, 2, 12, 0));
        row.setBackground(MainDashboard.C_BG);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 280));

        try {
            // Coût total par département
            Map<String, Double> couts = DWRepository.getCoutFormationParDept();
            DefaultCategoryDataset dsCout = new DefaultCategoryDataset();
            couts.forEach((dept, c) -> dsCout.addValue(c, "Coût ($)", dept));
            JFreeChart chartCout = ChartFactory.createBarChart(
                    null, "Département", "Coût ($)", dsCout,
                    PlotOrientation.VERTICAL, false, true, false);
            styleBar(chartCout, MainDashboard.C_WARNING);
            row.add(MainDashboard.buildCard("Coût total formation / département",
                    new ChartPanel(chartCout)));

            // Top 8 formations par fréquence
            DefaultCategoryDataset dsTop = new DefaultCategoryDataset();
            ResultSet rsTop = query("""
                SELECT df.intitule, COUNT(*) AS nb
                FROM fait_rh f
                JOIN dim_formation df ON f.formation_id = df.formation_id
                WHERE f.formation_id IS NOT NULL
                GROUP BY df.intitule
                ORDER BY nb DESC
                LIMIT 8
            """);
            while (rsTop.next())
                dsTop.addValue(rsTop.getInt("nb"), "Nb employés", rsTop.getString("intitule"));

            JFreeChart chartTop = ChartFactory.createBarChart(
                    null, null, "Employés", dsTop,
                    PlotOrientation.HORIZONTAL, false, true, false);
            styleBar(chartTop, MainDashboard.C_PRIMARY);
            row.add(MainDashboard.buildCard("Top formations (fréquence)", new ChartPanel(chartTop)));

        } catch (Exception e) {
            System.err.println("[Formation] Erreur graphiques ligne 1 : " + e.getMessage());
        }

        return row;
    }

    // ═══════════════════════════════════════════════════════════════════
    // GRAPHIQUES LIGNE 2 : Nb formations/dept (ligne) + Durée (camembert)
    // ═══════════════════════════════════════════════════════════════════

    private JPanel buildChartsRow2(String departement) {
        JPanel row = new JPanel(new GridLayout(1, 2, 12, 0));
        row.setBackground(MainDashboard.C_BG);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 280));

        try {
            // Nb moyen de formations par employé, par département
            DefaultCategoryDataset dsNb = new DefaultCategoryDataset();
            ResultSet rsNb = query("""
                SELECT d.nom_dept, ROUND(AVG(f.nb_formations), 2) AS moy
                FROM fait_rh f
                JOIN dim_departement d ON f.dept_id = d.dept_id
                WHERE f.nb_formations IS NOT NULL AND f.nb_formations >= 0
                GROUP BY d.nom_dept
                ORDER BY moy DESC
            """);
            while (rsNb.next())
                dsNb.addValue(rsNb.getDouble("moy"), "Formations/employé", rsNb.getString("nom_dept"));

            JFreeChart chartNb = ChartFactory.createLineChart(
                    null, "Département", "Moy. formations", dsNb,
                    PlotOrientation.VERTICAL, false, true, false);
            styleLine(chartNb, MainDashboard.C_SUCCESS);
            row.add(MainDashboard.buildCard("Nb moyen formations / département",
                    new ChartPanel(chartNb)));

            // Répartition par durée (tranches de jours)
            DefaultPieDataset<String> dsDuree = new DefaultPieDataset<>();
            String[] labels    = {"1 jour", "2-3 jours", "4-5 jours", "6+ jours"};
            String[] conditions = {
                    "df.duree_jours = 1",
                    "df.duree_jours BETWEEN 2 AND 3",
                    "df.duree_jours BETWEEN 4 AND 5",
                    "df.duree_jours >= 6"
            };
            Color[] pieColors = {
                    MainDashboard.C_PRIMARY,
                    MainDashboard.C_SUCCESS,
                    MainDashboard.C_WARNING,
                    MainDashboard.C_DANGER
            };
            for (int i = 0; i < labels.length; i++) {
                ResultSet rsD = query(
                        "SELECT COUNT(*) FROM fait_rh f " +
                                "JOIN dim_formation df ON f.formation_id = df.formation_id " +
                                "WHERE " + conditions[i]);
                dsDuree.setValue(labels[i], rsD.next() ? rsD.getInt(1) : 0);
            }
            JFreeChart chartDuree = ChartFactory.createPieChart(
                    null, dsDuree, true, true, false);
            stylePie(chartDuree, labels, pieColors);
            row.add(MainDashboard.buildCard("Répartition par durée de formation",
                    new ChartPanel(chartDuree)));

        } catch (Exception e) {
            System.err.println("[Formation] Erreur graphiques ligne 2 : " + e.getMessage());
        }

        return row;
    }

    // ═══════════════════════════════════════════════════════════════════
    // UTILITAIRES
    // ═══════════════════════════════════════════════════════════════════

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

    private void stylePie(JFreeChart chart, String[] labels, Color[] colors) {
        chart.setBackgroundPaint(MainDashboard.C_CARD);
        chart.setBorderVisible(false);
        PiePlot<?> plot = (PiePlot<?>) chart.getPlot();
        plot.setBackgroundPaint(MainDashboard.C_CARD);
        plot.setOutlineVisible(false);
        plot.setShadowPaint(null);
        plot.setLabelFont(new Font("Segoe UI", Font.PLAIN, 11));
        for (int i = 0; i < labels.length; i++)
            plot.setSectionPaint(labels[i], colors[i]);
    }

    @Override
    public void refresh(String annee, String departement) {
        removeAll();
        build(annee, departement);
        revalidate();
        repaint();
    }
}
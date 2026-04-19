package com.hrreporting.ui;

import com.hrreporting.db.DWRepository;
import org.jfree.chart.*;
import org.jfree.chart.plot.*;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.general.DefaultPieDataset;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.Map;

/**
 * DashboardPanel — Vue globale du reporting RH.
 * Contenu :
 *   - 4 KPI cards : Attrition, Effectif, Salaire moyen, Satisfaction
 *   - 3 graphiques : Effectif/dept, Salaire/dept, Satisfaction/dept
 *   - Section Insights : Risques, Opportunités, Recommandations
 */
public class DashboardPanel extends JPanel implements MainDashboard.Refreshable {

    public DashboardPanel(MainDashboard dashboard) {
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

        // Ligne KPI — 10% de la hauteur
        gbc.gridy = 0;
        gbc.weighty = 0.10;
        add(buildKpiRow(), gbc);

        // Ligne charts 1 - 70%
        gbc.gridy = 1;
        gbc.weighty = 0.70;
        add(buildChartsRow(), gbc);

        // Ligne insights — 20%
        gbc.gridy = 2;
        gbc.weighty = 0.20;
        add(buildInsightsRow(), gbc);
    }

    // ═══════════════════════════════════════════════════════════════════
    // KPI CARDS
    // ═══════════════════════════════════════════════════════════════════

    private JPanel buildKpiRow() {
        JPanel row = new JPanel(new GridLayout(1, 4, 12, 0));
        row.setBackground(MainDashboard.C_BG);

        try {
            // Attrition
            Map<String, Double> attrition = DWRepository.getTauxAttritionParDept();
            double tauxMoyen = attrition.values().stream()
                    .mapToDouble(Double::doubleValue).average().orElse(0);
            String badgeAttrition = tauxMoyen > 15 ? "Risque élevé" : tauxMoyen > 8 ? "Modéré" : "Stable";
            Color  colorAttrition = tauxMoyen > 15 ? MainDashboard.C_DANGER
                    : tauxMoyen > 8  ? MainDashboard.C_WARNING
                      : MainDashboard.C_SUCCESS;
            row.add(MainDashboard.buildKpiCard("Taux d'attrition",
                    String.format("%.1f%%", tauxMoyen), badgeAttrition, colorAttrition));

            // Effectif
            Map<String, Integer> effectifs = DWRepository.getEffectifParDept();
            int totalEffectif = effectifs.values().stream().mapToInt(Integer::intValue).sum();
            row.add(MainDashboard.buildKpiCard("Effectif total",
                    String.format("%,d", totalEffectif) + " emp.", "Actifs", MainDashboard.C_PRIMARY));

            // Salaire moyen
            Map<String, Double> salaires = DWRepository.getSalaireMoyenParDept();
            double salaireMoyen = salaires.values().stream()
                    .mapToDouble(Double::doubleValue).average().orElse(0);
            row.add(MainDashboard.buildKpiCard("Salaire moyen",
                    String.format("$%,.0f", salaireMoyen), "+1.2% vs N-1", MainDashboard.C_WARNING));

            // Satisfaction
            Map<String, Double> satisfaction = DWRepository.getSatisfactionParDept();
            double satisfMoyenne = satisfaction.values().stream()
                    .mapToDouble(Double::doubleValue).average().orElse(0);
            String badgeSatisf = satisfMoyenne >= 3.5 ? "Bon" : satisfMoyenne >= 2.5 ? "Moyen" : "Faible";
            Color  colorSatisf = satisfMoyenne >= 3.5 ? MainDashboard.C_SUCCESS
                    : satisfMoyenne >= 2.5 ? MainDashboard.C_WARNING
                      : MainDashboard.C_DANGER;
            row.add(MainDashboard.buildKpiCard("Satisfaction moy.",
                    String.format("%.1f / 4", satisfMoyenne), badgeSatisf, colorSatisf));

        } catch (Exception e) {
            System.err.println("[Dashboard] Erreur KPI : " + e.getMessage());
        }

        return row;
    }

    // ═══════════════════════════════════════════════════════════════════
    // GRAPHIQUES
    // ═══════════════════════════════════════════════════════════════════

    private JPanel buildChartsRow() {
        JPanel row = new JPanel(new GridLayout(1, 3, 12, 0));
        row.setBackground(MainDashboard.C_BG);

        try {
            // Graphique 1 : Effectif par département (barres horizontales)
            Map<String, Integer> effectifs = DWRepository.getEffectifParDept();
            DefaultCategoryDataset dsEffectif = new DefaultCategoryDataset();
            effectifs.forEach((dept, nb) -> dsEffectif.addValue(nb, "Effectif", dept));

            JFreeChart chartEffectif = ChartFactory.createBarChart(
                    null, null, null, dsEffectif,
                    PlotOrientation.HORIZONTAL, false, true, false);
            styleBarChart(chartEffectif, MainDashboard.C_PRIMARY);
            row.add(MainDashboard.buildCard("Effectif par département",
                    new ChartPanel(chartEffectif)));

            // Graphique 2 : Salaire moyen par département (barres horizontales)
            Map<String, Double> salaires = DWRepository.getSalaireMoyenParDept();
            DefaultCategoryDataset dsSalaire = new DefaultCategoryDataset();
            salaires.forEach((dept, sal) -> dsSalaire.addValue(sal, "Salaire", dept));

            JFreeChart chartSalaire = ChartFactory.createBarChart(
                    null, null, null, dsSalaire,
                    PlotOrientation.HORIZONTAL, false, true, false);
            styleBarChart(chartSalaire, MainDashboard.C_DANGER);
            row.add(MainDashboard.buildCard("Salaire moyen / dept.",
                    new ChartPanel(chartSalaire)));

            // Graphique 3 : Satisfaction par département (courbe)
            Map<String, Double> satisfaction = DWRepository.getSatisfactionParDept();
            DefaultCategoryDataset dsSatisf = new DefaultCategoryDataset();
            satisfaction.forEach((dept, s) -> dsSatisf.addValue(s, "Satisfaction", dept));

            JFreeChart chartSatisf = ChartFactory.createLineChart(
                    null, null, null, dsSatisf,
                    PlotOrientation.VERTICAL, false, true, false);
            styleLineChart(chartSatisf);
            row.add(MainDashboard.buildCard("Satisfaction employés",
                    new ChartPanel(chartSatisf)));

        } catch (Exception e) {
            System.err.println("[Dashboard] Erreur graphiques : " + e.getMessage());
        }

        return row;
    }

    // ═══════════════════════════════════════════════════════════════════
    // INSIGHTS
    // ═══════════════════════════════════════════════════════════════════

    private JPanel buildInsightsRow() {
        JPanel wrapper = new JPanel(new BorderLayout(0, 8));
        wrapper.setBackground(MainDashboard.C_BG);

        JLabel title = new JLabel("Insights RH — Aide à la décision");
        title.setFont(new Font("Segoe UI", Font.BOLD, 15));
        title.setForeground(MainDashboard.C_TEXT);
        wrapper.add(title, BorderLayout.NORTH);

        JPanel row = new JPanel(new GridLayout(1, 3, 12, 0));
        row.setBackground(MainDashboard.C_BG);

        try {
            Map<String, Double> attrition   = DWRepository.getTauxAttritionParDept();
            Map<String, Double> satisfaction = DWRepository.getSatisfactionParDept();
            Map<String, Double> salaires     = DWRepository.getSalaireMoyenParDept();
            Map<String, Integer> promotions  = DWRepository.getCandidatsPromotion();

            // Risques
            StringBuilder risques = new StringBuilder("<html><b style='color:#E24B4A'>Risques critiques</b><br><br>");
            attrition.forEach((dept, taux) -> {
                if (taux > 15) risques.append("● <b>").append(dept).append("</b> : attrition ").append(taux).append("%<br>");
            });
            satisfaction.forEach((dept, s) -> {
                if (s < 2.5) risques.append("● <b>").append(dept).append("</b> : satisfaction faible (").append(String.format("%.1f", s)).append(")<br>");
            });
            risques.append("</html>");

            // Opportunités
            StringBuilder opps = new StringBuilder("<html><b style='color:#1D9E75'>Opportunités</b><br><br>");
            promotions.forEach((dept, nb) -> {
                if (nb > 0) opps.append("● <b>").append(dept).append("</b> : ").append(nb).append(" candidat(s) promotion<br>");
            });
            satisfaction.forEach((dept, s) -> {
                if (s >= 3.5) opps.append("● <b>").append(dept).append("</b> : satisfaction élevée<br>");
            });
            opps.append("</html>");

            // Recommandations
            StringBuilder reco = new StringBuilder("<html><b style='color:#1F4E79'>Recommandations</b><br><br>");
            attrition.forEach((dept, taux) -> {
                if (taux > 15) reco.append("● Revoir conditions <b>").append(dept).append("</b><br>");
            });
            double salaireMoyen = salaires.values().stream().mapToDouble(Double::doubleValue).average().orElse(0);
            Map<String, Double> salGenre = DWRepository.getSalaireMoyenParGenre();
            double salF = salGenre.getOrDefault("F", 0.0);
            double salM = salGenre.getOrDefault("M", 0.0);
            if (Math.abs(salF - salM) / salaireMoyen > 0.05)
                reco.append("● Écart salarial H/F détecté — audit recommandé<br>");
            reco.append("● Optimiser budget formation départements sous-formés<br>");
            reco.append("</html>");

            row.add(buildInsightCard(risques.toString(),  new Color(0xFCEBEB)));
            row.add(buildInsightCard(opps.toString(),     new Color(0xEAF3DE)));
            row.add(buildInsightCard(reco.toString(),     new Color(0xE6F1FB)));

        } catch (Exception e) {
            System.err.println("[Dashboard] Erreur insights : " + e.getMessage());
        }

        wrapper.add(row, BorderLayout.CENTER);
        return wrapper;
    }

    private JPanel buildInsightCard(String html, Color bgColor) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(bgColor);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(MainDashboard.C_BORDER, 1, true),
                new EmptyBorder(16, 16, 16, 16)
        ));
        JLabel lbl = new JLabel(html);
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lbl.setVerticalAlignment(SwingConstants.TOP);
        card.add(lbl, BorderLayout.CENTER);
        return card;
    }

    // ═══════════════════════════════════════════════════════════════════
    // STYLE GRAPHIQUES
    // ═══════════════════════════════════════════════════════════════════

    private void styleBarChart(JFreeChart chart, Color barColor) {
        chart.setBackgroundPaint(MainDashboard.C_CARD);
        chart.setBorderVisible(false);
        CategoryPlot plot = chart.getCategoryPlot();
        plot.setBackgroundPaint(MainDashboard.C_CARD);
        plot.setOutlineVisible(false);
        plot.setRangeGridlinePaint(MainDashboard.C_BORDER);
        plot.setDomainGridlinesVisible(false);
        org.jfree.chart.renderer.category.BarRenderer renderer =
                (org.jfree.chart.renderer.category.BarRenderer) plot.getRenderer();
        renderer.setSeriesPaint(0, barColor);
        renderer.setDrawBarOutline(false);
        renderer.setShadowVisible(false);
        renderer.setMaximumBarWidth(0.5);
        plot.getDomainAxis().setTickLabelFont(new Font("Segoe UI", Font.PLAIN, 11));
        plot.getRangeAxis().setTickLabelFont(new Font("Segoe UI", Font.PLAIN, 11));
        plot.getDomainAxis().setAxisLineVisible(false);
        plot.getRangeAxis().setAxisLineVisible(false);
    }

    private void styleLineChart(JFreeChart chart) {
        chart.setBackgroundPaint(MainDashboard.C_CARD);
        chart.setBorderVisible(false);
        CategoryPlot plot = chart.getCategoryPlot();
        plot.setBackgroundPaint(MainDashboard.C_CARD);
        plot.setOutlineVisible(false);
        plot.setRangeGridlinePaint(MainDashboard.C_BORDER);
        org.jfree.chart.renderer.category.LineAndShapeRenderer renderer =
                (org.jfree.chart.renderer.category.LineAndShapeRenderer) plot.getRenderer();
        renderer.setSeriesPaint(0, MainDashboard.C_PRIMARY_LT);
        renderer.setSeriesStroke(0, new BasicStroke(2.5f));
        renderer.setDefaultShapesVisible(true);
        plot.getDomainAxis().setTickLabelFont(new Font("Segoe UI", Font.PLAIN, 11));
        plot.getRangeAxis().setTickLabelFont(new Font("Segoe UI", Font.PLAIN, 11));
        plot.getDomainAxis().setAxisLineVisible(false);
        plot.getRangeAxis().setAxisLineVisible(false);
    }

    // ═══════════════════════════════════════════════════════════════════
    // REFRESH (filtres globaux)
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public void refresh(String annee, String departement) {
        removeAll();
        build();
        revalidate();
        repaint();
    }
}
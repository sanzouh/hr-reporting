package com.hrreporting.ui;

import com.hrreporting.db.DWRepository;
import org.jfree.chart.*;
import org.jfree.chart.plot.*;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.general.DefaultPieDataset;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * DashboardPanel — Vue globale du reporting RH.
 * Contenu :
 *   - 4 KPI cards : Attrition, Effectif, Salaire moyen, Satisfaction
 *   - 3 graphiques : Effectif/dept, Salaire/dept, Satisfaction/dept
 *   - Section Insights : Risques, Opportunités, Recommandations
 */
public class DashboardPanel extends JPanel implements MainDashboard.Refreshable {

    private String annee       = "Toutes";
    private String departement = "Tous";

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
            double tauxMoyen = DWRepository.getTauxAttritionGlobal(annee, departement);
            String badgeAttrition = tauxMoyen > 15 ? "Risque élevé" : tauxMoyen > 8 ? "Modéré" : "Stable";
            Color  colorAttrition = tauxMoyen > 15 ? MainDashboard.C_DANGER
                    : tauxMoyen > 8 ? MainDashboard.C_WARNING : MainDashboard.C_SUCCESS;
            row.add(MainDashboard.buildKpiCard("Taux d'attrition",
                    String.format("%.1f%%", tauxMoyen), badgeAttrition, colorAttrition));

            // Effectif
            int totalEffectif = DWRepository.getEffectifTotal(annee, departement);
            row.add(MainDashboard.buildKpiCard("Effectif total",
                    String.format("%,d", totalEffectif) + " emp.", "Actifs", MainDashboard.C_PRIMARY));

            // Salaire moyen + delta dynamique
            double salaireMoyen = DWRepository.getSalaireMoyenGlobal(annee, departement);
            double delta        = DWRepository.getDeltaSalaireAnnuel(annee);
            String badgeSalaire = delta == 0 ? "vs N-1 : N/A"
                    : String.format("%+.1f%% vs N-1", delta);
            Color colorSalaire  = delta >= 0 ? MainDashboard.C_SUCCESS : MainDashboard.C_DANGER;
            row.add(MainDashboard.buildKpiCard("Salaire moyen",
                    String.format("$%,.0f", salaireMoyen), badgeSalaire, colorSalaire));

            // Satisfaction
            double satisfMoyenne = DWRepository.getSatisfactionMoyenneGlobale(annee, departement);
            String badgeSatisf = satisfMoyenne >= 3.5 ? "Bon" : satisfMoyenne >= 2.5 ? "Moyen" : "Faible";
            Color  colorSatisf = satisfMoyenne >= 3.5 ? MainDashboard.C_SUCCESS
                    : satisfMoyenne >= 2.5 ? MainDashboard.C_WARNING : MainDashboard.C_DANGER;
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
            Map<String, Integer> effectifs = DWRepository.getEffectifParDept(annee, departement);
            DefaultCategoryDataset dsEffectif = new DefaultCategoryDataset();
            effectifs.forEach((dept, nb) -> dsEffectif.addValue(nb, "Effectif", dept));

            JFreeChart chartEffectif = ChartFactory.createBarChart(
                    null, null, null, dsEffectif,
                    PlotOrientation.HORIZONTAL, false, true, false);
            styleBarChart(chartEffectif, MainDashboard.C_PRIMARY);
            row.add(MainDashboard.buildCard("Effectif par département",
                    new ChartPanel(chartEffectif)));

            // Graphique 2 : Salaire moyen par département (barres horizontales)
            Map<String, Double> salaires = DWRepository.getSalaireMoyenParDept(annee, departement);
            DefaultCategoryDataset dsSalaire = new DefaultCategoryDataset();
            salaires.forEach((dept, sal) -> dsSalaire.addValue(sal, "Salaire", dept));

            JFreeChart chartSalaire = ChartFactory.createBarChart(
                    null, null, null, dsSalaire,
                    PlotOrientation.HORIZONTAL, false, true, false);
            styleBarChart(chartSalaire, MainDashboard.C_DANGER);
            row.add(MainDashboard.buildCard("Salaire moyen / dept.",
                    new ChartPanel(chartSalaire)));

            // Graphique 3 : Satisfaction par département (courbe)
            Map<String, Double> satisfaction = DWRepository.getSatisfactionParDept(annee, departement);
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
            Map<String, Double>  attrition  = DWRepository.getTauxAttritionParDept(annee, departement);
            Map<String, Double>  satisf     = DWRepository.getSatisfactionParDept(annee, departement);
            Map<String, Double>  salaires   = DWRepository.getSalaireMoyenParDept(annee, departement);
            Map<String, Double>  perf       = DWRepository.getScorePerfMoyenParDept(annee, departement);
            Map<String, Double>  absences   = DWRepository.getAbsenteismeParDept(annee, departement);
            Map<String, Double>  formations = DWRepository.getCoutFormationParDept(annee, departement);
            Map<String, Integer> promotions = DWRepository.getCandidatsPromotion(annee, departement);
            Map<String, Double>  salGenre   = DWRepository.getSalaireMoyenParGenre(annee, departement);

            double salMoyGlobal = salaires.values().stream().mapToDouble(Double::doubleValue).average().orElse(1.0);

            // ── Risques ──────────────────────────────────────────────────
            List<String> risques = new ArrayList<>();
            attrition.forEach((dept, taux) -> {
                if (taux > 15)
                    risques.add("<b>" + dept + "</b> : attrition " + String.format("%.1f", taux) + "%");
            });
            satisf.forEach((dept, s) -> {
                if (s < 2.5)
                    risques.add("<b>" + dept + "</b> : satisfaction faible (" + String.format("%.1f", s) + " / 4)");
            });
            perf.forEach((dept, p) -> {
                if (p < 2.5)
                    risques.add("<b>" + dept + "</b> : performance en baisse (" + String.format("%.1f", p) + " / 4)");
            });
            absences.forEach((dept, abs) -> {
                if (abs > 40)
                    risques.add("<b>" + dept + "</b> : absentéisme élevé (" + String.format("%.0f", abs) + " j/an)");
            });

            // ── Opportunités ─────────────────────────────────────────────
            List<String> opps = new ArrayList<>();
            promotions.forEach((dept, nb) -> {
                if (nb > 0)
                    opps.add("<b>" + dept + "</b> : " + nb + " candidat(s) à la promotion");
            });
            perf.forEach((dept, p) -> {
                if (p >= 3.5)
                    opps.add("<b>" + dept + "</b> : excellence opérationnelle (" + String.format("%.1f", p) + " / 4)");
            });
            attrition.forEach((dept, taux) -> {
                if (taux < 10 && satisf.getOrDefault(dept, 0.0) >= 3.5)
                    opps.add("<b>" + dept + "</b> : fort engagement (attrition " + String.format("%.1f", taux) + "%)");
            });

            // ── Recommandations ───────────────────────────────────────────
            List<String> recos = new ArrayList<>();
            attrition.forEach((dept, taux) -> {
                if (taux > 15) {
                    double sat = satisf.getOrDefault(dept, -1.0);
                    double sal = salaires.getOrDefault(dept, 0.0);
                    if (sat > 0 && sat < 2.5)
                        recos.add("Revoir les conditions de travail — <b>" + dept
                                + "</b> (attrition " + String.format("%.0f", taux) + "%, sat. " + String.format("%.1f", sat) + ")");
                    else if (sal > 0 && sal < salMoyGlobal * 0.90)
                        recos.add("Réviser la grille salariale — <b>" + dept
                                + "</b> (attrition " + String.format("%.0f", taux) + "%)");
                    else
                        recos.add("Analyser les causes de départ — <b>" + dept
                                + "</b> (attrition " + String.format("%.0f", taux) + "%)");
                }
            });
            double salF = salGenre.getOrDefault("F", 0.0);
            double salM = salGenre.getOrDefault("M", 0.0);
            if (salF > 0 && salM > 0 && Math.abs(salF - salM) / salMoyGlobal > 0.05) {
                double gap = Math.abs(salM - salF) / salM * 100;
                recos.add("Écart salarial H/F de " + String.format("%.1f", gap) + "% — audit recommandé");
            }
            List<String> deptsSansForm = new ArrayList<>();
            salaires.keySet().forEach(dept -> {
                if (!formations.containsKey(dept)) deptsSansForm.add("<b>" + dept + "</b>");
            });
            if (!deptsSansForm.isEmpty())
                recos.add("Aucune formation déclarée — " + String.join(", ", deptsSansForm));
            absences.forEach((dept, abs) -> {
                if (abs > 40)
                    recos.add("Enquête bien-être recommandée — <b>" + dept
                            + "</b> (" + String.format("%.0f", abs) + " j/an)");
            });

            row.add(buildInsightCard("Risques critiques", new Color(0xE24B4A), risques));
            row.add(buildInsightCard("Opportunités",      new Color(0x1D9E75), opps));
            row.add(buildInsightCard("Recommandations",   new Color(0x1F4E79), recos));

        } catch (Exception e) {
            System.err.println("[Dashboard] Erreur insights : " + e.getMessage());
        }

        wrapper.add(row, BorderLayout.CENTER);
        return wrapper;
    }

    private JPanel buildInsightCard(String titleText, Color accentColor, List<String> items) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(MainDashboard.C_CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
                MainDashboard.roundedBorder(MainDashboard.C_BORDER),
                BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 4, 0, 0, accentColor),
                        new EmptyBorder(14, 14, 14, 14))));

        String hex = String.format("#%02X%02X%02X",
                accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue());

        StringBuilder html = new StringBuilder("<html>");
        html.append("<b style='color:").append(hex).append("'>").append(titleText);
        if (!items.isEmpty()) html.append("  (").append(items.size()).append(")");
        html.append("</b><hr>");
        if (items.isEmpty()) {
            html.append("<i style='color:#888888'>Aucun élément détecté</i>");
        } else {
            items.forEach(item -> html.append("&#9679;&nbsp;").append(item).append("<br>"));
        }
        html.append("</html>");

        JLabel lbl = new JLabel(html.toString());
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
        this.annee       = annee;
        this.departement = departement;
        removeAll();
        build();
        revalidate();
        repaint();
    }
}
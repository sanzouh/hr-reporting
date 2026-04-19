package com.hrreporting.ui;

import com.hrreporting.db.DWRepository;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.sql.SQLException;
import java.util.*;

/**
 * MainDashboard — Fenêtre principale du reporting RH.
 * Structure :
 * – Sidebar gauche : navigation entre les sections
 * – Header : titre + filtres globaux (année, département) + bouton refresh
 * – Panneau central : contenu dynamique selon la section active
 * Palette :
 *   Primaire #1F4E79 | Fond #F5F7FA
 *   Succès #1D9E75 | Alerte #E24B4A
 *   Warning #EF9F27 | Texte #2C2C2A
 */
public class MainDashboard extends JFrame {

    // ── Palette ───────────────────────────────────────────────────────
    public static final Color C_PRIMARY    = new Color(0x1F4E79);
    public static final Color C_PRIMARY_LT = new Color(0x2E74B5);
    public static final Color C_BG         = new Color(0xF5F7FA);
    public static final Color C_CARD       = Color.WHITE;
    public static final Color C_TEXT       = new Color(0x2C2C2A);
    public static final Color C_TEXT_SEC   = new Color(0x5F5E5A);
    public static final Color C_SUCCESS    = new Color(0x1D9E75);
    public static final Color C_DANGER     = new Color(0xE24B4A);
    public static final Color C_WARNING    = new Color(0xEF9F27);
    public static final Color C_BORDER     = new Color(0xDDDBD3);

    // ── Filtres globaux (partagés entre tous les panneaux) ────────────
    private JComboBox<String> cbAnnee;
    private JComboBox<String> cbDepartement;
    private String selectedAnnee       = "Toutes";
    private String selectedDepartement = "Tous";

    // ── Panneau central ───────────────────────────────────────────────
    private JPanel contentPanel;
    private CardLayout cardLayout;

    // ── Sections ─────────────────────────────────────────────────────
    private static final String[] SECTIONS = {
            "Dashboard", "Effectifs", "Turnover", "Performance", "Formation", "Promotions"
    };
    private static final String[] ICONS = { "⌂", "👥", "↩", "★", "🎓", "▲" };

    // Panneaux de contenu par section
    private final Map<String, JPanel> panels = new LinkedHashMap<>();
    private JButton activeButton = null;

    public MainDashboard() {
        super("HR Reporting — Système d'Information Décisionnel");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1280, 800);
        setMinimumSize(new Dimension(1100, 700));
        setLocationRelativeTo(null);
        getContentPane().setBackground(C_BG);

        setLayout(new BorderLayout(0, 0));
        add(buildSidebar(), BorderLayout.WEST);
        add(buildMainArea(), BorderLayout.CENTER);

        // Afficher Dashboard par défaut
        showSection("Dashboard");
        setVisible(true);
    }

    // ═══════════════════════════════════════════════════════════════════
    // SIDEBAR
    // ═══════════════════════════════════════════════════════════════════

    private JPanel buildSidebar() {
        JPanel sidebar = new JPanel();
        sidebar.setBackground(C_PRIMARY);
        sidebar.setPreferredSize(new Dimension(220, 0));
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBorder(new EmptyBorder(0, 0, 0, 0));

        // Logo / titre
        JPanel logoPanel = new JPanel(new BorderLayout());
        logoPanel.setBackground(new Color(0x163960));
        logoPanel.setMaximumSize(new Dimension(200, 70));
        logoPanel.setPreferredSize(new Dimension(200, 70));
        logoPanel.setBorder(new EmptyBorder(16, 16, 16, 16));

        JLabel logoLabel = new JLabel("HR Reporting");
        logoLabel.setForeground(Color.WHITE);
        logoLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        JLabel subLabel = new JLabel("SID — Ressources Humaines");
        subLabel.setForeground(new Color(0xB5D4F4));
        subLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));

        JPanel logoText = new JPanel();
        logoText.setBackground(new Color(0x163960));
        logoText.setLayout(new BoxLayout(logoText, BoxLayout.Y_AXIS));
        logoText.add(logoLabel);
        logoText.add(subLabel);
        logoPanel.add(logoText, BorderLayout.CENTER);
        logoText.setAlignmentX(Component.LEFT_ALIGNMENT);
        logoPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        sidebar.add(logoPanel);
        sidebar.add(Box.createVerticalStrut(16));

        // Boutons de navigation
        for (int i = 0; i < SECTIONS.length; i++) {
            String section = SECTIONS[i];
            String icon    = ICONS[i];
            JButton btn    = buildNavButton(icon + "  " + section, section);
            sidebar.add(btn);
            sidebar.add(Box.createVerticalStrut(4));
            if (i == 0) activeButton = btn; // Dashboard actif par défaut
        }

        sidebar.add(Box.createVerticalGlue());

        // Version en bas
        JLabel versionLabel = new JLabel("  v1.0 — Java 21 + H2");
        versionLabel.setForeground(new Color(0x7F9FBF));
        versionLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        versionLabel.setBorder(new EmptyBorder(12, 16, 16, 0));
        sidebar.add(versionLabel);

        return sidebar;
    }

    private JButton buildNavButton(String label, String section) {
        JButton btn = new JButton(label);
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        btn.setHorizontalAlignment(SwingConstants.LEFT);
        btn.setAlignmentX(Component.LEFT_ALIGNMENT);
        btn.setBorder(new EmptyBorder(10, 20, 10, 10));
        btn.setForeground(new Color(0xB5D4F4));
        btn.setBackground(C_PRIMARY);
        btn.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setOpaque(true);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        btn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) {
                if (btn != activeButton) btn.setBackground(C_PRIMARY_LT);
            }
            public void mouseExited(MouseEvent e) {
                if (btn != activeButton) btn.setBackground(C_PRIMARY);
            }
        });

        btn.addActionListener(e -> showSection(section));
        return btn;
    }

    // ═══════════════════════════════════════════════════════════════════
    // ZONE PRINCIPALE (header + contenu)
    // ═══════════════════════════════════════════════════════════════════

    private JPanel buildMainArea() {
        JPanel main = new JPanel(new BorderLayout(0, 0));
        main.setBackground(C_BG);
        main.add(buildHeader(), BorderLayout.NORTH);
        main.add(buildContentArea(), BorderLayout.CENTER);
        return main;
    }

    // ── Header ────────────────────────────────────────────────────────

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(C_CARD);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, C_BORDER),
                new EmptyBorder(14, 24, 14, 24)
        ));

        // Titre
        JPanel titlePanel = getJPanel();

        // Filtres + Refresh
        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        filterPanel.setBackground(C_CARD);

        // Filtre Année
        filterPanel.add(buildFilterLabel("Année :"));
        cbAnnee = buildComboBox(getAnneesDisponibles());
        cbAnnee.addActionListener(e -> {
            selectedAnnee = (String) cbAnnee.getSelectedItem();
            refreshCurrentPanel();
        });
        filterPanel.add(cbAnnee);

        // Filtre Département
        filterPanel.add(buildFilterLabel("Département :"));
        cbDepartement = buildComboBox(getDepartementsDisponibles());
        cbDepartement.addActionListener(e -> {
            selectedDepartement = (String) cbDepartement.getSelectedItem();
            refreshCurrentPanel();
        });
        filterPanel.add(cbDepartement);

        // Bouton Refresh
        JButton btnRefresh = new JButton("↺  Actualiser");
        btnRefresh.setBackground(C_PRIMARY);
        btnRefresh.setForeground(Color.WHITE);
        btnRefresh.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnRefresh.setBorder(new EmptyBorder(8, 16, 8, 16));
        btnRefresh.setFocusPainted(false);
        btnRefresh.setBorderPainted(false);
        btnRefresh.setOpaque(true);
        btnRefresh.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnRefresh.addActionListener(e -> refreshCurrentPanel());
        filterPanel.add(btnRefresh);

        header.add(titlePanel, BorderLayout.WEST);
        header.add(filterPanel, BorderLayout.EAST);
        return header;
    }

    private static JPanel getJPanel() {
        JPanel titlePanel = new JPanel();
        titlePanel.setBackground(C_CARD);
        titlePanel.setLayout(new BoxLayout(titlePanel, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("HR Analytics Dashboard");
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(C_TEXT);
        JLabel subtitle = new JLabel("Vue globale des ressources humaines");
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        subtitle.setForeground(C_TEXT_SEC);
        titlePanel.add(title);
        titlePanel.add(subtitle);
        return titlePanel;
    }

    // ── Zone de contenu avec CardLayout ──────────────────────────────

    private JPanel buildContentArea() {
        cardLayout  = new CardLayout();
        contentPanel = new JPanel(cardLayout);
        contentPanel.setBackground(C_BG);

        for (String section : SECTIONS) {
            JPanel panel = buildSectionPanel(section);
            panels.put(section, panel);
            contentPanel.add(panel, section);
        }

        return contentPanel;
    }

    // ═══════════════════════════════════════════════════════════════════
    // CONSTRUCTION DES PANNEAUX PAR SECTION
    // ═══════════════════════════════════════════════════════════════════

    private JPanel buildSectionPanel(String section) {
        return switch (section) {
            case "Dashboard"   -> new DashboardPanel(this);
            case "Effectifs"   -> new EffectifsPanel(this);
            case "Turnover"    -> new TurnoverPanel(this);
            case "Performance" -> new PerformancePanel(this);
            case "Formation"   -> new FormationPanel(this);
            case "Promotions"  -> new PromotionsPanel(this);
            default            -> buildPlaceholder(section);
        };
    }

    private JPanel buildPlaceholder(String section) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(C_BG);
        JLabel lbl = new JLabel(section + " — à venir", SwingConstants.CENTER);
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        lbl.setForeground(C_TEXT_SEC);
        p.add(lbl, BorderLayout.CENTER);
        return p;
    }

    // ═══════════════════════════════════════════════════════════════════
    // NAVIGATION & REFRESH
    // ═══════════════════════════════════════════════════════════════════

    private String currentSection = "Dashboard";

    public void showSection(String section) {
        currentSection = section;
        cardLayout.show(contentPanel, section);

        // Mettre à jour le bouton actif dans la sidebar
        for (Component c : ((JPanel) getContentPane().getComponent(0)).getComponents()) {
            if (c instanceof JButton btn) {
                boolean isActive = btn.getText().endsWith(section);
                btn.setBackground(isActive ? C_PRIMARY_LT : C_PRIMARY);
                btn.setForeground(isActive ? Color.WHITE : new Color(0xB5D4F4));
                btn.setFont(new Font("Segoe UI", isActive ? Font.BOLD : Font.PLAIN, 13));
                if (isActive) activeButton = btn;
            }
        }
    }

    public void refreshCurrentPanel() {
        JPanel panel = panels.get(currentSection);
        if (panel instanceof Refreshable r) {
            r.refresh(selectedAnnee, selectedDepartement);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // DONNÉES POUR LES FILTRES
    // ═══════════════════════════════════════════════════════════════════

    private String[] getAnneesDisponibles() {
        java.util.List<String> annees = new java.util.ArrayList<>();
        annees.add("Toutes");
        try {
            var rs = com.hrreporting.db.DatabaseManager.getConnection()
                    .createStatement()
                    .executeQuery("SELECT DISTINCT annee FROM dim_temps ORDER BY annee DESC");
            while (rs.next()) annees.add(String.valueOf(rs.getInt("annee")));
        } catch (SQLException e) {
            System.err.println("[Dashboard] Erreur chargement années : " + e.getMessage());
        }
        return annees.toArray(new String[0]);
    }

    private String[] getDepartementsDisponibles() {
        java.util.List<String> depts = new java.util.ArrayList<>();
        depts.add("Tous");
        try {
            var rs = com.hrreporting.db.DatabaseManager.getConnection()
                    .createStatement()
                    .executeQuery("SELECT nom_dept FROM dim_departement ORDER BY nom_dept");
            while (rs.next()) depts.add(rs.getString("nom_dept"));
        } catch (SQLException e) {
            System.err.println("[Dashboard] Erreur chargement départements : " + e.getMessage());
        }
        return depts.toArray(new String[0]);
    }

    // ═══════════════════════════════════════════════════════════════════
    // UTILITAIRES UI PARTAGÉS
    // ═══════════════════════════════════════════════════════════════════

    public String getSelectedAnnee()       { return selectedAnnee; }
    public String getSelectedDepartement() { return selectedDepartement; }

    private JLabel buildFilterLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lbl.setForeground(C_TEXT_SEC);
        return lbl;
    }

    private JComboBox<String> buildComboBox(String[] items) {
        JComboBox<String> cb = new JComboBox<>(items);
        cb.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        cb.setBackground(Color.WHITE);
        cb.setPreferredSize(new Dimension(140, 32));
        return cb;
    }

    /**
     * Construit une card blanche avec titre et contenu.
     * Utilisée par tous les panneaux de section.
     */
    public static JPanel buildCard(String title, JComponent content) {
        JPanel card = new JPanel(new BorderLayout(0, 12));
        card.setBackground(C_CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(C_BORDER, 1, true),
                new EmptyBorder(16, 16, 16, 16)
        ));

        if (title != null && !title.isBlank()) {
            JLabel lbl = new JLabel(title);
            lbl.setFont(new Font("Segoe UI", Font.BOLD, 14));
            lbl.setForeground(C_TEXT);
            lbl.setBorder(new EmptyBorder(0, 0, 8, 0));
            card.add(lbl, BorderLayout.NORTH);
        }
        card.add(content, BorderLayout.CENTER);
        return card;
    }

    /**
     * Construit une KPI card (valeur numérique + label + badge tendance).
     */
    public static JPanel buildKpiCard(String label, String value, String badge, Color badgeColor) {
        JPanel card = new JPanel();
        card.setBackground(C_CARD);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(C_BORDER, 1, true),
                new EmptyBorder(20, 20, 20, 20)
        ));

        JLabel lblLabel = new JLabel(label);
        lblLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblLabel.setForeground(C_TEXT_SEC);

        JLabel lblValue = new JLabel(value);
        lblValue.setFont(new Font("Segoe UI", Font.BOLD, 32));
        lblValue.setForeground(C_TEXT);

        card.add(lblLabel);
        card.add(Box.createVerticalStrut(8));
        card.add(lblValue);

        if (badge != null) {
            JLabel lblBadge = new JLabel(badge);
            lblBadge.setFont(new Font("Segoe UI", Font.BOLD, 11));
            lblBadge.setForeground(Color.WHITE);
            lblBadge.setBackground(badgeColor);
            lblBadge.setOpaque(true);
            lblBadge.setBorder(new EmptyBorder(3, 8, 3, 8));
            card.add(Box.createVerticalStrut(8));
            card.add(lblBadge);
        }

        return card;
    }

    /**
     * Interface à implémenter par les panneaux qui supportent le filtrage.
     */
    public interface Refreshable {
        void refresh(String annee, String departement);
    }
}
package com.hrreporting;

import com.formdev.flatlaf.FlatLightLaf;
import com.hrreporting.db.DatabaseManager;
import com.hrreporting.etl.ETLPipeline;
import com.hrreporting.ui.MainDashboard;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
    public static void main(String[] args) {
        try {
            // Chargement des fonts inter regular et bold
            Font interRegular = Font.createFont(Font.TRUETYPE_FONT,
                    Objects.requireNonNull(Main.class.getResourceAsStream("/fonts/Inter-Regular.ttf")));
            Font interBold = Font.createFont(Font.TRUETYPE_FONT,
                    Objects.requireNonNull(Main.class.getResourceAsStream("/fonts/Inter-Bold.ttf")));
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            ge.registerFont(interRegular);
            ge.registerFont(interBold);
            UIManager.put("defaultFont", interRegular.deriveFont(13f));

            // Add rounded corners to buttons and text fields
            UIManager.put("Button.arc", 16);
            UIManager.put("Component.arc", 16);
            UIManager.put("TextComponent.arc", 16);

            FlatLightLaf.setup();
            FlatLightLaf.setup(); // ou  FlatDarkLaf

            DatabaseManager.initialize();

            if (!DatabaseManager.isDatabasePopulated()) {
                ETLPipeline.run();
            }

            // Open the H2 database console. Access the console at http://localhost:8082 with JDBC URL: jdbc:h2:mem:hrdb, User Name: san, Password: (leave blank).
            org.h2.tools.Server.startWebServer(DatabaseManager.getConnection());
//            DatabaseManager.close();
//            console H2 sans bloquer le thread UI
//            org.h2.tools.Server.createWebServer("-web", "-webPort", "8082").start();

            SwingUtilities.invokeLater(MainDashboard::new);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
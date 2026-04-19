package com.hrreporting;

import com.formdev.flatlaf.FlatDarkLaf;
import com.hrreporting.db.DatabaseManager;
import com.hrreporting.etl.ETLPipeline;
import com.hrreporting.ui.MainDashboard;

import javax.swing.*;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
    public static void main(String[] args) {
        try {
            FlatDarkLaf.setup(); // ou FlatLightLaf
            DatabaseManager.initialize();
            ETLPipeline.run();
            // Open the H2 database console. Access the console at http://localhost:8082 with JDBC URL: jdbc:h2:mem:hrdb, User Name: san, Password: (leave blank).
//            org.h2.tools.Server.startWebServer(DatabaseManager.getConnection());
//            DatabaseManager.close();

            SwingUtilities.invokeLater(MainDashboard::new);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
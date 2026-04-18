package com.hrreporting;

import com.hrreporting.db.DatabaseManager;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
    public static void main(String[] args) {
        try {
            DatabaseManager.initialize();
            System.out.println("[OK] Connexion H2 réussie !");
            DatabaseManager.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
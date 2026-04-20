package com.hrreporting.etl;

import com.hrreporting.db.DWRepository;
import com.hrreporting.model.FaitRH;

import java.util.ArrayList;
import java.util.List;

/**
 * ETLPipeline — Orchestrateur du pipeline ETL NexCore Technologies.
 *
 * Sources :
 *   1. RH_Paie.csv             → données centrales employés (1 000 lignes)
 *   2. Timesheet_Operations.xlsx → absences et heures sup (~47 000 lignes)
 *   3. JobHistory.csv          → historique carrière (~2 000 lignes)
 *   4. Formations_Learning.csv → formations (~2 000 lignes)
 *   5. Evaluations_Semestral.db → évaluations SQLite (~8 000 lignes)
 */
public class ETLPipeline {

    public static void run() {
        System.out.println("═══════════════════════════════════════════");
        System.out.println("  Pipeline ETL — NexCore Technologies");
        System.out.println("═══════════════════════════════════════════");

        List<FaitRH> tousLesFaits = new ArrayList<>();
        long debut = System.currentTimeMillis();

        try {
            System.out.println("\n[1/5] RH_Paie.csv...");
            tousLesFaits.addAll(NexCoreRHLoader.load());
        } catch (Exception e) {
            System.err.println("[ETL] Erreur RH Paie : " + e.getMessage());
            e.printStackTrace();
        }

        try {
            System.out.println("\n[2/5] Timesheet_Operations.xlsx...");
            tousLesFaits.addAll(NexCoreTimesheetLoader.load());
        } catch (Exception e) {
            System.err.println("[ETL] Erreur Timesheet : " + e.getMessage());
            e.printStackTrace();
        }

        try {
            System.out.println("\n[3/5] JobHistory.csv...");
            tousLesFaits.addAll(NexCoreJobHistoryLoader.load());
        } catch (Exception e) {
            System.err.println("[ETL] Erreur JobHistory : " + e.getMessage());
            e.printStackTrace();
        }

        try {
            System.out.println("\n[4/5] Formations_Learning.csv...");
            tousLesFaits.addAll(NexCoreFormationsLoader.load());
        } catch (Exception e) {
            System.err.println("[ETL] Erreur Formations : " + e.getMessage());
            e.printStackTrace();
        }

        try {
            System.out.println("\n[5/5] Evaluations_Semestral.db...");
            tousLesFaits.addAll(NexCoreEvaluationsLoader.load());
        } catch (Exception e) {
            System.err.println("[ETL] Erreur Evaluations : " + e.getMessage());
            e.printStackTrace();
        }

        if (!tousLesFaits.isEmpty()) {
            try {
                System.out.println("\n[ETL] Insertion batch de " + tousLesFaits.size() + " faits...");
                DWRepository.insertFaitsBatch(tousLesFaits);
            } catch (Exception e) {
                System.err.println("[ETL] Erreur insertion batch : " + e.getMessage());
                e.printStackTrace();
            }
        }

        long duree = System.currentTimeMillis() - debut;
        System.out.println("\n═══════════════════════════════════════════");
        System.out.printf("  Pipeline terminé en %d ms%n", duree);
        System.out.println("  Total faits chargés : " + tousLesFaits.size());
        System.out.println("═══════════════════════════════════════════\n");
    }
}
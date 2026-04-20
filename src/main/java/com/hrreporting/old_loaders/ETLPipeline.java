package com.hrreporting.old_loaders;

import com.hrreporting.db.DWRepository;
import com.hrreporting.model.FaitRH;

import java.util.ArrayList;
import java.util.List;

/**
 * ETLPipeline — Orchestrateur du pipeline ETL.
 * Séquence d'exécution :
 *   1. IbmHrLoader      → 1 470 employés (attrition, salaire, satisfaction, performance)
 *   2. RshuebnerLoader  → 311 employés (absences, recrutement, motifs de départ)
 *   3. FormationsLoader → ~2 600 lignes agrégées par employé
 *   4. EvaluationsLoader→ ~1 800 lignes (scores, objectifs, promotions).
 * Tous les faits sont collectés puis insérés en un seul batch final
 * pour optimiser les performances (une seule transaction H2).
 */
public class ETLPipeline {

    public static void run() {
        System.out.println("═══════════════════════════════════════");
        System.out.println("  Démarrage du pipeline ETL");
        System.out.println("═══════════════════════════════════════");

        List<FaitRH> tousLesFaits = new ArrayList<>();
        long debut = System.currentTimeMillis();

        // ── ÉTAPE 1 : IBM HR ──────────────────────────────────────────
        try {
            System.out.println("\n[1/4] Chargement IBM HR Attrition...");
            List<FaitRH> faitsIbm = IbmHrLoader.load();
            tousLesFaits.addAll(faitsIbm);
        } catch (Exception e) {
            System.err.println("[ETL] Erreur IBM HR : " + e.getMessage());
            e.printStackTrace();
        }

        // ── ÉTAPE 2 : RSHUEBNER ───────────────────────────────────────
        try {
            System.out.println("\n[2/4] Chargement HRDataset rshuebner...");
            List<FaitRH> faitsRsh = RshuebnerLoader.load();
            tousLesFaits.addAll(faitsRsh);
        } catch (Exception e) {
            System.err.println("[ETL] Erreur rshuebner : " + e.getMessage());
            e.printStackTrace();
        }

        // ── ÉTAPE 3 : FORMATIONS ──────────────────────────────────────
        try {
            System.out.println("\n[3/4] Chargement formations...");
            List<FaitRH> faitsForm = FormationsLoader.load();
            tousLesFaits.addAll(faitsForm);
        } catch (Exception e) {
            System.err.println("[ETL] Erreur formations : " + e.getMessage());
            e.printStackTrace();
        }

        // ── ÉTAPE 4 : EVALUATIONS ─────────────────────────────────────
        try {
            System.out.println("\n[4/4] Chargement évaluations semestrielles...");
            List<FaitRH> faitsEval = EvaluationsLoader.load();
            tousLesFaits.addAll(faitsEval);
        } catch (Exception e) {
            System.err.println("[ETL] Erreur évaluations : " + e.getMessage());
            e.printStackTrace();
        }

        // ── INSERTION BATCH FINALE ────────────────────────────────────
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
        System.out.println("\n═══════════════════════════════════════");
        System.out.printf("  Pipeline ETL terminé en %d ms%n", duree);
        System.out.println("  Total faits chargés : " + tousLesFaits.size());
        System.out.println("═══════════════════════════════════════\n");
    }
}
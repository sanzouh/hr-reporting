package com.hrreporting.etl;

import com.hrreporting.db.DWRepository;
import com.hrreporting.model.FaitRH;

import java.util.*;

/**
 * ETLPipeline — Orchestrateur ETL NexCore Technologies.
 *
 * Stratégie : consolidation par employe_id avant insertion.
 * Chaque source enrichit le même FaitRH via merge(), garantissant
 * une ligne unique et complète par employé dans fait_rh.
 *
 * Sources :
 *   1. RH_Paie.csv              → base employé (salaire, attrition, satisfaction, perf)
 *   2. Timesheet_Operations.xlsx → nb_absences, heures_sup
 *   3. JobHistory.csv           → promotion effective, salaire le plus récent
 *   4. Formations_Learning.csv  → cout_formation, nb_formations
 *   5. Evaluations_Semestral.db → score_eval, objectifs, promotion_recommandee
 */
public class ETLPipeline {

    public static void run() {
        System.out.println("═══════════════════════════════════════════");
        System.out.println("  Pipeline ETL — NexCore Technologies");
        System.out.println("═══════════════════════════════════════════");

        // Map de consolidation : employe_id → FaitRH en construction
        Map<String, FaitRH.Builder> consolidated = new LinkedHashMap<>();

        long debut = System.currentTimeMillis();

        // ── 1. RH_Paie — base employé ─────────────────────────────────
        try {
            System.out.println("\n[1/5] RH_Paie.csv...");
            Map<String, FaitRH.Builder> rhData = NexCoreRHLoader.loadAsMap();
            rhData.forEach((id, b) -> consolidated.put(id, b));
            System.out.println("[1/5] " + rhData.size() + " employés chargés.");
        } catch (Exception e) {
            System.err.println("[ETL] Erreur RH Paie : " + e.getMessage());
            e.printStackTrace();
        }

        // ── 2. Timesheet — absences et heures sup ─────────────────────
        try {
            System.out.println("\n[2/5] Timesheet_Operations.xlsx...");
            Map<String, FaitRH.Builder> tsData = NexCoreTimesheetLoader.loadAsMap();
            tsData.forEach((id, b) -> consolidated.merge(id, b, ETLPipeline::mergeBuilders));
            System.out.println("[2/5] " + tsData.size() + " enregistrements timesheet mergés.");
        } catch (Exception e) {
            System.err.println("[ETL] Erreur Timesheet : " + e.getMessage());
            e.printStackTrace();
        }

        // ── 3. JobHistory — promotions effectives ─────────────────────
        try {
            System.out.println("\n[3/5] JobHistory.csv...");
            Map<String, FaitRH.Builder> jhData = NexCoreJobHistoryLoader.loadAsMap();
            jhData.forEach((id, b) -> consolidated.merge(id, b, ETLPipeline::mergeBuilders));
            System.out.println("[3/5] " + jhData.size() + " enregistrements job history mergés.");
        } catch (Exception e) {
            System.err.println("[ETL] Erreur JobHistory : " + e.getMessage());
            e.printStackTrace();
        }

        // ── 4. Formations ─────────────────────────────────────────────
        try {
            System.out.println("\n[4/5] Formations_Learning.csv...");
            Map<String, FaitRH.Builder> formData = NexCoreFormationsLoader.loadAsMap();
            formData.forEach((id, b) -> consolidated.merge(id, b, ETLPipeline::mergeBuilders));
            System.out.println("[4/5] " + formData.size() + " enregistrements formations mergés.");
        } catch (Exception e) {
            System.err.println("[ETL] Erreur Formations : " + e.getMessage());
            e.printStackTrace();
        }

        // ── 5. Evaluations ────────────────────────────────────────────
        try {
            System.out.println("\n[5/5] Evaluations_Semestral.db...");
            Map<String, FaitRH.Builder> evalData = NexCoreEvaluationsLoader.loadAsMap();
            evalData.forEach((id, b) -> consolidated.merge(id, b, ETLPipeline::mergeBuilders));
            System.out.println("[5/5] " + evalData.size() + " enregistrements évaluations mergés.");
        } catch (Exception e) {
            System.err.println("[ETL] Erreur Evaluations : " + e.getMessage());
            e.printStackTrace();
        }

        // ── Insertion batch ───────────────────────────────────────────
        List<FaitRH> faits = new ArrayList<>();
        for (Map.Entry<String, FaitRH.Builder> entry : consolidated.entrySet()) {
            try {
                faits.add(entry.getValue().build());
            } catch (Exception e) {
                System.err.println("[ETL] Erreur build fait " + entry.getKey() + " : " + e.getMessage());
            }
        }

        if (!faits.isEmpty()) {
            try {
                System.out.println("\n[ETL] Insertion batch de " + faits.size() + " faits consolidés...");
                DWRepository.insertFaitsBatch(faits);
            } catch (Exception e) {
                System.err.println("[ETL] Erreur insertion batch : " + e.getMessage());
                e.printStackTrace();
            }
        }

        long duree = System.currentTimeMillis() - debut;
        System.out.println("\n═══════════════════════════════════════════");
        System.out.printf("  Pipeline terminé en %d ms%n", duree);
        System.out.println("  Total employés consolidés : " + faits.size());
        System.out.println("═══════════════════════════════════════════\n");
    }

    /**
     * Merge deux builders : les champs non renseignés (-1/0) du builder existant
     * sont enrichis par les valeurs du nouveau builder si disponibles.
     */
    public static FaitRH.Builder mergeBuilders(FaitRH.Builder existing, FaitRH.Builder incoming) {
        FaitRH e = existing.build();
        FaitRH n = incoming.build();

        // Clés dimensionnelles : priorité à la source RH (existing), sauf si non renseigné
        if (e.getDeptId()  == -1 && n.getDeptId()  != -1) existing.deptId(n.getDeptId());
        if (e.getTempsId() == -1 && n.getTempsId() != -1) existing.tempsId(n.getTempsId());
        if (e.getPosteId() == -1 && n.getPosteId() != -1) existing.posteId(n.getPosteId());

        // Formation
        if (e.getFormationId() == -1 && n.getFormationId() != -1) existing.formationId(n.getFormationId());

        // Mesures — enrichissement si absent
        if (e.getSalaireMensuel() <= 0 && n.getSalaireMensuel() > 0)
            existing.salaireMensuel(n.getSalaireMensuel());

        if (e.getScorePerformance() == -1 && n.getScorePerformance() != -1)
            existing.scorePerformance(n.getScorePerformance());

        if (e.getSatisfactionEmploye() == -1 && n.getSatisfactionEmploye() != -1)
            existing.satisfactionEmploye(n.getSatisfactionEmploye());

        if (e.getNbAbsences() == -1 && n.getNbAbsences() != -1)
            existing.nbAbsences(n.getNbAbsences());

        if (e.getHeuresSup() == 0 && n.getHeuresSup() == 1)
            existing.heuresSup(1);

        if (e.getScoreEvaluation() == -1 && n.getScoreEvaluation() != -1)
            existing.scoreEvaluation(n.getScoreEvaluation());

        if (e.getObjectifsAtteintsP() == -1 && n.getObjectifsAtteintsP() != -1)
            existing.objectifsAtteintsP(n.getObjectifsAtteintsP());

        if (e.getCoutFormation() <= 0 && n.getCoutFormation() > 0)
            existing.coutFormation(n.getCoutFormation());

        if (e.getNbFormations() == -1 && n.getNbFormations() != -1)
            existing.nbFormations(n.getNbFormations());

        if (e.getDureeAvantDepart() == -1 && n.getDureeAvantDepart() != -1)
            existing.dureeAvantDepart(n.getDureeAvantDepart());

        // Promotion : 1 prend le dessus sur 0 ou -1
        if (n.getPromotionRecommandee() == 1)
            existing.promotionRecommandee(1);
        else if (e.getPromotionRecommandee() == -1 && n.getPromotionRecommandee() != -1)
            existing.promotionRecommandee(n.getPromotionRecommandee());

        return existing;
    }
}
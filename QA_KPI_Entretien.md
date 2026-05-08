# Q&A KPI — Entretien NexCore HR Reporting

> Format : **Source** → **Calcul** → **Valeur attendue**
> Chaque KPI est issu de la table de faits `fait_rh` (Data Warehouse H2), alimentée par l'ETL depuis 5 sources NexCore.

---

## Panel 1 — Dashboard (vue globale)

### KPI 1.1 — Taux d'attrition
**Source :** `RH_Paie.csv` — champ `TerminationDate`. Si rempli → `attrition = 1`, sinon `0`.

**Calcul :**
- Mode *Toutes* : `SUM(attrition) / COUNT(*) × 100` = départs / total employés ever
- Mode *année X* : `nb partis en X / nb actifs début X × 100`
  - actifs début X = embauchés avant X ET pas encore partis

**Attendu :** ~22% global (ATTRITION_BASE du générateur). SALES un peu au-dessus (~26%).

---

### KPI 1.2 — Effectif actif
**Source :** `RH_Paie.csv` — `HireDate` et `TerminationDate`.

**Calcul :**
- Mode *Toutes* : `COUNT(DISTINCT employe_id)` = 1 500 (tous employés ever, actifs + partis)
- Mode *année X* : idem mais filtré sur `annee_embauche <= X AND (annee_depart IS NULL OR annee_depart >= X)`

**Attendu :** 1 500 en "Toutes" (badge "Cumulatif"). Snapshot cohérent par année.

---

### KPI 1.3 — Salaire moyen
**Source :** `RH_Paie.csv` — champ `AnnualSalary`. Converti en **mensuel** (÷ 12) à l'ETL.

**Calcul :** `AVG(salaire_mensuel)` sur les employés actifs pour l'année filtrée.

**Attendu :** ~6 500–7 500 $/mois selon le mix de départements.

---

### KPI 1.4 — Satisfaction moyenne
**Source :** `RH_Paie.csv` — champ `EmployeeSatisfaction` (entier 1–4).

**Calcul :** `AVG(satisfaction_employe)` sur les employés actifs.

**Attendu :** ~2.7/4 (badge "Moyen"). Corrélé positivement à la performance dans les données.

---

## Panel 2 — Effectifs

### KPI 2.1 — Total employés
**Source :** `fait_rh` — clé `employe_id`.

**Calcul :** `COUNT(DISTINCT employe_id)` avec le filtre année actif si applicable.

**Attendu :** 1 500 en "Toutes", moins selon le filtre.

---

### KPI 2.2 — % Employés actifs / Taux de maintien
**Source :** `fait_rh` — champs `annee_depart`.

**Calcul :**
- Mode *Toutes* : `SUM(CASE WHEN annee_depart IS NULL) / COUNT(*) × 100` = % sans date de départ (encore en poste)
- Mode *année X* : `SUM(CASE WHEN annee_depart IS NULL OR annee_depart > X) / COUNT(*) × 100` = % encore présents à fin X parmi ceux actifs pendant X

**Attendu :** ~78% actifs en "Toutes" (inverse du taux d'attrition ~22%).

---

### KPI 2.3 — Âge moyen
**Source :** `RH_Paie.csv` — `DateOfBirth` → `annee_naissance` stocké dans `fait_rh`.

**Calcul :** `AVG(annee_ref - annee_naissance)` où `annee_ref` = année filtrée (ou année courante si "Toutes").

**Attendu :** ~38–40 ans (âges générés entre 23 et 58).

---

### KPI 2.4 — Ancienneté moyenne
**Source :** `RH_Paie.csv` — `HireDate` → `annee_embauche` dans `fait_rh`.

**Calcul :** `AVG(annee_ref - annee_embauche)` filtré sur les employés actifs.

**Attendu :** ~4–5 ans (anciennetés générées entre 1 et 13 ans).

---

## Panel 3 — Turnover & Rétention

### KPI 3.1 — Taux d'attrition
Identique au KPI 1.1 (même méthode `DWRepository.getTauxAttritionGlobal`).

---

### KPI 3.2 — Départs totaux
**Source :** `fait_rh` — champ `attrition`.

**Calcul :** `SUM(attrition)` filtré sur `annee_depart = X` si année précise, sinon total historique.

**Attendu :** ~330 en "Toutes" (22% × 1 500).

---

### KPI 3.3 — Durée moyenne avant départ
**Source :** `RH_Paie.csv` — écart `HireDate` ↔ `TerminationDate` → `duree_avant_depart` (en jours) dans `fait_rh`.

**Calcul :** `AVG(duree_avant_depart) / 365` → converti en années, sur les employés partis.

**Attendu :** ~2–4 ans selon les distributions de tenure.

---

### KPI 3.4 — Taux de rétention
**Source :** dérivé du taux d'attrition.

**Calcul :** `100 - taux_attrition` (pas une requête indépendante).

**Attendu :** ~78% en "Toutes".

---

## Panel 4 — Performance & Satisfaction

### KPI 4.1 — Score performance moyen
**Source :** `RH_Paie.csv` — champ `PerformanceRating` (entier 1–4).

**Calcul :** `AVG(score_performance)` sur les employés actifs avec score > 0.

**Attendu :** ~2.7–2.8/4 (distribution : 8% niveau 1, 32% niveau 2, 38% niveau 3, 22% niveau 4).

---

### KPI 4.2 — Satisfaction moyenne
Identique au KPI 1.4.

---

### KPI 4.3 — % Heures supplémentaires
**Source :** `Timesheet_Operations.xlsx` — `OvertimeHours` par mois. Si `maxOT > 10` sur l'année → `heures_sup = 1` (binaire).

**Calcul :** `SUM(heures_sup) / COUNT(*) × 100` = proportion d'employés ayant dépassé 10h sup sur leur meilleure année.

**Attendu :** ~35% (corrélé à la performance : perf 4 → 65% de chance d'être "overtime-prone").

---

### KPI 4.4 — Score évaluation moyen
**Source :** `Evaluations_Semestral.db` — table `evaluations`, champ `EvaluationScore` (1–5), semestres 2018–2023.

**Calcul :** moyenne de toutes les évaluations par employé agrégée à l'ETL → `AVG(score_evaluation)` affiché.

**Attendu :** ~3.0–3.2/5 (corrélé à la performance RH_Paie).

---

## Panel 5 — Formation

### KPI 5.1 — Formations réalisées
**Source :** `Formations_Learning.csv` — champ `completion_status = 'Completed'` + comptage par employé → `nb_formations` dans `fait_rh`.

**Calcul :** `SUM(nb_formations)` sur les employés actifs.

**Attendu :** volume total dépendant du nombre d'employés formés (~82% du pool).

---

### KPI 5.2 — Coût total formation
**Source :** `Formations_Learning.csv` — champ `training_cost_eur` (formations complétées uniquement, les annulées/en cours comptent 30%).

**Calcul :** `SUM(cout_formation)` agrégé par département, puis somme globale.

**Attendu :** plusieurs millions $ sur l'ensemble du portefeuille (fourchettes : 380–5 500 $ par formation).

---

### KPI 5.3 — Coût moyen par employé formé
**Source :** même que 5.2.

**Calcul :** `AVG(cout_formation)` sur les employés avec `nb_formations > 0`.

**Attendu :** ~2 000–3 000 $ par employé formé.

---

### KPI 5.4 — % employés formés
**Source :** `fait_rh` — champ `nb_formations`.

**Calcul :** `SUM(CASE WHEN nb_formations > 0 THEN 1) / COUNT(*) × 100`.

**Attendu :** ~82% (taux de couverture formations du générateur).

---

## Panel 6 — Promotions

### KPI 6.1 — Candidats à la promotion
**Source :** croisement de 3 sources :
- `RH_Paie.csv` → `score_performance`
- `Evaluations_Semestral.db` → `score_evaluation`, `objectifs_atteints_pct`, `promotion_recommandee`

**Calcul :** `COUNT(*)` avec critères cumulatifs :
```
promotion_recommandee = 1
AND score_performance >= 3
AND score_evaluation >= 4
AND objectifs_atteints_pct >= 80
```
Filtré sur `annee_promotion_recommandee = X` si année précise.

**Attendu :** nombre relativement faible (critères stricts : 3 conditions simultanées).

---

### KPI 6.2 — % promouvables
**Source :** dérivé des KPI 6.1 et 1.2.

**Calcul :** `total_candidats / effectif_total × 100`.

**Attendu :** 5–15% selon le filtre année/département.

---

### KPI 6.3 — Score perf. moyen des candidats
**Source :** `fait_rh` — champ `score_performance`.

**Calcul :** `AVG(score_performance)` filtré sur `promotion_recommandee = 1`.

**Attendu :** ≥ 3/4 par construction (critère d'éligibilité >= 3).

---

### KPI 6.4 — % objectifs atteints moyen des candidats
**Source :** `Evaluations_Semestral.db` → `objectifs_atteints_pct` agrégé dans `fait_rh`.

**Calcul :** `AVG(objectifs_atteints_pct)` filtré sur `promotion_recommandee = 1`.

**Attendu :** ≥ 80% par construction (critère d'éligibilité).

---

---

## Graphiques — Mécanique JFreeChart (commun à tous les panels)

**Comment un graphique est-il affiché ?**
1. Les données sont récupérées depuis `fait_rh` via `DWRepository` (méthodes réutilisées) ou une requête SQL directe dans le panel.
2. Elles sont injectées dans un dataset JFreeChart :
   - `DefaultCategoryDataset` → barres (verticales ou horizontales) et courbes
   - `DefaultPieDataset` → camembert
3. `ChartFactory.create[Bar/Line/Pie]Chart(...)` génère le graphique.
4. Le graphique est enveloppé dans un `ChartPanel` (composant Swing).
5. Un style custom est appliqué (couleurs, polices, pas de bordure, pas d'ombre).

**Même calcul que les KPI ?**
Oui pour les graphiques standards (ex. effectif/dept, satisfaction/dept) : ils appellent exactement les mêmes méthodes `DWRepository` avec les mêmes filtres année/département.
Non pour les graphiques complexes (pyramide des âges, corrélation, heures sup vs attrition) : ceux-là font une requête SQL directe inline dans le panel.

---

## Graphiques — Détail par panel

### Dashboard
| Graphique | Type | Ce qui se passe dedans |
|-----------|------|------------------------|
| Effectif par département | Barres horizontales | `fait_rh ⋈ dim_departement ⋈ dim_temps` → `COUNT(DISTINCT employe_id)` GROUP BY dept |
| Salaire moyen par département | Barres horizontales | `fait_rh ⋈ dim_departement ⋈ dim_temps` → `AVG(salaire_mensuel)` GROUP BY dept |
| Satisfaction par département | Courbe | `fait_rh ⋈ dim_departement ⋈ dim_temps` → `AVG(satisfaction_employe)` GROUP BY dept |

---

### Effectifs
| Graphique | Type | Ce qui se passe dedans |
|-----------|------|------------------------|
| Effectif par département | Barres verticales | `fait_rh ⋈ dim_departement ⋈ dim_temps` → `COUNT(DISTINCT employe_id)` GROUP BY dept |
| Répartition par genre | Camembert | `fait_rh ⋈ dim_employe ⋈ dim_departement ⋈ dim_temps` → `COUNT(DISTINCT employe_id)` GROUP BY genre |
| Pyramide des âges | Barres groupées H/F | `fait_rh ⋈ dim_employe ⋈ dim_departement` → `COUNT(*) WHERE genre='M'/'F'` pour 5 tranches `(annee_ref − annee_naissance)` |
| Ancienneté moy. par département | Barres verticales | `fait_rh ⋈ dim_departement` → `AVG(annee_ref − annee_embauche)` GROUP BY dept |

---

### Turnover
| Graphique | Type | Ce qui se passe dedans |
|-----------|------|------------------------|
| Taux d'attrition par département | Barres verticales | `fait_rh ⋈ dim_departement` → formule départs/actifs GROUP BY dept (voir KPI 1.1) |
| Motifs de départ | Camembert (top 7) | `fait_rh ⋈ dim_employe ⋈ dim_departement ⋈ dim_temps WHERE attrition=1` → `COUNT(*)` GROUP BY motif_depart |
| Durée moy. avant départ par département | Barres horizontales | `fait_rh ⋈ dim_departement WHERE duree_avant_depart > 0` → `AVG(duree_avant_depart) / 365` GROUP BY dept |
| Heures sup vs attrition | Barres groupées | `fait_rh ⋈ dim_departement` → `SUM(CASE heures_sup=1 AND attrition=1) / SUM(CASE heures_sup=1)` et idem pour `heures_sup=0`, GROUP BY dept |

---

### Performance
| Graphique | Type | Ce qui se passe dedans |
|-----------|------|------------------------|
| Score performance par département | Barres verticales | `fait_rh ⋈ dim_departement ⋈ dim_temps` → `AVG(score_performance)` GROUP BY dept |
| Satisfaction par département | Courbe | `fait_rh ⋈ dim_departement ⋈ dim_temps` → `AVG(satisfaction_employe)` GROUP BY dept |
| Corrélation satisfaction ↔ turnover | Barres groupées | `fait_rh ⋈ dim_departement WHERE satisfaction > 0` → `AVG(satisfaction_employe)` + taux attrition dans la **même requête** GROUP BY dept |
| Score évaluation par département | Barres verticales | `fait_rh ⋈ dim_departement WHERE score_evaluation > 0` → `AVG(score_evaluation)` GROUP BY dept |

---

### Formation
| Graphique | Type | Ce qui se passe dedans |
|-----------|------|------------------------|
| Coût total formation par département | Barres verticales | `fait_rh ⋈ dim_departement WHERE cout_formation > 0` → `SUM(cout_formation)` GROUP BY dept |
| Top formations (fréquence) | Barres horizontales top 8 | `fait_rh ⋈ dim_formation ⋈ dim_departement` → `COUNT(*)` GROUP BY intitulé ORDER BY nb DESC LIMIT 8 |
| Nb moyen formations par département | Courbe | `fait_rh ⋈ dim_departement WHERE nb_formations >= 0` → `AVG(nb_formations)` GROUP BY dept |
| Répartition par durée | Camembert | `fait_rh ⋈ dim_formation ⋈ dim_departement` → `COUNT(*)` pour 4 tranches sur `duree_jours` (1 / 2-3 / 4-5 / 6+) |

---

### Promotions
| Graphique | Type | Ce qui se passe dedans |
|-----------|------|------------------------|
| Candidats à la promotion par département | Barres verticales | `fait_rh ⋈ dim_departement WHERE promo=1 AND perf≥3 AND eval≥4 AND obj≥80` → `COUNT(*)` GROUP BY dept |
| Score perf. promouvables vs autres | Barres groupées | `fait_rh ⋈ dim_departement` → `AVG(score_performance) CASE WHEN promotion_recommandee=1` et `=0` dans la même requête, GROUP BY dept |
| % objectifs atteints par département | Courbe | `fait_rh ⋈ dim_departement WHERE objectifs >= 0` → `AVG(objectifs_atteints_pct)` GROUP BY dept |
| Genre des candidats | Camembert | `fait_rh ⋈ dim_employe ⋈ dim_departement WHERE promotion_recommandee=1` → `COUNT(*)` GROUP BY genre |

---

## Rappel architecture ETL

| Source | Fréquence entrée | Ce qu'elle apporte |
|--------|------------------|--------------------|
| `RH_Paie.csv` | 1 ligne/employé | Base : salaire, attrition, satisfaction, performance |
| `Timesheet_Operations.xlsx` | N lignes/mois/employé | Absences annuelles, heures sup (binaire) |
| `JobHistory.csv` | N lignes/événement/employé | Promotions effectives, dernier salaire |
| `Formations_Learning.csv` | N lignes/formation/employé | Coût total formations, nb formations |
| `Evaluations_Semestral.db` | 2 lignes/an/employé | Score éval, objectifs, recommandation promotion |

**→ L'ETLPipeline consolide tout en 1 ligne par employé dans `fait_rh` via merge progressif.**
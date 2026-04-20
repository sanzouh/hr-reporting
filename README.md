# HR Reporting — Système d'Information Décisionnel

> Application desktop Java simulant le reporting RH interne d'une entreprise.
> Pipeline ETL multi-sources → Data Warehouse H2 → Tableaux de bord JFreeChart.

---

## Stack technique

| Composant       | Technologie                 |
| --------------- | --------------------------- |
| Langage         | Java 21                     |
| Build           | Maven 3.8+                  |
| Base de données | H2 embarqué (schéma étoile) |
| Visualisation   | JFreeChart 1.5.4            |
| UI              | Java Swing + FlatLaf 3.4    |
| Lecture CSV     | OpenCSV 5.9                 |
| Lecture Excel   | Apache POI 5.3.0            |

---

## Architecture

```
[IBM HR CSV]                   ─┐
[rshuebner CSV]                ─┤─→ ETL Java ─→ H2 Data Warehouse ─→ JFreeChart ─→ Swing UI
[formations.csv]               ─┤
[evaluations_semestrielles.xlsx]─┘
```

### Structure du projet

```
hr-reporting/
├── pom.xml
├── README.md
├── LICENSE
├── .gitignore
├── database/
│   └── hr_reporting_dw.mv.db       ← générée automatiquement au premier lancement
└── src/main/
    ├── java/com/hrreporting/
    │   ├── Main.java
    │   ├── db/
    │   │   ├── DatabaseManager.java
    │   │   └── DWRepository.java
    │   ├── etl/
    │   │   ├── ETLPipeline.java
    │   │   ├── ETLUtils.java
    │   │   ├── IbmHrLoader.java
    │   │   ├── RshuebnerLoader.java
    │   │   ├── FormationsLoader.java
    │   │   └── EvaluationsLoader.java
    │   ├── model/
    │   │   └── FaitRH.java
    │   └── ui/
    │       ├── MainDashboard.java
    │       ├── DashboardPanel.java
    │       ├── EffectifsPanel.java
    │       ├── TurnoverPanel.java
    │       ├── PerformancePanel.java
    │       ├── FormationPanel.java
    │       └── PromotionsPanel.java
    └── resources/
        ├── data/
        │   ├── WA_Fn-UseC_-HR-Employee-Attrition.csv
        │   ├── HRDataset_v14.csv
        │   ├── formations.csv
        │   └── evaluations_semestrielles.xlsx
        ├── fonts/                  ← polices custom (ex: Inter)
        └── icons/                  ← icônes SVG Material Design + logo PNG
```

---

## Sources de données

| Fichier                                 | Format | Lignes | Contenu                                       |
| --------------------------------------- | ------ | ------ | --------------------------------------------- |
| `WA_Fn-UseC_-HR-Employee-Attrition.csv` | CSV    | 1 470  | Attrition, salaire, satisfaction, performance |
| `HRDataset_v14.csv`                     | CSV    | 311    | Absences, recrutement, motifs de départ       |
| `formations.csv`                        | CSV    | ~2 600 | Formations, durées, coûts                     |
| `evaluations_semestrielles.xlsx`        | Excel  | ~1 800 | Scores semestriels, objectifs                 |

> Les fichiers `formations.csv` et `evaluations_semestrielles.xlsx` contiennent intentionnellement des imperfections (dates mixtes, valeurs nulles, casses variables) pour simuler des données réelles et justifier le travail de nettoyage ETL.

---

## Schéma Data Warehouse (H2)

### Table de faits — `fait_rh`

| Colonne                  | Type          | Source           |
| ------------------------ | ------------- | ---------------- |
| `employe_id` (FK)        | VARCHAR       | Toutes sources   |
| `dept_id` (FK)           | INT           | dim_departement  |
| `temps_id` (FK)          | INT           | dim_temps        |
| `poste_id` (FK)          | INT           | dim_poste        |
| `formation_id` (FK)      | INT           | dim_formation    |
| `salaire_mensuel`        | DECIMAL       | IBM + rshuebner  |
| `attrition`              | TINYINT (0/1) | IBM HR           |
| `score_performance`      | INT (1-4)     | IBM + rshuebner  |
| `satisfaction_employe`   | INT (1-4)     | IBM HR           |
| `nb_absences`            | INT           | rshuebner        |
| `heures_sup`             | TINYINT (0/1) | IBM HR           |
| `score_evaluation`       | INT (1-5)     | evaluations.xlsx |
| `objectifs_atteints_pct` | INT           | evaluations.xlsx |
| `cout_formation`         | DECIMAL       | formations.csv   |
| `nb_formations`          | INT           | formations.csv   |
| `duree_avant_depart`     | INT (jours)   | Calculé ETL      |
| `promotion_recommandee`  | TINYINT (0/1) | evaluations.xlsx |

### Dimensions

| Table             | PK           | Colonnes principales                              |
| ----------------- | ------------ | ------------------------------------------------- |
| `dim_employe`     | employe_id   | nom, age, genre, anciennete, statut, motif_depart |
| `dim_departement` | dept_id      | nom_dept, localisation, responsable               |
| `dim_temps`       | temps_id     | annee, semestre, mois, trimestre                  |
| `dim_poste`       | poste_id     | titre, niveau, source_recrutement                 |
| `dim_formation`   | formation_id | intitule, duree_jours, cout_usd                   |

---

## Dashboard — Sections et KPI

| Section         | KPI                                                    | Graphiques                                                        |
| --------------- | ------------------------------------------------------ | ----------------------------------------------------------------- |
| **Dashboard**   | Attrition, Effectif, Salaire moyen, Satisfaction       | Effectif/dept, Salaire/dept, Satisfaction/dept + Insights         |
| **Effectifs**   | Total, % actifs, Âge moyen, Ancienneté                 | Effectif/dept, Genre, Pyramide des âges, Ancienneté               |
| **Turnover**    | Taux attrition, Départs, Durée avant départ, Rétention | Attrition/dept, Motifs, Durée, Heures sup vs attrition            |
| **Performance** | Score perf, Satisfaction, % heures sup, Score éval.    | Perf/dept, Satisfaction/dept, Corrélation, Évolution semestrielle |
| **Formation**   | Nb formations, Coût total, Coût/employé, % formés      | Coût/dept, Top formations, Moy/dept, Durée                        |
| **Promotions**  | Candidats, % promouvables, Score perf, % objectifs     | Candidats/dept, Promouvables vs autres, Objectifs, Genre          |

---

## Prérequis

- **Java 21** (JDK) — [télécharger](https://adoptium.net/)
- **Maven 3.8+** — [télécharger](https://maven.apache.org/download.cgi)
- IntelliJ IDEA recommandé (Community ou Ultimate)

---

## Installation & Lancement

### 1. Cloner le projet

```bash
git clone <repo-url>
cd hr-reporting
```

### 2. Vérifier les fichiers sources

Les 4 fichiers de données doivent être présents dans :

```
src/main/resources/data/
├── WA_Fn-UseC_-HR-Employee-Attrition.csv
├── HRDataset_v14.csv
├── formations.csv
└── evaluations_semestrielles.xlsx
```

### 3. Compiler et lancer

```bash
mvn clean install
mvn exec:java -Dexec.mainClass="com.hrreporting.Main"
```

Ou directement depuis **IntelliJ** : ouvrir `Main.java` → bouton **Run**.

### 4. Ordre d'exécution automatique

1. Initialisation H2 + création du schéma étoile
2. Pipeline ETL — chargement et nettoyage des 4 sources
3. Ouverture du dashboard Swing

> La base de données H2 est générée automatiquement dans `database/hr_reporting_dw.mv.db` au premier lancement. Elle est recréée à chaque exécution.

---

## Dépendances Maven

```xml
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <version>2.2.224</version>
</dependency>
<dependency>
    <groupId>org.jfree</groupId>
    <artifactId>jfreechart</artifactId>
    <version>1.5.4</version>
</dependency>
<dependency>
    <groupId>com.opencsv</groupId>
    <artifactId>opencsv</artifactId>
    <version>5.9</version>
</dependency>
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
    <version>5.3.0</version>
</dependency>
<dependency>
    <groupId>com.formdev</groupId>
    <artifactId>flatlaf</artifactId>
    <version>3.4</version>
</dependency>
<dependency>
    <groupId>com.formdev</groupId>
    <artifactId>flatlaf-extras</artifactId>
    <version>3.4</version>
</dependency>
```

---

## Notes ETL — traitements appliqués

| Problème détecté                                      | Traitement                                         |
| ----------------------------------------------------- | -------------------------------------------------- |
| Formats de dates mixtes (`DD/MM`, `MM-DD`, `YYYY/MM`) | Parser multi-format avec `DateTimeFormatter`       |
| Coûts avec symboles (`$`, espaces, `.00`)             | Regex strip + parsing `Double`                     |
| Départements mal orthographiés (`sales`, `R and D`)   | Table de mapping normalisée                        |
| Valeurs nulles ou vides                               | Remplacement par valeur sentinelle (`-1` ou `N/A`) |
| IDs hétérogènes (`1` vs `10026` vs `EMP-0001`)        | Clé surrogate auto-incrémentée H2                  |
| Salaires annuels vs mensuels                          | Division par 12 si source rshuebner                |
| Objectifs avec ou sans `%`                            | Strip `%` + parsing `Integer`                      |

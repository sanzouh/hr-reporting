# HR Reporting — Système d'Information Décisionnel

> Application desktop Java simulant le reporting RH interne d'une entreprise.
> Pipeline ETL multi-sources → Data Warehouse H2 → Tableaux de bord JFreeChart.

---

## Stack technique

| Composant | Technologie |
|---|---|
| Langage | Java 21 |
| Build | Maven 3.8+ |
| Base de données | H2 embarqué (schéma étoile) |
| Visualisation | JFreeChart 1.5.4 |
| Lecture CSV | OpenCSV 5.9 |
| Lecture Excel | Apache POI 5.3.0 |
| UI | Java Swing |

---

## Architecture

```
[IBM HR CSV]          ─┐
[rshuebner CSV]       ─┤─→ ETL Java ─→ H2 Data Warehouse ─→ JFreeChart ─→ Swing UI
[formations.csv]      ─┤
[evaluations.xlsx]    ─┘
```

### Structure du projet

```
hr-reporting/
├── pom.xml
├── README.md
└── src/main/
    ├── java/com/hrreporting/
    │   ├── Main.java
    │   ├── etl/
    │   │   ├── ETLPipeline.java
    │   │   ├── IbmHrLoader.java
    │   │   ├── RshuebnerLoader.java
    │   │   ├── FormationsLoader.java
    │   │   └── EvaluationsLoader.java
    │   ├── db/
    │   │   ├── DatabaseManager.java
    │   │   └── DWRepository.java
    │   ├── model/
    │   │   └── FaitRH.java
    │   └── ui/
    │       ├── MainDashboard.java
    │       └── charts/
    │           ├── TurnoverChart.java
    │           ├── SalaireChart.java
    │           ├── AbsenteismeChart.java
    │           └── PerformanceChart.java
    └── resources/
        └── data/
            ├── WA_Fn-UseC_-HR-Employee-Attrition.csv
            ├── HRDataset_v14.csv
            ├── formations.csv
            └── evaluations_semestrielles.xlsx
```

---

## Sources de données

| Fichier | Format | Lignes | Contenu |
|---|---|---|---|
| `WA_Fn-UseC_-HR-Employee-Attrition.csv` | CSV | 1 470 | Attrition, salaire, satisfaction, performance |
| `HRDataset_v14.csv` | CSV | 311 | Absences, recrutement, motifs de départ |
| `formations.csv` | CSV | ~2 600 | Formations, durées, coûts (formats mixtes) |
| `evaluations_semestrielles.xlsx` | Excel | ~1 800 | Scores semestriels, objectifs, managers |

> Les fichiers `formations.csv` et `evaluations_semestrielles.xlsx` contiennent intentionnellement des imperfections (dates mixtes, valeurs nulles, casses variables) pour simuler des données réelles et justifier le travail de nettoyage ETL.

---

## Schéma Data Warehouse (H2)

### Table de faits — `fait_rh`

| Colonne | Type | Source |
|---|---|---|
| `employe_id` (FK) | VARCHAR | Toutes sources |
| `dept_id` (FK) | INT | dim_departement |
| `temps_id` (FK) | INT | dim_temps |
| `poste_id` (FK) | INT | dim_poste |
| `formation_id` (FK) | INT | dim_formation |
| `salaire_mensuel` | DECIMAL | IBM + rshuebner |
| `attrition` | TINYINT (0/1) | IBM HR |
| `score_performance` | INT (1-4) | IBM + rshuebner |
| `satisfaction_employe` | INT (1-4) | IBM HR |
| `nb_absences` | INT | rshuebner |
| `heures_sup` | TINYINT (0/1) | IBM HR |
| `score_evaluation` | INT (1-5) | evaluations.xlsx |
| `objectifs_atteints_pct` | INT | evaluations.xlsx |
| `cout_formation` | DECIMAL | formations.csv |
| `nb_formations` | INT | formations.csv |
| `duree_avant_depart` | INT (jours) | Calculé ETL |

### Dimensions

| Table | PK | Colonnes principales |
|---|---|---|
| `dim_employe` | employe_id | nom, age, genre, anciennete, statut, motif_depart |
| `dim_departement` | dept_id | nom_dept, localisation, responsable |
| `dim_temps` | temps_id | annee, semestre, mois, trimestre |
| `dim_poste` | poste_id | titre, niveau, source_recrutement |
| `dim_formation` | formation_id | intitule, duree_jours, cout_usd |

---

## KPI du dashboard

| Onglet | KPI affiché | Graphique |
|---|---|---|
| Effectifs | Répartition par département, genre, âge | Camembert, barres |
| Turnover | Taux d'attrition par dept, motifs de départ | Barres groupées, camembert |
| Masse salariale | Salaire moyen par poste, écart H/F | Barres horizontales |
| Performance | Score moyen par dept, satisfaction vs turnover | Barres + courbe |
| Absentéisme | Jours absences par dept, évolution mensuelle | Courbe temporelle |
| Formation | Coût total, nb formations, ROI par dept | Barres empilées |

---

## Lancement

### Prérequis

- Java 21 (JDK)
- Maven 3.8+
- IntelliJ IDEA (recommandé)

### Installation

```bash
git clone <repo-url>
cd hr-reporting

# Placer les 4 fichiers sources dans :
# src/main/resources/data/

mvn clean install
mvn exec:java -Dexec.mainClass="com.hrreporting.Main"
```

### Ordre d'exécution automatique

1. Initialisation H2 + création des tables DW
2. Pipeline ETL (chargement et nettoyage des 4 sources)
3. Ouverture du dashboard Swing avec graphiques JFreeChart

---

## Notes ETL — traitements appliqués

| Problème détecté | Traitement |
|---|---|
| Formats de dates mixtes (`DD/MM`, `MM-DD`, `YYYY/MM`) | Parser multi-format avec `DateTimeFormatter` |
| Coûts avec symboles (`$`, espaces, `.00`) | Regex strip + parsing `Double` |
| Départements mal orthographiés (`sales`, `R and D`) | Table de mapping normalisée |
| Valeurs nulles ou vides | Remplacement par valeur sentinelle (`-1` ou `N/A`) |
| IDs hétérogènes (`1` vs `10026` vs `EMP-0001`) | Clé surrogate auto-incrémentée H2 |
| Salaires annuels vs mensuels | Division par 12 si source rshuebner |
| Objectifs avec ou sans `%` | Strip `%` + parsing `Integer` |

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
```

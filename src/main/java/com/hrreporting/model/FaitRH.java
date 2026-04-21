package com.hrreporting.model;

/**
 * FaitRH — Objet métier représentant une ligne de la table de faits fait_rh.
 *
 * Valeur sentinelle : -1 pour tout champ numérique absent/non renseigné.
 * Utilisé par DWRepository.insertFaitsBatch() pour l'alimentation du DW.
 */
public class FaitRH {

    // ── Clés étrangères ───────────────────────────────────────────────
    private final String employeId;
    private final int    deptId;
    private final int    tempsId;
    private final int    posteId;
    private final int    formationId;        // -1 si pas de formation associée

    // ── Mesures / KPI ─────────────────────────────────────────────────
    private final double salaireMensuel;     // -1 si inconnu
    private final int    attrition;          // 0 = actif, 1 = parti
    private final int    scorePerformance;   // 1-4, -1 si inconnu
    private final int    satisfactionEmploye;// 1-4, -1 si inconnu
    private final int    nbAbsences;         // -1 si inconnu
    private final int    heuresSup;          // 0 = non, 1 = oui
    private final int    scoreEvaluation;    // 1-5, -1 si inconnu
    private final int    objectifsAtteintsP; // 0-100, -1 si inconnu
    private final double coutFormation;      // -1 si pas de formation
    private final int    nbFormations;       // -1 si inconnu
    private final int    dureeAvantDepart;   // jours, -1 si encore actif
    private final int    promotionRecommandee; // 0 = Non, 1 = Oui, -1 = inconnu
    private final int    anneeDepart;        // année effective du départ, -1 si encore actif
    private final int    anneeEmbauche;  // année réelle d'embauche (pour effectif actif)

    // ── Constructeur complet ──────────────────────────────────────────
    public FaitRH(String employeId, int deptId, int tempsId, int posteId, int formationId,
                  double salaireMensuel, int attrition, int scorePerformance,
                  int satisfactionEmploye, int nbAbsences, int heuresSup,
                  int scoreEvaluation, int objectifsAtteintsP,
                  double coutFormation, int nbFormations, int dureeAvantDepart,
                  int promotionRecommandee, int anneeDepart, int anneeEmbauche) {
        this.employeId           = employeId;
        this.deptId              = deptId;
        this.tempsId             = tempsId;
        this.posteId             = posteId;
        this.formationId         = formationId;
        this.salaireMensuel      = salaireMensuel;
        this.attrition           = attrition;
        this.scorePerformance    = scorePerformance;
        this.satisfactionEmploye = satisfactionEmploye;
        this.nbAbsences          = nbAbsences;
        this.heuresSup           = heuresSup;
        this.scoreEvaluation     = scoreEvaluation;
        this.objectifsAtteintsP  = objectifsAtteintsP;
        this.coutFormation       = coutFormation;
        this.nbFormations        = nbFormations;
        this.dureeAvantDepart    = dureeAvantDepart;
        this.promotionRecommandee = promotionRecommandee;
        this.anneeDepart         = anneeDepart;
        this.anneeEmbauche       = anneeEmbauche;
    }

    // ── Builder statique pour construction progressive ────────────────
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String employeId;
        private int    deptId              = -1;
        private int    tempsId             = -1;
        private int    posteId             = -1;
        private int    formationId         = -1;
        private double salaireMensuel      = -1;
        private int    attrition           = 0;
        private int    scorePerformance    = -1;
        private int    satisfactionEmploye = -1;
        private int    nbAbsences          = -1;
        private int    heuresSup           = 0;
        private int    scoreEvaluation     = -1;
        private int    objectifsAtteintsP  = -1;
        private double coutFormation       = -1;
        private int    nbFormations        = -1;
        private int    dureeAvantDepart    = -1;
        private int    promotionRecommandee= -1;
        private int    anneeDepart         = -1;
        private int    anneeEmbauche       = -1;

        public Builder employeId(String v)            { this.employeId = v; return this; }
        public Builder deptId(int v)                  { this.deptId = v; return this; }
        public Builder tempsId(int v)                 { this.tempsId = v; return this; }
        public Builder posteId(int v)                 { this.posteId = v; return this; }
        public Builder formationId(int v)             { this.formationId = v; return this; }
        public Builder salaireMensuel(double v)       { this.salaireMensuel = v; return this; }
        public Builder attrition(int v)               { this.attrition = v; return this; }
        public Builder scorePerformance(int v)        { this.scorePerformance = v; return this; }
        public Builder satisfactionEmploye(int v)     { this.satisfactionEmploye = v; return this; }
        public Builder nbAbsences(int v)              { this.nbAbsences = v; return this; }
        public Builder heuresSup(int v)               { this.heuresSup = v; return this; }
        public Builder scoreEvaluation(int v)         { this.scoreEvaluation = v; return this; }
        public Builder objectifsAtteintsP(int v)      { this.objectifsAtteintsP = v; return this; }
        public Builder coutFormation(double v)        { this.coutFormation = v; return this; }
        public Builder nbFormations(int v)            { this.nbFormations = v; return this; }
        public Builder dureeAvantDepart(int v)        { this.dureeAvantDepart = v; return this; }
        public Builder promotionRecommandee(int v)    { this.promotionRecommandee = v; return this; }
        public Builder anneeDepart(int v)             { this.anneeDepart = v; return this; }
        public Builder anneeEmbauche(int v)           { this.anneeEmbauche = v; return this; }

        public FaitRH build() {
            return new FaitRH(employeId, deptId, tempsId, posteId, formationId,
                    salaireMensuel, attrition, scorePerformance, satisfactionEmploye,
                    nbAbsences, heuresSup, scoreEvaluation, objectifsAtteintsP,
                    coutFormation, nbFormations, dureeAvantDepart,
                    promotionRecommandee, anneeDepart, anneeEmbauche);
        }
    }

    // ── Getters ───────────────────────────────────────────────────────
    public String getEmployeId()            { return employeId; }
    public int    getDeptId()               { return deptId; }
    public int    getTempsId()              { return tempsId; }
    public int    getPosteId()              { return posteId; }
    public int    getFormationId()          { return formationId; }
    public double getSalaireMensuel()       { return salaireMensuel; }
    public int    getAttrition()            { return attrition; }
    public int    getScorePerformance()     { return scorePerformance; }
    public int    getSatisfactionEmploye()  { return satisfactionEmploye; }
    public int    getNbAbsences()           { return nbAbsences; }
    public int    getHeuresSup()            { return heuresSup; }
    public int    getScoreEvaluation()      { return scoreEvaluation; }
    public int    getObjectifsAtteintsP()   { return objectifsAtteintsP; }
    public double getCoutFormation()        { return coutFormation; }
    public int    getNbFormations()         { return nbFormations; }
    public int    getDureeAvantDepart()     { return dureeAvantDepart; }
    public int    getPromotionRecommandee() { return promotionRecommandee; }
    public int    getAnneeDepart()          { return anneeDepart; }
    public int    getAnneeEmbauche()        { return anneeEmbauche; }

    @Override
    public String toString() {
        return "FaitRH{employeId='" + employeId + "', dept=" + deptId +
                ", salaire=" + salaireMensuel + ", attrition=" + attrition +
                ", anneeDepart=" + anneeDepart + ", anneeEmbauche=" + anneeEmbauche + "}";
    }
}
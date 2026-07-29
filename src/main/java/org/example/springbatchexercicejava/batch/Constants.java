package org.example.springbatchexercicejava.batch;

import java.time.format.DateTimeFormatter;

/** Constantes partagees par tous les TP (equivalent des constantes de fichier en Kotlin). */
public final class Constants {

    private Constants() {
    }

    public static final double TVA = 1.20;
    public static final int CHUNK_SIZE = 10;
    public static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    public static final String VENTES_CSV = "data/ventes.csv";
    // Les SORTIES generees vont dans data/out/.
    public static final String TP5_OUTPUT = "data/out/tp5_ventes.csv";

    // TP6 : 2e fichier source propose dans le menu deroulant de l'IHM (12 lignes).
    public static final String TP6_VENTES_CSV = "data/tp6_ventes.csv";

    // TP7 : 80 ventes dont 5 corrompues (3 illisibles + 2 montants negatifs).
    public static final String TP7_VENTES_5L_CSV = "data/tp7_ventes_5lcorrompues.csv";

    // TP7 : 80 ventes dont 10 corrompues (7 illisibles + 3 montants negatifs).
    public static final String TP7_VENTES_10L_CSV = "data/tp7_ventes_10lcorrompues.csv";

    // TP7 : les lignes rejetees et leur cause, ECRASE a chaque execution du step (sortie -> data/out/).
    public static final String TP7_REJETS_CSV = "data/out/tp7_rejets.csv";

    // TP7 : 4 lignes valides + 1 ligne PARFAITEMENT lisible mais a date invalide
    // ("pas-une-date"). Elle passe le reader et la regle metier, mais fait echouer
    // LocalDate.parse -> DateTimeParseException, une exception NI skippee NI retry.
    public static final String TP7_VENTES_ERREUR_INATTENDUE_CSV = "data/tp7_ventes_erreur_inattendue.csv";

    // TP8 : objectif de chiffre d'affaires du jour. Le bilan (afterStep) en deduit
    // l'ExitStatus metier : >= objectif -> OBJECTIF_ATTEINT, >= moitie -> A_SURVEILLER,
    // sinon -> ALERTE. Ces exitCodes aiguilleront les flows au TP8 partie 2.
    public static final double OBJECTIF_CA = 1000.0;

    // TP10 : 500 ventes a importer. Le processor simule une action LENTE (Thread.sleep)
    // pour rendre le step long en mono-thread -> on le parallelise avec un TaskExecutor.
    public static final String TP10_VENTES_CSV = "data/tp10_ventes.csv";

    // Duree du "gros traitement" simule par item (ms). En mono-thread : 500 x cette duree.
    public static final long TP10_SLEEP_MS = 8L;

    // Meme "gros traitement" simule qu'au TP10, pour rendre le parallelisme observable.
    public static final long TP11_SLEEP_MS = 8L;

    // TP12 : tests de batch. Le job fusionne deux CSV en un 3e, en supprimant les
    // doublons (lignes identiques). L'en-tete attendue est VALIDEE par le reader
    // (skippedLinesCallback) : ordre/nom des colonnes faux -> refus.
    public static final String TP12_ENTETE = "date;idBoutique;produit;montantHt";
    public static final String TP12_VENTES_A = "data/tp12/ventes_A.csv";
    public static final String TP12_VENTES_B = "data/tp12/ventes_B.csv";
    public static final String TP12_FUSION_OUTPUT = "data/out/tp12_fusion.csv";

    /** Le fichier du soir : 500 commandes, toutes valides. */
    public static final String TPFINAL_COMMANDES_CSV = "data/tpfinal/commandes.csv";


    /** Racine des sorties. L'archive et les rejets vont dans un sous-dossier /dateDuJour. */
    public static final String TPFINAL_OUT = "data/out";

    /** Chargement des camions : une ligne "Ville : total" par ville. */
    public static final String TPFINAL_CAMIONS_TXT = "data/out/entrepot/camions.txt";

    /** Tournees des chauffeurs : un fichier <Ville>.txt par ville. */
    public static final String TPFINAL_CHAUFFEURS_DIR = "data/out/chauffeurs";

}

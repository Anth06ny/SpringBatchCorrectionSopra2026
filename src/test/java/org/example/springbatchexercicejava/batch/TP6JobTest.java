package org.example.springbatchexercicejava.batch;

import org.example.springbatchexercicejava.batch.config.TP6_JobConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamReader;
import org.springframework.batch.test.MetaDataInstanceFactory;
import org.springframework.batch.test.StepScopeTestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.example.springbatchexercicejava.batch.config.TP6_JobConfig.cheminRapportTp6;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Suite de validation du TP6 (passage de parametres / late binding).
 *
 * Les tests sont numerotes et executes dans l'ordre du COMPOSANT concerne :
 *   01-02  READER     : le parametre `fichierSource` choisit le fichier lu
 *   03-06  PROCESSOR  : `montantMini` filtre, `totalTtc` convertit
 *   07-10  WRITER     : `format` choisit le writer, l'extension et l'en-tete
 *   11-12  JOB        : le JobParametersValidator refuse les parametres invalides
 *   13     CHAINE     : les 4 parametres combines sur un meme lancement
 *
 * Tant que les beans ne sont pas @StepScope (avec @Value("#{jobParameters[...]}")),
 * ces tests echouent : c'est exactement le retour attendu cote projet apprenant.
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.MethodName.class)
class TP6JobTest {

    @Autowired
    JobOperator jobOperator;

    @Autowired
    @Qualifier("tp6Job")
    Job tp6Job;

    // Le PROXY @StepScope, injectable normalement comme n'importe quel bean
    @Autowired
    ItemStreamReader<TP6_JobConfig.TP6VenteDTO> tp6Reader;

    /* ========================================================================= */
    /* READER — le parametre `fichierSource` decide du fichier lu                */
    /* ========================================================================= */

    /**
     * Les deux tests suivants sont volontairement symetriques : un reader qui aurait
     * le fichier EN DUR passerait l'un des deux mais echouerait sur l'autre.
     */
    @Test
    @DisplayName("01 reader - le parametre fichierSource choisit ventes.csv")
    void test01ReaderFichierSourceVentesCsv() throws Exception {
        JobExecution execution = lancer(Constants.VENTES_CSV, "CSV", false, 0.0);

        List<String> attendues = lignesSource(Constants.VENTES_CSV);   // 25 lignes
        List<String> obtenues = lignesData(cheminRapportTp6("CSV"));

        assertEquals(
                attendues.size(), obtenues.size(),
                "La sortie doit contenir autant de lignes que ventes.csv"
        );
        assertEquals(
                attendues.stream().map(this::produit).toList(),
                obtenues.stream().map(this::produit).toList(),
                "Les produits exportes doivent etre ceux de ventes.csv"
        );
        assertEquals((long) attendues.size(), stepChunk(execution).getReadCount());
    }

    @Test
    @DisplayName("02 reader - le parametre fichierSource choisit tp6_ventes.csv")
    void test02ReaderFichierSourceTp6VentesCsv() throws Exception {
        JobExecution execution = lancer(Constants.TP6_VENTES_CSV, "CSV", false, 0.0);

        List<String> attendues = lignesSource(Constants.TP6_VENTES_CSV);  // 12 lignes
        List<String> obtenues = lignesData(cheminRapportTp6("CSV"));

        assertEquals(
                attendues.size(), obtenues.size(),
                "La sortie doit contenir autant de lignes que tp6_ventes.csv"
        );
        assertEquals(
                attendues.stream().map(this::produit).toList(),
                obtenues.stream().map(this::produit).toList(),
                "Les produits exportes doivent etre ceux de tp6_ventes.csv"
        );
        assertEquals((long) attendues.size(), stepChunk(execution).getReadCount());
    }

    /**
     * Meme point que les tests 01/02 (le SpEL `#{jobParameters['fichierSource']}` doit se
     * resoudre), mais isole : pas de job, pas de processor, pas de writer, pas de fichier de
     * sortie. Si ce test casse, le probleme vient du reader ; s'il passe mais que 01/02
     * echouent, le probleme est ailleurs dans la chaine (processor, writer...).
     */
    @Test
    @DisplayName("02b reader isole - StepScope resout fichierSource sans lancer tout le job")
    void test02bReaderIsoleStepScope() throws Exception {
        JobParameters params = new JobParametersBuilder()
                .addString("fichierSource", Constants.TP6_VENTES_CSV)
                .toJobParameters();

        // Fabrique une StepExecution portant CES JobParameters, sans jamais lancer tp6Job.
        StepExecution stepExecution = MetaDataInstanceFactory.createStepExecution(params);

        // StepScopeTestUtils.doInStepScope enregistre ce StepExecution le temps du bloc.
        TP6_JobConfig.TP6VenteDTO premiereLigne = StepScopeTestUtils.doInStepScope(stepExecution, () -> {
            tp6Reader.open(new ExecutionContext());
            TP6_JobConfig.TP6VenteDTO ligne = tp6Reader.read();
            if (ligne == null) {
                throw new IllegalStateException("tp6Reader n'a renvoye aucune ligne : fichierSource mal resolu ?");
            }
            return ligne;
        });

        // tp6_ventes.csv (boutiques B05-B07) : premiere ligne de donnees = B05
        assertEquals("B05", premiereLigne.getIdBoutique());
    }

    /* ========================================================================= */
    /* PROCESSOR — `montantMini` filtre, `totalTtc` convertit                    */
    /* ========================================================================= */

    @Test
    @DisplayName("03 processor - montantMini filtre les lignes sous le seuil")
    void test03ProcessorMontantMiniFiltre() throws Exception {
        double seuil = 50.0;
        JobExecution execution = lancer(Constants.VENTES_CSV, "CSV", false, seuil);

        List<String> source = lignesSource(Constants.VENTES_CSV);
        List<String> gardees = source.stream().filter(l -> montant(l) >= seuil).toList();
        List<String> obtenues = lignesData(cheminRapportTp6("CSV"));

        assertTrue(
                gardees.size() < source.size(),
                "Le seuil de test doit ecarter des lignes, sinon le test ne prouve rien"
        );
        assertEquals(
                gardees.size(), obtenues.size(),
                "Seules les ventes dont le montant HT atteint " + seuil + " doivent etre exportees"
        );
        assertTrue(
                obtenues.stream().allMatch(l -> montant(l) >= seuil),
                "Aucune ligne sous le seuil ne doit se retrouver dans la sortie"
        );

        // Un processor qui renvoie null n'ecrit pas : la ligne compte dans filterCount.
        StepExecution step = stepChunk(execution);
        assertEquals((long) source.size(), step.getReadCount(), "Toutes les lignes doivent etre LUES");
        assertEquals((long) gardees.size(), step.getWriteCount(), "writeCount = lignes gardees");
        assertEquals(
                (long) (source.size() - gardees.size()), step.getFilterCount(),
                "Les lignes ecartees par le processor doivent apparaitre en filterCount"
        );
    }

    @Test
    @DisplayName("04 processor - montantMini a zero ne filtre aucune ligne")
    void test04ProcessorMontantMiniZero() throws Exception {
        JobExecution execution = lancer(Constants.TP6_VENTES_CSV, "CSV", false, 0.0);

        StepExecution step = stepChunk(execution);
        assertEquals(0L, step.getFilterCount(), "Avec un seuil a 0, aucune ligne ne doit etre filtree");
        assertEquals(step.getReadCount(), step.getWriteCount(), "Toutes les lignes lues doivent etre ecrites");
    }

    @Test
    @DisplayName("05 processor - totalTtc coche convertit les montants en TTC")
    void test05ProcessorTotalTtcCoche() throws Exception {
        lancer(Constants.TP6_VENTES_CSV, "CSV", true, 0.0);

        List<String> attendus = lignesSource(Constants.TP6_VENTES_CSV).stream()
                .map(l -> format(montant(l) * Constants.TVA)).toList();
        List<String> obtenus = lignesData(cheminRapportTp6("CSV")).stream()
                .map(l -> format(montant(l))).toList();

        assertEquals(
                attendus, obtenus,
                "Case cochee : les montants doivent valoir HT x 1,20"
        );
    }

    @Test
    @DisplayName("06 processor - totalTtc decoche laisse les montants en HT")
    void test06ProcessorTotalTtcDecoche() throws Exception {
        lancer(Constants.TP6_VENTES_CSV, "CSV", false, 0.0);

        List<String> attendus = lignesSource(Constants.TP6_VENTES_CSV).stream()
                .map(l -> format(montant(l))).toList();
        List<String> obtenus = lignesData(cheminRapportTp6("CSV")).stream()
                .map(l -> format(montant(l))).toList();

        assertEquals(
                attendus, obtenus,
                "Case decochee : les montants doivent rester ceux du fichier source"
        );
    }

    /* ========================================================================= */
    /* WRITER — `format` choisit le writer, l'extension et l'en-tete             */
    /* ========================================================================= */

    @Test
    @DisplayName("07 writer - format CSV produit un fichier csv delimite")
    void test07WriterFormatCsv() throws Exception {
        lancer(Constants.TP6_VENTES_CSV, "CSV", false, 0.0);

        List<String> lignes = lignesFichier(cheminRapportTp6("CSV"));
        List<String> donnees = lignes.stream().skip(1).toList(); // on saute l'en-tete

        assertTrue(!donnees.isEmpty(), "Le fichier CSV doit contenir des lignes de donnees");
        assertTrue(
                donnees.stream().allMatch(l -> l.split(";").length == 4),
                "Chaque ligne CSV doit avoir 4 colonnes separees par ';'"
        );
    }

    @Test
    @DisplayName("08 writer - format JSON produit un tableau json")
    void test08WriterFormatJson() throws Exception {
        lancer(Constants.TP6_VENTES_CSV, "JSON", false, 0.0);

        String contenu = Files.readString(Path.of(cheminRapportTp6("JSON"))).trim();

        assertTrue(contenu.startsWith("["), "Le rapport JSON doit etre un tableau");
        assertTrue(contenu.endsWith("]"), "Le rapport JSON doit etre un tableau");
        assertTrue(contenu.contains("idBoutique"), "Le DTO du TP6 doit etre serialise tel quel");

        List<Double> montantsJson = montantsDuJson(contenu);
        List<String> attendus = lignesSource(Constants.TP6_VENTES_CSV).stream()
                .map(l -> format(montant(l))).toList();

        assertEquals(
                attendus, montantsJson.stream().map(this::format).toList(),
                "Le JSON doit contenir les memes ventes que le CSV source"
        );
    }

    @Test
    @DisplayName("09 writer - l'extension du fichier de sortie suit le format")
    void test09WriterExtension() throws Exception {
        assertEquals("data/out/tp6_ventes_sortie.csv", cheminRapportTp6("CSV"));
        assertEquals("data/out/tp6_ventes_sortie.json", cheminRapportTp6("JSON"));

        Files.deleteIfExists(Path.of(cheminRapportTp6("CSV")));
        Files.deleteIfExists(Path.of(cheminRapportTp6("JSON")));

        lancer(Constants.TP6_VENTES_CSV, "CSV", false, 0.0);
        assertTrue(Files.exists(Path.of(cheminRapportTp6("CSV"))), "format=CSV doit ecrire le fichier .csv");

        lancer(Constants.TP6_VENTES_CSV, "JSON", false, 0.0);
        assertTrue(Files.exists(Path.of(cheminRapportTp6("JSON"))), "format=JSON doit ecrire le fichier .json");
    }

    @Test
    @DisplayName("10 writer - l'en-tete CSV indique montantHT ou montantTTC")
    void test10WriterEntete() throws Exception {
        lancer(Constants.TP6_VENTES_CSV, "CSV", false, 0.0);
        assertEquals(
                "date;idBoutique;produit;montantHT", lignesFichier(cheminRapportTp6("CSV")).get(0),
                "Case decochee : la 4e colonne doit s'appeler montantHT"
        );

        lancer(Constants.TP6_VENTES_CSV, "CSV", true, 0.0);
        assertEquals(
                "date;idBoutique;produit;montantTTC", lignesFichier(cheminRapportTp6("CSV")).get(0),
                "Case cochee : la 4e colonne doit s'appeler montantTTC"
        );
    }

    /* ========================================================================= */
    /* JOB — le JobParametersValidator                                           */
    /* ========================================================================= */

    @Test
    @DisplayName("11 job - le validator refuse un parametre manquant")
    void test11JobValidatorRefuseParametreManquant() {
        // `format`, `totalTtc` et `montantMini` sont absents : le validator doit refuser
        // le lancement AVANT que le reader ne tente d'ouvrir un fichier.
        JobParameters incomplets = new JobParametersBuilder()
                .addString("fichierSource", Constants.VENTES_CSV)
                .addLong("timestamp", System.nanoTime())
                .toJobParameters();

        assertThrows(Exception.class, () -> jobOperator.start(tp6Job, incomplets),
                "Un parametre requis manquant doit empecher le demarrage");
    }

    @Test
    @DisplayName("12 job - le validator tolere un parametre inconnu et se contente d'un warning")
    void test12JobValidatorTolereParametreInconnu() throws Exception {
        // Tous les parametres requis sont la, mais `couleur` n'est ni requis ni optionnel.
        // ATTENTION : DefaultJobParametersValidator ne LEVE PAS pour une cle inconnue,
        // il se contente d'un logger.warn(). Seule une cle REQUISE manquante fait echouer
        // le demarrage (cf. test 11). A savoir si on attend une validation stricte.
        JobParameters inconnu = new JobParametersBuilder()
                .addString("fichierSource", Constants.VENTES_CSV)
                .addString("format", "CSV")
                .addString("totalTtc", "false")
                .addDouble("montantMini", 0.0)
                .addString("couleur", "rouge")
                .addLong("timestamp", System.nanoTime())
                .toJobParameters();

        JobExecution execution = jobOperator.start(tp6Job, inconnu);

        assertEquals(
                BatchStatus.COMPLETED, execution.getStatus(),
                "Une cle inconnue ne doit pas empecher le job de tourner (simple warning)"
        );
    }

    /* ========================================================================= */
    /* CHAINE COMPLETE — les 4 parametres sur un meme lancement                  */
    /* ========================================================================= */

    @Test
    @DisplayName("13 chaine complete - les quatre parametres se combinent")
    void test13ChaineComplete() throws Exception {
        double seuil = 50.0;
        // ventes.csv + JSON + TTC + seuil : chaque parametre doit jouer son role.
        JobExecution execution = lancer(Constants.VENTES_CSV, "JSON", true, seuil);

        List<String> source = lignesSource(Constants.VENTES_CSV);
        List<String> gardees = source.stream().filter(l -> montant(l) >= seuil).toList();  // filtre sur le HT
        List<String> attendus = gardees.stream()
                .map(l -> format(montant(l) * Constants.TVA)).toList();                    // puis conversion TTC

        String contenu = Files.readString(Path.of(cheminRapportTp6("JSON"))).trim();
        List<String> obtenus = montantsDuJson(contenu).stream().map(this::format).toList();

        assertEquals(
                attendus, obtenus,
                "Le filtre porte sur le HT, la conversion TTC s'applique ensuite, le tout en JSON"
        );

        StepExecution step = stepChunk(execution);
        assertEquals((long) source.size(), step.getReadCount());
        assertEquals((long) gardees.size(), step.getWriteCount());
        assertEquals((long) (source.size() - gardees.size()), step.getFilterCount());
    }

    /* --------------------------------- Helpers --------------------------------- */

    private JobExecution lancer(String fichierSource, String format, boolean totalTtc, double montantMini)
            throws Exception {
        JobParameters params = new JobParametersBuilder()
                .addString("fichierSource", fichierSource)
                .addString("format", format)
                .addString("totalTtc", String.valueOf(totalTtc))
                .addDouble("montantMini", montantMini)
                .addLong("timestamp", System.nanoTime())
                .toJobParameters();

        JobExecution execution = jobOperator.start(tp6Job, params);
        assertEquals(
                BatchStatus.COMPLETED, execution.getStatus(),
                "tp6Job doit se terminer avec succes (" + execution.getAllFailureExceptions() + ")"
        );
        return execution;
    }

    private StepExecution stepChunk(JobExecution execution) {
        return execution.getStepExecutions().stream()
                .filter(s -> "tp6Step".equals(s.getStepName()))
                .findFirst()
                .orElseThrow();
    }

    /** Lignes non vides du fichier genere (racine du projet = repertoire de travail). */
    private List<String> lignesFichier(String chemin) throws IOException {
        return Files.readAllLines(Path.of(chemin)).stream().filter(l -> !l.isBlank()).toList();
    }

    /** Lignes de donnees de la sortie CSV (en-tete exclu). */
    private List<String> lignesData(String chemin) throws IOException {
        return lignesFichier(chemin).stream().filter(ligne -> {
            String[] cols = ligne.split(";");
            if (cols.length != 4) {
                return false;
            }
            try {
                Double.parseDouble(cols[3]);
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }).toList();
    }

    /** Lignes de donnees d'un CSV source (en-tete saute). */
    private List<String> lignesSource(String chemin) throws IOException {
        return Files.readAllLines(Path.of(chemin)).stream()
                .filter(l -> !l.isBlank())
                .skip(1)
                .toList();
    }

    /** Les valeurs du champ `montant` d'un rapport JSON, dans l'ordre du fichier. */
    private List<Double> montantsDuJson(String contenu) {
        Matcher m = Pattern.compile("\"montant\"\\s*:\\s*([0-9.]+)").matcher(contenu);
        List<Double> montants = new ArrayList<>();
        while (m.find()) {
            montants.add(Double.parseDouble(m.group(1)));
        }
        return montants;
    }

    private double montant(String ligne) {
        return Double.parseDouble(ligne.split(";")[3]);
    }

    private String produit(String ligne) {
        return ligne.split(";")[2];
    }

    private String format(double montant) {
        return String.format(Locale.US, "%.2f", montant);
    }
}

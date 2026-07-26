package org.example.springbatchexercicejava.batch;

import org.example.springbatchexercicejava.batch.model.VenteEntity;
import org.example.springbatchexercicejava.batch.repository.VenteRepository;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeParseException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Suite de validation du TP7 (tolerance aux fautes : skip, retry, tracage des rejets).
 *
 * Les tests sont numerotes et executes dans l'ordre du cours :
 *   01-02  SKIP LECTURE    : FlatFileParseException skippee, skipLimit(6)
 *   03-05  SKIP TRAITEMENT : VenteInvalideException (montant <= 0) skippee
 *   06-08  LISTENER        : les rejets sont traces dans data/out/tp7_rejets.csv
 *   09-11  RETRY           : VenteInstableException (panne simulee) rejouee
 *   12     CHAINE          : skip et retry combines sur le fichier 10L
 *
 * Rappel des jeux de donnees (80 ventes chacun, memes lignes valides) :
 *   - tp7_ventes_5lcorrompues.csv  : 3 illisibles + 2 montants negatifs -> 75 valides
 *   - tp7_ventes_10lcorrompues.csv : 7 illisibles + 3 montants negatifs -> 70 valides
 *
 * MOTEUR BATCH 6 : le skip (lecture comme traitement) et le retry se font SUR PLACE,
 * sans rollback du chunk — les tests 05 et 11 verrouillent ce comportement.
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.MethodName.class)
class TP7JobTest {

    @Autowired
    JobOperator jobOperator;

    @Autowired
    @Qualifier("tp7Job")
    Job tp7Job;

    @Autowired
    VenteRepository venteRepository;

    /* ========================================================================= */
    /* SKIP LECTURE — les lignes illisibles sont ecartees, dans la limite de 6   */
    /* ========================================================================= */

    @Test
    @DisplayName("01 skip lecture - tp7_ventes_5lcorrompues passe sous la limite")
    void test01SkipLecture5L() throws Exception {
        JobExecution execution = lancer(Constants.TP7_VENTES_5L_CSV, false);

        assertEquals(BatchStatus.COMPLETED, execution.getStatus(),
                "3 lignes illisibles < skipLimit(6) : le job doit reussir");
        assertEquals(3L, step(execution).getReadSkipCount(),
                "Les 3 lignes illisibles doivent etre skippees en LECTURE");
        assertEquals(77L, step(execution).getReadCount(),
                "readCount ne compte pas les lignes skippees : 80 - 3 = 77");
    }

    @Test
    @DisplayName("02 skip lecture - tp7_ventes_10lcorrompues depasse la limite")
    void test02SkipLecture10L() throws Exception {
        JobExecution execution = lancer(Constants.TP7_VENTES_10L_CSV, false);

        assertEquals(BatchStatus.FAILED, execution.getStatus(),
                "10 lignes corrompues > skipLimit(6) : le job doit echouer");
        assertTrue(step(execution).getExitStatus().getExitDescription().contains("SkipLimitExceeded"),
                "L'echec doit venir du depassement de la limite de skip");
        assertEquals(6L, step(execution).getSkipCount(),
                "La limite est un DEPASSEMENT : 6 rejets sont passes, le 7e a tout arrete");
    }

    /* ========================================================================= */
    /* SKIP TRAITEMENT — la regle metier (montant <= 0) rejette via une exception */
    /* ========================================================================= */

    @Test
    @DisplayName("03 skip traitement - les montants negatifs sont rejetes")
    void test03SkipTraitementMontantsNegatifs() throws Exception {
        JobExecution execution = lancer(Constants.TP7_VENTES_5L_CSV, false);

        assertEquals(2L, step(execution).getProcessSkipCount(),
                "Les 2 montants negatifs doivent etre skippes en TRAITEMENT (VenteInvalideException)");
        assertEquals(0L, step(execution).getFilterCount(),
                "Un rejet par exception n'est PAS un filtrage (pas de return null ici)");
    }

    /**
     * CONTRE-EXEMPLE : le step ne skippe QUE les exceptions declarees
     * (FlatFileParseException, VenteInvalideException). Toute AUTRE exception
     * n'est pas rattrapee -> elle arrete le job. Ici une ligne parfaitement
     * lisible mais a date invalide ("pas-une-date") passe le reader et la regle
     * metier, puis fait echouer LocalDate.parse -> DateTimeParseException.
     * C'est le garde-fou : une erreur inconnue n'est jamais ignoree en silence.
     */
    @Test
    @DisplayName("04 skip traitement - une exception non declaree n'est PAS skippee et arrete le job")
    void test04SkipTraitementExceptionNonDeclaree() throws Exception {
        JobExecution execution = lancer(Constants.TP7_VENTES_ERREUR_INATTENDUE_CSV, false);

        assertEquals(BatchStatus.FAILED, execution.getStatus(),
                "Une exception hors de la liste des skips doit faire ECHOUER le job");
        assertEquals(0L, step(execution).getSkipCount(),
                "La ligne fautive n'est PAS skippee : ce n'est ni FlatFileParseException ni VenteInvalideException");
        assertFalse(step(execution).getExitStatus().getExitDescription().contains("SkipLimitExceeded"),
                "L'echec vient de l'exception non geree, pas d'un depassement de skipLimit");
        assertTrue(
                step(execution).getFailureExceptions().stream()
                        .anyMatch(ex -> aPourCause(ex, DateTimeParseException.class)),
                "La cause de l'echec doit etre la DateTimeParseException de LocalDate.parse");
    }

    @Test
    @DisplayName("05 skip traitement - seules les lignes valides sont en base")
    void test05SkipTraitementLignesValides() throws Exception {
        lancer(Constants.TP7_VENTES_5L_CSV, false);

        List<VenteEntity> ventes = venteRepository.findAll();
        assertEquals(75, ventes.size(),
                "80 lignes - 3 illisibles - 2 montants negatifs = 75 ventes en base");
        assertTrue(ventes.stream().noneMatch(v -> v.getPrixHt() <= 0),
                "Aucun montant negatif ne doit atteindre la base");
        // Somme calculee depuis les entites (sans dependre de sumPrixTtc, exercice du TP5).
        double sommeTtc = ventes.stream().mapToDouble(VenteEntity::getPrixTtc).sum();
        assertTrue(Math.abs(sommeTtc - 6167.35) < 0.1,
                "La somme TTC attendue est 6167.35 (75 lignes valides), obtenu : " + sommeTtc);
    }

    /* ========================================================================= */
    /* LISTENER — chaque rejet est trace dans data/out/tp7_rejets.csv            */
    /* ========================================================================= */

    @Test
    @DisplayName("06 listener - tp7_rejets.csv contient une ligne par rejet")
    void test06ListenerUneLigneParRejet() throws Exception {
        lancer(Constants.TP7_VENTES_5L_CSV, false);

        assertEquals(5, rejets().size(),
                "5 lignes corrompues = 5 rejets traces (3 lecture + 2 traitement)");
    }

    @Test
    @DisplayName("07 listener - tp7_rejets.csv precise la phase et la cause")
    void test07ListenerPhaseEtCause() throws Exception {
        lancer(Constants.TP7_VENTES_5L_CSV, false);

        List<String> lignes = rejets();
        assertEquals(3, lignes.stream().filter(l -> l.startsWith("LECTURE;")).count(),
                "3 rejets en phase LECTURE (onSkipInRead)");
        assertEquals(2, lignes.stream().filter(l -> l.startsWith("TRAITEMENT;")).count(),
                "2 rejets en phase TRAITEMENT (onSkipInProcess)");
        assertTrue(lignes.stream().anyMatch(l -> l.startsWith("LECTURE;") && l.contains("quarante euros")),
                "La ligne brute illisible doit etre recopiee (FlatFileParseException.input)");
        assertTrue(lignes.stream().anyMatch(l -> l.startsWith("LECTURE;") && l.contains("illisible")),
                "La cause d'un rejet de lecture doit indiquer une ligne illisible");
        assertTrue(lignes.stream().anyMatch(l -> l.startsWith("TRAITEMENT;") && l.contains("Montant invalide")),
                "La cause d'un rejet de traitement doit venir de VenteInvalideException");
    }

    @Test
    @DisplayName("08 listener - tp7_rejets.csv est ecrase a chaque execution")
    void test08ListenerFichierEcrase() throws Exception {
        lancer(Constants.TP7_VENTES_10L_CSV, false);   // laisse 6 rejets dans le fichier
        lancer(Constants.TP7_VENTES_5L_CSV, false);    // beforeStep doit REPARTIR d'un fichier vierge

        List<String> lignes = rejets();
        assertEquals(5, lignes.size(),
                "Les rejets du lancement precedent ne doivent pas s'accumuler");
        assertTrue(lignes.stream().noneMatch(l -> l.contains("abc")),
                "Aucune trace du fichier 10L (corruption 'abc') ne doit rester");
    }

    /* ========================================================================= */
    /* RETRY — la panne passagere est rejouee au lieu de faire echouer le step   */
    /* ========================================================================= */

    @Test
    @DisplayName("09 retry - risque desactive, le job passe sans panne")
    void test09RetryRisqueDesactive() throws Exception {
        JobExecution execution = lancer(Constants.TP7_VENTES_5L_CSV, false);

        assertEquals(BatchStatus.COMPLETED, execution.getStatus());
        assertEquals(75L, step(execution).getWriteCount());
    }

    @Test
    @DisplayName("10 retry - risque active, le job passe grace au retry")
    void test10RetryRisqueActive() throws Exception {
        JobExecution execution = lancer(Constants.TP7_VENTES_5L_CSV, true);

        assertEquals(BatchStatus.COMPLETED, execution.getStatus(),
                "Chaque panne simulee (1 appel sur 10) doit etre rejouee avec succes");
        assertEquals(75L, step(execution).getWriteCount(),
                "Le retry ne doit perdre AUCUNE ligne valide (contrairement a un skip)");
        assertEquals(75, (int) venteRepository.count());
    }

    /* ========================================================================= */
    /* CHAINE COMPLETE — skip et retry se combinent                              */
    /* ========================================================================= */

    @Test
    @DisplayName("11 chaine complete - skip et retry se combinent")
    void test11ChaineComplete() throws Exception {
        JobExecution execution = lancer(Constants.TP7_VENTES_10L_CSV, true);

        assertEquals(BatchStatus.FAILED, execution.getStatus(),
                "Le retry guerit les pannes, mais les 10 lignes corrompues depassent toujours skipLimit(6)");
        assertTrue(step(execution).getExitStatus().getExitDescription().contains("SkipLimitExceeded"),
                "L'echec doit venir du skip, pas de la panne simulee (guerie par le retry)");
    }

    /* ========================================================================= */
    /* Aides                                                                     */
    /* ========================================================================= */

    /**
     * Lance tp7Job comme le ferait l'IHM : table VENTE videe avant, runId unique
     * (nouvelle JobInstance a chaque appel), fichier et risque non-identifiants.
     */
    private JobExecution lancer(String fichierSource, boolean risque) throws Exception {
        venteRepository.deleteAll();
        JobParameters params = new JobParametersBuilder()
                .addString("runId", "test-" + System.nanoTime())
                .addString("fichierSource", fichierSource, false)
                .addString("risque", String.valueOf(risque), false)
                .toJobParameters();
        return jobOperator.start(tp7Job, params);
    }

    private StepExecution step(JobExecution execution) {
        return execution.getStepExecutions().stream()
                .filter(s -> "tp7Step".equals(s.getStepName()))
                .findFirst()
                .orElseThrow();
    }

    /** Cherche un type d'exception dans la chaine des causes (l'exception elle-meme incluse). */
    private boolean aPourCause(Throwable t, Class<? extends Throwable> type) {
        for (Throwable courant = t; courant != null; courant = courant.getCause()) {
            if (type.isInstance(courant)) {
                return true;
            }
            if (courant.getCause() == courant) {
                break;
            }
        }
        return false;
    }

    /** Les lignes de rejet (sans la ligne d'en-tete `phase;donnee;cause`). */
    private List<String> rejets() throws IOException {
        return Files.readAllLines(Path.of(Constants.TP7_REJETS_CSV)).stream()
                .skip(1)
                .filter(l -> !l.isBlank())
                .toList();
    }
}

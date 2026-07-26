package org.example.springbatchexercicejava.batch;

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
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.test.JobOperatorTestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Suite de validation du TP9 (conditionnement du flow).
 *
 * 1 test = 1 exercice. Chaque test lance le job de l'exercice avec les parametres
 * qui pilotent la branche (scenario / montant / echouer) et verifie :
 *   - le BatchStatus final (COMPLETED / FAILED / STOPPED) ;
 *   - le CHEMIN reellement parcouru = la liste des steps executes.
 *
 * Les briques (steps) sont dans TP9_Steps ; les jobs a reproduire dans TP9_JobConfig.
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.MethodName.class)
class TP9JobTest {

    @Autowired
    JobOperator jobOperator;

    @Autowired
    ApplicationContext applicationContext;

    @Autowired
    JobRepository jobRepository;

    // Le step a isoler : injecte directement, pas besoin de passer par un Job complet
    @Autowired
    @Qualifier("controleStep")
    Step controleStep;

    /* ===== EX 1 — Sequentiel ================================================= */

    @Test
    @DisplayName("01 sequentiel - preparation puis expedition puis archivage")
    void test01Sequentiel() throws Exception {
        JobExecution execution = lancer(1, "", 0.0, false);

        assertEquals(BatchStatus.COMPLETED, execution.getStatus());
        assertEquals(List.of("preparationStep", "expeditionStep", "archivageStep"), steps(execution));
    }

    /* ===== EX 2 — Deux branches sur ExitStatus =============================== */

    @Test
    @DisplayName("02 deux branches - PREMIUM vers expedition sinon preparation")
    void test02DeuxBranches() throws Exception {
        JobExecution premium = lancer(2, "PREMIUM", 0.0, false);
        assertEquals(BatchStatus.COMPLETED, premium.getStatus());
        assertEquals(List.of("controleStep", "expeditionStep"), steps(premium));

        JobExecution standard = lancer(2, "STANDARD", 0.0, false);
        assertEquals(List.of("controleStep", "preparationStep"), steps(standard));
    }

    /**
     * Meme point que le test 02 (le ScenarioListener doit transformer `scenario` en
     * ExitStatus), mais isole : on ne passe par aucun job/flow, donc aucune dependance
     * au routage .on(...).to(...). Si ce test casse, le probleme vient du listener ;
     * s'il passe mais que 02 echoue, le probleme est dans le flow (routage).
     */
    @Test
    @DisplayName("02b controleStep isole - exitStatus suit le parametre scenario")
    void test02bControleStepIsole() throws Exception {
        JobParameters params = new JobParametersBuilder()
                .addString("scenario", "PREMIUM", false)
                .addLong("run", System.nanoTime())
                .toJobParameters();

        // Construit a la main (pas @SpringBatchTest : son JobScopeTestExecutionListener
        JobOperatorTestUtils jobOperatorTestUtils = new JobOperatorTestUtils(jobOperator, jobRepository);

        JobExecution execution = jobOperatorTestUtils.startStep(controleStep, params, new ExecutionContext());

        assertEquals(BatchStatus.COMPLETED, execution.getStatus());
        assertEquals("PREMIUM", execution.getStepExecutions().iterator().next().getExitStatus().getExitCode());
    }

    /* ===== EX 3 — Fin anticipee .end() ====================================== */

    @Test
    @DisplayName("03 fin anticipee - VIDE termine le job sans archivage")
    void test03FinAnticipee() throws Exception {
        JobExecution vide = lancer(3, "VIDE", 0.0, false);
        assertEquals(BatchStatus.COMPLETED, vide.getStatus());
        assertEquals(List.of("controleStep"), steps(vide),
                "Sur VIDE, le job se termine des le controle : pas d'archivage");

        JobExecution plein = lancer(3, "PLEIN", 0.0, false);
        assertTrue(steps(plein).contains("archivageStep"));
    }

    /* ===== EX 4 — Echec explicite .fail() =================================== */

    @Test
    @DisplayName("04 echec explicite - CORROMPU passe par alerte puis fait echouer le job")
    void test04EchecExplicite() throws Exception {
        JobExecution corrompu = lancer(4, "CORROMPU", 0.0, false);
        assertEquals(BatchStatus.FAILED, corrompu.getStatus());
        assertEquals(List.of("controleStep", "alerteStep"), steps(corrompu));

        JobExecution ok = lancer(4, "OK", 0.0, false);
        assertEquals(BatchStatus.COMPLETED, ok.getStatus());
        assertTrue(steps(ok).contains("archivageStep"));
    }

    /* ===== EX 5 — Reagir a l'echec (.on("FAILED")) ========================== */

    @Test
    @DisplayName("05 reagir a l'echec - traitement KO route vers notification, job COMPLETED")
    void test05ReagirALEchec() throws Exception {
        JobExecution ko = lancer(5, "", 0.0, true);
        assertEquals(BatchStatus.COMPLETED, ko.getStatus(),
                "L'echec du traitement est ABSORBE par la branche .on(FAILED) : le job reussit");
        assertEquals(List.of("traitementStep", "notificationStep"), steps(ko));

        JobExecution ok = lancer(5, "", 0.0, false);
        assertEquals(BatchStatus.COMPLETED, ok.getStatus());
        assertEquals(List.of("traitementStep", "archivageStep"), steps(ok));
    }

    /* ===== EX 6 — Wildcards ? et * ========================================== */

    @Test
    @DisplayName("06 wildcards - A suivi d'un caractere vers preparation, sinon archivage")
    void test06Wildcards() throws Exception {
        assertTrue(steps(lancer(6, "A1", 0.0, false)).contains("preparationStep"),
                "A1 correspond au motif A?");
        assertTrue(steps(lancer(6, "B1", 0.0, false)).contains("archivageStep"),
                "B1 ne correspond pas a A?, il tombe dans *");
        assertTrue(steps(lancer(6, "A", 0.0, false)).contains("archivageStep"),
                "A seul (1 caractere) ne correspond PAS a A? (qui exige A + 1 caractere)");
    }

    /* ===== EX 7 — Pause .stopAndRestart() =================================== */

    @Test
    @DisplayName("07 pause - ATTENTE met le job en STOPPED")
    void test07Pause() throws Exception {
        JobExecution attente = lancer(7, "ATTENTE", 0.0, false);
        assertEquals(BatchStatus.STOPPED, attente.getStatus());
        assertFalse(steps(attente).contains("archivageStep"),
                "En pause, l'archivage n'est pas atteint");

        JobExecution ok = lancer(7, "OK", 0.0, false);
        assertEquals(BatchStatus.COMPLETED, ok.getStatus());
        assertTrue(steps(ok).contains("archivageStep"));
    }

    @Test
    @DisplayName("07b reprise - relancer la meme instance repart de controleStep")
    void test07bReprise() throws Exception {
        // runId FIXE => les deux lancements ciblent la MEME JobInstance.
        String runId = "tp9ex7-restart-" + System.nanoTime();

        // 1er lancement : en attente de validation -> STOPPED.
        JobExecution premier = lancerAvecRunId(7, runId, "ATTENTE");
        assertEquals(BatchStatus.STOPPED, premier.getStatus());

        // 2e lancement, MEME instance : la validation est arrivee (scenario != ATTENTE).
        // Le job ne repart PAS tout seul : c'est ce relancement manuel qui le reprend.
        JobExecution second = lancerAvecRunId(7, runId, "OK");
        assertEquals(BatchStatus.COMPLETED, second.getStatus(),
                "La reprise doit mener le job jusqu'au bout");
        assertTrue(steps(second).contains("controleStep"),
                "La reprise repart bien de controleStep (re-execute au restart)");
        assertTrue(steps(second).contains("archivageStep"),
                "controleStep re-evalue sort desormais vers archivageStep");
    }

    /* ===== EX 8 — Publication catalogue : flow imbrique ==================== */

    @Test
    @DisplayName("08 publication - licence, deja publie, validation absorbee")
    void test08Publication() throws Exception {
        JobExecution sansLicence = lancer(8, "SANS_LICENCE", 0.0, false);
        assertEquals(BatchStatus.FAILED, sansLicence.getStatus());
        assertEquals(List.of("controleStep", "alerteStep"), steps(sansLicence));

        JobExecution dejaPublie = lancer(8, "DEJA_PUBLIE", 0.0, false);
        assertEquals(BatchStatus.COMPLETED, dejaPublie.getStatus());
        assertEquals(List.of("controleStep"), steps(dejaPublie));

        JobExecution brouillon = lancer(8, "BROUILLON", 0.0, false);
        assertEquals(BatchStatus.STOPPED, brouillon.getStatus());
        assertEquals(List.of("controleStep"), steps(brouillon),
                "En brouillon, le job est mis en pause des le controle (stopAndRestart)");

        JobExecution normal = lancer(8, "NOUVEAU", 0.0, false);
        assertEquals(BatchStatus.COMPLETED, normal.getStatus());
        assertEquals(
                List.of("controleStep", "preparationStep", "traitementStep", "expeditionStep", "archivageStep"),
                steps(normal)
        );

        // Validation KO : ICI l'echec est ABSORBE (notification -> end) -> job COMPLETED.
        JobExecution validationKo = lancer(8, "NOUVEAU", 0.0, true);
        assertEquals(BatchStatus.COMPLETED, validationKo.getStatus(),
                "L'echec de validation est absorbe (notification -> end), le job reussit");
        assertEquals(List.of("controleStep", "preparationStep", "traitementStep", "notificationStep"),
                steps(validationKo));
    }

    /* ===== EX 9 — Commande : flow riche imbrique ============================ */

    @Test
    @DisplayName("09 commande - rupture, stock partiel, controle qualite")
    void test09Commande() throws Exception {
        JobExecution rupture = lancer(9, "RUPTURE", 0.0, false);
        assertEquals(BatchStatus.FAILED, rupture.getStatus());
        assertEquals(List.of("controleStep", "alerteStep"), steps(rupture));

        JobExecution partiel = lancer(9, "STOCK_PARTIEL", 0.0, false);
        assertEquals(BatchStatus.COMPLETED, partiel.getStatus());
        assertEquals(List.of("controleStep", "notificationStep"), steps(partiel));

        JobExecution ok = lancer(9, "STOCK_OK", 0.0, false);
        assertEquals(BatchStatus.COMPLETED, ok.getStatus());
        assertEquals(
                List.of("controleStep", "preparationStep", "traitementStep", "expeditionStep", "archivageStep"),
                steps(ok)
        );

        JobExecution qualiteKo = lancer(9, "STOCK_OK", 0.0, true);
        assertEquals(BatchStatus.FAILED, qualiteKo.getStatus());
        assertEquals(
                List.of("controleStep", "preparationStep", "traitementStep", "rapportStep"),
                steps(qualiteKo),
                "Le controle qualite en echec route vers rapportStep avant .fail()"
        );
    }

    /* ===== EX 10 — Paiement (depuis une histoire) ========================== */

    @Test
    @DisplayName("10 paiement - carte refusee, fraude, capture")
    void test10Paiement() throws Exception {
        JobExecution refusee = lancer(10, "CARTE_REFUSEE", 0.0, false);
        assertEquals(BatchStatus.COMPLETED, refusee.getStatus(),
                "Carte refusee : on notifie le client puis le job se termine normalement");
        assertEquals(List.of("controleStep", "notificationStep"), steps(refusee));

        JobExecution fraude = lancer(10, "FRAUDE", 0.0, false);
        assertEquals(BatchStatus.FAILED, fraude.getStatus());
        assertEquals(List.of("controleStep", "alerteStep"), steps(fraude));

        JobExecution paye = lancer(10, "PAYE", 0.0, false);
        assertEquals(BatchStatus.COMPLETED, paye.getStatus());
        assertEquals(
                List.of("controleStep", "traitementStep", "expeditionStep", "archivageStep"),
                steps(paye)
        );

        JobExecution captureKo = lancer(10, "PAYE", 0.0, true);
        assertEquals(BatchStatus.FAILED, captureKo.getStatus());
        assertTrue(steps(captureKo).containsAll(List.of("traitementStep", "rapportStep")),
                "Echec de capture : rapport d'incident puis .fail()");
    }

    /* ===== EX 11 — JobExecutionDecider ====================================== */

    @Test
    @DisplayName("11 decider - gros montant vers notification, petit vers archivage")
    void test11Decider() throws Exception {
        JobExecution gros = lancer(11, "", 5000.0, false);
        assertEquals(BatchStatus.COMPLETED, gros.getStatus());
        assertEquals(List.of("importStep", "notificationStep"), steps(gros));

        JobExecution petit = lancer(11, "", 50.0, false);
        assertEquals(List.of("importStep", "archivageStep"), steps(petit));
    }

    /* ===== EX 12 — split() parallele ======================================== */

    @Test
    @DisplayName("12 split - rapport et archivage en parallele puis notification")
    void test12Split() throws Exception {
        JobExecution execution = lancer(12, "", 0.0, false);

        assertEquals(BatchStatus.COMPLETED, execution.getStatus());
        List<String> noms = steps(execution);
        assertTrue(noms.containsAll(List.of("rapportStep", "archivageStep", "notificationStep")),
                "Les 3 steps doivent s'executer (rapport et archivage en parallele)");
        assertEquals("notificationStep", noms.get(noms.size() - 1),
                "La notification vient APRES le split (les 2 flux paralleles rejoignent avant)");
    }

    /* ===== EX 13 — Deux split() enchaines =================================== */

    @Test
    @DisplayName("13 cloture nuit - deux splits enchaines")
    void test13ClotureNuit() throws Exception {
        JobExecution execution = lancer(13, "", 0.0, false);

        assertEquals(BatchStatus.COMPLETED, execution.getStatus());
        List<String> noms = steps(execution);

        // Tous les steps du flow ont tourne.
        assertTrue(noms.containsAll(List.of(
                "importStep", "preparationStep", "expeditionStep", "rapportStep",
                "traitementStep", "notificationStep", "archivageStep"
        )));
        // archivageStep est REUTILISE dans les deux splits -> deux StepExecutions.
        assertEquals(2, noms.stream().filter("archivageStep"::equals).count(),
                "archivageStep apparait dans le split 1 (compta) ET dans le split 2 (final)");

        // Ordre : import en premier, puis split1, puis traitement (jointure), puis split2.
        assertEquals("importStep", noms.get(0));
        int idxTraitement = noms.indexOf("traitementStep");
        assertTrue(noms.indexOf("expeditionStep") < idxTraitement,
                "Le split 1 (logistique) est termine avant la consolidation");
        assertTrue(noms.indexOf("rapportStep") < idxTraitement,
                "Le split 1 (compta) est termine avant la consolidation");
        assertTrue(idxTraitement < noms.indexOf("notificationStep"),
                "Le split 2 demarre APRES la consolidation");

        // Ordre INTRA-branche : chaque branche est une sequence, son ordre interne est garanti.
        assertTrue(noms.indexOf("preparationStep") < noms.indexOf("expeditionStep"),
                "Branche logistique : la preparation precede l'expedition");
        assertTrue(noms.indexOf("rapportStep") < noms.indexOf("archivageStep"),
                "Branche compta : le rapport precede l'archivage");
    }

    /* ========================================================================= */
    /* Aides                                                                     */
    /* ========================================================================= */

    private JobExecution lancer(int exercice, String scenario, double montant, boolean echouer) throws Exception {
        Job job = applicationContext.getBean("tp9ex" + exercice + "Job", Job.class);
        JobParameters params = new JobParametersBuilder()
                .addString("scenario", scenario, false)
                .addDouble("montant", montant, false)
                .addString("echouer", String.valueOf(echouer), false)
                .addLong("run", System.nanoTime())
                .toJobParameters();
        return jobOperator.start(job, params);
    }

    /**
     * Lance avec un `runId` IDENTIFIANT fixe (et scenario NON identifiant) : deux appels
     * avec le meme runId ciblent la meme JobInstance -> le 2e est un RESTART, pas une
     * nouvelle instance. Sert a tester la reprise apres un STOPPED.
     */
    private JobExecution lancerAvecRunId(int exercice, String runId, String scenario) throws Exception {
        Job job = applicationContext.getBean("tp9ex" + exercice + "Job", Job.class);
        JobParameters params = new JobParametersBuilder()
                .addString("runId", runId)
                .addString("scenario", scenario, false)
                .addString("echouer", "false", false)
                .toJobParameters();
        return jobOperator.start(job, params);
    }

    /** Les steps reellement executes, dans l'ordre (par id d'execution). */
    private List<String> steps(JobExecution execution) {
        return execution.getStepExecutions().stream()
                .sorted(Comparator.comparing(StepExecution::getId))
                .map(StepExecution::getStepName)
                .toList();
    }
}

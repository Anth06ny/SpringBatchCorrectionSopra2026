package org.example.springbatchexercicejava.batch;

import org.example.springbatchexercicejava.batch.config.TP11_JobConfig;
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

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Suite de validation du TP11 (partitionnement).
 *
 *   01  PARTITIONER   : le job lance 10 partitions (une par fichier/boutique).
 *   02  ISOLATION     : total importe == 500, chaque partition n'a lu QUE sa boutique
 *                       (50 ventes par boutique, 10 boutiques distinctes).
 *   03  PARALLELISME  : plusieurs threads utilises + duree < plancher sequentiel.
 *   04  REPRISE       : une partition en echec -> job FAILED ; au restart (meme instance),
 *                       SEULE la partition FAILED rejoue, le job finit COMPLETED.
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.MethodName.class)
class TP11JobTest {

    @Autowired
    JobOperator jobOperator;

    @Autowired
    @Qualifier("tp11Job")
    Job tp11Job;

    @Autowired
    VenteRepository venteRepository;

    @Autowired
    TP11_JobConfig.Tp11Tracker tp11Tracker;

    @Test
    @DisplayName("01 partitioner - dix partitions lancees")
    void test01PartitionerDixPartitions() throws Exception {
        prepare();
        JobExecution execution = lancer();

        assertEquals(BatchStatus.COMPLETED, execution.getStatus());
        List<StepExecution> workers = partitions(execution);
        assertEquals(10, workers.size(), "Une partition (step esclave) par fichier");
    }

    @Test
    @DisplayName("02 isolation - chaque partition importe sa seule boutique")
    void test02Isolation() throws Exception {
        prepare();
        JobExecution execution = lancer();
        assertEquals(BatchStatus.COMPLETED, execution.getStatus());

        // Tout est importe, sans perte ni doublon.
        assertEquals(500, venteRepository.count(), "500 ventes au total");

        // Chaque boutique est presente 50 fois : preuve que chaque partition n'a traite
        // QUE son fichier (aucun recouvrement).
        Map<String, Long> parBoutique = venteRepository.findAll().stream()
                .collect(Collectors.groupingBy(VenteEntity::getBoutique, Collectors.counting()));
        assertEquals(10, parBoutique.size(), "10 boutiques distinctes");
        assertTrue(
                parBoutique.values().stream().allMatch(n -> n == 50L),
                "Chaque boutique importee exactement 50 fois : " + parBoutique
        );
    }

    @Test
    @DisplayName("03 parallelisme - plusieurs threads et gain de temps")
    void test03Parallelisme() throws Exception {
        prepare();
        JobExecution execution = lancer();
        assertEquals(BatchStatus.COMPLETED, execution.getStatus());

        Set<String> threads = tp11Tracker.threads();
        assertTrue(
                threads.size() > 1,
                "Les partitions doivent tourner sur plusieurs threads. Threads vus : " + threads
        );

        // Plancher sequentiel = 500 items x sleep. En parallele on passe dessous.
        long plancherSequentiel = 500 * Constants.TP11_SLEEP_MS;
        long duree = Duration.between(execution.getStartTime(), execution.getEndTime()).toMillis();
        assertTrue(
                duree < plancherSequentiel,
                "Duree=" + duree + "ms >= plancher sequentiel " + plancherSequentiel + "ms : pas parallelise"
        );
    }

    @Test
    @DisplayName("04 reprise - seule la partition en echec rejoue au restart")
    void test04Reprise() throws Exception {
        prepare();
        String runId = "tp11-restart-" + System.nanoTime();

        // 1er lancement : B10 casse -> sa partition FAILED, les 9 autres COMPLETED.
        JobExecution premier = lancerRestart(runId, true);
        assertEquals(BatchStatus.FAILED, premier.getStatus());
        List<StepExecution> workers1 = partitions(premier);
        assertEquals(1, workers1.stream().filter(s -> s.getStatus() == BatchStatus.FAILED).count(),
                "1 partition en echec");
        assertEquals(9, workers1.stream().filter(s -> s.getStatus() == BatchStatus.COMPLETED).count(),
                "9 partitions OK");

        // Restart (MEME instance) sans casser : seule la partition B10 doit rejouer.
        JobExecution second = lancerRestart(runId, false);
        assertEquals(BatchStatus.COMPLETED, second.getStatus(), "La reprise mene le job au bout");
        List<StepExecution> workers2 = partitions(second);
        assertEquals(
                1, workers2.size(),
                "Au restart, SEULE la partition en echec rejoue (les 9 COMPLETED sont sautees)"
        );
        assertEquals(500, venteRepository.count(), "Les 500 ventes sont finalement en base");
    }

    /* ========================================================================= */
    /* Aides                                                                     */
    /* ========================================================================= */

    private void prepare() {
        tp11Tracker.reset();
        venteRepository.deleteAll();
    }

    private List<StepExecution> partitions(JobExecution execution) {
        return execution.getStepExecutions().stream()
                .filter(s -> s.getStepName().startsWith("tp11WorkerStep:"))
                .toList();
    }

    private JobExecution lancer() throws Exception {
        JobParameters params = new JobParametersBuilder()
                .addLong("run", System.nanoTime())
                .toJobParameters();
        return jobOperator.start(tp11Job, params);
    }

    /** runId IDENTIFIANT fixe (restart) + casserB10 NON identifiant. */
    private JobExecution lancerRestart(String runId, boolean casserB10) throws Exception {
        JobParameters params = new JobParametersBuilder()
                .addString("runId", runId)
                .addString("casserB10", String.valueOf(casserB10), false)
                .toJobParameters();
        return jobOperator.start(tp11Job, params);
    }
}

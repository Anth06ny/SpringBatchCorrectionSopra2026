package org.example.springbatchexercicejava.batch;

import org.example.springbatchexercicejava.batch.config.TP10_JobConfig;
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
import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@TestMethodOrder(MethodOrderer.MethodName.class)
class TP10JobTest {

    @Autowired
    JobOperator jobOperator;

    @Autowired
    @Qualifier("tp10Job")
    Job tp10Job;

    @Autowired
    VenteRepository venteRepository;

    @Autowired
    TP10_JobConfig.Tp10Tracker tp10Tracker;

    @Test
    @DisplayName("01 etat partage - numeros de traitement tous uniques")
    void test01EtatPartageNumerosUniques() throws Exception {
        tp10Tracker.reset();
        venteRepository.deleteAll();

        JobExecution execution = lancer();
        assertEquals(BatchStatus.COMPLETED, execution.getStatus());

        long attendues = lignesData(Constants.TP10_VENTES_CSV);
        StepExecution step = execution.getStepExecutions().stream()
                .filter(s -> "tp10Step".equals(s.getStepName()))
                .findFirst()
                .orElseThrow();

        // Les items ne sont JAMAIS perdus : reader et writer sont mono-thread en Batch 6.
        assertEquals(attendues, step.getWriteCount(), "Tous les items sont ecrits");
        assertEquals(attendues, venteRepository.count(), "Toutes les ventes sont en base");

        // LE point : le compteur PARTAGE du processor doit rester correct en parallele.
        // Des numeros dupliques = lost updates = compteur non thread-safe.
        assertEquals((int) attendues, tp10Tracker.totalNumeros(),
                "Chaque vente recoit un numero");
        assertEquals((int) attendues, tp10Tracker.numerosDistincts(),
                "Les numeros doivent etre TOUS uniques : des doublons = course sur l'etat partage");
    }

    @Test
    @DisplayName("02 parallelisme reel - plusieurs threads utilises")
    void test02ParallelismeReel() throws Exception {
        tp10Tracker.reset();
        venteRepository.deleteAll();

        JobExecution execution = lancer();
        assertEquals(BatchStatus.COMPLETED, execution.getStatus());

        Set<String> threads = tp10Tracker.threads();
        assertTrue(threads.size() > 1,
                "Le processing doit s'executer sur plusieurs threads (.taskExecutor). Threads vus : " + threads);
    }

    @Test
    @DisplayName("03 gain de temps - le parallelisme passe sous le temps mono-thread")
    void test03GainDeTemps() throws Exception {
        tp10Tracker.reset();
        venteRepository.deleteAll();

        JobExecution execution = lancer();
        assertEquals(BatchStatus.COMPLETED, execution.getStatus());

        // Temps PLANCHER en mono-thread : la somme des sleeps du processor (items traites
        // l'un apres l'autre). Un run parallelise passe largement dessous. Seuil robuste :
        // Thread.sleep libere le CPU, les sleeps se recouvrent meme avec peu de coeurs.
        long attendues = lignesData(Constants.TP10_VENTES_CSV);
        long plancherMono = attendues * Constants.TP10_SLEEP_MS;

        long duree = Duration.between(execution.getStartTime(), execution.getEndTime()).toMillis();

        assertTrue(duree < plancherMono,
                "Duree=" + duree + "ms >= plancher mono-thread " + plancherMono + "ms : le step n'est PAS parallelise");
    }

    @Test
    @DisplayName("04 numero dans le libelle - le dernier produit porte le numero 500")
    void test04NumeroDansLeLibelle() throws Exception {
        tp10Tracker.reset();
        venteRepository.deleteAll();

        JobExecution execution = lancer();
        assertEquals(BatchStatus.COMPLETED, execution.getStatus());

        int attendues = (int) lignesData(Constants.TP10_VENTES_CSV);

        // Chaque libelleProduit persiste se termine par "_<numero>" (les noms de produits
        // ne contiennent pas de "_"). Avec un compteur correct, les numeros sont exactement
        // 1..500 -> le plus grand (le "dernier" attribue) vaut 500.
        List<Integer> numeros = venteRepository.findAll().stream()
                .map(VenteEntity::getLibelleProduit)
                .map(l -> Integer.parseInt(l.substring(l.lastIndexOf('_') + 1)))
                .toList();

        assertEquals(attendues, numeros.stream().mapToInt(Integer::intValue).max().orElseThrow(),
                "Le dernier numero attribue (le plus grand) doit valoir " + attendues);
        assertEquals(attendues, Set.copyOf(numeros).size(),
                "Les numeros portes par les libelles doivent etre tous uniques (1.." + attendues + ")");
    }

    private JobExecution lancer() throws Exception {
        JobParameters params = new JobParametersBuilder()
                .addLong("run", System.nanoTime())
                .toJobParameters();
        return jobOperator.start(tp10Job, params);
    }

    /** Nombre de lignes de donnees du CSV (hors en-tete, hors lignes vides). */
    private long lignesData(String chemin) throws IOException {
        return Files.readAllLines(Path.of(chemin)).stream()
                .skip(1)
                .filter(l -> !l.isBlank())
                .count();
    }
}

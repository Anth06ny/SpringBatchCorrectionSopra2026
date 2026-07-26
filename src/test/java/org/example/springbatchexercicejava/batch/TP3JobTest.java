package org.example.springbatchexercicejava.batch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Suite de validation du TP 3 : tous les tests doivent passer au vert.
 *
 * On n'inspecte pas la console : tout est verifie via les metadonnees
 * que Spring Batch persiste dans le JobRepository (statuts, StepExecutions).
 */
@SpringBootTest
class TP3JobTest {

    @Autowired
    JobOperator jobOperator;

    // Injection par nom : il faut un @Bean nomme tp3Job
    @Autowired
    @Qualifier("tp3Job")
    Job tp3Job;

    /** Chaque test utilise un runId unique pour partir d'une JobInstance vierge. */
    private JobParameters params(String runId, boolean fail) {
        return new JobParametersBuilder()
                .addString("runId", runId)
                .addString("fail", String.valueOf(fail), false)
                .toJobParameters();
    }

    private String newRunId() {
        return UUID.randomUUID().toString();
    }

    @Test
    @DisplayName("Enchaine les 4 steps dans l'ordre avec succes")
    void enchaineLes4StepsDansLOrdreAvecSucces() throws Exception {
        JobExecution execution = jobOperator.start(tp3Job, params(newRunId(), false));

        assertEquals(BatchStatus.COMPLETED, execution.getStatus());
        assertEquals(
                List.of("tp3Step1", "tp3Step2", "tp3Step3", "tp3Step4"),
                nomsDesSteps(execution),
                "Le job doit executer les 4 steps, dans l'ordre"
        );
    }

    @Test
    @DisplayName("Step 3 echoue et step 4 non execute")
    void step3EchoueEtStep4NonExecute() throws Exception {
        JobExecution execution = jobOperator.start(tp3Job, params(newRunId(), true));

        assertEquals(BatchStatus.FAILED, execution.getStatus());

        List<StepExecution> steps = stepsTries(execution);
        assertEquals(
                List.of("tp3Step1", "tp3Step2", "tp3Step3"),
                steps.stream().map(StepExecution::getStepName).toList(),
                "Le step 4 ne doit jamais demarrer apres l'echec du step 3"
        );
        assertEquals(BatchStatus.COMPLETED, steps.get(0).getStatus());
        assertEquals(BatchStatus.COMPLETED, steps.get(1).getStatus());
        assertEquals(BatchStatus.FAILED, steps.get(2).getStatus());
    }

    @Test
    @DisplayName("Restart : step 1 est saute, step 2 est rejoue")
    void restartStep1SauteStep2Rejoue() throws Exception {
        String runId = newRunId();

        // 1ere tentative : echec au step 3
        JobExecution execution1 = jobOperator.start(tp3Job, params(runId, true));
        assertEquals(BatchStatus.FAILED, execution1.getStatus());

        // 2eme tentative, MEMES parametres identifiants : restart de la meme instance
        JobExecution execution2 = jobOperator.start(tp3Job, params(runId, false));
        assertEquals(BatchStatus.COMPLETED, execution2.getStatus());
        assertEquals(
                execution1.getJobInstance().getId(), execution2.getJobInstance().getId(),
                "Les 2 tentatives doivent appartenir a la meme JobInstance"
        );

        assertEquals(
                List.of("tp3Step2", "tp3Step3", "tp3Step4"),
                nomsDesSteps(execution2),
                "Au restart : step1 saute (deja COMPLETED), step2 rejoue (allowStartIfComplete), "
                        + "step3 repart, step4 enfin execute"
        );
    }

    @Test
    @DisplayName("Non rejeu d'une instance terminee avec succes")
    void nonRejeuDUneInstanceTermineeAvecSucces() throws Exception {
        String runId = newRunId();

        jobOperator.start(tp3Job, params(runId, false));

        assertThrows(JobInstanceAlreadyCompleteException.class, () ->
                jobOperator.start(tp3Job, params(runId, false))
        );
    }

    /* -------------------------------- Helpers -------------------------------- */

    private List<StepExecution> stepsTries(JobExecution execution) {
        return execution.getStepExecutions().stream()
                .sorted(Comparator.comparing(StepExecution::getId))
                .toList();
    }

    private List<String> nomsDesSteps(JobExecution execution) {
        return stepsTries(execution).stream().map(StepExecution::getStepName).toList();
    }
}

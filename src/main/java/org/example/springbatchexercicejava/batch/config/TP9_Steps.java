package org.example.springbatchexercicejava.batch.config;

import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.annotation.AfterStep;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class TP9_Steps {

    Step logStep(String nom, JobRepository jobRepository, PlatformTransactionManager tm) {
        Tasklet task = (contribution, chunkContext) -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            System.out.println("🧩 " + nom);
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return RepeatStatus.FINISHED;
        };
        return new StepBuilder(nom, jobRepository)
                .tasklet(task, tm)
                .build();
    }

    @Bean
    public Step preparationStep(JobRepository jobRepository, PlatformTransactionManager tm) {
        return logStep("preparationStep", jobRepository, tm);
    }

    @Bean
    public Step expeditionStep(JobRepository jobRepository, PlatformTransactionManager tm) {
        return logStep("expeditionStep", jobRepository, tm);
    }

    @Bean
    public Step archivageStep(JobRepository jobRepository, PlatformTransactionManager tm) {
        return logStep("archivageStep", jobRepository, tm);
    }

    @Bean
    public Step alerteStep(JobRepository jobRepository, PlatformTransactionManager tm) {
        return logStep("alerteStep", jobRepository, tm);
    }

    @Bean
    public Step notificationStep(JobRepository jobRepository, PlatformTransactionManager tm) {
        return logStep("notificationStep", jobRepository, tm);
    }

    @Bean
    public Step rapportStep(JobRepository jobRepository, PlatformTransactionManager tm) {
        return logStep("rapportStep", jobRepository, tm);
    }

    @Bean
    public Step importStep(JobRepository jobRepository, PlatformTransactionManager tm) {
        return logStep("importStep", jobRepository, tm);
    }

    @Bean
    public Step controleStep(JobRepository jobRepository, PlatformTransactionManager tm) {
        Tasklet task = (contribution, chunkContext) -> {
            Object scenario = chunkContext.getStepContext().getJobParameters()
                    .getOrDefault("scenario", "");
            System.out.println("🧩 controleStep (scenario=" + scenario + ")");
            return RepeatStatus.FINISHED;
        };
        return new StepBuilder("controleStep", jobRepository)
                .tasklet(task, tm)
                .listener(new ScenarioListener())
                // INDISPENSABLE pour le stopAndRestart(controleStep) de l'ex7 : sans ca, au
                // restart Spring saute controleStep (deja COMPLETED) et REUTILISE son ancien
                // ExitStatus (ATTENTE) -> il redonne STOPPED indefiniment. allowStartIfComplete
                // force le RE-jeu : controleStep relit le scenario (change) et repart.
                .allowStartIfComplete(true)
                .build();
    }

    public static class ScenarioListener {
        @AfterStep
        public ExitStatus afterStep(StepExecution stepExecution) {
            String scenario = stepExecution.getJobExecution().getJobParameters().getString("scenario");
            return (scenario == null || scenario.isBlank())
                    ? ExitStatus.COMPLETED
                    : new ExitStatus(scenario);
        }
    }

    @Bean
    public Step traitementStep(JobRepository jobRepository, PlatformTransactionManager tm) {
        Tasklet task = (contribution, chunkContext) -> {
            boolean echouer = "true".equals(chunkContext.getStepContext().getJobParameters().get("echouer"));
            System.out.println("🧩 traitementStep (echouer=" + echouer + ")");
            if (echouer) {
                throw new IllegalStateException("Traitement en echec (simulation)");
            }
            return RepeatStatus.FINISHED;
        };
        return new StepBuilder("traitementStep", jobRepository)
                .tasklet(task, tm)
                .build();
    }
}

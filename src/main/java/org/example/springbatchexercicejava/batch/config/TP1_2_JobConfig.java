package org.example.springbatchexercicejava.batch.config;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class TP1_2_JobConfig {

    // Batch rappelle execute() jusqu'a RepeatStatus.FINISHED.
    @Bean
    public Tasklet helloTask() {
        return (contribution, chunkContext) -> {

            // Recuperation des parametres du job (Map<String, Object>)
            Object brut = chunkContext.getStepContext().getJobParameters().get("message");
            String message = (brut instanceof String s) ? s : "-";

            System.out.println("Bonjour depuis un Tasklet ! : " + message);
            return RepeatStatus.FINISHED;
        };
    }

    // Une etape de type Tasklet
    @Bean
    public Step helloStep(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        return new StepBuilder("helloStep", jobRepository)
                .tasklet(helloTask(), transactionManager)
                .build();
    }

    // Le Job : un enchainement d'etapes (ici une seule)
    @Bean
    public Job helloJob(JobRepository jobRepository, Step helloStep) {
        return new JobBuilder("helloJob", jobRepository)
                .start(helloStep)
                .build();
    }

    /* -------------------------------- */
    // 2eme tache
    /* -------------------------------- */

}

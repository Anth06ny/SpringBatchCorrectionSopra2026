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
public class TP3_JobConfig {

    // Un Tasklet qui ne fait qu'afficher un message
    private Tasklet logTasklet(String message) {
        return (contribution, chunkContext) -> {
            System.out.println(message);
            return RepeatStatus.FINISHED;
        };
    }

    @Bean
    public Step tp3Step1(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        return new StepBuilder("tp3Step1", jobRepository)
                .tasklet(logTasklet("TP3 - Step 1"), transactionManager)
                .build();
    }

    @Bean
    public Job tp3Job(JobRepository jobRepository, Step tp3Step1) {
        return new JobBuilder("tp3Job", jobRepository)
                .start(tp3Step1)
                .build();
    }
}

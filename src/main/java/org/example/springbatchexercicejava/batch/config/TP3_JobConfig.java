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
    public Step tp3Step2(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        return new StepBuilder("tp3Step2", jobRepository)
                .tasklet(logTasklet("TP3 - Step 2"), transactionManager)
                .allowStartIfComplete(true)
                .build();
    }

    @Bean
    public Step tp3Step3(JobRepository jobRepository, PlatformTransactionManager transactionManager) {

        Tasklet tasklet =  (contribution, chunkContext) -> {
            String message = chunkContext.getStepContext().getJobParameters().getOrDefault("fail", "false").toString();
            if(message.equalsIgnoreCase("true")) throw new Exception("fail");
            return RepeatStatus.FINISHED;
        };

        return new StepBuilder("tp3Step3", jobRepository)
                .tasklet(tasklet, transactionManager)
                .allowStartIfComplete(true)
                .build();
    }

    @Bean
    public Step tp3Step4(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        return new StepBuilder("tp3Step4", jobRepository)
                .tasklet(logTasklet("TP3 - Step 4"), transactionManager)
                .build();
    }

    @Bean
    public Job tp3Job(JobRepository jobRepository, Step tp3Step1, Step tp3Step2, Step tp3Step3, Step tp3Step4) {
        return new JobBuilder("tp3Job", jobRepository)
                .start(tp3Step1)
                .next(tp3Step2)
                .next(tp3Step3)
                .next(tp3Step4)
                .build();
    }
}

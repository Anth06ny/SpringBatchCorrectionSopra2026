package org.example.springbatchexercicejava.batch.config;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Locale;

@Configuration
public class TP5_JobConfig {

    // Step chunk : lit la base -> ecrit le CSV, par lots de 10.
    @Bean
    public Step tp5Step(JobRepository jobRepository,
                        PlatformTransactionManager transactionManager,
                        Tasklet helloTask) {
        return new StepBuilder("tp5Step", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    throw new UnsupportedOperationException("A remplacer par un chunk step");
                }, transactionManager)
                .build();
    }

    @Bean
    public Job tp5Job(JobRepository jobRepository, Step tp5Step) {
        return new JobBuilder("tp5Job", jobRepository)
                .start(tp5Step)
                .build();
    }

    /** Montant a 2 decimales avec un point (comme dans ventes.csv). */
    private String formatMontant(double montant) {
        return String.format(Locale.US, "%.2f", montant);
    }
}

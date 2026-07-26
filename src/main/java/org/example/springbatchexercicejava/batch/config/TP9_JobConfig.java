package org.example.springbatchexercicejava.batch.config;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TP9_JobConfig {

    @Bean
    public Job tp9ex1Job(JobRepository jobRepository,
                         Step preparationStep,
                         Step expeditionStep,
                         Step archivageStep) {
        return new JobBuilder("tp9ex1Job", jobRepository)
                .start(preparationStep)
                .build();
    }

    @Bean
    public Job tp9ex2Job(JobRepository jobRepository,
                         Step controleStep,
                         Step expeditionStep,
                         Step preparationStep) {
        return new JobBuilder("tp9ex2Job", jobRepository)
                .start(controleStep)
                .build();
    }

    @Bean
    public Job tp9ex3Job(JobRepository jobRepository,
                         Step controleStep,
                         Step archivageStep) {
        return new JobBuilder("tp9ex3Job", jobRepository)
                .start(controleStep)
                .build();
    }

    @Bean
    public Job tp9ex4Job(JobRepository jobRepository,
                         Step controleStep,
                         Step alerteStep,
                         Step archivageStep) {
        return new JobBuilder("tp9ex4Job", jobRepository)
                .start(controleStep)
                .build();
    }

    @Bean
    public Job tp9ex5Job(JobRepository jobRepository,
                         Step traitementStep,
                         Step notificationStep,
                         Step archivageStep) {
        return new JobBuilder("tp9ex5Job", jobRepository)
                .start(traitementStep)
                .build();
    }

    @Bean
    public Job tp9ex6Job(JobRepository jobRepository,
                         Step controleStep,
                         Step preparationStep,
                         Step archivageStep) {
        return new JobBuilder("tp9ex6Job", jobRepository)
                .start(controleStep)
                .build();
    }

    @Bean
    public Job tp9ex7Job(JobRepository jobRepository,
                         Step controleStep,
                         Step archivageStep) {
        return new JobBuilder("tp9ex7Job", jobRepository)
                .start(controleStep)
                .build();
    }

    @Bean
    public Job tp9ex8Job(JobRepository jobRepository,
                         Step controleStep,
                         Step alerteStep,
                         Step preparationStep,
                         Step traitementStep,
                         Step notificationStep,
                         Step expeditionStep,
                         Step archivageStep) {
        return new JobBuilder("tp9ex8Job", jobRepository)
                .start(controleStep)
                .build();
    }

    @Bean
    public Job tp9ex9Job(JobRepository jobRepository,
                         Step controleStep,
                         Step alerteStep,
                         Step notificationStep,
                         Step preparationStep,
                         Step traitementStep,
                         Step rapportStep,
                         Step expeditionStep,
                         Step archivageStep) {
        return new JobBuilder("tp9ex9Job", jobRepository)
                .start(controleStep)
                .build();
    }

    @Bean
    public Job tp9ex10Job(JobRepository jobRepository,
                          Step controleStep,
                          Step notificationStep,
                          Step alerteStep,
                          Step traitementStep,
                          Step rapportStep,
                          Step expeditionStep,
                          Step archivageStep) {
        return new JobBuilder("tp9ex10Job", jobRepository)
                .start(controleStep)
                .build();
    }

    @Bean
    public Job tp9ex11Job(JobRepository jobRepository,
                          Step importStep,
                          Step notificationStep,
                          Step archivageStep
                          //, MontantDecider montantDecider
    ) {
        return new JobBuilder("tp9ex11Job", jobRepository)
                .start(importStep)
                //.next(montantDecider).on("GROS").to(notificationStep)
                //.from(montantDecider).on("PETIT").to(archivageStep)
                //.end()
                .build();
    }

    @Bean
    public Job tp9ex12Job(JobRepository jobRepository,
                          Step rapportStep,
                          Step archivageStep,
                          Step notificationStep) {
        return new JobBuilder("tp9ex12Job", jobRepository)
                .start(rapportStep)
                .build();
    }

    // EX 13 — Cloture de nuit : DEUX split() enchaines (voir l'histoire + le schema).
    //   import -> [prepa->expedition || rapport->archivage] -> traitement -> [notification || archivage]
    @Bean
    public Job tp9ex13Job(JobRepository jobRepository,
                          Step importStep,
                          Step preparationStep,
                          Step expeditionStep,
                          Step rapportStep,
                          Step traitementStep,
                          Step notificationStep,
                          Step archivageStep) {
        return new JobBuilder("tp9ex13Job", jobRepository)
                .start(importStep)
                .build();
    }
}

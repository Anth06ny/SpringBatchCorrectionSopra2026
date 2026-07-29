package org.example.springbatchexercicejava.batch.config;

import org.jspecify.annotations.Nullable;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.builder.FlowBuilder;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.flow.Flow;
import org.springframework.batch.core.job.flow.FlowExecutionStatus;
import org.springframework.batch.core.job.flow.JobExecutionDecider;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

@Configuration
public class TP9_JobConfig {

    @Bean
    public Job tp9ex1Job(JobRepository jobRepository,
                         Step preparationStep,
                         Step expeditionStep,
                         Step archivageStep) {
        return new JobBuilder("tp9ex1Job", jobRepository)
                .start(preparationStep)
                .next(expeditionStep)
                .next(archivageStep)
                .build();
    }

    @Bean
    public Job tp9ex2Job(JobRepository jobRepository,
                         Step controleStep,
                         Step expeditionStep,
                         Step preparationStep) {
        return new JobBuilder("tp9ex2Job", jobRepository)
                .start(controleStep).on("PREMIUM").to(expeditionStep)
                .from(controleStep).on("*").to(preparationStep)
                .end()
                .build();
    }

    @Bean
    public Job tp9ex3Job(JobRepository jobRepository,
                         Step controleStep,
                         Step archivageStep) {
        return new JobBuilder("tp9ex3Job", jobRepository)
                .start(controleStep).on("VIDE").end()
                .from(controleStep).on("*").to(archivageStep)
                .end()
                .build();
    }

    @Bean
    public Job tp9ex4Job(JobRepository jobRepository,
                         Step controleStep,
                         Step alerteStep,
                         Step archivageStep) {
        // @formatter:off
        return new JobBuilder("tp9ex4Job", jobRepository)
                .start(controleStep).on("CORROMPU").to(alerteStep)
                .from(alerteStep).on("*").fail()
                .from(controleStep).on("*").to(archivageStep)
                .end()
                .build();
        // @formatter:on
    }

    @Bean
    public Job tp9ex5Job(JobRepository jobRepository,
                         Step traitementStep,
                         Step notificationStep,
                         Step archivageStep) {
        // @formatter:off
        return new JobBuilder("tp9ex5Job", jobRepository)
                .start(traitementStep).on("FAILED").to(notificationStep)
                    .from(notificationStep).on("*").end()
                .from(traitementStep).on("*").to(archivageStep)
                .end()
                .build();
        // @formatter:on
    }

    @Bean
    public Job tp9ex6Job(JobRepository jobRepository,
                         Step controleStep,
                         Step preparationStep,
                         Step archivageStep) {
        return new JobBuilder("tp9ex6Job", jobRepository)
                .start(controleStep).on("A?").to(preparationStep)
                .from(controleStep).on("*").to(archivageStep)
                .end()
                .build();
    }

    @Bean
    public Job tp9ex7Job(JobRepository jobRepository,
                         Step controleStep,
                         Step archivageStep) {
        return new JobBuilder("tp9ex7Job", jobRepository)
                .start(controleStep).on("ATTENTE").stopAndRestart(controleStep)
                .from(controleStep).on("*").to(archivageStep)
                .end()
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
        // @formatter:off
        return new JobBuilder("tp9ex8Job", jobRepository)
                .start(controleStep).on("SANS_LICENCE").to(alerteStep)
                    .from(alerteStep).on("*").fail()
                .from(controleStep).on("DEJA_PUBLIE").end()
                .from(controleStep).on("BROUILLON").stopAndRestart(controleStep)
                .from(controleStep).on("*").to(preparationStep)
                    .from(preparationStep).on("*").to(traitementStep)
                        .from(traitementStep).on("FAILED").to(notificationStep)
                                .from(notificationStep).on("*").end()
                        .from(traitementStep).on("*").to(expeditionStep)
                            .from(expeditionStep).on("*").to(archivageStep)
                .end()
                .build();
        // @formatter:on
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
        // @formatter:off
        return new JobBuilder("tp9ex9Job", jobRepository)
                .start(controleStep).on("RUPTURE").to(alerteStep)
                    .from(alerteStep).on("*").fail()
                .from(controleStep).on("STOCK_PARTIEL").to(notificationStep)
                    .from(notificationStep).on("*").end()
                .from(controleStep).on("*").to(preparationStep)
                    .from(preparationStep).on("*").to(traitementStep)
                        .from(traitementStep).on("FAILED").to(rapportStep)
                            .from(rapportStep).on("*").fail()
                        .from(traitementStep).on("*").to(expeditionStep)
                            .from(expeditionStep).on("*").to(archivageStep)
                .end()
                .build();
        // @formatter:on
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
        // @formatter:off
        return new JobBuilder("tp9ex10Job", jobRepository)
                .start(controleStep).on("CARTE_REFUSEE").to(notificationStep)
                .from(notificationStep).on("*").end()
                .from(controleStep).on("FRAUDE").to(alerteStep)
                .from(alerteStep).on("*").fail()
                .from(controleStep).on("*").to(traitementStep)
                .from(traitementStep).on("FAILED").to(rapportStep)
                .from(rapportStep).on("*").fail()
                .from(traitementStep).on("*").to(expeditionStep)
                .from(expeditionStep).on("*").to(archivageStep)
                .end()
                .build();
        // @formatter:on
    }

    @Bean
    public Job tp9ex11Job(JobRepository jobRepository,
                          Step importStep,
                          Step notificationStep,
                          Step archivageStep,
                          MontantDecider montantDecider
    ) {
        return new JobBuilder("tp9ex11Job", jobRepository)
                .start(importStep)
                .next(montantDecider).on("GROS").to(notificationStep)
                .from(montantDecider).on("PETIT").to(archivageStep)
                .end()
                .build();
    }

    public static class MontantDecider implements JobExecutionDecider {

        @Override
        public FlowExecutionStatus decide(JobExecution jobExecution, @Nullable StepExecution stepExecution) {

            double montant = jobExecution.getJobParameters().getDouble("montant", 0.0);

            return new FlowExecutionStatus(montant > 1000 ? "GROS" : "PETIT");
        }
    }

    @Bean
    public MontantDecider montantDecider() {
        return new MontantDecider();
    }

    @Bean
    public Job tp9ex12Job(JobRepository jobRepository,
                          Step importStep,
                          Step rapportStep,
                          Step archivageStep,
                          Step notificationStep) {

        //Flow 1 — thread 1
        Flow flow1 = new FlowBuilder<Flow>("flow1")
                .start(rapportStep)
                .build();

// Flow 2 — thread 2
        Flow flow2 = new FlowBuilder<Flow>("flow2")
                .start(archivageStep)
                .build();

        Flow splitFlow1 = new FlowBuilder<Flow>("splitFlow1")
                .split(new SimpleAsyncTaskExecutor())
                .add(flow1, flow2)
                .build();

        Flow mainFlow = new FlowBuilder<Flow>("mainFlow")
                .start(importStep)
                .next(splitFlow1)
                .next(notificationStep)
                .build();

        return new JobBuilder("tp9ex13Job", jobRepository)
                .start(mainFlow)
                .end()
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

        //Flow 1 — thread 1
        Flow flowLogistique = new FlowBuilder<Flow>("flowLogistique")
                .start(preparationStep)
                .next(expeditionStep)
                .build();

        // Flow 2 — thread 2
        Flow flowCompta = new FlowBuilder<Flow>("flowCompta")
                .start(rapportStep)
                .next(archivageStep)
                .build();

        Flow splitFlow1 = new FlowBuilder<Flow>("splitFlow1")
                .split(new SimpleAsyncTaskExecutor())
                .add(flowLogistique, flowCompta)
                .build();

        //Flow 1 — thread 1
        Flow flowNotification = new FlowBuilder<Flow>("flowNotification")
                .start(notificationStep)
                .build();

        // Flow 2 — thread 2
        Flow flowArchivage = new FlowBuilder<Flow>("flowArchivage")
                .start(archivageStep)
                .build();

        Flow splitFlow2 = new FlowBuilder<Flow>("splitFlow2")
                .split(new SimpleAsyncTaskExecutor())
                .add(flowNotification, flowArchivage)
                .build();

        Flow mainFlow = new FlowBuilder<Flow>("mainFlow")
                .start(importStep)
                .next(splitFlow1)
                .next(traitementStep)
                .next(splitFlow2)
                .build();

        return new JobBuilder("tp9ex13Job", jobRepository)
                .start(mainFlow)
                .end()
                .build();
    }
}

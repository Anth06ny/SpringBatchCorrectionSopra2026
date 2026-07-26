package org.example.springbatchexercicejava.batch.config;

import jakarta.persistence.EntityManagerFactory;
import org.example.springbatchexercicejava.batch.Constants;
import org.example.springbatchexercicejava.batch.model.VenteCsvDTO;
import org.example.springbatchexercicejava.batch.model.VenteEntity;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class TP4_JobConfig {

    /**
     * Reader : lit ventes.csv, saute l'en-tete, decoupe sur ';' et mappe
     * chaque ligne vers un VenteCsvDTO (donnee brute, non calculee).
     */
    @Bean
    public FlatFileItemReader<VenteCsvDTO> venteReader() {
        return new FlatFileItemReaderBuilder<VenteCsvDTO>()
                .name("venteReader")
                .resource(new FileSystemResource(Constants.VENTES_CSV))
                .linesToSkip(1) // ligne d'en-tete
                .delimited()
                .delimiter(";")
                .names("date", "idBoutique", "produit", "montantHt")
                .targetType(VenteCsvDTO.class)
                .build();
    }

    /**
     * Processor : transforme la ligne brute en entite persistable et calcule
     * le TTC. Ne retourne JAMAIS null (aucun filtrage).
     */
    @Bean
    public ItemProcessor<VenteCsvDTO, VenteEntity> venteProcessor() {
        return csv -> null;
    }

    /**
     * Writer : persiste le lot en base H2 via JpaItemWriter.
     * On enveloppe le writer JPA pour tracer la TAILLE du lot recu : c'est ce
     * log qui rend visible l'effet de la taille de chunk aux stagiaires.
     */
    @Bean
    public ItemWriter<VenteEntity> venteWriter(EntityManagerFactory entityManagerFactory) {
        return chunk -> System.out.println("Writer: ecriture d'un lot de " + chunk.size() + " ventes");
    }

    // Step chunk : lit/traite/ecrit par lots de 10, chaque lot = 1 transaction/commit
    @Bean
    public Step tp4Step(JobRepository jobRepository,
                        PlatformTransactionManager transactionManager,
                        Tasklet helloTask) {
        return new StepBuilder("tp4Step", jobRepository)
                .tasklet(helloTask, transactionManager)
                .build();
    }

    @Bean
    public Job tp4Job(JobRepository jobRepository) {
        return new JobBuilder("tp4Job", jobRepository)
                .start((Step) stepExecution -> {
                })
                .build();
    }
}

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
import org.springframework.batch.infrastructure.item.database.JpaItemWriter;
import org.springframework.batch.infrastructure.item.database.builder.JpaItemWriterBuilder;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDate;

import static org.example.springbatchexercicejava.batch.Constants.TVA;

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
        return csv -> new VenteEntity(
                LocalDate.parse(csv.getDate()),
                csv.getIdBoutique(),
                csv.getProduit(),
                csv.getMontantHt(),
                // calcul metier : TTC = HT x 1.20
                csv.getMontantHt() * TVA) ;
    }

    /**
     * Writer : persiste le lot en base H2 via JpaItemWriter.
     * On enveloppe le writer JPA pour tracer la TAILLE du lot recu : c'est ce
     * log qui rend visible l'effet de la taille de chunk aux stagiaires.
     */
    @Bean
    public JpaItemWriter<VenteEntity> venteWriter(EntityManagerFactory entityManagerFactory) {

        JpaItemWriter<VenteEntity> jpaWriter = new JpaItemWriterBuilder<VenteEntity>()
                .entityManagerFactory(entityManagerFactory)
                //Force l'insert sans merge (pas de select pour voir si l'object existe en base)
                //.usePersist(true)
                .build();
        return jpaWriter;
    }

    // Step chunk : lit/traite/ecrit par lots de 10, chaque lot = 1 transaction/commit
    @Bean
    public Step tp4Step(JobRepository jobRepository,
                        PlatformTransactionManager transactionManager,
                        //1 Reader
                        FlatFileItemReader<VenteCsvDTO> venteReader,
                        //0 à 1 Processor  (Si on en veut plusieurs, on charge celui qui fera la liaison)
                        ItemProcessor<VenteCsvDTO, VenteEntity> venteProcessor,
                        //1 Writer (Si plusieurs on charge celui qui fera la liaison)
                        ItemWriter<VenteEntity> venteWriter
                        ) {
        return new StepBuilder("tp4Step", jobRepository)
                .<VenteCsvDTO, VenteEntity>chunk(10)
                .reader(venteReader)
                .processor(venteProcessor)
                .writer(venteWriter)
                .transactionManager(transactionManager)
                .build();
    }

    @Bean
    public Job tp4Job(JobRepository jobRepository, Step tp4Step) {
        return new JobBuilder("tp4Job", jobRepository)
                .start(tp4Step)
                .build();
    }
}

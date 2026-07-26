package org.example.springbatchexercicejava.batch.config;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.QueryTimeoutException;
import org.example.springbatchexercicejava.batch.Constants;
import org.example.springbatchexercicejava.batch.model.VenteEntity;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.ItemStreamReader;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.batch.infrastructure.item.database.JpaItemWriter;
import org.springframework.batch.infrastructure.item.database.builder.JpaItemWriterBuilder;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class TP7_JobConfig {

    @Bean
    @StepScope
    public ItemStreamReader<TP7VenteDTO> tp7Reader(
            @Value("#{jobParameters['fichierSource']}") String fichierSource) {
        return new FlatFileItemReaderBuilder<TP7VenteDTO>()
                .name("tp7Reader")
                .resource(new FileSystemResource(fichierSource))
                .linesToSkip(1) // ligne d'en-tete
                .delimited()
                .delimiter(";")
                .names("date", "idBoutique", "produit", "montantHt")
                .targetType(TP7VenteDTO.class)
                .build();
    }

    /* ------------------------------------------------------------------ */
    /* Processor : mapping TP4 + regle metier + panne simulee              */
    /* ------------------------------------------------------------------ */

    @Bean
    @StepScope
    public ItemProcessor<TP7VenteDTO, VenteEntity> tp7Processor(
            @Value("#{jobParameters['risque']}") boolean risque) {
        AtomicInteger compteurAppels = new AtomicInteger(0);
        return csv -> {
            if (risque) {
                int appel = compteurAppels.incrementAndGet();
                if (appel % 10 == 0) {
                    throw new QueryTimeoutException("Panne simulee (appel n" + appel + ")");
                }
            }

            return new VenteEntity(
                    LocalDate.parse(csv.getDate()),
                    csv.getIdBoutique(),
                    csv.getProduit(),
                    csv.getMontantHt(),
                    csv.getMontantHt() * Constants.TVA
            );
        };
    }

    /* ------------------------------------------------------------------ */
    /* Writer : le meme JpaItemWriter que le TP4, avec le log de lot       */
    /* ------------------------------------------------------------------ */

    @Bean
    public ItemWriter<VenteEntity> tp7Writer(EntityManagerFactory entityManagerFactory) {
        JpaItemWriter<VenteEntity> jpaWriter = new JpaItemWriterBuilder<VenteEntity>()
                .entityManagerFactory(entityManagerFactory)
                //.usePersist(true)
                .build();

        return chunk -> {
            System.out.println("TP7 Writer: ecriture d'un lot de " + chunk.size() + " ventes");
            jpaWriter.write(chunk);
        };
    }

    /* ------------------------------------------------------------------ */
    /* Step : chunk + faultTolerant + skip + retry                         */
    /* ------------------------------------------------------------------ */

    @Bean
    public Step tp7Step(JobRepository jobRepository,
                        PlatformTransactionManager transactionManager,
                        ItemStreamReader<TP7VenteDTO> tp7Reader,
                        ItemProcessor<TP7VenteDTO, VenteEntity> tp7Processor,
                        ItemWriter<VenteEntity> tp7Writer) {
        return new StepBuilder("tp7Step", jobRepository)
                .<TP7VenteDTO, VenteEntity>chunk(Constants.CHUNK_SIZE)
                .reader(tp7Reader)
                .processor(tp7Processor)
                .writer(tp7Writer)
                .faultTolerant()
                .transactionManager(transactionManager)
                .build();
    }

    @Bean
    public Job tp7Job(JobRepository jobRepository, Step tp7Step) {
        return new JobBuilder("tp7Job", jobRepository)
                .start(tp7Step)
                .build();
    }

    /** DTO du CSV, propre au TP7 (VenteCsvDTO est l'exercice du TP4, on n'en depend pas). */
    public static class TP7VenteDTO {
        private String date = "";
        private String idBoutique = "";
        private String produit = "";
        private double montantHt = 0.0;

        public String getDate() {
            return date;
        }

        public void setDate(String date) {
            this.date = date;
        }

        public String getIdBoutique() {
            return idBoutique;
        }

        public void setIdBoutique(String idBoutique) {
            this.idBoutique = idBoutique;
        }

        public String getProduit() {
            return produit;
        }

        public void setProduit(String produit) {
            this.produit = produit;
        }

        public double getMontantHt() {
            return montantHt;
        }

        public void setMontantHt(double montantHt) {
            this.montantHt = montantHt;
        }
    }
}

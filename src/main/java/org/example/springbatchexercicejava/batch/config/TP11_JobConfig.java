package org.example.springbatchexercicejava.batch.config;

import jakarta.persistence.EntityManagerFactory;
import org.example.springbatchexercicejava.batch.Constants;
import org.example.springbatchexercicejava.batch.model.VenteEntity;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.partition.Partitioner;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.ItemStreamReader;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.batch.infrastructure.item.database.builder.JpaItemWriterBuilder;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
public class TP11_JobConfig {

    @Bean
    @StepScope
    public ItemStreamReader<TP11VenteDTO> tp11Reader(
            @Value("#{stepExecutionContext['filePath']}") String filePath
    ) {
        System.out.println("path : " + filePath);
        return new FlatFileItemReaderBuilder<TP11VenteDTO>()
                .name("tp11Reader")
                .resource(new FileSystemResource(filePath))
                .linesToSkip(1) // ligne d'en-tete
                .delimited()
                .delimiter(";")
                .names("date", "idBoutique", "produit", "montantHt")
                .targetType(TP11VenteDTO.class)
                .build();
    }

    @Bean
    @StepScope
    public ItemProcessor<TP11VenteDTO, VenteEntity> tp11Processor(
            @Value("#{jobParameters['casserB10'] ?: false}") boolean casserB10,
            Tp11Tracker tp11Tracker) {
        return dto -> {
            tp11Tracker.enregistrerThread(Thread.currentThread().getName());
            Thread.sleep(Constants.TP11_SLEEP_MS); // simule une tarification lente

            if (casserB10 && "B10".equals(dto.getIdBoutique())) {
                throw new IllegalStateException("Panne simulee sur la boutique B10");
            }

            return new VenteEntity(
                    LocalDate.parse(dto.getDate()),
                    dto.getIdBoutique(),
                    dto.getProduit(),
                    dto.getMontantHt(),
                    dto.getMontantHt() * Constants.TVA
            );
        };
    }

    @Bean
    public ItemWriter<VenteEntity> tp11Writer(EntityManagerFactory entityManagerFactory) {
        return new JpaItemWriterBuilder<VenteEntity>()
                .entityManagerFactory(entityManagerFactory)
                .build();
    }

    // Step chunk : lit un fichier de ventes -> ecrit en base, par lots de 10.
    @Bean
    public Step tp11WorkerStep(JobRepository jobRepository,
                               PlatformTransactionManager transactionManager,
                               ItemStreamReader<TP11VenteDTO> tp11Reader,
                               ItemProcessor<TP11VenteDTO, VenteEntity> tp11Processor,
                               ItemWriter<VenteEntity> tp11Writer) {
        return new StepBuilder("tp11WorkerStep", jobRepository)
                .<TP11VenteDTO, VenteEntity>chunk(Constants.CHUNK_SIZE)
                .reader(tp11Reader)
                .processor(tp11Processor)
                .writer(tp11Writer)
                .transactionManager(transactionManager)
                .build();
    }

    @Bean
    public Step tp11MasterStep(
            JobRepository jobRepository,
            Step tp11WorkerStep
    ) {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("part-");
        executor.setConcurrencyLimit(4);

        return new StepBuilder("tp11MasterStep", jobRepository)
                .partitioner("tp11MasterStep", new MyPartitioner())
                .step(tp11WorkerStep)
                .gridSize(10)
                .taskExecutor(executor)
                .build();
    }

    @Bean
    public Job tp11Job(JobRepository jobRepository, Step tp11MasterStep) {
        return new JobBuilder("tp11Job", jobRepository)
                .start(tp11MasterStep)
                .build();
    }

    // Le partitionneur
    public static class MyPartitioner implements Partitioner {

        // gridSize : Pas obligatoire, si on veut rendre le nombre de partition paramètrable
        @Override
        public Map<String, ExecutionContext> partition(int gridSize) {
            System.out.println("partition gridSize : " + gridSize);

            Map<String, ExecutionContext> partitions = new HashMap<>();

            for (int i = 1; i <= gridSize; i++) {
                ExecutionContext context = new ExecutionContext();
                // On peut passer des paramètres
                if (i >= 10) {
                    context.putString("filePath", "data/tp11/ventes_B" + i + ".csv");
                } else {
                    context.putString("filePath", "data/tp11/ventes_B0" + i + ".csv");
                }

                partitions.put("partition_" + i, context);
            }

            return partitions;
        }
    }

    /* ------------------------------------------------------------------ */
    /* Infra fournie (DTO + tracker)                                       */
    /* ------------------------------------------------------------------ */

    /**
     * DTO du CSV, propre au TP11.
     */
    public static class TP11VenteDTO {
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

    /**
     * Collecteur thread-safe des threads utilises (preuve du parallelisme, test 3).
     */
    public static class Tp11Tracker {
        private final Set<String> threads = ConcurrentHashMap.newKeySet();

        public void enregistrerThread(String nom) {
            threads.add(nom);
        }

        public Set<String> threads() {
            return Set.copyOf(threads);
        }

        public void reset() {
            threads.clear();
        }
    }

    @Bean
    public Tp11Tracker tp11Tracker() {
        return new Tp11Tracker();
    }
}

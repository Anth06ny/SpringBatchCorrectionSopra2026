package org.example.springbatchexercicejava.batch.config;

import jakarta.persistence.EntityManagerFactory;
import org.example.springbatchexercicejava.batch.Constants;
import org.example.springbatchexercicejava.batch.model.VenteEntity;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.batch.infrastructure.item.database.builder.JpaItemWriterBuilder;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class TP10_JobConfig {

    @Bean
    public ItemReader<TP10VenteDTO> tp10Reader() {
        return new FlatFileItemReaderBuilder<TP10VenteDTO>()
                .name("tp10Reader")
                .resource(new FileSystemResource(Constants.TP10_VENTES_CSV))
                .linesToSkip(1) // ligne d'en-tete
                .delimited()
                .delimiter(";")
                .names("date", "idBoutique", "produit", "montantHt")
                .targetType(TP10VenteDTO.class)
                .build();
    }

    @Bean
    @StepScope
    public ItemProcessor<TP10VenteDTO, VenteEntity> tp10Processor(Tp10Tracker tp10Tracker) {
        // Compteur PARTAGE par tous les items du step. En Java une variable locale
        // capturee par un lambda doit etre "effectivement finale" : on passe donc par
        // une case de tableau. Ce compteur reste volontairement NON thread-safe :
        // c'est l'objet meme du TP.
        AtomicInteger compteur = new AtomicInteger(0);
        return dto -> {
            tp10Tracker.enregistrerThread(Thread.currentThread().getName());
            int numero = compteur.incrementAndGet();
            Thread.sleep(Constants.TP10_SLEEP_MS);

            tp10Tracker.enregistrerNumero(numero);
            return new VenteEntity(
                    LocalDate.parse(dto.getDate()),
                    dto.getIdBoutique(),
                    dto.getProduit() + "_" + numero,
                    dto.getMontantHt(),
                    dto.getMontantHt() * Constants.TVA
            );
        };
    }

    @Bean
    public ItemWriter<VenteEntity> tp10Writer(EntityManagerFactory entityManagerFactory) {
        return new JpaItemWriterBuilder<VenteEntity>()
                .entityManagerFactory(entityManagerFactory)
                .build();
    }

    @Bean
    public Step tp10Step(JobRepository jobRepository,
                         PlatformTransactionManager transactionManager,
                         ItemReader<TP10VenteDTO> tp10Reader,
                         ItemProcessor<TP10VenteDTO, VenteEntity> tp10Processor,
                         ItemWriter<VenteEntity> tp10Writer,
                         AsyncTaskExecutor tarificationTaskExecutor
    ) {
        return new StepBuilder("tp10Step", jobRepository)
                .<TP10VenteDTO, VenteEntity>chunk(Constants.CHUNK_SIZE)
                .reader(tp10Reader)
                .processor(tp10Processor)
                .writer(tp10Writer)
                .transactionManager(transactionManager)
                .taskExecutor(tarificationTaskExecutor)
                .build();
    }

    @Bean
    public AsyncTaskExecutor tarificationTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);       // nombre de threads au départ
        executor.setMaxPoolSize(4);        // nombre max de threads
        executor.setQueueCapacity(0);      // 0 = pas de file d'attente, on attend qu'un thread se libère
        executor.setThreadNamePrefix("batch-mt-"); // prefixe pour reperer ces threads dans les logs/thread dumps
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    @Bean
    public Job tp10Job(JobRepository jobRepository, Step tp10Step) {
        return new JobBuilder("tp10Job", jobRepository)
                .start(tp10Step)
                .build();
    }

    /* ------------------------------------------------------------------ */
    /* Infra fournie (DTO + tracker) — ne pas modifier                     */
    /* ------------------------------------------------------------------ */

    public static class TP10VenteDTO {
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
     * Collecteur thread-safe pour la demo et les tests :
     * - les threads reellement utilises par le processor (preuve du parallelisme) ;
     * - tous les numeros de traitement attribues (des doublons = course sur le compteur).
     */
    public static class Tp10Tracker {
        private final Set<String> threads = ConcurrentHashMap.newKeySet();
        private final List<Integer> numeros = Collections.synchronizedList(new ArrayList<>());

        public void enregistrerThread(String nom) {
            threads.add(nom);
        }

        public void enregistrerNumero(int n) {
            numeros.add(n);
        }

        public Set<String> threads() {
            return Set.copyOf(threads);
        }

        public int totalNumeros() {
            synchronized (numeros) {
                return numeros.size();
            }
        }

        public int numerosDistincts() {
            synchronized (numeros) {
                return new HashSet<>(numeros).size();
            }
        }

        public void reset() {
            threads.clear();
            synchronized (numeros) {
                numeros.clear();
            }
        }
    }

    @Bean
    public Tp10Tracker tp10Tracker() {
        return new Tp10Tracker();
    }
}

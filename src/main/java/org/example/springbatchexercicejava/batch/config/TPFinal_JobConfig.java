package org.example.springbatchexercicejava.batch.config;

import jakarta.persistence.EntityManagerFactory;
import org.example.springbatchexercicejava.batch.Constants;
import org.example.springbatchexercicejava.batch.model.CommandeEntity;
import org.example.springbatchexercicejava.batch.repository.CommandeRepository;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.FlowBuilder;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.flow.Flow;
import org.springframework.batch.core.listener.SkipListener;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.partition.Partitioner;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.ItemStreamReader;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.batch.infrastructure.item.database.builder.JpaItemWriterBuilder;
import org.springframework.batch.infrastructure.item.database.builder.JpaPagingItemReaderBuilder;
import org.springframework.batch.infrastructure.item.file.FlatFileParseException;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Configuration
public class TPFinal_JobConfig {


    @Bean
    public Step tpFinalControleFichierStep(JobRepository jobRepository, PlatformTransactionManager tm) {
        Tasklet task = (contribution, chunkContext) -> {
            Path fichier = Path.of(Constants.TPFINAL_COMMANDES_CSV);

            // Une exception -> le step passe FAILED -> le flow part sur .fail().
            if (!Files.exists(fichier)) {
                throw new IllegalStateException(
                        "Aucun fichier de commandes ce soir : " + fichier.toAbsolutePath());
            }
            if (Files.size(fichier) == 0) {
                throw new IllegalStateException("Fichier de commandes vide : " + fichier);
            }

            System.out.println("🧩 controleFichierStep : " + fichier + " (" + Files.size(fichier) + " octets)");
            return RepeatStatus.FINISHED;
        };
        return new StepBuilder("controleFichierStep", jobRepository)
                .tasklet(task, tm)
                .build();
    }


    @Bean
    public Step tpFinalControleEnteteStep(JobRepository jobRepository, PlatformTransactionManager tm) {
        Tasklet task = (contribution, chunkContext) -> {
            Path fichier = Path.of(Constants.TPFINAL_COMMANDES_CSV);

            // On ne lit QUE les deux premieres lignes : inutile de charger les 500.
            try (var lecteur = Files.newBufferedReader(fichier)) {
                String entete = lecteur.readLine();
                if (entete == null || !entete.trim().equals("ville;magasin;nb")) {
                    throw new IllegalStateException("En-tete invalide : \"" + entete + "\" (attendu \"" + "ville;magasin;nb" + "\")");
                }

                String premiereDonnee = lecteur.readLine();
                if (premiereDonnee == null || premiereDonnee.isBlank()) {
                    throw new IllegalStateException("Fichier sans aucune commande : " + fichier);
                }
            }

            System.out.println("ControleEnteteStep : en-tete conforme et fichier non vide");
            return RepeatStatus.FINISHED;
        };
        return new StepBuilder("controleEnteteStep", jobRepository)
                .tasklet(task, tm)
                .build();
    }

    @Bean
    @StepScope
    public ItemStreamReader<CommandeCsvDTO> tpFinalCommandeReader() {
        return new FlatFileItemReaderBuilder<CommandeCsvDTO>()
                .name("tpFinalCommandeReader")
                .resource(new FileSystemResource(Constants.TPFINAL_COMMANDES_CSV))
                .linesToSkip(1) // l'en-tete a deja ete validee par le controleEnteteStep
                .delimited()
                .delimiter(";")
                .names("ville", "magasin", "nb")
                .targetType(CommandeCsvDTO.class)
                .build();
    }

    @Bean
    public ItemProcessor<CommandeCsvDTO, CommandeEntity> tpFinalCommandeProcessor() {
        return dto -> {
            if (dto.getVille().isBlank() || dto.getMagasin().isBlank()) {
                throw new CommandeInvalideException("ville ou magasin manquant");
            }
            if (dto.getNb() <= 0) {
                throw new CommandeInvalideException("nombre de bouteilles invalide : " + dto.getNb());
            }
            return new CommandeEntity(dto.getVille(), dto.getMagasin(), dto.getNb());
        };
    }

    @Bean
    public ItemWriter<CommandeEntity> tpFinalCommandeWriter(EntityManagerFactory entityManagerFactory) {
        return new JpaItemWriterBuilder<CommandeEntity>()
                .entityManagerFactory(entityManagerFactory)
                .build();
    }

    @Bean
    public Step tpFinalImportStep(JobRepository jobRepository,
                                  PlatformTransactionManager tm,
                                  ItemStreamReader<CommandeCsvDTO> tpFinalCommandeReader,
                                  ItemProcessor<CommandeCsvDTO, CommandeEntity> tpFinalCommandeProcessor,
                                  ItemWriter<CommandeEntity> tpFinalCommandeWriter,
                                  CommandeRepository commandeRepository) {

        RejetsListener rejetsListener = new RejetsListener();

        return new StepBuilder("importStep", jobRepository)
                .<CommandeCsvDTO, CommandeEntity>chunk(Constants.CHUNK_SIZE)
                .reader(tpFinalCommandeReader)
                .processor(tpFinalCommandeProcessor)
                .writer(tpFinalCommandeWriter)
                .transactionManager(tm)
                .faultTolerant()
                .skip(FlatFileParseException.class)   // "nb" illisible ("abc")
                .skip(NumberFormatException.class)
                .skip(CommandeInvalideException.class) // "nb" negatif, ville/magasin vide
                .skipLimit(20)
                .skipListener(rejetsListener)
                .listener(rejetsListener)
                .build();
    }

    @Bean
    public Step tpFinalNettoyageStep(JobRepository jobRepository, PlatformTransactionManager tm) {
        Tasklet task = (contribution, chunkContext) -> {
            Files.deleteIfExists(Path.of(Constants.TPFINAL_CAMIONS_TXT));

            Path chauffeurs = Path.of(Constants.TPFINAL_CHAUFFEURS_DIR);
            if (Files.isDirectory(chauffeurs)) {
                try (Stream<Path> fichiers = Files.list(chauffeurs)) {
                    for (Path fichier : fichiers.toList()) {
                        Files.deleteIfExists(fichier);
                    }
                }
            }

            System.out.println("NettoyageStep : anciens camions.txt et chauffeurs/* supprimes");
            return RepeatStatus.FINISHED;
        };
        return new StepBuilder("nettoyageStep", jobRepository)
                .tasklet(task, tm)
                .build();
    }


    public static class VillePartitioner implements Partitioner {

        private final CommandeRepository commandeRepository;

        public VillePartitioner(CommandeRepository commandeRepository) {
            this.commandeRepository = commandeRepository;
        }

        @Override
        public Map<String, ExecutionContext> partition(int gridSize) {

            List<String> villes = commandeRepository.villesDistinctes();
            System.out.println("MeteoMasterStep : " + villes.size() + " ville(s) -> " + villes);

            Map<String, ExecutionContext> partitions = new LinkedHashMap<>();
            for (String ville : villes) {
                ExecutionContext contexte = new ExecutionContext();
                contexte.putString("ville", ville);
                partitions.put("partition_" + ville, contexte);
            }
            return partitions;
        }
    }

    @Bean
    @StepScope
    public ItemStreamReader<CommandeEntity> tpFinalMeteoReader(
            @Value("#{stepExecutionContext['ville']}") String ville,
            EntityManagerFactory entityManagerFactory) {
        return new JpaPagingItemReaderBuilder<CommandeEntity>()
                .name("tpFinalMeteoReader")
                .entityManagerFactory(entityManagerFactory)
                // ORDER BY obligatoire : sans tri stable, la pagination peut sauter des lignes.
                .queryString("SELECT c FROM CommandeEntity c WHERE c.ville = :ville ORDER BY c.id")
                .parameterValues(Map.of("ville", ville))
                .pageSize(Constants.CHUNK_SIZE)
                .build();
    }

    @Bean
    @StepScope
    public ItemProcessor<CommandeEntity, CommandeEntity> tpFinalMeteoProcessor(
            @Value("#{stepExecutionContext['ville']}") String ville,
            MeteoService meteoService) {

        double temp = meteoService.temperature(ville);
        double coef = 1.0;
        if (temp > 35) {
            coef *= 1.20;
        } else if (temp > 25) {
            coef = 1.10;
        }

        final double finalCoed = coef;

        return commande -> {
            commande.setNb((int) Math.round(commande.getNb() * finalCoed));
            return commande;
        };
    }

    @Bean
    public ItemWriter<CommandeEntity> tpFinalMeteoWriter(EntityManagerFactory entityManagerFactory) {
        // merge (defaut) : ce sont des lignes DEJA en base qu'on met a jour.
        return new JpaItemWriterBuilder<CommandeEntity>()
                .entityManagerFactory(entityManagerFactory)
                .build();
    }

    @Bean
    public Step tpFinalMeteoWorkerStep(JobRepository jobRepository,
                                       PlatformTransactionManager tm,
                                       ItemStreamReader<CommandeEntity> tpFinalMeteoReader,
                                       ItemProcessor<CommandeEntity, CommandeEntity> tpFinalMeteoProcessor,
                                       ItemWriter<CommandeEntity> tpFinalMeteoWriter) {
        return new StepBuilder("meteoWorkerStep", jobRepository)
                .<CommandeEntity, CommandeEntity>chunk(Constants.CHUNK_SIZE)
                .reader(tpFinalMeteoReader)
                .processor(tpFinalMeteoProcessor)
                .writer(tpFinalMeteoWriter)
                .transactionManager(tm)
                .build();
    }

    @Bean
    public Step tpFinalMeteoMasterStep(JobRepository jobRepository,
                                       Step tpFinalMeteoWorkerStep,
                                       CommandeRepository commandeRepository) {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("meteo-");
        executor.setConcurrencyLimit(8); // 8 villes traitees de front

        return new StepBuilder("meteoMasterStep", jobRepository)
                .partitioner("meteoWorkerStep", new VillePartitioner(commandeRepository))
                .step(tpFinalMeteoWorkerStep)
                .taskExecutor(executor)
                .build();
    }

    @Bean
    public Step tpFinalCamionsStep(JobRepository jobRepository,
                                   PlatformTransactionManager tm,
                                   CommandeRepository commandeRepository) {
        Tasklet task = (contribution, chunkContext) -> {
            String contenu = commandeRepository.totauxParVille().stream()
                    .map(total -> total.getVille() + " : " + total.getTotal())
                    .collect(Collectors.joining("\n", "", "\n"));

            Path fichier = Path.of(Constants.TPFINAL_CAMIONS_TXT);
            Files.createDirectories(fichier.getParent());
            Files.writeString(fichier, contenu);

            System.out.println("CamionsStep : " + fichier);
            return RepeatStatus.FINISHED;
        };
        return new StepBuilder("camionsStep", jobRepository)
                .tasklet(task, tm)
                .build();
    }

    @Bean
    public Step tpFinalChauffeursStep(JobRepository jobRepository,
                                      PlatformTransactionManager tm,
                                      CommandeRepository commandeRepository) {
        Tasklet task = (contribution, chunkContext) -> {
            Path dossier = Path.of(Constants.TPFINAL_CHAUFFEURS_DIR);
            Files.createDirectories(dossier);

            Map<String, List<CommandeRepository.TotalMagasin>> parVille =
                    commandeRepository.totauxParMagasin().stream()
                            .collect(Collectors.groupingBy(
                                    CommandeRepository.TotalMagasin::getVille,
                                    LinkedHashMap::new,
                                    Collectors.toList()));

            for (var entree : parVille.entrySet()) {
                String contenu = entree.getValue().stream()
                        .map(total -> total.getMagasin() + " : " + total.getTotal())
                        .collect(Collectors.joining("\n", "", "\n"));
                Files.writeString(dossier.resolve(entree.getKey() + ".txt"), contenu);
            }

            System.out.println("ChauffeursStep : " + parVille.size() + " fichier(s) dans " + dossier);
            return RepeatStatus.FINISHED;
        };
        return new StepBuilder("chauffeursStep", jobRepository)
                .tasklet(task, tm)
                .build();
    }


    @Bean
    public Step tpFinalArchivageStep(JobRepository jobRepository,
                                     PlatformTransactionManager tm,
                                     CommandeRepository commandeRepository) {
        Tasklet task = (contribution, chunkContext) -> {
            Path source = Path.of(fichierSource(chunkContext));
            Path archive = fichierArchive();
            Files.createDirectories(archive.getParent());
            Files.copy(source, archive, StandardCopyOption.REPLACE_EXISTING);

            long lignes = commandeRepository.count();
            commandeRepository.deleteAllInBatch();

            System.out.println("ArchivageStep : " + archive + ", " + lignes + " commande(s) purgee(s)");
            return RepeatStatus.FINISHED;
        };
        return new StepBuilder("archivageStep", jobRepository)
                .tasklet(task, tm)
                .build();
    }

    @Bean
    public Job tpFinalJob(JobRepository jobRepository,
                          Step tpFinalControleFichierStep,
                          Step tpFinalControleEnteteStep,
                          Step tpFinalImportStep,
                          Step tpFinalNettoyageStep,
                          Step tpFinalMeteoMasterStep,
                          Step tpFinalCamionsStep,
                          Step tpFinalChauffeursStep,
                          Step tpFinalArchivageStep) {

        // Les deux generations de fichiers sont independantes -> un split, donc deux threads.
        // Le job ne repart vers archivageStep que quand les DEUX branches sont COMPLETED.
        Flow flowCamions = new FlowBuilder<Flow>("flowCamions")
                .start(tpFinalCamionsStep)
                .build();

        Flow flowChauffeurs = new FlowBuilder<Flow>("flowChauffeurs")
                .start(tpFinalChauffeursStep)
                .build();

        Flow splitGeneration = new FlowBuilder<Flow>("splitGeneration")
                .split(new SimpleAsyncTaskExecutor("generation-"))
                .add(flowCamions, flowChauffeurs)
                .build();

        // @formatter:off
        return new JobBuilder("tpFinalJob", jobRepository)
                .start(tpFinalControleFichierStep).on("FAILED").fail()
                .from(tpFinalControleFichierStep).on("*").to(tpFinalControleEnteteStep)
                    .from(tpFinalControleEnteteStep).on("FAILED").fail()
                    .from(tpFinalControleEnteteStep).on("*").to(tpFinalImportStep)
                        // 20 rejets ou plus : skipLimit depasse -> FAILED -> fichier refuse
                        .from(tpFinalImportStep).on("FAILED").fail()
                        .from(tpFinalImportStep).on("*").to(tpFinalNettoyageStep)
                            .from(tpFinalNettoyageStep).on("*").to(tpFinalMeteoMasterStep)
                                .from(tpFinalMeteoMasterStep).on("*").to(splitGeneration)
                                    .from(splitGeneration).on("*").to(tpFinalArchivageStep)
                .end()
                .build();
        // @formatter:on
    }

    public static class CommandeCsvDTO {
        private String ville = "";
        private String magasin = "";
        private int nb = 0;

        public String getVille() {
            return ville;
        }

        public void setVille(String ville) {
            this.ville = ville;
        }

        public String getMagasin() {
            return magasin;
        }

        public void setMagasin(String magasin) {
            this.magasin = magasin;
        }

        public int getNb() {
            return nb;
        }

        public void setNb(int nb) {
            this.nb = nb;
        }

        @Override
        public String toString() {
            return ville + ";" + magasin + ";" + nb;
        }
    }


    public static class CommandeInvalideException extends RuntimeException {
        public CommandeInvalideException(String message) {
            super(message);
        }
    }

    public static class RejetsListener
            implements SkipListener<CommandeCsvDTO, CommandeEntity>, StepExecutionListener {

        private Path fichier;

        @Override
        public void beforeStep(StepExecution stepExecution) {
            fichier = fichierRejets();
            try {
                Files.createDirectories(fichier.getParent());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            ecrire("phase;donnee;cause\n", StandardOpenOption.TRUNCATE_EXISTING);
        }

        @Override
        public void onSkipInRead(Throwable t) {
            if (t instanceof FlatFileParseException e) {
                ecrire("LECTURE;" + e.getInput() + ";ligne " + e.getLineNumber() + " illisible\n",
                        StandardOpenOption.APPEND);
            } else {
                ecrire("LECTURE;?;" + t.getMessage() + "\n", StandardOpenOption.APPEND);
            }
        }

        @Override
        public void onSkipInProcess(CommandeCsvDTO item, Throwable t) {
            ecrire("TRAITEMENT;" + item + ";" + t.getMessage() + "\n", StandardOpenOption.APPEND);
        }

        @Override
        public void onSkipInWrite(CommandeEntity item, Throwable t) {
            ecrire("ECRITURE;" + item + ";" + t.getMessage() + "\n", StandardOpenOption.APPEND);
        }

        private void ecrire(String ligne, StandardOpenOption option) {
            try {
                Files.writeString(fichier, ligne, StandardOpenOption.CREATE, option);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    public static class MeteoService {

        private static final String URL = "https://api.openweathermap.org/data/2.5/find"
                + "?q={ville}&appid=b80967f0a6bd10d23e44848547b26550&units=metric&lang=fr";

        private final RestClient restClient = RestClient.create();

        /**
         * La temperature relevee, ou {@code Double.NaN} si l'API est injoignable.
         */
        public double temperature(String ville) {
            try {
                Reponse reponse = restClient.get()
                        .uri(URL, ville)
                        .retrieve()
                        .body(Reponse.class);

                if (reponse == null || reponse.list() == null || reponse.list().isEmpty()) {
                    System.out.println("Meteo : aucune donnee pour " + ville);
                    return 0.0;
                }
                return reponse.list().getFirst().main().temp();

            } catch (Exception e) {
                System.out.println("Meteo indisponible pour " + ville + " : " + e.getMessage());
                return 0.0;
            }
        }


        /* Le JSON de /find, reduit a ce qui nous interesse (Boot ignore le reste). */
        public record Reponse(List<Releve> list) {
        }

        public record Releve(String name, Main main) {
        }

        public record Main(double temp) {
        }
    }

    @Bean
    public MeteoService meteoService() {
        return new MeteoService();
    }

    public static Path dossierDuJour() {
        return Path.of(Constants.TPFINAL_OUT, LocalDate.now().toString());
    }

    public static Path fichierRejets() {
        return dossierDuJour().resolve("rejets.csv");
    }

    public static Path fichierArchive() {
        return dossierDuJour().resolve("commandes.csv");
    }

    private static String fichierSource(ChunkContext ctx) {
        Object valeur = ctx.getStepContext().getJobParameters().get("fichierSource");
        return valeur == null ? Constants.TPFINAL_COMMANDES_CSV : valeur.toString();
    }
}

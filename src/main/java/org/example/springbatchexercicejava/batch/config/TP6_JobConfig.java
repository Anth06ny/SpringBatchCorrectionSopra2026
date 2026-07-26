package org.example.springbatchexercicejava.batch.config;

import org.example.springbatchexercicejava.batch.Constants;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.ItemStreamReader;
import org.springframework.batch.infrastructure.item.ItemStreamWriter;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemWriterBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Locale;

@Configuration
public class TP6_JobConfig {

    /* ------------------------------------------------------------------ */
    /* Reader : le fichier lu depend du parametre `fichierSource`          */
    /* ------------------------------------------------------------------ */

    @Bean
    public ItemStreamReader<TP6VenteDTO> tp6Reader() {
        return new FlatFileItemReaderBuilder<TP6VenteDTO>()
                .name("tp6Reader")
                // La ressource n'est plus figee : elle vient du menu deroulant de l'IHM.
                .resource(new FileSystemResource(Constants.TP6_VENTES_CSV))
                .linesToSkip(1) // ligne d'en-tete
                .delimited()
                .delimiter(";")
                // La 4e colonne du CSV s'appelle "montantHt", mais on la mappe sur la propriete `montant`
                .names("date", "idBoutique", "produit", "montant")
                .targetType(TP6VenteDTO.class)
                .build();
    }

    @Bean
    public ItemProcessor<TP6VenteDTO, TP6VenteDTO> tp6Processor() {
        return new ItemProcessor<TP6VenteDTO, TP6VenteDTO>() {
            @Override
            public TP6VenteDTO process(TP6VenteDTO item) {
                //TODO
                return item;
            }
        };
    }

    @Bean
    public ItemStreamWriter<TP6VenteDTO> tp6Writer() {
        FileSystemResource sortie = new FileSystemResource(cheminRapportTp6("csv"));
        return new FlatFileItemWriterBuilder<TP6VenteDTO>()
                .name("tp6WriterCsv")
                .resource(sortie)
                .lineAggregator(v ->
                        v.getDate() + ";" + v.getIdBoutique() + ";" + v.getProduit()
                                + ";" + formatMontant(v.getMontant()))
                .headerCallback(writer -> writer.write("date;idBoutique;produit;montantHT"))
                .build();
    }

    /* ------------------------------------------------------------------ */
    /* Steps                                                               */
    /* ------------------------------------------------------------------ */

    @Bean
    public Step tp6Step(JobRepository jobRepository,
                        PlatformTransactionManager transactionManager,
                        ItemStreamReader<TP6VenteDTO> tp6Reader,
                        ItemProcessor<TP6VenteDTO, TP6VenteDTO> tp6Processor,
                        ItemStreamWriter<TP6VenteDTO> tp6Writer) {
        return new StepBuilder("tp6Step", jobRepository)
                .<TP6VenteDTO, TP6VenteDTO>chunk(Constants.CHUNK_SIZE)
                .reader(tp6Reader)
                .processor(tp6Processor)
                .writer(tp6Writer)
                .transactionManager(transactionManager)
                .build();
    }

    @Bean
    public Job tp6Job(JobRepository jobRepository, Step tp6Step) {
        return new JobBuilder("tp6Job", jobRepository)
                .start(tp6Step)
                .build();
    }

    /** Montant a 2 decimales avec un point (comme dans ventes.csv). */
    private static String formatMontant(double montant) {
        return String.format(Locale.US, "%.2f", montant);
    }

    /** Chemin du rapport TP6, l'extension suivant le format demande. */
    public static String cheminRapportTp6(String format) {
        return "data/out/tp6_ventes_sortie." + format.toLowerCase(Locale.ROOT);
    }

    public static class TP6VenteDTO {
        private String date = "";
        private String idBoutique = "";
        private String produit = "";
        private double montant = 0.0;

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

        public double getMontant() {
            return montant;
        }

        public void setMontant(double montant) {
            this.montant = montant;
        }
    }
}

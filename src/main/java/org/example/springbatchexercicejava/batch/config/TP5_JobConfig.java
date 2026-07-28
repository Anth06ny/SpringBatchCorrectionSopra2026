package org.example.springbatchexercicejava.batch.config;

import jakarta.persistence.EntityManagerFactory;
import org.example.springbatchexercicejava.batch.Constants;
import org.example.springbatchexercicejava.batch.model.VenteEntity;
import org.example.springbatchexercicejava.batch.repository.VenteRepository;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.database.JpaPagingItemReader;
import org.springframework.batch.infrastructure.item.database.builder.JpaPagingItemReaderBuilder;
import org.springframework.batch.infrastructure.item.file.FlatFileItemWriter;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemWriterBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Locale;

@Configuration
public class TP5_JobConfig {

    @Bean
    public JpaPagingItemReader<VenteEntity> venteJpaReader(EntityManagerFactory emf) {
        return new JpaPagingItemReaderBuilder<VenteEntity>()
                .name("venteJpaReader")
                .entityManagerFactory(emf)
                //ORDER Obligatoire sur une clé unique
                .queryString("SELECT v FROM VenteEntity v ORDER BY v.prixHt desc")
                .pageSize(10)
                .build();
    }

    @Bean
    public FlatFileItemWriter<VenteEntity> venteFileWriter(VenteRepository venteRepository) {
        return new FlatFileItemWriterBuilder<VenteEntity>()
                .name("venteFileWriter")
                .resource(new FileSystemResource(Constants.TP5_OUTPUT))

                //V1 : Consrtuit la ligne par instrospection en appelant les attributs de VenteEntity
                //.delimited().delimiter(";").names("dateVente", "boutique", "libelleProduit", "prixHt", "prixTtc")
                .lineAggregator(v -> v.getDateVente().toString() + ";" + v.getBoutique() + ";" + v.getLibelleProduit() + ";" + formatMontant(v.getPrixHt()))
                //Ligne d'en tête
                .headerCallback(writer -> writer.write("date;idBoutique;produit;montantHt"))
                //Ligne de fin
                .footerCallback(writer -> {
                            var total = venteRepository.sumPrixTtc();
                            writer.write("Total : " + formatMontant(total));
                        }
                )
                .build();
    }


    // Step chunk : lit la base -> ecrit le CSV, par lots de 10.
    @Bean
    public Step tp5Step(JobRepository jobRepository,
                        PlatformTransactionManager transactionManager,
                        //1 Reader
                        JpaPagingItemReader<VenteEntity> venteJpaReader,
                        //1 Writer (Si plusieurs on charge celui qui fera la liaison)
                        FlatFileItemWriter<VenteEntity> venteFileWriter) {
        return new StepBuilder("tp5Step", jobRepository)
                .<VenteEntity, VenteEntity>chunk(10)
                    .reader(venteJpaReader)
                    .writer(venteFileWriter)
                .transactionManager(transactionManager)
                .build();
    }

    @Bean
    public Job tp5Job(JobRepository jobRepository, Step tp5Step) {
        return new JobBuilder("tp5Job", jobRepository)
                .start(tp5Step)
                .build();
    }

    /**
     * Montant a 2 decimales avec un point (comme dans ventes.csv).
     */
    private String formatMontant(double montant) {
        return String.format(Locale.US, "%.2f", montant);
    }
}

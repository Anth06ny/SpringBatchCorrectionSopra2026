package org.example.springbatchexercicejava.batch.config;

import org.example.springbatchexercicejava.batch.Constants;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.item.file.FlatFileItemWriter;
import org.springframework.batch.infrastructure.item.file.MultiResourceItemReader;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemWriterBuilder;
import org.springframework.batch.infrastructure.item.file.builder.MultiResourceItemReaderBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.HashSet;
import java.util.Set;

/**
 * TP12 — Tester un batch (le TP est INVERSE : ce JobConfig est fourni COMPLET,
 * c'est TP12JobTest que tu remplis).
 *
 * Le job fusionne deux CSV (`ventes_A.csv` + `ventes_B.csv`) en un troisieme
 * (`tp12_fusion.csv`) en supprimant les DOUBLONS (lignes identiques). Deux angles
 * de test a couvrir :
 *
 *   - le READER en ISOLATION : il VALIDE l'en-tete (skippedLinesCallback). Un CSV
 *     dont les colonnes sont dans le mauvais ordre, ou une ligne au mauvais nombre
 *     de colonnes, doit etre REFUSE.
 *   - le JOB COMPLET : lance de bout en bout (jobOperator.start), on verifie le
 *     statut et le contenu du fichier de sortie (fusion sans doublon).
 *
 * ⚠️ Piege pedagogique du TP : un FlatFileItemReader mappe les colonnes PAR POSITION
 * et SAUTE l'en-tete sans la lire. Donc, seul, il ne detecte PAS un mauvais ORDRE de
 * colonnes (il mapperait "produit" sur le champ "date" sans broncher). C'est le
 * skippedLinesCallback ci-dessous qui compare la ligne d'en-tete a TP12_ENTETE et
 * leve une exception si elle differe. Le mauvais NOMBRE de colonnes, lui, est detecte
 * nativement (tokenizer `strict` par defaut -> FlatFileParseException a la lecture).
 */
@Configuration
public class TP12_JobConfig {

    /**
     * Reader d'UN fichier de ventes, avec VALIDATION de l'en-tete.
     * Pas de `.resource(...)` ici : la ressource est fixee soit par le
     * MultiResourceItemReader (dans le job), soit par le test (en isolation).
     * Type de retour CONCRET (FlatFileItemReader) pour que le test puisse appeler
     * `setResource(...)` puis `open()/read()`.
     */
    @Bean
    public FlatFileItemReader<TP12VenteDTO> tp12Reader() {
        return new FlatFileItemReaderBuilder<TP12VenteDTO>()
                .name("tp12Reader")
                .linesToSkip(1) // on saute l'en-tete...
                .skippedLinesCallback(entete -> { // ...mais on la VALIDE au passage
                    if (!entete.trim().equals(Constants.TP12_ENTETE)) {
                        throw new IllegalArgumentException(
                                "En-tete CSV invalide : \"" + entete + "\" (attendu \""
                                        + Constants.TP12_ENTETE + "\")");
                    }
                })
                .delimited()
                .delimiter(";")
                .names("date", "idBoutique", "produit", "montantHt")
                .targetType(TP12VenteDTO.class)
                .build();
    }

    /**
     * Lit les DEUX fichiers a la suite via le meme delegate (tp12Reader).
     * MultiResourceItemReader fixe la ressource du delegate fichier par fichier.
     */
    @Bean
    public MultiResourceItemReader<TP12VenteDTO> tp12MultiReader(
            FlatFileItemReader<TP12VenteDTO> tp12Reader) {
        return new MultiResourceItemReaderBuilder<TP12VenteDTO>()
                .name("tp12MultiReader")
                .delegate(tp12Reader)
                .resources(new FileSystemResource(Constants.TP12_VENTES_A),
                        new FileSystemResource(Constants.TP12_VENTES_B))
                .build();
    }

    /**
     * Deduplication : on garde une ligne la PREMIERE fois qu'on la voit, on renvoie
     * `null` (= filtre) ensuite. La cle est la ligne entiere (les 4 champs).
     * @StepScope => le Set est NEUF a chaque execution (sinon il garderait les cles
     * du run precedent). Step mono-thread ici : un HashSet simple suffit.
     */
    @Bean
    @StepScope
    public ItemProcessor<TP12VenteDTO, TP12VenteDTO> tp12Processor() {
        Set<String> dejaVues = new HashSet<>();
        return dto -> {
            String cle = dto.getDate() + ";" + dto.getIdBoutique() + ";"
                    + dto.getProduit() + ";" + dto.getMontantHt();
            return dejaVues.add(cle) ? dto : null; // add() == false -> doublon -> filtre
        };
    }

    /** Ecrit le CSV fusionne (memes colonnes, meme en-tete). */
    @Bean
    public FlatFileItemWriter<TP12VenteDTO> tp12Writer() {
        return new FlatFileItemWriterBuilder<TP12VenteDTO>()
                .name("tp12Writer")
                .resource(new FileSystemResource(Constants.TP12_FUSION_OUTPUT))
                .headerCallback(writer -> writer.write(Constants.TP12_ENTETE))
                .delimited()
                .delimiter(";")
                .names("date", "idBoutique", "produit", "montantHt")
                .build();
    }

    @Bean
    public Step tp12Step(JobRepository jobRepository,
                         PlatformTransactionManager transactionManager,
                         MultiResourceItemReader<TP12VenteDTO> tp12MultiReader,
                         ItemProcessor<TP12VenteDTO, TP12VenteDTO> tp12Processor,
                         FlatFileItemWriter<TP12VenteDTO> tp12Writer) {
        return new StepBuilder("tp12Step", jobRepository)
                .<TP12VenteDTO, TP12VenteDTO>chunk(Constants.CHUNK_SIZE)
                .reader(tp12MultiReader)
                .processor(tp12Processor)
                .writer(tp12Writer)
                .transactionManager(transactionManager)
                .build();
    }

    @Bean
    public Job tp12Job(JobRepository jobRepository, Step tp12Step) {
        return new JobBuilder("tp12Job", jobRepository)
                .start(tp12Step)
                .build();
    }

    /** DTO du CSV, propre au TP12 (constructeur sans argument + setters, requis par BeanWrapperFieldSetMapper). */
    public static class TP12VenteDTO {
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

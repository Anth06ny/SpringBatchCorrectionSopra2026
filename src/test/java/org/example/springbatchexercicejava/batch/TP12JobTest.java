package org.example.springbatchexercicejava.batch;

import org.example.springbatchexercicejava.batch.config.TP12_JobConfig;
import org.example.springbatchexercicejava.batch.config.TP12_JobConfig.TP12VenteDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamException;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.item.file.FlatFileParseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.FileSystemResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.example.springbatchexercicejava.batch.Constants.TP12_FUSION_OUTPUT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

@SpringBootTest
@TestMethodOrder(MethodOrderer.MethodName.class)
class TP12JobTest {

    @Autowired
    JobOperator jobOperator;

    @Autowired
    FlatFileItemReader<TP12VenteDTO> tp12Reader;

    @Autowired
    @Qualifier("tp12Job")
    Job tp12Job;

    @Test
    @DisplayName("01 reader - entete valide lit toutes les lignes")
    void test01ReaderEnteteValide() {
        // TODO 01 : lire "data/tp12/ventes_A.csv" via tp12Reader EN ISOLATION, puis verifier :
        FlatFileItemReader<TP12VenteDTO> reader = new TP12_JobConfig().tp12Reader();
        reader.setResource(new FileSystemResource("data/tp12/ventes_A.csv"));
        reader.open(new ExecutionContext());   // obligatoire avant read()
        try {
            List<TP12VenteDTO> lignes = new ArrayList<>();
            TP12VenteDTO ligne;
            while ((ligne = reader.read()) != null) {
                lignes.add(ligne);
            }
            assertEquals(5, lignes.size());

            assertEquals("Clavier", lignes.getFirst().getProduit());
            assertEquals("B01", lignes.getFirst().getIdBoutique());

        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            reader.close();
        }
    }

    @Test
    @DisplayName("02 reader - entete dans le mauvais ordre est rejetee")
    void test02ReaderEnteteDesordre() {
        // TODO 02 : lire "data/tp12/entete_desordre.csv" doit ECHOUER des l'open()
        //   (le reader valide l'en-tete). Utiliser assertThrows(..., () -> { ... }).
        FlatFileItemReader<TP12VenteDTO> reader = new TP12_JobConfig().tp12Reader();
        reader.setResource(new FileSystemResource("data/tp12/entete_desordre.csv"));

        try {
            reader.open(new ExecutionContext());   // obligatoire avant read()
            fail("IllegalArgumentException aurait du être levé suite à l'en tête invalide");
        } catch (ItemStreamException e) {

        } catch (Exception e) {
            e.printStackTrace();
            fail("ItemStreamException aurait du être levé suite à l'en tête invalide");
        } finally {
            reader.close();
        }

    }

    @Test
    @DisplayName("03 reader - nombre de colonnes invalide est rejete")
    void test03ReaderColonnesManquantes() {
        // TODO 03 : lire "data/tp12/colonnes_manquantes.csv" doit ECHOUER a la LECTURE
        //   de la ligne fautive (3 colonnes). Utiliser assertThrows(..., () -> { ... }).
        FlatFileItemReader<TP12VenteDTO> reader = new TP12_JobConfig().tp12Reader();
        reader.setResource(new FileSystemResource("data/tp12/colonnes_manquantes.csv"));
        reader.open(new ExecutionContext());   // obligatoire avant read()
        try {
            List<TP12VenteDTO> lignes = new ArrayList<>();
            TP12VenteDTO ligne;
            while ((ligne = reader.read()) != null) {
                lignes.add(ligne);
            }

            fail("FlatFileParseException aurait du être levé suite à une ligne au mauvais nombre de colonnes");

        } catch (FlatFileParseException e) {

        } catch (Exception e) {
            e.printStackTrace();
            fail("FlatFileParseException aurait du être levé suite à une ligne au mauvais nombre de colonnes");
        } finally {
            reader.close();
        }
    }

    @Test
    @DisplayName("04 job - fusionne les deux csv sans doublon")
    void test04JobFusionSansDoublon() throws Exception {
        // TODO 04 : lancer tp12Job (jobOperator.start avec un parametre unique), verifier
        //   BatchStatus.COMPLETED, puis relire TP12_FUSION_OUTPUT : 8 lignes de donnees
        //   (5 + 5 - 2 doublons), aucune ligne en double.
        JobParameters params = new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        JobExecution execution = jobOperator.start(tp12Job, params);
        assertEquals(BatchStatus.COMPLETED, execution.getStatus());


        //version je lis moi
        List<String> lignes = Files.readAllLines(Path.of(TP12_FUSION_OUTPUT));
        lignes = lignes.subList(1, lignes.size()); // on retire l'en-tete
        assertEquals(8L, lignes.size(), "Le fichier de sortie devrait normalement avoir 8 lignes hors en-tête");

        //version je récupère le nombre de lignes écrites par les steps
        long ecrites = execution.getStepExecutions().stream()
                .mapToLong(StepExecution::getWriteCount)
                .sum();
        assertEquals(8L, ecrites, "Le fichier de sortie devrait normalement avoir 8 lignes hors en-tête");

        assertEquals(lignes.size(), new HashSet<>(lignes).size(), "aucune ligne en double dans la sortie");


    }
}

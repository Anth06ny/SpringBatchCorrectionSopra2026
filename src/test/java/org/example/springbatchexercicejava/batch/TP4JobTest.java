package org.example.springbatchexercicejava.batch;

import jakarta.persistence.EntityManagerFactory;
import org.example.springbatchexercicejava.batch.config.TP4_JobConfig;
import org.example.springbatchexercicejava.batch.model.VenteCsvDTO;
import org.example.springbatchexercicejava.batch.model.VenteEntity;
import org.example.springbatchexercicejava.batch.repository.VenteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

@SpringBootTest
class TP4JobTest {

    @Autowired
    JobOperator jobOperator;

    // Injection par nom : il faut un @Bean nomme tp4Job
    @Autowired
    @Qualifier("tp4Job")
    Job tp4Job;

    @Autowired
    VenteRepository venteRepository;

    // Le writer JPA a besoin de l'EntityManagerFactory et d'un gestionnaire de
    // transaction : contrairement au reader/processor, on ne peut pas le tester
    // sans le contexte Spring (mais on l'isole quand meme du job et du step).
    @Autowired
    EntityManagerFactory entityManagerFactory;

    @Autowired
    PlatformTransactionManager transactionManager;

    /**
     * Test ISOLE du reader : ni job, ni processor, ni writer, ni contexte Spring.
     * On instancie directement la config et on ouvre le reader a la main.
     *
     * Les proprietes du DTO sont lues par INTROSPECTION (voir `lire`) : le test
     * compile donc meme si VenteCsvDTO n'a encore aucun attribut, et echoue avec
     * un message parlant tant que l'apprenant n'a pas ajoute les champs attendus.
     */
    @Test
    void venteReaderTest() throws Exception {
        int attendu = nbLignesCsv();
        FlatFileItemReader<VenteCsvDTO> reader = new TP4_JobConfig().venteReader();

        // open() est OBLIGATOIRE avant read() (sinon ReaderNotOpenException).
        reader.open(new ExecutionContext());
        try {
            List<VenteCsvDTO> lignes = new ArrayList<>();
            VenteCsvDTO ligne;
            while ((ligne = reader.read()) != null) {
                lignes.add(ligne);
            }

            // Toutes les lignes de donnees sont lues, l'en-tete est saute.
            assertEquals(
                    attendu, lignes.size(),
                    "Nombre de lignes lues incorrect : le reader doit sauter l'en-tete (.linesToSkip(1)) et lire les "
                            + attendu + " lignes de data/ventes.csv"
            );
            assertNull(
                    reader.read(),
                    "Le reader doit renvoyer null en fin de flux (une fois toutes les lignes lues)"
            );

            // Le decoupage ';' et le mapping vers le DTO sont corrects (1re ligne).
            VenteCsvDTO premiere = lignes.get(0);
            assertEquals(
                    "2026-01-05", lire(premiere, "date"),
                    "Colonne 'date' mal mappee : verifie l'ordre des .names(...) du reader et le champ 'date' de VenteCsvDTO"
            );
            assertEquals(
                    "B01", lire(premiere, "idBoutique"),
                    "Colonne 'idBoutique' mal mappee : verifie les .names(...) du reader et le champ 'idBoutique' de VenteCsvDTO"
            );
            assertEquals(
                    "Clavier mecanique", lire(premiere, "produit"),
                    "Colonne 'produit' mal mappee : verifie les .names(...) du reader et le champ 'produit' de VenteCsvDTO"
            );
            assertEquals(
                    79.90, ((Number) lire(premiere, "montantHt")).doubleValue(), 0.001,
                    "Colonne 'montantHt' mal mappee : verifie le delimiteur ';', les .names(...) et le type double du champ 'montantHt'"
            );
        } finally {
            reader.close();
        }
    }

    /**
     * Test ISOLE du processor : simple appel de fonction sur un DTO construit a la main.
     * -> valide le mapping DTO->Entity et le calcul du TTC sans base ni contexte Spring.
     *
     * Le DTO est alimente par INTROSPECTION (`ecrire`) car VenteCsvDTO est a coder :
     * le test compile meme quand le DTO est vide. En revanche VenteEntity est FOURNIE,
     * donc ses champs sont lus directement (pas d'introspection cote entite).
     */
    @Test
    void venteProcessorTest() throws Exception {
        VenteCsvDTO csv = new VenteCsvDTO();
        ecrire(csv, "date", "2026-01-05");
        ecrire(csv, "idBoutique", "B01");
        ecrire(csv, "produit", "Clavier mecanique");
        ecrire(csv, "montantHt", 100.0);

        // process() renvoie null si le processor n'est pas encore code
        VenteEntity entity = new TP4_JobConfig().venteProcessor().process(csv);
        if (entity == null) {
            fail("venteProcessor().process(...) a renvoye null : le processor doit toujours retourner une VenteEntity (aucun filtrage dans ce TP)");
        }

        // Noms cote entite = noms METIER (differents du DTO) : c'est ce mapping qu'on verifie.
        assertEquals(
                LocalDate.parse("2026-01-05"), entity.getDateVente(),
                "Le processor doit mapper csv.date (String) -> VenteEntity.dateVente (LocalDate.parse)"
        );
        assertEquals(
                "B01", entity.getBoutique(),
                "Le processor doit mapper csv.idBoutique -> VenteEntity.boutique"
        );
        assertEquals(
                "Clavier mecanique", entity.getLibelleProduit(),
                "Le processor doit mapper csv.produit -> VenteEntity.libelleProduit"
        );
        assertEquals(
                100.0, entity.getPrixHt(), 0.001,
                "Le processor doit mapper csv.montantHt -> VenteEntity.prixHt"
        );
        assertEquals(
                100.0 * Constants.TVA, entity.getPrixTtc(), 0.001,
                "prixTtc mal calcule : il doit valoir prixHt * TVA (" + Constants.TVA + "), calcule dans le processor"
        );
    }

    /**
     * Test ISOLE du writer : hors job et hors step, mais AVEC le contexte Spring.
     * Le JpaItemWriter exige un EntityManagerFactory et une transaction active pour
     * persister/flusher — impossible de s'en passer, d'ou l'usage d'un TransactionTemplate.
     *
     * VenteEntity etant fournie, on construit les entites directement (pas d'introspection).
     */
    @Test
    void venteWriterTest() {
        ItemWriter<VenteEntity> writer = new TP4_JobConfig().venteWriter(entityManagerFactory);

        Chunk<VenteEntity> lot = new Chunk<>(List.of(
                new VenteEntity(LocalDate.parse("2026-01-05"), "B01", "Clavier mecanique", 100.0, 120.0),
                new VenteEntity(LocalDate.parse("2026-01-06"), "B02", "Souris sans fil", 50.0, 60.0)
        ));

        // JpaItemWriter persiste dans l'EntityManager lie a la transaction courante :
        // sans transaction active, rien n'est ecrit. On enveloppe donc l'appel.
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            try {
                writer.write(lot);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });

        // Tout le lot est en base.
        assertEquals(
                2L, venteRepository.count(),
                "Le writer doit persister toutes les ventes du lot recu"
        );

        // Les valeurs sont bien celles ecrites (verifie sur la ligne B01).
        VenteEntity persistee = venteRepository.findAll().stream()
                .filter(v -> "B01".equals(v.getBoutique()))
                .findFirst()
                .orElseThrow();
        assertEquals(
                "Clavier mecanique",
                persistee.getLibelleProduit(),
                "Le writer ne doit pas alterer les donnees"
        );
        assertEquals(100.0, persistee.getPrixHt(), 0.001);
        assertEquals(120.0, persistee.getPrixTtc(), 0.001);
    }

    @Test
    void testCompletTP4Job() throws Exception {
        int attendu = nbLignesCsv();

        JobParameters params = new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        JobExecution execution = jobOperator.start(tp4Job, params);

        // 1) Le job se termine avec succes
        assertEquals(BatchStatus.COMPLETED, execution.getStatus());

        // 2) Toutes les lignes du fichier sont en base
        List<VenteEntity> ventes = venteRepository.findAll();
        assertEquals(
                (long) attendu, venteRepository.count(),
                "La table VENTE doit contenir exactement le nombre de lignes du CSV"
        );

        // 3) Le TTC est bien calcule (HT * 1,20) — verifie sur au moins une ligne
        VenteEntity vente = ventes.get(0);
        double ttcAttendu = vente.getPrixHt() * Constants.TVA;
        assertEquals(
                0, Double.compare(vente.getPrixTtc(), ttcAttendu),
                "prixTtc doit valoir prixHt * 1,20"
        );

        // 4) Metriques du step : tout lu = tout ecrit, et 1 commit par chunk
        StepExecution step = execution.getStepExecutions().iterator().next();
        assertEquals((long) attendu, step.getReadCount(), "readCount = nb de lignes");
        assertEquals((long) attendu, step.getWriteCount(), "writeCount = nb de lignes");
        assertEquals(
                (long) ((attendu + Constants.CHUNK_SIZE - 1) / Constants.CHUNK_SIZE), step.getCommitCount(),
                "commitCount = ceil(nbLignes / tailleChunk)"
        );
    }

    /**
     * Lit une propriete par INTROSPECTION. Si la classe ne l'expose pas encore
     * (attribut a coder dans le TP), on echoue avec un message explicite plutot
     * que de casser la COMPILATION du test.
     */
    private Object lire(Object cible, String nom) {
        BeanWrapper bw = new BeanWrapperImpl(cible);
        if (!bw.isReadableProperty(nom)) {
            fail("La classe " + cible.getClass().getSimpleName() + " doit exposer une propriete '"
                    + nom + "' avec son getter (a coder dans le TP)");
        }
        return bw.getPropertyValue(nom);
    }

    /** Ecrit une propriete par INTROSPECTION (meme logique que `lire`, cote setter). */
    private void ecrire(Object cible, String nom, Object valeur) {
        BeanWrapper bw = new BeanWrapperImpl(cible);
        if (!bw.isWritableProperty(nom)) {
            fail("La classe " + cible.getClass().getSimpleName() + " doit exposer une propriete '"
                    + nom + "' avec son setter (a coder dans le TP)");
        }
        bw.setPropertyValue(nom, valeur);
    }

    /** Nombre de lignes de donnees du CSV (hors en-tete) : la reference attendue en base. */
    private int nbLignesCsv() throws IOException {
        return (int) Files.readAllLines(Path.of(Constants.VENTES_CSV)).stream()
                .filter(l -> !l.isBlank())
                .count() - 1;
    }

    @BeforeEach
    void clean() {
        venteRepository.deleteAll();
    }
}

package org.example.springbatchexercicejava.batch;

import org.example.springbatchexercicejava.batch.repository.VenteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Suite de validation du TP5 (export DB -> CSV).
 *
 * Mise en place : on importe d'abord ventes.csv en base (tp4Job) puis on genere
 * ventes_tp5.csv (tp5Job). Les assertions portent ensuite sur le fichier produit.
 */
@SpringBootTest
class TP5JobTest {

    @Autowired
    JobOperator jobOperator;

    @Autowired
    @Qualifier("tp4Job")
    Job tp4Job;

    @Autowired
    @Qualifier("tp5Job")
    Job tp5Job;

    @Autowired
    VenteRepository venteRepository;

    /** Table remise a zero, puis import (TP4) + export (TP5) : le fichier est pret pour les tests. */
    @BeforeEach
    void genererLeCsv() throws Exception {
        venteRepository.deleteAll();
        lancer(tp4Job);
        lancer(tp5Job);
    }

    @Test
    @DisplayName("ventes_tp5.csv triees par montant decroissant")
    void ventesTp5CsvTrieesParMontantDecroissant() throws IOException {
        // Reference : les lignes de ventes.csv, triees par montant DECROISSANT.
        List<String> attendues = lignesDataSource().stream()
                .sorted(Comparator.comparingDouble(this::montant).reversed())
                .toList();

        // Observe : uniquement les lignes de donnees du fichier genere (en-tete et
        // pied de page exclus). Ce test reste vrai meme SANS ligne d'en-tete, car on
        // ne garde que les lignes ayant un montant numerique en 4e colonne.
        List<String> obtenues = lignesFichier().stream().filter(this::estLigneData).toList();

        assertEquals(
                attendues, obtenues,
                "Les ventes exportees doivent etre celles de ventes.csv, triees par montant HT decroissant"
        );
    }

    @Test
    @DisplayName("ventes_tp5.csv avec header")
    void ventesTp5CsvAvecHeader() throws IOException {
        assertEquals(
                "date;idBoutique;produit;montantHt", lignesFichier().get(0),
                "La 1re ligne du fichier doit etre l'en-tete des colonnes"
        );
    }

    @Test
    @DisplayName("ventes_tp5.csv avec footer")
    void ventesTp5CsvAvecFooter() throws IOException {
        double totalAttendu = lignesDataSource().stream().mapToDouble(this::montant).sum() * Constants.TVA;

        List<String> lignes = lignesFichier();
        String derniere = lignes.get(lignes.size() - 1);
        assertTrue(
                derniere.startsWith("Total : "),
                "La derniere ligne doit commencer par 'Total : ' (etait: \"" + derniere + "\")"
        );
        assertEquals(
                "Total : " + format(totalAttendu), derniere,
                "Le total en pied de page doit valoir la somme des montants TTC"
        );
    }

    /* -------------------------------- Helpers -------------------------------- */

    private void lancer(Job job) throws Exception {
        JobParameters params = new JobParametersBuilder()
                .addLong("timestamp", System.nanoTime())
                .toJobParameters();
        JobExecution execution = jobOperator.start(job, params);
        assertEquals(
                BatchStatus.COMPLETED,
                execution.getStatus(),
                job.getName() + " doit se terminer avec succes"
        );
    }

    /** Lignes non vides du fichier genere (racine du projet = repertoire de travail). */
    private List<String> lignesFichier() throws IOException {
        return Files.readAllLines(Path.of(Constants.TP5_OUTPUT)).stream()
                .filter(l -> !l.isBlank())
                .toList();
    }

    /** Lignes de donnees de ventes.csv (en-tete saute). */
    private List<String> lignesDataSource() throws IOException {
        return Files.readAllLines(Path.of(Constants.VENTES_CSV)).stream()
                .filter(l -> !l.isBlank())
                .skip(1)
                .toList();
    }

    /** Une ligne de donnees = 4 colonnes dont la 4e est un montant numerique (exclut en-tete et total). */
    private boolean estLigneData(String ligne) {
        String[] cols = ligne.split(";");
        if (cols.length != 4) {
            return false;
        }
        try {
            Double.parseDouble(cols[3]);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private double montant(String ligne) {
        return Double.parseDouble(ligne.split(";")[3]);
    }

    private String format(double montant) {
        return String.format(Locale.US, "%.2f", montant);
    }
}

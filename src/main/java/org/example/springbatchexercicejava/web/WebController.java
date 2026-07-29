package org.example.springbatchexercicejava.web;

import org.example.springbatchexercicejava.batch.Constants;
import org.example.springbatchexercicejava.batch.config.TP10_JobConfig;
import org.example.springbatchexercicejava.batch.config.TP11_JobConfig;
import org.example.springbatchexercicejava.batch.config.TP6_JobConfig;
import org.example.springbatchexercicejava.batch.config.TPFinal_JobConfig;
import org.example.springbatchexercicejava.batch.model.VenteEntity;
import org.example.springbatchexercicejava.batch.repository.VenteRepository;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobExecutionException;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.launch.JobRestartException;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Controller
public class WebController {

    private final JobOperator jobOperator;
    private final JobRepository jobRepository;
    private final VenteRepository venteRepository;
    private final Job helloJob, secondJob;
    private final Job tp5Job, tp3Job,tp4Job;
    private final Job tp6Job;
    private final Job tp7Job;
    private final Job tp8Job;
    private final Job tp10Job;
    private final TP10_JobConfig.Tp10Tracker tp10Tracker;
    private final Job tp11Job;
    private final TP11_JobConfig.Tp11Tracker tp11Tracker;
    private final Job tp12Job;
    private final Job tpFinalJob;
    private final ApplicationContext applicationContext;

    public WebController(JobOperator jobOperator,
                         JobRepository jobRepository,
                         VenteRepository venteRepository,
                         @Qualifier("helloJob") Job helloJob,
                         Job secondJob,
                         @Qualifier("tp5Job") Job tp5Job,
                         Job tp3Job,
                         Job tp4Job,
                         @Qualifier("tp6Job") Job tp6Job,
                         @Qualifier("tp7Job") Job tp7Job,
                         @Qualifier("tp8Job") Job tp8Job,
                         @Qualifier("tp10Job") Job tp10Job,
                         TP10_JobConfig.Tp10Tracker tp10Tracker,
                         @Qualifier("tp11Job") Job tp11Job,
                         TP11_JobConfig.Tp11Tracker tp11Tracker,
                         @Qualifier("tp12Job") Job tp12Job,
                         @Qualifier("tpFinalJob") Job tpFinalJob,
                         ApplicationContext applicationContext) {
        this.jobOperator = jobOperator;
        this.jobRepository = jobRepository;
        this.venteRepository = venteRepository;
        this.helloJob = helloJob;
        this.secondJob = secondJob;
        this.tp5Job = tp5Job;
        this.tp3Job = tp3Job;
        this.tp4Job = tp4Job;
        this.tp6Job = tp6Job;
        this.tp7Job = tp7Job;
        this.tp8Job = tp8Job;
        this.tp10Job = tp10Job;
        this.tp10Tracker = tp10Tracker;
        this.tp11Job = tp11Job;
        this.tp11Tracker = tp11Tracker;
        this.tp12Job = tp12Job;
        this.tpFinalJob = tpFinalJob;
        this.applicationContext = applicationContext;
    }

    /**
     * Le numero place en suffixe d'un libelle "Produit_42" (0 si absent).
     */
    private static int numeroDuLibelle(String libelle) {
        String suffixe = libelle.substring(libelle.lastIndexOf('_') + 1);
        try {
            return Integer.parseInt(suffixe);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("executions", recentExecutions(12));
        return "index";
    }

    @PostMapping("/jobs/hello")
    public String tp1(RedirectAttributes redirect) throws Exception {
        // Parametre unique : sinon Spring Batch refuse de relancer un job deja
        // termine avec les memes parametres.
        var params = new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        JobExecution execution = jobOperator.start(helloJob, params);

        redirect.addFlashAttribute(
                "message",
                "helloJob → " + execution.getStatus() + " (exécution #" + execution.getId() + ")"
        );
        return "redirect:/";
    }

    @PostMapping("/jobs/second")
    public String tp1_2(RedirectAttributes redirect) throws JobInstanceAlreadyCompleteException, InvalidJobParametersException, JobExecutionAlreadyRunningException, JobRestartException {

        // Parametre unique : sinon Spring Batch refuse de relancer un job deja
        // termine avec les memes parametres.
        var params = new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        JobExecution execution = jobOperator.start(secondJob, params);

        redirect.addFlashAttribute(
                "message",
                "secondJob → " + execution.getStatus() + " (exécution #" + execution.getId() + ")"
        );
        return "redirect:/";
    }

    @PostMapping("/jobs/tp2")
    public String tp2(@RequestParam(defaultValue = "") String message,
                      RedirectAttributes redirect) throws JobInstanceAlreadyCompleteException, InvalidJobParametersException, JobExecutionAlreadyRunningException, JobRestartException {

        // Parametre unique : sinon Spring Batch refuse de relancer un job deja
        // termine avec les memes parametres.
        var params = new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .addString("message", message)
                .toJobParameters();

        JobExecution execution = jobOperator.start(secondJob, params);
        redirect.addFlashAttribute(
                "message",
                "tp2Job → " + execution.getStatus() + " (exécution #" + execution.getId() + ")"
        );

        return "redirect:/";
    }

    @PostMapping("/jobs/tp3")
    public String tp3(@RequestParam(defaultValue = "1") String runId,
                      @RequestParam(defaultValue = "false") boolean fail,
                      RedirectAttributes redirect) throws JobInstanceAlreadyCompleteException, InvalidJobParametersException, JobExecutionAlreadyRunningException, JobRestartException {

        var params = new JobParametersBuilder()
                .addString("runId", runId)
                .addString("fail", String.valueOf(fail), false)
                .toJobParameters();

        JobExecution execution = jobOperator.start(tp3Job, params);

        redirect.addFlashAttribute(
                "message",
                "tp3Job (runId=" + runId + ") → " + execution.getStatus() + " (exécution #" + execution.getId() + ")"
        );
        return "redirect:/";
    }

    @PostMapping("/jobs/tp4")
    public String tp4(RedirectAttributes redirect) throws JobInstanceAlreadyCompleteException, InvalidJobParametersException, JobExecutionAlreadyRunningException, JobRestartException {

        // On vide la table VENTE avant l'import : chaque clic reflete donc le seul
        // contenu du CSV (sinon les lignes s'accumuleraient a chaque relance).
        venteRepository.deleteAll();

        var params = new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        JobExecution execution = jobOperator.start(tp4Job, params);

        redirect.addFlashAttribute(
                "message",
                "tp4Job → " + execution.getStatus() + " (exécution #" + execution.getId() + ")"
        );

        return "redirect:/";
    }

    @PostMapping("/jobs/tp5")
    public String tp5(RedirectAttributes redirect) throws Exception {

        // TP5 exporte le contenu actuel de la table VENTE vers ventes_tp5.csv.
        // (Pense a lancer le TP4 avant, pour avoir des donnees a exporter.)
        var params = new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        JobExecution execution = jobOperator.start(tp5Job, params);

        redirect.addFlashAttribute(
                "message",
                "tp5Job → " + execution.getStatus() + " (exécution #" + execution.getId()
                        + ") → ventes_tp5.csv"
        );
        return "redirect:/";
    }

    @PostMapping("/jobs/tp6")
    public String tp6(@RequestParam(defaultValue = Constants.VENTES_CSV) String fichierSource,
                      @RequestParam(defaultValue = "CSV") String format,
                      // ATTENTION : une case NON cochee n'envoie RIEN dans un POST HTML.
                      // Sans defaultValue, le parametre serait absent et la requete echouerait.
                      @RequestParam(defaultValue = "false") boolean totalTtc,
                      @RequestParam(defaultValue = "0") double montantMini,
                      RedirectAttributes redirect) throws Exception {

        // JobParameters n'accepte que String / Long / Double / Date & co.
        var params = new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .addString("fichierSource", fichierSource)
                .addString("format", format)
                .addDouble("montantMini", montantMini)
                .addString("totalTtc", String.valueOf(totalTtc))
                .toJobParameters();

        JobExecution execution = jobOperator.start(tp6Job, params);

        // Bilan pour l'IHM : les compteurs du step suffisent, pas besoin d'un step dedie.
        // filterCount = lignes ecartees par le processor (celles sous montantMini).
        StepExecution step = step(execution, "tp6Step");
        String bilan = step.getWriteCount() + " ligne(s) exportée(s) sur " + step.getReadCount()
                + " lue(s) (" + step.getFilterCount() + " filtrée(s)) → "
                + TP6_JobConfig.cheminRapportTp6(format);

        redirect.addFlashAttribute(
                "message",
                "tp6Job → " + execution.getStatus() + " (exécution #" + execution.getId() + ") — " + bilan
        );
        return "redirect:/";
    }

    @PostMapping("/jobs/tp7")
    public String tp7(@RequestParam(defaultValue = Constants.TP7_VENTES_5L_CSV) String fichierSource,
                      @RequestParam(defaultValue = "") String runId,
                      @RequestParam(defaultValue = "false") boolean risque,
                      RedirectAttributes redirect) throws Exception {

        // chaque clic repart d'une table VENTE vide, le contenu
        venteRepository.deleteAll();

        // runId vide -> on en genere un depuis la date : chaque clic est alors une
        // nouvelle JobInstance. Saisir un runId a la main permet de REJOUER la meme
        // instance
        String id = runId.isBlank()
                ? LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                : runId;

        var params = new JobParametersBuilder()
                .addString("runId", id)
                // Non-identifiants : on peut changer de fichier ou cocher le risque
                // entre deux tentatives sans changer d'instance.
                .addString("fichierSource", fichierSource, false)
                .addString("risque", String.valueOf(risque), false)
                .toJobParameters();

        JobExecution execution = jobOperator.start(tp7Job, params);

        StepExecution step = step(execution, "tp7Step");
        String bilan = "lues=" + step.getReadCount() + ", écrites=" + step.getWriteCount()
                + ", rejets lecture=" + step.getReadSkipCount()
                + ", rejets traitement=" + step.getProcessSkipCount()
                + ", rollbacks=" + step.getRollbackCount()
                + " → rejets dans " + Constants.TP7_REJETS_CSV;

        String texte = "tp7Job (runId=" + id + ") → " + execution.getStatus()
                + " (exécution #" + execution.getId() + ") — " + bilan;
        if (execution.getStatus() == BatchStatus.COMPLETED) {
            redirect.addFlashAttribute("message", texte);
        } else {
            var cause = execution.getAllFailureExceptions().get(0).getCause().getCause();

            redirect.addFlashAttribute("errorMessage", texte + "\n" + cause.getMessage());
        }
        return "redirect:/";
    }

    @PostMapping("/jobs/tp8")
    public String tp8(@RequestParam(defaultValue = "0") double caJour,
                      RedirectAttributes redirect) throws Exception {

        var params = new JobParametersBuilder()
                .addDouble("caJour", caJour)
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        JobExecution execution = jobOperator.start(tp8Job, params);

        // Le job REUSSIT toujours (BatchStatus COMPLETED) : c'est l'exitCode (metier)
        // qui change selon le CA. Il apparait dans la colonne "exit" du panneau.
        redirect.addFlashAttribute(
                "message",
                """
                        tp8Job → statut=%s
                        exitsSatus -> %s
                        (CA=%s €, objectif=%s €)"
                        """.formatted(execution.getStatus(), execution.getExitStatus(), caJour, Constants.OBJECTIF_CA)
        );
        return "redirect:/";
    }

    @PostMapping("/jobs/tp9")
    public String tp9(@RequestParam(defaultValue = "1") int exercice,
                      @RequestParam(defaultValue = "") String scenario,
                      @RequestParam(defaultValue = "0") double montant,
                      @RequestParam(defaultValue = "false") boolean echouer,
                      RedirectAttributes redirect) throws Exception {

        // Un seul controleur pour les 10 exercices : on recupere le job par son nom.
        Job job = applicationContext.getBean("tp9ex" + exercice + "Job", Job.class);

        var params = new JobParametersBuilder()
                .addString("scenario", scenario, false)
                .addDouble("montant", montant, false)
                .addString("echouer", String.valueOf(echouer), false)
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        JobExecution execution = jobOperator.start(job, params);

        // Le panneau de droite montre les steps reellement executes (le chemin pris).
        String chemin = execution.getStepExecutions().stream()
                .sorted(Comparator.comparing(StepExecution::getId))
                .map(StepExecution::getStepName)
                .collect(Collectors.joining(" → "));
        String texte = "tp9ex" + exercice + "Job → " + execution.getStatus()
                + " (exitCode=" + execution.getExitStatus().getExitCode() + ") — chemin : " + chemin;

        if (execution.getStatus() == BatchStatus.COMPLETED) {
            redirect.addFlashAttribute("message", texte);
        } else {
            redirect.addFlashAttribute("errorMessage", texte);
        }
        return "redirect:/";
    }

    @PostMapping("/jobs/tp10")
    public String tp10(RedirectAttributes redirect) throws Exception {

        // On vide la table VENTE avant l'import (comme au TP4) : chaque clic reflete
        // le seul contenu du CSV. On remet aussi le tracker a zero pour que le bilan
        // affiche les numeros de CE lancement uniquement.
        venteRepository.deleteAll();
        tp10Tracker.reset();

        var params = new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        JobExecution execution = jobOperator.start(tp10Job, params);

        StepExecution step = step(execution, "tp10Step");
        var start = execution.getStartTime();
        var end = execution.getEndTime();
        long duree = (start != null && end != null) ? Duration.between(start, end).toMillis() : 0;

        // LE point du TP : le compteur PARTAGE du processor doit rester correct en parallele.
        // distincts < total => des numeros ont ete dupliques (course sur l'etat partage).
        int total = tp10Tracker.totalNumeros();
        int distincts = tp10Tracker.numerosDistincts();
        int threads = tp10Tracker.threads().size();
        String etatCompteur = (distincts == total)
                ? distincts + "/" + total + " uniques ✅"
                : distincts + "/" + total + " uniques ⚠️ DOUBLONS, Le compteur n'est pas unique par vente !";

        // Le produit portant le PLUS GRAND numero : doit finir par "_500" si le compteur
        // est correct, un numero plus petit s'il a ete corrompu par la course.
        String dernierLibelle = venteRepository.findAll().stream()
                .max(Comparator.comparingInt(v -> numeroDuLibelle(v.getLibelleProduit())))
                .map(VenteEntity::getLibelleProduit)
                .orElse("-");

        String bilan = "lues=" + step.getReadCount() + ", écrites=" + step.getWriteCount()
                + ", numéros=" + etatCompteur + ", threads=" + threads + " en " + duree + " ms"
                + "\nLibelle le plus élevé=" + dernierLibelle;

        String texte = "tp10Job → " + execution.getStatus()
                + " (exécution #" + execution.getId() + ") — " + bilan;
        if (execution.getStatus() == BatchStatus.COMPLETED) {
            redirect.addFlashAttribute("message", texte);
        } else {
            redirect.addFlashAttribute("errorMessage", texte);
        }
        return "redirect:/";
    }

    @PostMapping("/jobs/tp11")
    public String tp11(@RequestParam(defaultValue = "") String runId,
                       @RequestParam(defaultValue = "false") boolean casserB10,
                       RedirectAttributes redirect) throws Exception {

        // runId vide -> genere depuis la date : chaque clic = nouvelle JobInstance.
        // Ressaisir le MEME runId = restart de la meme instance (cf. TP3) : c'est ce
        // qui permet l'exercice de reprise (casser B10 puis relancer sans casser).
        String id = runId.isBlank()
                ? LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                : runId;

        // Restart ? = une instance existe deja pour ce runId (parametre IDENTIFIANT).
        // On ne vide la table QUE pour un nouveau run : au restart, les 9 partitions
        // deja COMPLETED ne rejouent pas -> leurs lignes doivent rester en base
        // (sinon on perdrait 450 ventes et le total final ne ferait plus 500).
        boolean estRestart = jobRepository.getJobInstance(
                "tp11Job",
                new JobParametersBuilder().addString("runId", id).toJobParameters()
        ) != null;
        if (!estRestart) {
            venteRepository.deleteAll();
        }
        tp11Tracker.reset();

        var params = new JobParametersBuilder()
                .addString("runId", id)                                   // IDENTIFIANT
                .addString("casserB10", String.valueOf(casserB10), false) // NON identifiant
                .toJobParameters();

        JobExecution execution = jobOperator.start(tp11Job, params);

        var start = execution.getStartTime();
        var end = execution.getEndTime();
        long duree = (start != null && end != null) ? Duration.between(start, end).toMillis() : 0;

        // Steps esclaves rejoues dans CETTE execution (nommes "tp11WorkerStep:partitionN").
        // Au restart, seule la partition en echec rejoue -> on en voit 1 (et non 10).
        long partitions = execution.getStepExecutions().stream()
                .filter(s -> s.getStepName().startsWith("tp11WorkerStep"))
                .count();
        int threads = tp11Tracker.threads().size();

        String bilan = partitions + " partition(s) exécutée(s), " + venteRepository.count()
                + " ventes en base, threads=" + threads + ", en " + duree + " ms";

        String texte = "tp11Job (runId=" + id + ") → " + execution.getStatus()
                + " (exécution #" + execution.getId() + ") — " + bilan;
        if (execution.getStatus() == BatchStatus.COMPLETED) {
            redirect.addFlashAttribute("message", texte);
        } else {
            redirect.addFlashAttribute("errorMessage", texte);
        }
        return "redirect:/";
    }

    /* -------------------------------- */
    // Pour l'UI
    /* -------------------------------- */

    @PostMapping("/jobs/tp12")
    public String tp12(RedirectAttributes redirect) throws Exception {

        var params = new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        JobExecution execution = jobOperator.start(tp12Job, params);

        // On relit le fichier fusionne pour afficher le resultat (hors en-tete).
        Path fichier = Path.of(Constants.TP12_FUSION_OUTPUT);
        List<String> lignes = Files.exists(fichier)
                ? Files.readAllLines(fichier).stream().skip(1).toList()
                : List.of();
        String bilan = lignes.size() + " lignes fusionnees (sans doublon) → " + Constants.TP12_FUSION_OUTPUT;

        String texte = "tp12Job → " + execution.getStatus()
                + " (exécution #" + execution.getId() + ") — " + bilan;
        if (execution.getStatus() == BatchStatus.COMPLETED) {
            redirect.addFlashAttribute("message", texte);
        } else {
            redirect.addFlashAttribute("errorMessage", texte);
        }
        return "redirect:/";
    }

    @PostMapping("/jobs/tpfinal")
    public String tpFinal(@RequestParam(defaultValue = Constants.TPFINAL_COMMANDES_CSV) String fichierSource,
                          RedirectAttributes redirect) throws Exception {

        var params = new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .addString("fichierSource", fichierSource, false)
                .toJobParameters();

        JobExecution execution = jobOperator.start(tpFinalJob, params);

        // Le chemin reellement emprunte : c'est lui qui montre ou le flow s'est arrete.
        String chemin = execution.getStepExecutions().stream()
                .sorted(Comparator.comparing(StepExecution::getId))
                .map(StepExecution::getStepName)
                .collect(Collectors.joining(" → "));

        // importStep n'existe pas si un des deux controles a echoue avant.
        String bilan = execution.getStepExecutions().stream()
                .filter(s -> s.getStepName().equals("importStep"))
                .findFirst()
                .map(s -> s.getWriteCount() + " commande(s) importée(s), "
                        + s.getSkipCount() + " rejetée(s) → " + TPFinal_JobConfig.fichierRejets())
                .orElse("import non atteint");

        String texte = "tpFinalJob → " + execution.getStatus() + " (exécution #" + execution.getId()
                + ") — " + bilan + "\nchemin : " + chemin;

        if (execution.getStatus() == BatchStatus.COMPLETED) {
            redirect.addFlashAttribute("message", texte + "\nsorties : "
                    + Constants.TPFINAL_CAMIONS_TXT + ", " + Constants.TPFINAL_CHAUFFEURS_DIR
                    + "/*.txt, " + TPFinal_JobConfig.fichierArchive());
        } else {
            redirect.addFlashAttribute("errorMessage", texte);
        }
        return "redirect:/";
    }

    @ExceptionHandler(Exception.class)
    public String onJobError(Exception e, RedirectAttributes redirect) {
        e.printStackTrace();
        if (e instanceof JobExecutionException) {
            redirect.addFlashAttribute("errorMessage", "Impossible de lancer le job : " + e.getMessage());
        } else {
            redirect.addFlashAttribute("errorMessage", "Une erreur est survenue : " + e.getMessage());
        }
        return "redirect:/";
    }

    /**
     * Le step de l'execution portant ce nom (les compteurs affiches par l'IHM).
     */
    private StepExecution step(JobExecution execution, String nom) {
        return execution.getStepExecutions().stream()
                .filter(s -> s.getStepName().equals(nom))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Aucun step nomme " + nom));
    }

    /**
     * Les dernieres executions, tous jobs confondus, de la plus recente a la plus ancienne.
     */
    private List<ExecView> recentExecutions(int limit) {
        return jobRepository.getJobNames().stream()
                .flatMap(name -> jobRepository.getJobInstances(name, 0, 50).stream())
                .flatMap(instance -> jobRepository.getJobExecutions(instance).stream())
                .sorted(Comparator.comparing(JobExecution::getId).reversed())
                .limit(limit)
                .map(e -> {
                    var start = e.getStartTime();
                    var end = e.getEndTime();
                    String description = e.getExitStatus().getExitDescription();
                    return new ExecView(
                            e.getJobInstance().getJobName(),
                            e.getId(),
                            e.getStatus().name(),
                            e.getStatus() == BatchStatus.COMPLETED,
                            e.getStatus() == BatchStatus.FAILED,
                            start != null ? start.format(Constants.TIME_FORMAT) : "—",
                            (start != null && end != null)
                                    ? Duration.between(start, end).toMillis() + " ms"
                                    : "—",
                            e.getExitStatus().getExitCode(),
                            StreamSupport.stream(e.getJobParameters().spliterator(), false)
                                    .map(p -> p.name() + "=" + p.value())
                                    .collect(Collectors.joining(", ")),
                            description.substring(0, Math.min(400, description.length())),
                            e.getStepExecutions().stream()
                                    .map(s -> new StepView(
                                            s.getStepName(),
                                            s.getStatus().name(),
                                            s.getReadCount(),
                                            s.getWriteCount(),
                                            s.getCommitCount(),
                                            s.getRollbackCount(),
                                            s.getSkipCount()
                                    ))
                                    .toList()
                    );
                })
                .toList();
    }
}

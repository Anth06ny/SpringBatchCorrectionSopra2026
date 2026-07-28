package org.example.springbatchexercicejava.batch.config;

import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.annotation.AfterStep;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import static org.example.springbatchexercicejava.batch.Constants.OBJECTIF_CA;

@Configuration
public class TP8_JobConfig {

    @Bean
    public Step tp8Step(JobRepository jobRepository, PlatformTransactionManager transactionManager) {

        // Le step ne "fait" presque rien : il lit juste le CA pour la console
        // (lecture d'un parametre depuis un Tasklet, comme au TP2).
        Tasklet bilanTask = (contribution, chunkContext) -> {
            Object brut = chunkContext.getStepContext().getJobParameters().get("caJour");
            double caJour = (brut instanceof Double d) ? d : 0.0;
            System.out.println("TP8 : CA du jour = " + caJour + " EUR (objectif = "
                    + OBJECTIF_CA + " EUR)");
            return RepeatStatus.FINISHED;
        };

        return new StepBuilder("tp8Step", jobRepository)
                .tasklet(bilanTask, transactionManager)
                .listener(new ControleListener())
                .build();
    }

    @Bean
    public Job tp8Job(JobRepository jobRepository, Step tp8Step) {
        return new JobBuilder("tp8Job", jobRepository)
                .start(tp8Step)
                .build();
    }

    public static class ControleListener implements StepExecutionListener {


        @Override
        // Appelée après le step, même si celui-ci a échoué
        public ExitStatus afterStep(StepExecution stepExecution) {

            Double caJour = stepExecution.getJobExecution().getJobParameters().getDouble("caJour");
            double valeur = caJour != null ? caJour : 0.0;

            // Le step s'est bien déroulé, mais combien de lignes a-t-il rejetées ?
            long rejets = stepExecution.getSkipCount();

            if (valeur >= OBJECTIF_CA) {
                return new ExitStatus("OBJECTIF_ATTEINT");
            } else if (valeur >= OBJECTIF_CA / 2) {
                return new ExitStatus("A_SURVEILLER");
            } else {
                return new ExitStatus("ALERTE");
            }
        }
    }


}

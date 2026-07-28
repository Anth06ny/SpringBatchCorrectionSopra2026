package org.example.springbatchexercicejava.batch;

import org.example.springbatchexercicejava.batch.config.TP7_JobConfig;
import org.example.springbatchexercicejava.batch.model.VenteEntity;
import org.springframework.batch.core.listener.SkipListener;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.file.FlatFileParseException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.example.springbatchexercicejava.batch.Constants.TP7_REJETS_CSV;

public class MyRejetsListener implements SkipListener<TP7_JobConfig.TP7VenteDTO, VenteEntity>, StepExecutionListener {

    private static final Path FICHIER = Path.of(TP7_REJETS_CSV);

    @Override
    public void beforeStep(StepExecution stepExecution) {
        ecrire("phase;donnee;cause\n", StandardOpenOption.TRUNCATE_EXISTING);
    }

    @Override
    public void onSkipInRead(Throwable t) {
        if (t instanceof FlatFileParseException e) {
            ecrire("LECTURE;" + e.getInput() + ";ligne " + e.getLineNumber() + " illisible\n",
                    StandardOpenOption.APPEND);
        } else {
            ecrire("LECTURE;?;" + t.getMessage() + " " + t.getClass().getSimpleName() + "\n",
                    StandardOpenOption.APPEND);
        }
    }

    @Override
    public void onSkipInProcess(TP7_JobConfig.TP7VenteDTO item, Throwable t) {
            ecrire("TRAITEMENT;" + item + ";" + t.getMessage() + "\n", StandardOpenOption.APPEND);
    }

    @Override
    public void onSkipInWrite(VenteEntity item, Throwable t) {
        ecrire("ECRITURE;" + item + ";" + t.getMessage() + "\n", StandardOpenOption.APPEND);
    }

    private void ecrire(String ligne, OpenOption option) {
        try {
            Files.writeString(FICHIER, ligne, StandardOpenOption.CREATE, option);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

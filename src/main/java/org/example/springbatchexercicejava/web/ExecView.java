package org.example.springbatchexercicejava.web;

import java.util.List;

/** Vue simplifiee d'une execution de job pour l'affichage (evite d'exposer les blobs). */
public record ExecView(
        String jobName,
        Long id,
        String status,
        boolean ok,
        boolean failed,
        String start,
        String duration,
        String exitCode,
        String params,
        String exitMessage,
        List<StepView> steps
) {
}

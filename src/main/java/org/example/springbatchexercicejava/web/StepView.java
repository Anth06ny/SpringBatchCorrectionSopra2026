package org.example.springbatchexercicejava.web;

public record StepView(
        String name,
        String status,
        long read,
        long write,
        long commit,
        long rollback,
        // skips = lecture + traitement + ecriture (TP7)
        long skip
) {
}

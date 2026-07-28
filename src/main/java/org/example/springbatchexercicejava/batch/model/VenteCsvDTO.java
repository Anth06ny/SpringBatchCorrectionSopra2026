package org.example.springbatchexercicejava.batch.model;

import java.time.LocalDate;

/**
 * TP4 — DTO du CSV, a completer : un champ (+ getter/setter) par colonne de
 * data/ventes.csv. Le FlatFileItemReader alimente les proprietes via leurs SETTERS,
 * il lui faut donc un constructeur sans argument et des accesseurs publics.
 */
public class VenteCsvDTO {

    private String date;
    private String idBoutique;
    private String produit;
    private double montantHt;

    public VenteCsvDTO() {
    }

    public VenteCsvDTO(String date, String idBoutique, String produit, double montantHt) {
        this.date = date;
        this.idBoutique = idBoutique;
        this.produit = produit;
        this.montantHt = montantHt;
    }

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

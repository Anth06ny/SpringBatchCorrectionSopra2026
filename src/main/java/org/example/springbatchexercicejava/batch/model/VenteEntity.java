package org.example.springbatchexercicejava.batch.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "VENTE")
public class VenteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDate dateVente;

    private String boutique = "";

    private String libelleProduit = "";

    private double prixHt = 0.0;

    private double prixTtc = 0.0;

    /** Constructeur sans argument : exige par JPA/Hibernate. */
    public VenteEntity() {
    }

    public VenteEntity(LocalDate dateVente, String boutique, String libelleProduit,
                       double prixHt, double prixTtc) {
        this.dateVente = dateVente;
        this.boutique = boutique;
        this.libelleProduit = libelleProduit;
        this.prixHt = prixHt;
        this.prixTtc = prixTtc;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDate getDateVente() {
        return dateVente;
    }

    public void setDateVente(LocalDate dateVente) {
        this.dateVente = dateVente;
    }

    public String getBoutique() {
        return boutique;
    }

    public void setBoutique(String boutique) {
        this.boutique = boutique;
    }

    public String getLibelleProduit() {
        return libelleProduit;
    }

    public void setLibelleProduit(String libelleProduit) {
        this.libelleProduit = libelleProduit;
    }

    public double getPrixHt() {
        return prixHt;
    }

    public void setPrixHt(double prixHt) {
        this.prixHt = prixHt;
    }

    public double getPrixTtc() {
        return prixTtc;
    }

    public void setPrixTtc(double prixTtc) {
        this.prixTtc = prixTtc;
    }
}

package org.example.springbatchexercicejava.batch.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Une ligne de commandes.csv (ville;magasin;nb) mise en base par le TP final. */
@Entity
@Table(name = "COMMANDE")
public class CommandeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String ville = "";

    private String magasin = "";

    /** Nombre de bouteilles commandees (majore par la meteo au meteoWorkerStep). */
    private int nb = 0;

    /** Constructeur sans argument : exige par JPA/Hibernate. */
    public CommandeEntity() {
    }

    public CommandeEntity(String ville, String magasin, int nb) {
        this.ville = ville;
        this.magasin = magasin;
        this.nb = nb;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getVille() {
        return ville;
    }

    public void setVille(String ville) {
        this.ville = ville;
    }

    public String getMagasin() {
        return magasin;
    }

    public void setMagasin(String magasin) {
        this.magasin = magasin;
    }

    public int getNb() {
        return nb;
    }

    public void setNb(int nb) {
        this.nb = nb;
    }

    @Override
    public String toString() {
        return "CommandeEntity{ville='" + ville + "', magasin='" + magasin + "', nb=" + nb + '}';
    }
}

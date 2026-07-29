package org.example.springbatchexercicejava.batch.repository;

import org.example.springbatchexercicejava.batch.model.CommandeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CommandeRepository extends JpaRepository<CommandeEntity, Long> {

    /** Les villes presentes en base : une partition (= un appel meteo) par ville. */
    @Query("SELECT DISTINCT c.ville FROM CommandeEntity c ORDER BY c.ville")
    List<String> villesDistinctes();

    /** Total de bouteilles par ville -> /entrepot/camions.txt */
    @Query("""
            SELECT c.ville AS ville, SUM(c.nb) AS total
            FROM CommandeEntity c
            GROUP BY c.ville
            ORDER BY c.ville
            """)
    List<TotalVille> totauxParVille();

    /** Total de bouteilles par magasin -> /chauffeurs/<Ville>.txt */
    @Query("""
            SELECT c.ville AS ville, c.magasin AS magasin, SUM(c.nb) AS total
            FROM CommandeEntity c
            GROUP BY c.ville, c.magasin
            ORDER BY c.ville, c.magasin
            """)
    List<TotalMagasin> totauxParMagasin();

    /** Projection Spring Data : les alias de la requete donnent les getters. */
    interface TotalVille {
        String getVille();

        long getTotal();
    }

    interface TotalMagasin {
        String getVille();

        String getMagasin();

        long getTotal();
    }
}

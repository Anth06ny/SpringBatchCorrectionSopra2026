package org.example.springbatchexercicejava.batch.repository;

import org.example.springbatchexercicejava.batch.model.VenteEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface VenteRepository extends JpaRepository<VenteEntity, Long> {

    @Query("SELECT SUM(v.prixTtc) FROM VenteEntity v")
    public Double sumPrixTtc();
}

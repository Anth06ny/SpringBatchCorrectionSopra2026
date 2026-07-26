package org.example.springbatchexercicejava.batch.repository;

import org.example.springbatchexercicejava.batch.model.VenteEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VenteRepository extends JpaRepository<VenteEntity, Long> {
}

package com.makerpixel.contabil.repository;

import com.makerpixel.contabil.domain.EmDiaModeloTarefa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmDiaModeloTarefaRepository extends JpaRepository<EmDiaModeloTarefa, UUID> {
    List<EmDiaModeloTarefa> findByEscritorioIdAndAtivoTrueOrderByDiaVencimentoAsc(UUID escritorioId);
    List<EmDiaModeloTarefa> findByEscritorioIdOrderByDiaVencimentoAsc(UUID escritorioId);
    Optional<EmDiaModeloTarefa> findByIdAndEscritorioId(UUID id, UUID escritorioId);
}

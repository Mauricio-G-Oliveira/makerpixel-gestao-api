package com.makerpixel.contabil.repository;

import com.makerpixel.contabil.domain.ReformaLote;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReformaLoteRepository extends JpaRepository<ReformaLote, UUID> {

    List<ReformaLote> findByEscritorioIdOrderByCriadoEmDesc(UUID escritorioId);

    Page<ReformaLote> findByEscritorioIdOrderByCriadoEmDesc(UUID escritorioId, Pageable pageable);

    Optional<ReformaLote> findByIdAndEscritorioId(UUID id, UUID escritorioId);

    Optional<ReformaLote> findFirstByEscritorioIdAndEmpresaIdAndCompetenciaAndOrigemOrderByCriadoEmDesc(
            UUID escritorioId, UUID empresaId, String competencia, String origem);

    List<ReformaLote> findByEscritorioIdAndCompetenciaAndOrigem(UUID escritorioId, String competencia, String origem);

    @org.springframework.data.jpa.repository.Query("SELECT COALESCE(SUM(l.totalNotas), 0) FROM ReformaLote l WHERE l.escritorioId = :escritorioId AND l.competencia = :competencia")
    long sumTotalNotasPorCompetencia(
            @org.springframework.data.repository.query.Param("escritorioId") UUID escritorioId,
            @org.springframework.data.repository.query.Param("competencia") String competencia
    );
}

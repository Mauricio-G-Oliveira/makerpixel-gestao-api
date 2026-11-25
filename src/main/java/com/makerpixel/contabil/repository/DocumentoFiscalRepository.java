package com.makerpixel.contabil.repository;

import com.makerpixel.contabil.domain.DocumentoFiscal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentoFiscalRepository extends JpaRepository<DocumentoFiscal, UUID> {

    Optional<DocumentoFiscal> findByEscritorioIdAndChaveAcesso(UUID escritorioId, String chaveAcesso);

    List<DocumentoFiscal> findByEscritorioIdAndEmpresaIdAndCompetencia(UUID escritorioId, UUID empresaId, String competencia);

    Page<DocumentoFiscal> findByEscritorioIdAndEmpresaId(UUID escritorioId, UUID empresaId, Pageable pageable);

    @Query("SELECT d FROM DocumentoFiscal d WHERE d.escritorioId = :escritorioId AND d.recebidoEm >= :desde")
    List<DocumentoFiscal> findNovasDesde(
            @Param("escritorioId") UUID escritorioId,
            @Param("desde") OffsetDateTime desde
    );

    long countByEscritorioIdAndCompetencia(UUID escritorioId, String competencia);

    long countByEscritorioIdAndEmpresaIdAndCompetencia(UUID escritorioId, UUID empresaId, String competencia);
}

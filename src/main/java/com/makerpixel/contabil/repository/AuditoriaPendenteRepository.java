package com.makerpixel.contabil.repository;

import com.makerpixel.contabil.domain.AuditoriaPendente;
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
public interface AuditoriaPendenteRepository extends JpaRepository<AuditoriaPendente, UUID> {

    Optional<AuditoriaPendente> findByEscritorioIdAndDocumentoId(UUID escritorioId, UUID documentoId);

    @Query("SELECT p FROM AuditoriaPendente p JOIN FETCH p.documento WHERE p.status = 'PENDENTE' AND (p.proximaTentativa IS NULL OR p.proximaTentativa <= :agora) ORDER BY p.criadoEm ASC")
    List<AuditoriaPendente> findPendentesParaProcessar(@Param("agora") OffsetDateTime agora, Pageable pageable);

    @Query("SELECT p FROM AuditoriaPendente p JOIN FETCH p.documento WHERE p.escritorioId = :escritorioId AND p.status = 'PENDENTE' AND (p.proximaTentativa IS NULL OR p.proximaTentativa <= :agora) ORDER BY p.criadoEm ASC")
    List<AuditoriaPendente> findPendentesPorEscritorio(@Param("escritorioId") UUID escritorioId, @Param("agora") OffsetDateTime agora, Pageable pageable);

    long countByEscritorioIdAndStatus(UUID escritorioId, String status);

    @Query("SELECT COUNT(p) FROM AuditoriaPendente p JOIN p.documento d WHERE p.escritorioId = :escritorioId AND d.empresaId = :empresaId AND d.competencia = :competencia AND p.status = 'PENDENTE'")
    long countPendentesPorEmpresaECompetencia(
            @Param("escritorioId") UUID escritorioId,
            @Param("empresaId") UUID empresaId,
            @Param("competencia") String competencia
    );

    @org.springframework.data.jpa.repository.Modifying
    @Query(value = "UPDATE auditoria_pendente SET status = 'PENDENTE', atualizado_em = NOW() WHERE status = 'PROCESSANDO' AND atualizado_em < :limiteTempo", nativeQuery = true)
    int resetarPendenciasTravadas(@Param("limiteTempo") OffsetDateTime limiteTempo);

    @Query(value = "SELECT * FROM auditoria_pendente WHERE status = 'PENDENTE' AND (proxima_tentativa IS NULL OR proxima_tentativa <= :agora) ORDER BY proxima_tentativa ASC NULLS FIRST LIMIT :limite FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<AuditoriaPendente> findPendentesParaProcessarComLock(@Param("agora") OffsetDateTime agora, @Param("limite") int limite);

    @Query(value = "SELECT * FROM auditoria_pendente WHERE escritorio_id = :escritorioId AND status = 'PENDENTE' AND (proxima_tentativa IS NULL OR proxima_tentativa <= :agora) ORDER BY proxima_tentativa ASC NULLS FIRST LIMIT :limite FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<AuditoriaPendente> findPendentesPorEscritorioComLock(@Param("escritorioId") UUID escritorioId, @Param("agora") OffsetDateTime agora, @Param("limite") int limite);
}

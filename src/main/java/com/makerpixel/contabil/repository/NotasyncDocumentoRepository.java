package com.makerpixel.contabil.repository;

import com.makerpixel.contabil.domain.NotasyncDocumento;
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
public interface NotasyncDocumentoRepository extends JpaRepository<NotasyncDocumento, UUID> {

    Optional<NotasyncDocumento> findByEscritorioIdAndChaveAcesso(UUID escritorioId, String chaveAcesso);

    List<NotasyncDocumento> findByEscritorioIdAndCnpj(UUID escritorioId, String cnpj);

    @Query("SELECT d FROM NotasyncDocumento d WHERE d.escritorioId = :escritorioId AND d.cnpj = :cnpj AND d.dataEmissao >= :inicio AND d.dataEmissao < :fim ORDER BY d.dataEmissao ASC")
    List<NotasyncDocumento> findByEscritorioAndCnpjAndPeriodo(
            @Param("escritorioId") UUID escritorioId,
            @Param("cnpj") String cnpj,
            @Param("inicio") OffsetDateTime inicio,
            @Param("fim") OffsetDateTime fim
    );

    Page<NotasyncDocumento> findByEscritorioId(UUID escritorioId, Pageable pageable);

    Page<NotasyncDocumento> findByEscritorioIdAndCnpj(UUID escritorioId, String cnpj, Pageable pageable);

    long countByEscritorioId(UUID escritorioId);

    long countByEscritorioIdAndCnpj(UUID escritorioId, String cnpj);
}

package com.makerpixel.contabil.repository;

import com.makerpixel.contabil.domain.NfceDocumento;
import com.makerpixel.contabil.domain.SituacaoNfce;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NfceDocumentoRepository extends JpaRepository<NfceDocumento, UUID> {

    Optional<NfceDocumento> findByEscritorioIdAndChave(UUID escritorioId, String chave);

    List<NfceDocumento> findByEscritorioId(UUID escritorioId);

    Page<NfceDocumento> findByEscritorioId(UUID escritorioId, Pageable pageable);

    Page<NfceDocumento> findByEscritorioIdAndCnpjEmitente(UUID escritorioId, String cnpjEmitente, Pageable pageable);

    Page<NfceDocumento> findByEscritorioIdAndSituacao(UUID escritorioId, SituacaoNfce situacao, Pageable pageable);

    Page<NfceDocumento> findByEscritorioIdAndCnpjEmitenteAndSituacao(UUID escritorioId, String cnpjEmitente, SituacaoNfce situacao, Pageable pageable);

    long countByEscritorioId(UUID escritorioId);

    long countByEscritorioIdAndSituacao(UUID escritorioId, SituacaoNfce situacao);

    @Query("SELECT COALESCE(SUM(n.valorTotal), 0) FROM NfceDocumento n WHERE n.escritorioId = :escritorioId")
    BigDecimal sumValorTotalByEscritorioId(@Param("escritorioId") UUID escritorioId);

    @Query("SELECT COALESCE(MAX(n.nsu), 0) FROM NfceDocumento n WHERE n.escritorioId = :escritorioId AND n.cnpjEmitente = :cnpj")
    Long findUltimoNsuByEscritorioIdAndCnpj(@Param("escritorioId") UUID escritorioId, @Param("cnpj") String cnpj);
}

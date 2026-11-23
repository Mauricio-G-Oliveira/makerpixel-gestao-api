package com.makerpixel.contabil.repository;

import com.makerpixel.contabil.domain.ReformaItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReformaItemRepository extends JpaRepository<ReformaItem, UUID> {

    List<ReformaItem> findByLoteIdAndEscritorioIdOrderByNItemAsc(UUID loteId, UUID escritorioId);

    Page<ReformaItem> findByLoteIdAndEscritorioId(UUID loteId, UUID escritorioId, Pageable pageable);

    Page<ReformaItem> findByLoteIdAndEscritorioIdAndClassificacao(UUID loteId, UUID escritorioId, String classificacao, Pageable pageable);

    Optional<ReformaItem> findByIdAndEscritorioId(UUID id, UUID escritorioId);
}

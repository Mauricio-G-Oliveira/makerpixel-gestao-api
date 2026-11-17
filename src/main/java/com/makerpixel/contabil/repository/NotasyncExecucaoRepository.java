package com.makerpixel.contabil.repository;

import com.makerpixel.contabil.domain.NotasyncExecucao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NotasyncExecucaoRepository extends JpaRepository<NotasyncExecucao, UUID> {

    List<NotasyncExecucao> findTop20ByEscritorioIdOrderByDataHoraDesc(UUID escritorioId);

    Page<NotasyncExecucao> findByEscritorioId(UUID escritorioId, Pageable pageable);

    List<NotasyncExecucao> findByEscritorioIdAndCnpjOrderByDataHoraDesc(UUID escritorioId, String cnpj);

    long countByEscritorioIdAndCstat(UUID escritorioId, Integer cstat);
}

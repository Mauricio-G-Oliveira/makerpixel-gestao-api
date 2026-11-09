package com.makerpixel.contabil.repository;

import com.makerpixel.contabil.domain.ConciliacaoBancaria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConciliacaoBancariaRepository extends JpaRepository<ConciliacaoBancaria, UUID> {

    List<ConciliacaoBancaria> findByEscritorioIdAndCnpjAndCompetencia(UUID escritorioId, String cnpj, String competencia);

    List<ConciliacaoBancaria> findByEscritorioIdAndCompetencia(UUID escritorioId, String competencia);

    Optional<ConciliacaoBancaria> findByEscritorioIdAndCnpjAndBancoAndAgenciaAndContaAndCompetencia(
            UUID escritorioId, String cnpj, String banco, String agencia, String conta, String competencia
    );
}

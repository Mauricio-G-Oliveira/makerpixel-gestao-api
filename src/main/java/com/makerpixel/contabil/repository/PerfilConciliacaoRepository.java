package com.makerpixel.contabil.repository;

import com.makerpixel.contabil.domain.PerfilConciliacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PerfilConciliacaoRepository extends JpaRepository<PerfilConciliacao, UUID> {

    Optional<PerfilConciliacao> findByEscritorioIdAndCnpj(UUID escritorioId, String cnpj);
}

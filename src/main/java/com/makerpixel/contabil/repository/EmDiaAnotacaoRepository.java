package com.makerpixel.contabil.repository;

import com.makerpixel.contabil.domain.EmDiaAnotacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EmDiaAnotacaoRepository extends JpaRepository<EmDiaAnotacao, UUID> {
    List<EmDiaAnotacao> findByEscritorioIdAndEmpresaIdOrderByCriadoEmDesc(UUID escritorioId, UUID empresaId);
}

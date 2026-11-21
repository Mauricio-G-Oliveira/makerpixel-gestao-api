package com.makerpixel.contabil.repository;

import com.makerpixel.contabil.domain.NotasyncEmpresa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotasyncEmpresaRepository extends JpaRepository<NotasyncEmpresa, UUID> {

    List<NotasyncEmpresa> findByEscritorioIdOrderByRazaoSocialAsc(UUID escritorioId);

    List<NotasyncEmpresa> findByEscritorioIdAndAtivoTrue(UUID escritorioId);

    Optional<NotasyncEmpresa> findByEscritorioIdAndCnpj(UUID escritorioId, String cnpj);

    long countByEscritorioIdAndAtivoTrue(UUID escritorioId);

    long countByEscritorioId(UUID escritorioId);
}

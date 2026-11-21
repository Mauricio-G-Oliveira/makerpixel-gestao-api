package com.makerpixel.contabil.repository;

import com.makerpixel.contabil.domain.NotasyncEstado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotasyncEstadoRepository extends JpaRepository<NotasyncEstado, UUID> {

    Optional<NotasyncEstado> findByEscritorioIdAndCnpjAndFonte(UUID escritorioId, String cnpj, String fonte);

    List<NotasyncEstado> findByEscritorioId(UUID escritorioId);
}

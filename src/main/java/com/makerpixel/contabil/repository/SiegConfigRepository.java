package com.makerpixel.contabil.repository;

import com.makerpixel.contabil.domain.SiegConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SiegConfigRepository extends JpaRepository<SiegConfig, UUID> {

    Optional<SiegConfig> findByEscritorioId(UUID escritorioId);

    List<SiegConfig> findAllByAtivoTrue();
}

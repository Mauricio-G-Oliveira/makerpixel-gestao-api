package com.makerpixel.contabil.repository;

import com.makerpixel.contabil.domain.AuditoriaCert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AuditoriaCertRepository extends JpaRepository<AuditoriaCert, UUID> {

    List<AuditoriaCert> findByEscritorioIdOrderByExecutadoEmDesc(UUID escritorioId);
}

package com.makerpixel.contabil.repository;

import com.makerpixel.contabil.domain.CertificadoCofre;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CertificadoCofreRepository extends JpaRepository<CertificadoCofre, UUID> {

    Optional<CertificadoCofre> findByEscritorioIdAndAtivoTrue(UUID escritorioId);

    Optional<CertificadoCofre> findByEscritorioIdAndEmpresaIdIsNullAndAtivoTrue(UUID escritorioId);

    Optional<CertificadoCofre> findByEscritorioIdAndEmpresaIdAndAtivoTrue(UUID escritorioId, UUID empresaId);

    List<CertificadoCofre> findByEscritorioId(UUID escritorioId);

    List<CertificadoCofre> findAllByAtivoTrue();
}

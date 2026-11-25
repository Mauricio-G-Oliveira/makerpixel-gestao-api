package com.makerpixel.contabil.repository;

import com.makerpixel.contabil.domain.EmDiaTarefa;
import com.makerpixel.contabil.domain.StatusTarefa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmDiaTarefaRepository extends JpaRepository<EmDiaTarefa, UUID> {

    List<EmDiaTarefa> findByEscritorioIdAndMesCompetenciaOrderByPrazoAsc(UUID escritorioId, String mesCompetencia);

    List<EmDiaTarefa> findByEscritorioIdAndEmpresaIdAndMesCompetenciaOrderByPrazoAsc(UUID escritorioId, UUID empresaId, String mesCompetencia);

    Optional<EmDiaTarefa> findByIdAndEscritorioId(UUID id, UUID escritorioId);

    boolean existsByEscritorioIdAndEmpresaIdAndModeloIdAndMesCompetencia(UUID escritorioId, UUID empresaId, UUID modeloId, String mesCompetencia);

    boolean existsByEscritorioIdAndChaveDeduplicacaoAndStatusNotIn(UUID escritorioId, String chaveDeduplicacao, List<StatusTarefa> statusIgnorados);

    Optional<EmDiaTarefa> findFirstByEscritorioIdAndChaveDeduplicacaoAndStatusNotIn(UUID escritorioId, String chaveDeduplicacao, List<StatusTarefa> statusIgnorados);

    @Query("SELECT t FROM EmDiaTarefa t WHERE t.escritorioId = :escritorioId AND t.responsavel = :responsavel AND t.status NOT IN ('CONCLUIDA', 'NAO_SE_APLICA') ORDER BY t.prazo ASC")
    List<EmDiaTarefa> findMinhasTarefasAbertas(@Param("escritorioId") UUID escritorioId, @Param("responsavel") String responsavel);

    @Query("SELECT t FROM EmDiaTarefa t WHERE t.escritorioId = :escritorioId AND t.status NOT IN ('CONCLUIDA', 'NAO_SE_APLICA') ORDER BY t.prazo ASC")
    List<EmDiaTarefa> findTodasTarefasAbertas(@Param("escritorioId") UUID escritorioId);

    @Query("SELECT t FROM EmDiaTarefa t WHERE t.escritorioId = :escritorioId AND t.prazo < :hoje AND t.status NOT IN ('CONCLUIDA', 'NAO_SE_APLICA')")
    List<EmDiaTarefa> findAtrasadas(@Param("escritorioId") UUID escritorioId, @Param("hoje") LocalDate hoje);
}

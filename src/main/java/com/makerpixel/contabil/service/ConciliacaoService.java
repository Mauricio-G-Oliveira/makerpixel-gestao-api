package com.makerpixel.contabil.service;

import com.makerpixel.contabil.domain.ConciliacaoBancaria;
import com.makerpixel.contabil.domain.PerfilConciliacao;
import com.makerpixel.contabil.dto.ConciliacaoDTOs.*;
import com.makerpixel.contabil.exception.ConciliacaoConflictException;
import com.makerpixel.contabil.repository.ConciliacaoBancariaRepository;
import com.makerpixel.contabil.repository.PerfilConciliacaoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
public class ConciliacaoService {

    private final ConciliacaoBancariaRepository repository;
    private final PerfilConciliacaoRepository perfilRepository;

    private static final DateTimeFormatter HORA_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final ZoneId ZONE_SP = ZoneId.of("America/Sao_Paulo");

    public ConciliacaoService(ConciliacaoBancariaRepository repository, PerfilConciliacaoRepository perfilRepository) {
        this.repository = repository;
        this.perfilRepository = perfilRepository;
    }

    @Transactional(readOnly = true)
    public List<ConciliacaoResumoResponse> listarPorEmpresaEMes(UUID escritorioId, String cnpj, String competencia) {
        String cleanCnpj = cnpj.replaceAll("\\D", "");
        return repository.findByEscritorioIdAndCnpjAndCompetencia(escritorioId, cleanCnpj, competencia)
                .stream()
                .map(this::toResumoResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ConciliacaoResumoResponse> listarPorMes(UUID escritorioId, String competencia) {
        return repository.findByEscritorioIdAndCompetencia(escritorioId, competencia)
                .stream()
                .map(this::toResumoResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ConciliacaoResponse obterPorId(UUID escritorioId, UUID id) {
        ConciliacaoBancaria c = repository.findById(id)
                .filter(x -> x.getEscritorioId().equals(escritorioId))
                .orElseThrow(() -> new IllegalArgumentException("Conciliação não encontrada."));
        return toResponse(c);
    }

    @Transactional
    public ConciliacaoResponse salvar(UUID escritorioId, UUID usuarioId, String usuarioNome, SalvarConciliacaoRequest req) {
        String cleanCnpj = req.cnpj().replaceAll("\\D", "");
        String agencia = req.agencia() != null ? req.agencia() : "";

        ConciliacaoBancaria c = repository.findByEscritorioIdAndCnpjAndBancoAndAgenciaAndContaAndCompetencia(
                escritorioId, cleanCnpj, req.banco(), agencia, req.conta(), req.competencia()
        ).orElse(null);

        if (c != null) {
            // Checa concorrência otimista (versão)
            if (req.versao() != null && !req.versao().equals(c.getVersao())) {
                String hora = c.getAtualizadoEm() != null
                        ? c.getAtualizadoEm().atZoneSameInstant(ZONE_SP).format(HORA_FORMATTER)
                        : "--:--";
                throw new ConciliacaoConflictException(
                        String.format("%s salvou esta conciliação às %s; abra de novo", c.getUltimoUsuarioNome(), hora)
                );
            }
            c.setVersao(c.getVersao() + 1L);
        } else {
            c = new ConciliacaoBancaria();
            c.setEscritorioId(escritorioId);
            c.setCnpj(cleanCnpj);
            c.setBanco(req.banco());
            c.setAgencia(agencia);
            c.setConta(req.conta());
            c.setCompetencia(req.competencia());
            c.setVersao(1L);
        }

        c.setExtratoJson(req.extratoJson() != null ? req.extratoJson() : "[]");
        c.setPlanilhaJson(req.planilhaJson() != null ? req.planilhaJson() : "[]");
        c.setConferidosJson(req.conferidosJson() != null ? req.conferidosJson() : "[]");
        c.setJuncoesManuaisJson(req.juncoesManuaisJson() != null ? req.juncoesManuaisJson() : "[]");
        c.setObservacoes(req.observacoes());
        c.setResumoJson(req.resumoJson() != null ? req.resumoJson() : "{}");
        c.setUltimoUsuarioId(usuarioId);
        c.setUltimoUsuarioNome(usuarioNome != null ? usuarioNome : "Usuário");

        ConciliacaoBancaria salvo = repository.save(c);
        return toResponse(salvo);
    }

    @Transactional
    public void apagar(UUID escritorioId, UUID id) {
        ConciliacaoBancaria c = repository.findById(id)
                .filter(x -> x.getEscritorioId().equals(escritorioId))
                .orElseThrow(() -> new IllegalArgumentException("Conciliação não encontrada."));
        repository.delete(c);
    }

    // Perfil de conciliação por cliente
    @Transactional(readOnly = true)
    public PerfilConciliacaoDTO obterPerfil(UUID escritorioId, String cnpj) {
        String cleanCnpj = cnpj.replaceAll("\\D", "");
        return perfilRepository.findByEscritorioIdAndCnpj(escritorioId, cleanCnpj)
                .map(this::toPerfilDTO)
                .orElseGet(() -> new PerfilConciliacaoDTO(cleanCnpj, 2, "[]", true, "{}"));
    }

    @Transactional
    public PerfilConciliacaoDTO salvarPerfil(UUID escritorioId, PerfilConciliacaoDTO dto) {
        String cleanCnpj = dto.cnpj().replaceAll("\\D", "");
        PerfilConciliacao perfil = perfilRepository.findByEscritorioIdAndCnpj(escritorioId, cleanCnpj)
                .orElseGet(() -> {
                    PerfilConciliacao p = new PerfilConciliacao();
                    p.setEscritorioId(escritorioId);
                    p.setCnpj(cleanCnpj);
                    return p;
                });

        if (dto.toleranciaDias() != null) perfil.setToleranciaDias(dto.toleranciaDias());
        if (dto.palavrasChaveJson() != null) perfil.setPalavrasChaveJson(dto.palavrasChaveJson());
        if (dto.conciliarEmLote() != null) perfil.setConciliarEmLote(dto.conciliarEmLote());
        if (dto.mapeamentoColunasJson() != null) perfil.setMapeamentoColunasJson(dto.mapeamentoColunasJson());

        PerfilConciliacao salvo = perfilRepository.save(perfil);
        return toPerfilDTO(salvo);
    }

    private ConciliacaoResponse toResponse(ConciliacaoBancaria c) {
        return new ConciliacaoResponse(
                c.getId(),
                c.getCnpj(),
                c.getBanco(),
                c.getAgencia(),
                c.getConta(),
                c.getCompetencia(),
                c.getExtratoJson(),
                c.getPlanilhaJson(),
                c.getConferidosJson(),
                c.getJuncoesManuaisJson(),
                c.getObservacoes(),
                c.getResumoJson(),
                c.getUltimoUsuarioNome(),
                c.getVersao(),
                c.getAtualizadoEm()
        );
    }

    private ConciliacaoResumoResponse toResumoResponse(ConciliacaoBancaria c) {
        return new ConciliacaoResumoResponse(
                c.getId(),
                c.getCnpj(),
                c.getBanco(),
                c.getAgencia(),
                c.getConta(),
                c.getCompetencia(),
                c.getResumoJson(),
                c.getUltimoUsuarioNome(),
                c.getVersao(),
                c.getAtualizadoEm()
        );
    }

    private PerfilConciliacaoDTO toPerfilDTO(PerfilConciliacao p) {
        return new PerfilConciliacaoDTO(
                p.getCnpj(),
                p.getToleranciaDias(),
                p.getPalavrasChaveJson(),
                p.getConciliarEmLote(),
                p.getMapeamentoColunasJson()
        );
    }
}

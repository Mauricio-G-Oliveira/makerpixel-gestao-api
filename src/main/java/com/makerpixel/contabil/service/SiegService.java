package com.makerpixel.contabil.service;

import com.makerpixel.contabil.domain.SiegConfig;
import com.makerpixel.contabil.repository.SiegConfigRepository;
import com.makerpixel.contabil.service.CofreCryptoService.TextoEncifrado;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;

@Service
public class SiegService {

    private static final Logger log = LoggerFactory.getLogger(SiegService.class);
    private static final DateTimeFormatter FMT_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final SiegConfigRepository siegConfigRepository;
    private final CofreCryptoService cofreCryptoService;

    @org.springframework.beans.factory.annotation.Value("${sieg.habilitada:false}")
    private boolean siegHabilitada;

    public SiegService(SiegConfigRepository siegConfigRepository, CofreCryptoService cofreCryptoService) {
        this.siegConfigRepository = siegConfigRepository;
        this.cofreCryptoService = cofreCryptoService;
    }

    public record SiegConfigResponse(
            boolean configurada,
            String ultimosQuatro,
            String configuradaEm,
            String ultimaBusca,
            String ultimoStatus,
            String ultimoErro
    ) {}

    public record TesteConexaoResponse(
            boolean sucesso,
            String mensagem,
            String testadoEm
    ) {}

    @Transactional(readOnly = true)
    public SiegConfigResponse obterConfiguracao(UUID escritorioId) {
        Optional<SiegConfig> opt = siegConfigRepository.findByEscritorioId(escritorioId);
        if (opt.isEmpty()) {
            return new SiegConfigResponse(false, null, null, null, null, null);
        }

        SiegConfig cfg = opt.get();
        return new SiegConfigResponse(
                true,
                cfg.getUltimosQuatro(),
                cfg.getCriadoEm() != null ? cfg.getCriadoEm().format(FMT_DATA) : null,
                cfg.getUltimaBusca() != null ? cfg.getUltimaBusca().format(FMT_DATA) : null,
                cfg.getUltimoStatus(),
                cfg.getUltimoErro()
        );
    }

    @Transactional
    public SiegConfigResponse salvarChave(UUID escritorioId, String apiKey) {
        if (apiKey == null || apiKey.trim().length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A chave de API da SIEG informada é inválida ou muito curta.");
        }

        String chaveLimpa = apiKey.trim();
        String ultimos4 = chaveLimpa.substring(chaveLimpa.length() - 4);

        TextoEncifrado enc = cofreCryptoService.cifrarTexto(chaveLimpa);

        SiegConfig cfg = siegConfigRepository.findByEscritorioId(escritorioId)
                .orElseGet(() -> {
                    SiegConfig novo = new SiegConfig();
                    novo.setEscritorioId(escritorioId);
                    return novo;
                });

        cfg.setApiKeyCifrada(enc.ciphertext());
        cfg.setIvDados(enc.iv());
        cfg.setUltimosQuatro(ultimos4);
        cfg.setAtivo(true);
        cfg.setUltimoStatus("EM_PREPARACAO");
        cfg.setUltimoErro("Integração em fase de homologação: aguardando documentação oficial da API SIEG.");
        cfg.setAtualizadoEm(OffsetDateTime.now());

        siegConfigRepository.save(cfg);

        log.info("Chave de API da SIEG criptografada e salva no cofre para escritorioId: {} (final: ****{}). Status: EM_PREPARACAO.",
                escritorioId, ultimos4);

        return obterConfiguracao(escritorioId);
    }

    @Transactional
    public TesteConexaoResponse testarConexao(UUID escritorioId) {
        SiegConfig cfg = siegConfigRepository.findByEscritorioId(escritorioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Integração SIEG não configurada para este escritório."));

        String chave = cofreCryptoService.decifrarTexto(cfg.getApiKeyCifrada(), cfg.getIvDados());
        if (chave == null || chave.isBlank()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Falha ao decifrar a credencial do cofre.");
        }

        OffsetDateTime agora = OffsetDateTime.now();
        cfg.setUltimaBusca(agora);
        cfg.setUltimoStatus("EM_PREPARACAO");
        cfg.setUltimoErro("Integração SIEG em preparação: aguardando documentação oficial da API.");
        siegConfigRepository.save(cfg);

        log.info("Teste de conexão SIEG para escritorioId: {} informado como EM_PREPARACAO (sem chamadas fictícias).", escritorioId);

        return new TesteConexaoResponse(
                false,
                "Integração SIEG em preparação: aguardando documentação oficial da API.",
                agora.format(FMT_DATA)
        );
    }

    @Transactional
    public void removerChave(UUID escritorioId) {
        siegConfigRepository.findByEscritorioId(escritorioId)
                .ifPresent(siegConfigRepository::delete);
        log.info("Configuração da integração SIEG removida para escritorioId: {}", escritorioId);
    }
}

package com.makerpixel.contabil.service;

import com.makerpixel.contabil.domain.Escritorio;
import com.makerpixel.contabil.domain.NfceDocumento;
import com.makerpixel.contabil.domain.NotasyncExecucao;
import com.makerpixel.contabil.domain.SituacaoNfce;
import com.makerpixel.contabil.dto.NotaSyncDTOs.*;
import com.makerpixel.contabil.repository.EscritorioRepository;
import com.makerpixel.contabil.repository.NfceDocumentoRepository;
import com.makerpixel.contabil.repository.NotasyncExecucaoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class NotaSyncService {

    private final NfceDocumentoRepository documentoRepository;
    private final NotasyncExecucaoRepository execucaoRepository;
    private final EscritorioRepository escritorioRepository;

    public NotaSyncService(
            NfceDocumentoRepository documentoRepository,
            NotasyncExecucaoRepository execucaoRepository,
            EscritorioRepository escritorioRepository
    ) {
        this.documentoRepository = documentoRepository;
        this.execucaoRepository = execucaoRepository;
        this.escritorioRepository = escritorioRepository;
    }

    /**
     * Validação do dígito verificador da chave de acesso de 44 dígitos (Módulo 11).
     */
    public boolean validarChaveAcesso(String chave) {
        if (chave == null) return false;
        String limpa = chave.replaceAll("\\D", "");
        if (limpa.length() != 44) return false;

        String corpo = limpa.substring(0, 43);
        int dvInformado = Character.getNumericValue(limpa.charAt(43));

        int soma = 0;
        int peso = 2;
        for (int i = corpo.length() - 1; i >= 0; i--) {
            soma += Character.getNumericValue(corpo.charAt(i)) * peso;
            peso = (peso == 9) ? 2 : peso + 1;
        }

        int resto = soma % 11;
        int dvCalculado = (resto == 0 || resto == 1) ? 0 : 11 - resto;
        return dvInformado == dvCalculado;
    }

    /**
     * Processa um lote de notas fiscais enviado pelo agente local,
     * aplicando deduplicação estrita pela chave de acesso e controle de cancelamentos.
     */
    @Transactional
    public EnviarLoteResponse processarLote(UUID escritorioId, EnviarLoteRequest req) {
        Escritorio esc = escritorioRepository.findById(escritorioId)
                .orElseThrow(() -> new IllegalArgumentException("Escritório não encontrado."));

        if (!esc.hasModulo("NOTASYNC")) {
            throw new IllegalStateException("Módulo NotaSync não está ativado para este escritório.");
        }

        int inseridas = 0;
        int atualizadas = 0;
        int canceladas = 0;

        if (req.documentos() != null) {
            for (NfceItemDTO item : req.documentos()) {
                if (item.chave() == null || item.chave().trim().isEmpty()) continue;
                String chave = item.chave().replaceAll("\\D", "");

                Optional<NfceDocumento> existenteOpt = documentoRepository.findByEscritorioIdAndChave(escritorioId, chave);

                if (existenteOpt.isPresent()) {
                    NfceDocumento doc = existenteOpt.get();
                    boolean mudou = false;

                    if (item.situacao() == SituacaoNfce.CANCELADA && doc.getSituacao() != SituacaoNfce.CANCELADA) {
                        doc.setSituacao(SituacaoNfce.CANCELADA);
                        doc.setMotivoCancelamento(item.motivoCancelamento() != null ? item.motivoCancelamento() : "Cancelada na SEF/SC");
                        canceladas++;
                        mudou = true;
                    }

                    if (item.nsu() != null && (doc.getNsu() == null || item.nsu() > doc.getNsu())) {
                        doc.setNsu(item.nsu());
                        mudou = true;
                    }

                    if (mudou) {
                        documentoRepository.save(doc);
                        atualizadas++;
                    }
                } else {
                    NfceDocumento novo = new NfceDocumento();
                    novo.setEscritorioId(escritorioId);
                    novo.setChave(chave);
                    novo.setCnpjEmitente(req.cnpjEmitente() != null ? req.cnpjEmitente().replaceAll("\\D", "") : "");
                    novo.setNumero(item.numero() != null ? item.numero() : 1);
                    novo.setSerie(item.serie() != null ? item.serie() : 1);
                    novo.setDataEmissao(item.dataEmissao());
                    novo.setValorTotal(item.valorTotal() != null ? item.valorTotal() : BigDecimal.ZERO);
                    novo.setSituacao(item.situacao() != null ? item.situacao() : SituacaoNfce.AUTORIZADA);
                    novo.setMotivoCancelamento(item.motivoCancelamento());
                    novo.setNsu(item.nsu());
                    novo.setOrigem("SEF_SC_WS");

                    documentoRepository.save(novo);
                    inseridas++;
                    if (novo.getSituacao() == SituacaoNfce.CANCELADA) canceladas++;
                }
            }
        }

        // Registra histórico de execução do agente
        if (req.execucao() != null) {
            NotasyncExecucao exec = new NotasyncExecucao();
            exec.setEscritorioId(escritorioId);
            exec.setCnpj(req.cnpjEmitente() != null ? req.cnpjEmitente().replaceAll("\\D", "") : "");
            exec.setCstat(req.execucao().cstat());
            exec.setXmotivo(req.execucao().xmotivo());
            exec.setQtdDocumentos(req.execucao().qtdDocumentos() != null ? req.execucao().qtdDocumentos() : inseridas);
            exec.setDuracaoMs(req.execucao().duracaoMs() != null ? req.execucao().duracaoMs() : 0L);
            exec.setUltNsu(req.execucao().ultNsu());
            exec.setStatus(req.execucao().status() != null ? req.execucao().status() : "OK");
            execucaoRepository.save(exec);
        }

        return new EnviarLoteResponse(
                (req.documentos() != null ? req.documentos().size() : 0),
                inseridas,
                atualizadas,
                canceladas,
                "Lote processado com sucesso pelo NotaSync."
        );
    }

    /**
     * Métricas agregadas do escritório para cards de exibição.
     */
    @Transactional(readOnly = true)
    public ResumoMetricasDTO obterResumo(UUID escritorioId) {
        long total = documentoRepository.countByEscritorioId(escritorioId);
        BigDecimal valor = documentoRepository.sumValorTotalByEscritorioId(escritorioId);
        long canceladas = documentoRepository.countByEscritorioIdAndSituacao(escritorioId, SituacaoNfce.CANCELADA);
        long pendenciasSat = execucaoRepository.countByEscritorioIdAndCstat(escritorioId, 8002);

        return new ResumoMetricasDTO(
                total,
                valor != null ? valor : BigDecimal.ZERO,
                canceladas,
                total - canceladas,
                pendenciasSat
        );
    }

    @Transactional(readOnly = true)
    public Page<NfceDocumento> listarDocumentos(UUID escritorioId, String cnpj, SituacaoNfce situacao, Pageable pageable) {
        if (cnpj != null && !cnpj.trim().isEmpty() && situacao != null) {
            return documentoRepository.findByEscritorioIdAndCnpjEmitenteAndSituacao(escritorioId, cnpj.replaceAll("\\D", ""), situacao, pageable);
        }
        if (cnpj != null && !cnpj.trim().isEmpty()) {
            return documentoRepository.findByEscritorioIdAndCnpjEmitente(escritorioId, cnpj.replaceAll("\\D", ""), pageable);
        }
        if (situacao != null) {
            return documentoRepository.findByEscritorioIdAndSituacao(escritorioId, situacao, pageable);
        }
        return documentoRepository.findByEscritorioId(escritorioId, pageable);
    }

    @Transactional(readOnly = true)
    public List<NotasyncExecucao> listarUltimasExecucoes(UUID escritorioId) {
        return execucaoRepository.findTop20ByEscritorioIdOrderByDataHoraDesc(escritorioId);
    }
}

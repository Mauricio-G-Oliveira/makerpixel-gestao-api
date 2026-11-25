package com.makerpixel.contabil.service;

import com.makerpixel.contabil.domain.AuditoriaPendente;
import com.makerpixel.contabil.domain.DocumentoFiscal;
import com.makerpixel.contabil.domain.Empresa;
import com.makerpixel.contabil.fonte.DocumentoColetadoDTO;
import com.makerpixel.contabil.repository.AuditoriaPendenteRepository;
import com.makerpixel.contabil.repository.DocumentoFiscalRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;

@Service
public class DocumentoFiscalIngestaoService {

    private static final Logger log = LoggerFactory.getLogger(DocumentoFiscalIngestaoService.class);

    private final DocumentoFiscalRepository documentoRepository;
    private final AuditoriaPendenteRepository pendenteRepository;

    public DocumentoFiscalIngestaoService(
            DocumentoFiscalRepository documentoRepository,
            AuditoriaPendenteRepository pendenteRepository
    ) {
        this.documentoRepository = documentoRepository;
        this.pendenteRepository = pendenteRepository;
    }

    /**
     * Ingestão unificada e deduplicada de documentos fiscais vindo de qualquer fonte
     * (NotaSync SEF/SC, SIEG ou Upload manual).
     */
    @Transactional
    public DocumentoFiscal ingerirDocumento(UUID escritorioId, Empresa empresa, DocumentoColetadoDTO dto) {
        if (dto.chaveAcesso() == null || dto.chaveAcesso().isBlank()) {
            throw new IllegalArgumentException("Chave de acesso não pode ser nula ou vazia.");
        }

        Optional<DocumentoFiscal> existenteOpt = documentoRepository.findByEscritorioIdAndChaveAcesso(
                escritorioId, dto.chaveAcesso()
        );

        DocumentoFiscal doc;
        boolean novo = false;

        if (existenteOpt.isPresent()) {
            doc = existenteOpt.get();
            // Registra a nova fonte que também encontrou este documento
            doc.adicionarFonte(dto.fonteOrigem());

            // Se a nova ocorrência é um cancelamento e o anterior estava autorizado, atualiza a situação
            if ("CANCELADA".equalsIgnoreCase(dto.situacao())) {
                doc.setSituacao("CANCELADA");
                doc.setMotivoCancelamento(dto.motivoCancelamento());
                log.info("Documento {} atualizado para CANCELADA pelo fluxo de deduplicação.", doc.getChaveAcesso());
            }

            if (dto.storagePath() != null && !dto.storagePath().isBlank()) {
                doc.setStoragePath(dto.storagePath());
            }
            if (dto.conteudoXml() != null && !dto.conteudoXml().isBlank()) {
                doc.setConteudoXml(dto.conteudoXml());
            }
            doc.setAtualizadoEm(OffsetDateTime.now());
        } else {
            novo = true;
            doc = new DocumentoFiscal();
            doc.setEscritorioId(escritorioId);
            doc.setEmpresaId(empresa != null ? empresa.getId() : null);
            doc.setChaveAcesso(dto.chaveAcesso());
            doc.setModelo(dto.modelo() != null ? dto.modelo() : "55");
            doc.setSerie(dto.serie());
            doc.setNumero(dto.numero());
            doc.setDataEmissao(dto.dataEmissao() != null ? dto.dataEmissao() : OffsetDateTime.now());

            String comp = dto.dataEmissao() != null
                    ? YearMonth.from(dto.dataEmissao()).toString()
                    : YearMonth.now().toString();
            doc.setCompetencia(comp);

            doc.setValorTotal(dto.valorTotal() != null ? dto.valorTotal() : java.math.BigDecimal.ZERO);
            doc.setSituacao(dto.situacao() != null ? dto.situacao() : "AUTORIZADA");
            doc.setMotivoCancelamento(dto.motivoCancelamento());
            doc.adicionarFonte(dto.fonteOrigem());
            doc.setStoragePath(dto.storagePath());
            doc.setConteudoXml(dto.conteudoXml());
            doc.setRecebidoEm(OffsetDateTime.now());
            doc.setCriadoEm(OffsetDateTime.now());
            doc.setAtualizadoEm(OffsetDateTime.now());
        }

        doc = documentoRepository.save(doc);

        // Enfileira pendência de auditoria contínua de forma desacoplada
        enfileirarAuditoriaPendente(escritorioId, doc, novo);

        return doc;
    }

    private void enfileirarAuditoriaPendente(UUID escritorioId, DocumentoFiscal doc, boolean novo) {
        // Se já existe pendência em aberto, mantém; se não, cria
        Optional<AuditoriaPendente> pendenteOpt = pendenteRepository.findByEscritorioIdAndDocumentoId(
                escritorioId, doc.getId()
        );

        if (pendenteOpt.isEmpty()) {
            AuditoriaPendente p = new AuditoriaPendente();
            p.setEscritorioId(escritorioId);
            p.setDocumento(doc);
            p.setStatus("PENDENTE");
            p.setTentativas(0);
            p.setProximaTentativa(OffsetDateTime.now());
            pendenteRepository.save(p);
        } else if ("CANCELADA".equalsIgnoreCase(doc.getSituacao())) {
            // Se foi cancelado posteriormente, reabre para re-auditar e marcar cancelado
            AuditoriaPendente p = pendenteOpt.get();
            p.setStatus("PENDENTE");
            p.setProximaTentativa(OffsetDateTime.now());
            pendenteRepository.save(p);
        }
    }
}

package com.makerpixel.contabil.service;

import com.makerpixel.contabil.domain.*;
import com.makerpixel.contabil.reforma.CalculadoraRtcClient;
import com.makerpixel.contabil.reforma.CalculadoraRtcIndisponivelException;
import com.makerpixel.contabil.reforma.LeitorNfeIbsCbs;
import com.makerpixel.contabil.reforma.LeitorNfeIbsCbs.ItemNotaIbsCbs;
import com.makerpixel.contabil.reforma.LeitorNfeIbsCbs.NotaFiscalIbsCbs;
import com.makerpixel.contabil.reforma.dto.*;
import com.makerpixel.contabil.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.*;

@Service
public class AuditoriaContinuaWorkerService {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaContinuaWorkerService.class);
    private static final BigDecimal TOLERANCIA = new BigDecimal("0.01");
    private static final int MAX_TENTATIVAS = 5;

    private final AuditoriaPendenteRepository auditoriaPendenteRepository;
    private final DocumentoFiscalRepository documentoFiscalRepository;
    private final ReformaLoteRepository reformaLoteRepository;
    private final ReformaItemRepository reformaItemRepository;
    private final EmpresaRepository empresaRepository;
    private final EscritorioRepository escritorioRepository;
    private final CalculadoraRtcClient calculadoraRtcClient;
    private final SupabaseStorageService storageService;
    private final EmDiaService emDiaService;

    public AuditoriaContinuaWorkerService(
            AuditoriaPendenteRepository auditoriaPendenteRepository,
            DocumentoFiscalRepository documentoFiscalRepository,
            ReformaLoteRepository reformaLoteRepository,
            ReformaItemRepository reformaItemRepository,
            EmpresaRepository empresaRepository,
            EscritorioRepository escritorioRepository,
            CalculadoraRtcClient calculadoraRtcClient,
            SupabaseStorageService storageService,
            EmDiaService emDiaService
    ) {
        this.auditoriaPendenteRepository = auditoriaPendenteRepository;
        this.documentoFiscalRepository = documentoFiscalRepository;
        this.reformaLoteRepository = reformaLoteRepository;
        this.reformaItemRepository = reformaItemRepository;
        this.empresaRepository = empresaRepository;
        this.escritorioRepository = escritorioRepository;
        this.calculadoraRtcClient = calculadoraRtcClient;
        this.storageService = storageService;
        this.emDiaService = emDiaService;
    }

    public record ResultadoCicloAuditoria(
            int processados,
            int conferidosComSucesso,
            int jaAuditadosIgnorados,
            int falhasComReagendamento,
            int errosPermanentes,
            int limitePlanoAtingido
    ) {}

    /**
     * Processa pendências de auditoria de forma contínua e assíncrona.
     * NÃO anota com @Transactional na classe/método raiz para manter chamadas I/O HTTP fora do pool de DB.
     * Cada documento possui transação atômica curta independente com Propagation.REQUIRES_NEW.
     */
    public ResultadoCicloAuditoria processarPendentes(UUID escritorioIdFiltro, int tamanhoLote) {
        OffsetDateTime agora = OffsetDateTime.now();

        // 1. Reset de pendências travadas há mais de 15 minutos em status PROCESSANDO
        resetarPendenciasTravadas(agora.minusMinutes(15));

        // 2. Resiliência: Checa versão oficial da Calculadora RTC
        VersaoCalculadoraDto versaoRtc = null;
        try {
            versaoRtc = calculadoraRtcClient.obterVersao();
        } catch (Exception e) {
            log.warn("Calculadora RTC indisponível ao consultar versão: {}. Ciclo de auditoria contínua adiado.", e.getMessage());
        }

        if (versaoRtc == null || versaoRtc.versaoMotor() == null || "OFFLINE".equalsIgnoreCase(versaoRtc.status())) {
            log.warn("Calculadora RTC oficial está offline. Nenhuma nota será auditada para evitar gravação de versão fictícia.");
            return new ResultadoCicloAuditoria(0, 0, 0, 0, 0, 0);
        }

        String versaoCalculadora = versaoRtc.versaoMotor();

        // 3. Trava lote com SELECT ... FOR UPDATE SKIP LOCKED e marca status = 'PROCESSANDO'
        List<AuditoriaPendente> pendentes = travarLoteParaProcessamento(escritorioIdFiltro, agora, tamanhoLote);
        if (pendentes.isEmpty()) {
            return new ResultadoCicloAuditoria(0, 0, 0, 0, 0, 0);
        }

        int conferidos = 0;
        int ignorados = 0;
        int reagendados = 0;
        int erros = 0;
        int limiteAtingido = 0;

        for (AuditoriaPendente pendente : pendentes) {
            try {
                DocumentoFiscal doc = pendente.getDocumento();
                if (doc == null) {
                    atualizarPendenteStatus(pendente.getId(), "ERRO", "Documento fiscal não encontrado.", null, agora);
                    erros++;
                    continue;
                }

                // 4. Verificação de Limite Mensal do Plano do Escritório
                String competenciaDoc = doc.getCompetencia() != null ? doc.getCompetencia() : YearMonth.from(agora).toString();
                long limitePlano = obterLimiteMensal(pendente.getEscritorioId());
                long totalAuditadoNoMes = reformaLoteRepository.sumTotalNotasPorCompetencia(pendente.getEscritorioId(), competenciaDoc);

                if (totalAuditadoNoMes >= limitePlano) {
                    log.info("Escritório {} atingiu o limite mensal do plano ({} notas). Auditoria da nota {} adiada.",
                            pendente.getEscritorioId(), limitePlano, doc.getChaveAcesso());
                    adiarPorLimitePlano(pendente.getId(), agora);
                    limiteAtingido++;
                    continue;
                }

                // 5. Documento cancelado não necessita de recálculo
                if ("CANCELADA".equalsIgnoreCase(doc.getSituacao())) {
                    concluirPendente(pendente.getId(), versaoCalculadora, agora);
                    conferidos++;
                    continue;
                }

                // 6. Idempotência por chave e versão oficial da calculadora
                boolean jaAuditado = reformaItemRepository.existsByEscritorioIdAndChaveNfeAndVersaoCalculadora(
                        doc.getEscritorioId(), doc.getChaveAcesso(), versaoCalculadora
                );
                if (jaAuditado) {
                    concluirPendente(pendente.getId(), versaoCalculadora, agora);
                    ignorados++;
                    continue;
                }

                // 7. Obtenção do XML (Storage Privado ou fallback local)
                InputStream isXml = null;
                if (doc.getStoragePath() != null && !doc.getStoragePath().isBlank()) {
                    byte[] bytes = storageService.baixarArquivo(doc.getStoragePath());
                    if (bytes != null && bytes.length > 0) {
                        isXml = new ByteArrayInputStream(bytes);
                    }
                } else if (doc.getConteudoXml() != null && !doc.getConteudoXml().isBlank()) {
                    isXml = new ByteArrayInputStream(doc.getConteudoXml().getBytes(StandardCharsets.UTF_8));
                }

                if (isXml == null) {
                    int tent = pendente.getTentativas() + 1;
                    if (tent >= MAX_TENTATIVAS) {
                        atualizarPendenteStatus(pendente.getId(), "ERRO", "XML não localizado no storage privado após " + tent + " tentativas.", null, agora);
                        erros++;
                    } else {
                        atualizarPendenteStatus(pendente.getId(), "PENDENTE", "Aguardando disponibilidade do XML no storage.", agora.plusMinutes(5L * tent), agora);
                        reagendados++;
                    }
                    continue;
                }

                // 8. Parse do XML (FORA de qualquer transação de banco)
                NotaFiscalIbsCbs nota = LeitorNfeIbsCbs.lerXml(isXml);
                if (nota == null || nota.itens() == null || nota.itens().isEmpty()) {
                    atualizarPendenteStatus(pendente.getId(), "CONCLUIDO", "Nota sem itens identificados para auditoria.", null, agora);
                    conferidos++;
                    continue;
                }

                // 9. Chamada HTTP à Calculadora RTC Oficial com Observabilidade (FORA de transação de banco)
                OperacaoRtcRequest op = new OperacaoRtcRequest();
                op.setIdOperacao(nota.chaveAcesso());
                op.setDataEmissao(nota.dataEmissao() != null ? nota.dataEmissao().toString() : agora.toString());
                op.setModeloDocumento(nota.modelo());

                List<OperacaoItemRtcRequest> itensOp = new ArrayList<>();
                for (ItemNotaIbsCbs item : nota.itens()) {
                    OperacaoItemRtcRequest reqItem = new OperacaoItemRtcRequest();
                    reqItem.setNumeroItem(item.numeroItem());
                    reqItem.setCodigoProduto(item.codigoProduto());
                    reqItem.setDescricao(item.descricao());
                    reqItem.setNcm(item.ncm());
                    reqItem.setCfop(item.cfop());
                    reqItem.setCst(item.cstInformado());
                    reqItem.setcClassTrib(item.cClassTrib());
                    reqItem.setQuantidade(item.quantidade());
                    reqItem.setValorTotal(item.valorItem());
                    reqItem.setBaseCalculo(item.baseCalculoInformada());
                    reqItem.setUfOrigem(nota.ufEmitente());
                    reqItem.setMunicipioOrigem(nota.municipioEmitente());
                    reqItem.setUfDestino(nota.ufDestinatario());
                    reqItem.setMunicipioDestino(nota.municipioDestinatario());
                    itensOp.add(reqItem);
                }
                op.setItens(itensOp);

                CalculoRtcResponse respCalculo;
                try {
                    respCalculo = calculadoraRtcClient.calcularComObservabilidade(op);
                } catch (CalculadoraRtcIndisponivelException e) {
                    log.warn("Calculadora RTC indisponível ao calcular nota {}. Reagendando pendência com backoff.", doc.getChaveAcesso());
                    atualizarPendenteStatus(pendente.getId(), "PENDENTE", "Calculadora RTC oficial temporariamente indisponível. Reagendado.", agora.plusMinutes(2), agora);
                    reagendados++;
                    continue;
                }

                Map<Integer, CalculoItemRtcResponse> mapaCalculados = new HashMap<>();
                if (respCalculo != null && respCalculo.getItens() != null) {
                    for (CalculoItemRtcResponse ci : respCalculo.getItens()) {
                        mapaCalculados.put(ci.getNumeroItem(), ci);
                    }
                }

                // 10. Persistência atômica em transação curta exclusiva por documento
                salvarResultadoAuditoriaDocumento(doc, nota, mapaCalculados, versaoCalculadora, pendente.getId(), agora);
                conferidos++;

            } catch (Exception e) {
                log.error("Falha ao auditar documento pendente id: {}", pendente.getId(), e);
                int tent = pendente.getTentativas() + 1;
                if (tent >= MAX_TENTATIVAS) {
                    atualizarPendenteStatus(pendente.getId(), "ERRO", "Erro no processamento da nota: " + e.getMessage(), null, agora);
                    erros++;
                } else {
                    atualizarPendenteStatus(pendente.getId(), "PENDENTE", e.getMessage(), agora.plusMinutes(5L * tent), agora);
                    reagendados++;
                }
            }
        }

        return new ResultadoCicloAuditoria(
                pendentes.size(), conferidos, ignorados, reagendados, erros, limiteAtingido
        );
    }

    /**
     * Transação atômica isolada (REQUIRES_NEW) para persistência dos resultados do documento auditado.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void salvarResultadoAuditoriaDocumento(
            DocumentoFiscal doc,
            NotaFiscalIbsCbs nota,
            Map<Integer, CalculoItemRtcResponse> mapaCalculados,
            String versaoCalculadora,
            UUID pendenteId,
            OffsetDateTime agora
    ) {
        String competencia = doc.getCompetencia() != null ? doc.getCompetencia() : agora.toString().substring(0, 7);
        ReformaLote lote = obterOuCriarLoteContinua(doc.getEscritorioId(), doc.getEmpresaId(), competencia, versaoCalculadora);

        int novosItens = 0;
        int novasDivergencias = 0;
        int novosOk = 0;
        int novosSemGrupo = 0;
        int novosErros = 0;

        for (ItemNotaIbsCbs item : nota.itens()) {
            novosItens++;
            ReformaItem ri = new ReformaItem();
            ri.setLote(lote);
            ri.setEscritorioId(doc.getEscritorioId());
            ri.setDocumentoId(doc.getId());
            ri.setVersaoCalculadora(versaoCalculadora);
            ri.setChaveNfe(nota.chaveAcesso());
            ri.setNumeroNota(nota.numeroNota());
            ri.setModelo(nota.modelo());
            ri.setDataEmissao(nota.dataEmissao());
            ri.setnItem(item.numeroItem());
            ri.setCodigoProduto(item.codigoProduto());
            ri.setDescricao(item.descricao());
            ri.setNcm(item.ncm());
            ri.setCfop(item.cfop());
            ri.setCstInformado(item.cstInformado());
            ri.setcClassTrib(item.cClassTrib());
            ri.setValorItem(item.valorItem());
            ri.setBaseCalculo(item.baseCalculoInformada());
            ri.setCbsDestacado(item.valorCbsInformado());
            ri.setIbsUfDestacado(item.valorIbsUfInformado());
            ri.setIbsMunDestacado(item.valorIbsMunInformado());

            CalculoItemRtcResponse calc = mapaCalculados.get(item.numeroItem());

            if (!item.temGrupoIbsCbs()) {
                ri.setClassificacao("SEM_GRUPO_IBSCBS");
                ri.setMotivoDivergencia("Nota fiscal emitida sem grupo de tributação IBS/CBS.");
                novosSemGrupo++;
                novasDivergencias++;
            } else if (calc == null) {
                ri.setClassificacao("ERRO_CALCULO");
                ri.setMotivoDivergencia("Motor RTC indisponível para cálculo do item.");
                novosErros++;
                novasDivergencias++;
            } else if ("INCONSISTENCIA_ENTRADA".equals(calc.getStatus())) {
                ri.setClassificacao("ENQUADRAMENTO_INVALIDO");
                ri.setMotivoDivergencia(calc.getMensagemInconsistencia());
                novosErros++;
                novasDivergencias++;
            } else {
                BigDecimal cbsCalc = calc.getValorCbs() != null ? calc.getValorCbs() : BigDecimal.ZERO;
                BigDecimal ibsUfCalc = calc.getValorIbsUf() != null ? calc.getValorIbsUf() : BigDecimal.ZERO;
                BigDecimal ibsMunCalc = calc.getValorIbsMun() != null ? calc.getValorIbsMun() : BigDecimal.ZERO;

                ri.setCbsCalculado(cbsCalc);
                ri.setIbsUfCalculado(ibsUfCalc);
                ri.setIbsMunCalculado(ibsMunCalc);
                ri.setCstCalculado(calc.getCstCalculado());
                ri.setMemoriaCalculo(calc.getMemoriaCalculo());
                ri.setFundamentacaoLegal(calc.getFundamentacaoLegal());

                BigDecimal difCbs = item.valorCbsInformado().subtract(cbsCalc).abs();
                BigDecimal difIbsUf = item.valorIbsUfInformado().subtract(ibsUfCalc).abs();
                BigDecimal difIbsMun = item.valorIbsMunInformado().subtract(ibsMunCalc).abs();
                BigDecimal difTotal = difCbs.add(difIbsUf).add(difIbsMun).setScale(2, RoundingMode.HALF_UP);
                ri.setDiferencaTotal(difTotal);

                if (difTotal.compareTo(TOLERANCIA) <= 0) {
                    ri.setClassificacao("OK");
                    novosOk++;
                } else {
                    ri.setClassificacao("DIVERGENCIA_VALOR");
                    ri.setMotivoDivergencia(String.format("Divergência IBS/CBS: informado R$ %s vs calculado R$ %s",
                            item.valorCbsInformado().add(item.valorIbsTotalInformado()),
                            cbsCalc.add(ibsUfCalc).add(ibsMunCalc)));
                    novasDivergencias++;
                }
            }

            reformaItemRepository.save(ri);
        }

        // Atualiza contadores do lote
        lote.setTotalNotas(lote.getTotalNotas() + 1);
        lote.setTotalItens(lote.getTotalItens() + novosItens);
        lote.setTotalOk(lote.getTotalOk() + novosOk);
        lote.setTotalDivergencias(lote.getTotalDivergencias() + novasDivergencias);
        lote.setTotalSemDestaque(lote.getTotalSemDestaque() + novosSemGrupo);
        lote.setTotalErros(lote.getTotalErros() + novosErros);
        reformaLoteRepository.save(lote);

        // Atualiza tarefa no Em Dia se houver divergências
        if (lote.getTotalDivergencias() > 0 && doc.getEmpresaId() != null) {
            Empresa emp = empresaRepository.findById(doc.getEmpresaId()).orElse(null);
            String razao = emp != null ? emp.getRazaoSocial() : "Empresa";
            emDiaService.criarOuAtualizarTarefaDivergenciaContinua(
                    doc.getEscritorioId(), doc.getEmpresaId(), razao, competencia, lote.getTotalDivergencias()
            );
        }

        // Conclui a pendência
        auditoriaPendenteRepository.findById(pendenteId).ifPresent(p -> {
            p.setStatus("CONCLUIDO");
            p.setVersaoCalculadora(versaoCalculadora);
            p.setErroMensagem(null);
            p.setAtualizadoEm(agora);
            auditoriaPendenteRepository.save(p);
        });
    }

    /**
     * Trava lote de pendências usando SELECT ... FOR UPDATE SKIP LOCKED
     * e marca status 'PROCESSANDO' imediatamente para liberar trava de linha.
     */
    @Transactional
    public List<AuditoriaPendente> travarLoteParaProcessamento(UUID escritorioIdFiltro, OffsetDateTime agora, int tamanhoLote) {
        List<AuditoriaPendente> itens = escritorioIdFiltro != null
                ? auditoriaPendenteRepository.findPendentesPorEscritorioComLock(escritorioIdFiltro, agora, tamanhoLote)
                : auditoriaPendenteRepository.findPendentesParaProcessarComLock(agora, tamanhoLote);

        for (AuditoriaPendente p : itens) {
            p.setStatus("PROCESSANDO");
            p.setAtualizadoEm(agora);
            auditoriaPendenteRepository.save(p);
        }
        return itens;
    }

    @Transactional
    public void resetarPendenciasTravadas(OffsetDateTime limiteTempo) {
        try {
            int resetados = auditoriaPendenteRepository.resetarPendenciasTravadas(limiteTempo);
            if (resetados > 0) {
                log.info("Resetadas {} pendências travadas em PROCESSANDO há mais de 15 minutos para PENDENTE.", resetados);
            }
        } catch (Exception e) {
            log.warn("Erro ao resetar pendências travadas: {}", e.getMessage());
        }
    }

    @Transactional
    public void adiarPorLimitePlano(UUID pendenteId, OffsetDateTime agora) {
        auditoriaPendenteRepository.findById(pendenteId).ifPresent(p -> {
            p.setStatus("PENDENTE");
            p.setProximaTentativa(agora.plusHours(12));
            p.setErroMensagem("Limite mensal de auditoria do plano atingido. Reagendado.");
            p.setAtualizadoEm(agora);
            auditoriaPendenteRepository.save(p);
        });
    }

    @Transactional
    public void concluirPendente(UUID pendenteId, String versaoCalculadora, OffsetDateTime agora) {
        auditoriaPendenteRepository.findById(pendenteId).ifPresent(p -> {
            p.setStatus("CONCLUIDO");
            p.setVersaoCalculadora(versaoCalculadora);
            p.setErroMensagem(null);
            p.setAtualizadoEm(agora);
            auditoriaPendenteRepository.save(p);
        });
    }

    @Transactional
    public void atualizarPendenteStatus(UUID pendenteId, String status, String erro, OffsetDateTime proximaTentativa, OffsetDateTime agora) {
        auditoriaPendenteRepository.findById(pendenteId).ifPresent(p -> {
            p.setStatus(status);
            p.setErroMensagem(erro);
            p.setProximaTentativa(proximaTentativa);
            p.setAtualizadoEm(agora);
            if ("PENDENTE".equals(status)) {
                p.setTentativas(p.getTentativas() + 1);
            }
            auditoriaPendenteRepository.save(p);
        });
    }

    private long obterLimiteMensal(UUID escritorioId) {
        if (escritorioId == null) return 5000L;
        return escritorioRepository.findById(escritorioId)
                .map(Escritorio::getPlanoId)
                .map(plano -> switch (plano.toLowerCase()) {
                    case "plano-essencial", "basico" -> 1000L;
                    case "plano-escritorio-plus", "avancado", "plus" -> 20000L;
                    default -> 5000L; // plano-escritorio / intermediario
                })
                .orElse(5000L);
    }

    private ReformaLote obterOuCriarLoteContinua(UUID escritorioId, UUID empresaId, String competencia, String versaoCalculadora) {
        return reformaLoteRepository.findFirstByEscritorioIdAndEmpresaIdAndCompetenciaAndOrigemOrderByCriadoEmDesc(
                escritorioId, empresaId, competencia, "CONTINUA"
        ).orElseGet(() -> {
            ReformaLote novo = new ReformaLote();
            novo.setEscritorioId(escritorioId);
            novo.setEmpresaId(empresaId);
            novo.setCompetencia(competencia);
            novo.setOrigem("CONTINUA");
            novo.setVersaoCalculadora(versaoCalculadora);
            novo.setTotalNotas(0);
            novo.setTotalItens(0);
            novo.setTotalOk(0);
            novo.setTotalDivergencias(0);
            novo.setTotalErros(0);
            novo.setTotalSemDestaque(0);
            novo.setValorTotalItens(BigDecimal.ZERO);
            novo.setValorTotalCbs(BigDecimal.ZERO);
            novo.setValorTotalIbs(BigDecimal.ZERO);
            return reformaLoteRepository.save(novo);
        });
    }

    /**
     * Reconferência solicitada pelo usuário para uma empresa e mês específicos.
     */
    @Transactional
    public Map<String, Object> reconferirMes(UUID escritorioId, UUID empresaId, String anoMes) {
        Optional<ReformaLote> optLote = reformaLoteRepository.findFirstByEscritorioIdAndEmpresaIdAndCompetenciaAndOrigemOrderByCriadoEmDesc(
                escritorioId, empresaId, anoMes, "CONTINUA"
        );

        if (optLote.isPresent()) {
            ReformaLote lote = optLote.get();
            reformaItemRepository.deleteByLoteIdAndEscritorioId(lote.getId(), escritorioId);
            reformaLoteRepository.delete(lote);
        }

        List<DocumentoFiscal> docs = documentoFiscalRepository.findByEscritorioIdAndEmpresaIdAndCompetencia(
                escritorioId, empresaId, anoMes
        );

        int reenfileirados = 0;
        for (DocumentoFiscal doc : docs) {
            AuditoriaPendente pendente = auditoriaPendenteRepository
                    .findByEscritorioIdAndDocumentoId(escritorioId, doc.getId())
                    .orElseGet(() -> {
                        AuditoriaPendente p = new AuditoriaPendente();
                        p.setEscritorioId(escritorioId);
                        p.setDocumento(doc);
                        return p;
                    });

            pendente.setStatus("PENDENTE");
            pendente.setTentativas(0);
            pendente.setProximaTentativa(null);
            pendente.setErroMensagem("Reconferência solicitada pelo usuário.");
            pendente.setAtualizadoEm(OffsetDateTime.now());
            auditoriaPendenteRepository.save(pendente);
            reenfileirados++;
        }

        // Processa imediatamente o primeiro lote
        processarPendentes(escritorioId, 50);

        return Map.of(
                "empresaId", empresaId.toString(),
                "anoMes", anoMes,
                "documentosReenfileirados", reenfileirados,
                "mensagem", String.format("Reconferência iniciada para %d documentos da competência %s.", reenfileirados, anoMes)
        );
    }
}

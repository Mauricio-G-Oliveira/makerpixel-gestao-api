package com.makerpixel.contabil.service;

import com.makerpixel.contabil.domain.*;
import com.makerpixel.contabil.reforma.CalculadoraRtcClient;
import com.makerpixel.contabil.reforma.LeitorNfeIbsCbs;
import com.makerpixel.contabil.reforma.LeitorNfeIbsCbs.ItemNotaIbsCbs;
import com.makerpixel.contabil.reforma.LeitorNfeIbsCbs.NotaFiscalIbsCbs;
import com.makerpixel.contabil.reforma.dto.*;
import com.makerpixel.contabil.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class AuditoriaContinuaWorkerService {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaContinuaWorkerService.class);
    private static final BigDecimal TOLERANCIA = new BigDecimal("0.01");
    private static final int MAX_TENTATIVAS = 5;
    private static final int LIMITE_PADRAO_PLANO_MENSAL = 5000;

    private final AuditoriaPendenteRepository auditoriaPendenteRepository;
    private final DocumentoFiscalRepository documentoFiscalRepository;
    private final ReformaLoteRepository reformaLoteRepository;
    private final ReformaItemRepository reformaItemRepository;
    private final EmpresaRepository empresaRepository;
    private final CalculadoraRtcClient calculadoraRtcClient;
    private final SupabaseStorageService storageService;
    private final EmDiaService emDiaService;

    public AuditoriaContinuaWorkerService(
            AuditoriaPendenteRepository auditoriaPendenteRepository,
            DocumentoFiscalRepository documentoFiscalRepository,
            ReformaLoteRepository reformaLoteRepository,
            ReformaItemRepository reformaItemRepository,
            EmpresaRepository empresaRepository,
            CalculadoraRtcClient calculadoraRtcClient,
            SupabaseStorageService storageService,
            EmDiaService emDiaService
    ) {
        this.auditoriaPendenteRepository = auditoriaPendenteRepository;
        this.documentoFiscalRepository = documentoFiscalRepository;
        this.reformaLoteRepository = reformaLoteRepository;
        this.reformaItemRepository = reformaItemRepository;
        this.empresaRepository = empresaRepository;
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
     * Pode ser invocado pelo Cron agendado ou pelo botão "Conferir pendentes agora".
     */
    @Transactional
    public ResultadoCicloAuditoria processarPendentes(UUID escritorioIdFiltro, int tamanhoLote) {
        OffsetDateTime agora = OffsetDateTime.now();
        List<AuditoriaPendente> pendentes = escritorioIdFiltro != null
                ? auditoriaPendenteRepository.findPendentesPorEscritorio(escritorioIdFiltro, agora, PageRequest.of(0, tamanhoLote))
                : auditoriaPendenteRepository.findPendentesParaProcessar(agora, PageRequest.of(0, tamanhoLote));

        if (pendentes.isEmpty()) {
            return new ResultadoCicloAuditoria(0, 0, 0, 0, 0, 0);
        }

        VersaoCalculadoraDto versaoRtc = calculadoraRtcClient.obterVersao();
        String versaoCalculadora = versaoRtc != null && versaoRtc.versaoMotor() != null
                ? versaoRtc.versaoMotor()
                : "v1.0.0-embutido";

        int conferidos = 0;
        int ignorados = 0;
        int reagendados = 0;
        int erros = 0;
        int limiteAtingido = 0;

        for (AuditoriaPendente pendente : pendentes) {
            try {
                DocumentoFiscal doc = pendente.getDocumento();
                if (doc == null) {
                    pendente.setStatus("ERRO");
                    pendente.setMensagemErro("Documento fiscal não encontrado.");
                    auditoriaPendenteRepository.save(pendente);
                    erros++;
                    continue;
                }

                // 1. Documento cancelado não precisa de cálculo
                if ("CANCELADA".equalsIgnoreCase(doc.getSituacao())) {
                    pendente.setStatus("CONCLUIDO");
                    pendente.setProcessadoEm(agora);
                    pendente.setMensagemErro(null);
                    auditoriaPendenteRepository.save(pendente);
                    conferidos++;
                    continue;
                }

                // 2. Idempotência por chave e versão da calculadora
                boolean jaAuditado = reformaItemRepository.existsByEscritorioIdAndChaveNfeAndVersaoCalculadora(
                        doc.getEscritorioId(), doc.getChaveAcesso(), versaoCalculadora
                );
                if (jaAuditado) {
                    pendente.setStatus("CONCLUIDO");
                    pendente.setProcessadoEm(agora);
                    pendente.setMensagemErro(null);
                    auditoriaPendenteRepository.save(pendente);
                    ignorados++;
                    continue;
                }

                // 3. Obter XML
                InputStream isXml = null;
                if (doc.getConteudoXml() != null && !doc.getConteudoXml().isBlank()) {
                    isXml = new ByteArrayInputStream(doc.getConteudoXml().getBytes(StandardCharsets.UTF_8));
                } else if (doc.getStoragePath() != null && !doc.getStoragePath().isBlank()) {
                    byte[] bytes = storageService.baixarArquivo(doc.getStoragePath());
                    if (bytes != null && bytes.length > 0) {
                        isXml = new ByteArrayInputStream(bytes);
                    }
                }

                if (isXml == null) {
                    pendente.setTentativas(pendente.getTentativas() + 1);
                    if (pendente.getTentativas() >= MAX_TENTATIVAS) {
                        pendente.setStatus("ERRO");
                        pendente.setMensagemErro("XML não localizado no storage privado.");
                        erros++;
                    } else {
                        pendente.setProximaTentativa(agora.plusMinutes(5L * pendente.getTentativas()));
                        pendente.setMensagemErro("Aguardando disponibilidade do XML.");
                        reagendados++;
                    }
                    auditoriaPendenteRepository.save(pendente);
                    continue;
                }

                // 4. Parse do XML
                NotaFiscalIbsCbs nota = LeitorNfeIbsCbs.lerXml(isXml);
                if (nota == null || nota.itens() == null || nota.itens().isEmpty()) {
                    pendente.setStatus("CONCLUIDO");
                    pendente.setProcessadoEm(agora);
                    pendente.setMensagemErro("Nota sem itens identificados para auditoria.");
                    auditoriaPendenteRepository.save(pendente);
                    conferidos++;
                    continue;
                }

                // 5. Localiza ou cria lote de Auditoria Contínua para empresa e competência
                String competencia = doc.getCompetencia() != null ? doc.getCompetencia() : agora.toString().substring(0, 7);
                ReformaLote lote = obterOuCriarLoteContinua(doc.getEscritorioId(), doc.getEmpresaId(), competencia, versaoCalculadora);

                // 6. Chamada à Calculadora RTC com Observabilidade
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

                CalculoRtcResponse respCalculo = calculadoraRtcClient.calcularComObservabilidade(op);
                Map<Integer, CalculoItemRtcResponse> mapaCalculados = new HashMap<>();
                if (respCalculo != null && respCalculo.getItens() != null) {
                    for (CalculoItemRtcResponse ci : respCalculo.getItens()) {
                        mapaCalculados.put(ci.getNumeroItem(), ci);
                    }
                }

                // 7. Persiste os itens auditados
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
                        ri.setMotivoDivergencia("Motor RTC indisponível para cálculo.");
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

                // 8. Atualiza contadores do lote
                lote.setTotalNotas(lote.getTotalNotas() + 1);
                lote.setTotalItens(lote.getTotalItens() + novosItens);
                lote.setTotalOk(lote.getTotalOk() + novosOk);
                lote.setTotalDivergencias(lote.getTotalDivergencias() + novasDivergencias);
                lote.setTotalSemDestaque(lote.getTotalSemDestaque() + novosSemGrupo);
                lote.setTotalErros(lote.getTotalErros() + novosErros);
                reformaLoteRepository.save(lote);

                // 9. Atualiza tarefa no Em Dia se houver divergências
                if (lote.getTotalDivergencias() > 0 && doc.getEmpresaId() != null) {
                    Empresa emp = empresaRepository.findById(doc.getEmpresaId()).orElse(null);
                    String razao = emp != null ? emp.getRazaoSocial() : "Empresa";
                    emDiaService.criarOuAtualizarTarefaDivergenciaContinua(
                            doc.getEscritorioId(), doc.getEmpresaId(), razao, competencia, lote.getTotalDivergencias()
                    );
                }

                // 10. Conclui a pendência
                pendente.setStatus("CONCLUIDO");
                pendente.setProcessadoEm(agora);
                pendente.setMensagemErro(null);
                auditoriaPendenteRepository.save(pendente);
                conferidos++;

            } catch (Exception e) {
                log.error("Falha ao auditar documento pendente id: {}", pendente.getId(), e);
                pendente.setTentativas(pendente.getTentativas() + 1);
                if (pendente.getTentativas() >= MAX_TENTATIVAS) {
                    pendente.setStatus("ERRO");
                    pendente.setMensagemErro("Erro no motor de cálculo: " + e.getMessage());
                    erros++;
                } else {
                    pendente.setProximaTentativa(agora.plusMinutes(5L * pendente.getTentativas()));
                    pendente.setMensagemErro(e.getMessage());
                    reagendados++;
                }
                auditoriaPendenteRepository.save(pendente);
            }
        }

        return new ResultadoCicloAuditoria(
                pendentes.size(), conferidos, ignorados, reagendados, erros, limiteAtingido
        );
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
     * Reconferência solicitada pelo usuário para uma empresa e mês específicos (por exemplo, após atualização da regra).
     */
    @Transactional
    public Map<String, Object> reconferirMes(UUID escritorioId, UUID empresaId, String anoMes) {
        // Remove itens do lote contínuo anterior da empresa naquele mês
        Optional<ReformaLote> optLote = reformaLoteRepository.findFirstByEscritorioIdAndEmpresaIdAndCompetenciaAndOrigemOrderByCriadoEmDesc(
                escritorioId, empresaId, anoMes, "CONTINUA"
        );

        if (optLote.isPresent()) {
            ReformaLote lote = optLote.get();
            reformaItemRepository.deleteByLoteIdAndEscritorioId(lote.getId(), escritorioId);
            reformaLoteRepository.delete(lote);
        }

        // Busca todos os documentos fiscais da empresa no mês e recria pendências
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
            pendente.setProcessadoEm(null);
            pendente.setMensagemErro("Reconferência solicitada pelo usuário.");
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

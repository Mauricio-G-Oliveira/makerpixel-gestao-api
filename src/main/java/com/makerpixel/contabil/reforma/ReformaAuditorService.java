package com.makerpixel.contabil.reforma;

import com.makerpixel.contabil.domain.*;
import com.makerpixel.contabil.reforma.LeitorNfeIbsCbs.ItemNotaIbsCbs;
import com.makerpixel.contabil.reforma.LeitorNfeIbsCbs.NotaFiscalIbsCbs;
import com.makerpixel.contabil.reforma.dto.*;
import com.makerpixel.contabil.repository.*;
import com.makerpixel.contabil.service.SupabaseStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class ReformaAuditorService {

    private static final Logger log = LoggerFactory.getLogger(ReformaAuditorService.class);
    private static final BigDecimal TOLERANCIA = new BigDecimal("0.01");

    private final ReformaLoteRepository loteRepository;
    private final ReformaItemRepository itemRepository;
    private final NotasyncDocumentoRepository notasyncDocumentoRepository;
    private final SupabaseStorageService storageService;
    private final CalculadoraRtcClient calculadoraRtcClient;

    public ReformaAuditorService(
            ReformaLoteRepository loteRepository,
            ReformaItemRepository itemRepository,
            NotasyncDocumentoRepository notasyncDocumentoRepository,
            SupabaseStorageService storageService,
            CalculadoraRtcClient calculadoraRtcClient
    ) {
        this.loteRepository = loteRepository;
        this.itemRepository = itemRepository;
        this.notasyncDocumentoRepository = notasyncDocumentoRepository;
        this.storageService = storageService;
        this.calculadoraRtcClient = calculadoraRtcClient;
    }

    /**
     * Processa arquivos XMLs ou ZIPs enviados via upload multipart.
     */
    @Transactional
    public ReformaLote processarUpload(UUID escritorioId, UUID empresaId, String competencia, List<MultipartFile> arquivos) {
        List<NotaFiscalIbsCbs> notas = new ArrayList<>();
        String origem = "UPLOAD_XML";

        for (MultipartFile file : arquivos) {
            String nome = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
            try (InputStream is = file.getInputStream()) {
                if (nome.endsWith(".zip")) {
                    origem = "UPLOAD_ZIP";
                    notas.addAll(LeitorNfeIbsCbs.lerZip(is));
                } else if (nome.endsWith(".xml")) {
                    NotaFiscalIbsCbs nf = LeitorNfeIbsCbs.lerXml(is);
                    if (nf.itens() != null && !nf.itens().isEmpty()) {
                        notas.add(nf);
                    }
                }
            } catch (Exception e) {
                log.error("Erro ao ler arquivo {} no upload da Reforma: {}", nome, e.getMessage());
            }
        }

        return executarAuditoriaELog(escritorioId, empresaId, competencia, origem, notas);
    }

    /**
     * Processa notas fiscais já armazenadas e sincronizadas pelo NotaSync para a empresa e competência.
     */
    @Transactional
    public ReformaLote processarDoNotasync(UUID escritorioId, UUID empresaId, String competencia) {
        List<NotasyncDocumento> docs = notasyncDocumentoRepository.findByEscritorioIdAndEmpresaIdAndCompetencia(
                escritorioId, empresaId, competencia
        );

        List<NotaFiscalIbsCbs> notas = new ArrayList<>();
        for (NotasyncDocumento doc : docs) {
            try {
                byte[] bytesXml = null;
                if (doc.getCaminhoStorage() != null && !doc.getCaminhoStorage().isEmpty()) {
                    bytesXml = storageService.baixarArquivo(doc.getCaminhoStorage());
                }
                if (bytesXml != null) {
                    NotaFiscalIbsCbs nf = LeitorNfeIbsCbs.lerXml(new ByteArrayInputStream(bytesXml));
                    if (nf.itens() != null && !nf.itens().isEmpty()) {
                        notas.add(nf);
                    }
                }
            } catch (Exception e) {
                log.debug("Nota {} do NotaSync não pôde ser lida: {}", doc.getChaveAcesso(), e.getMessage());
            }
        }

        return executarAuditoriaELog(escritorioId, empresaId, competencia, "NOTASYNC", notas);
    }

    /**
     * Executa a auditoria item a item, compara com a Calculadora RTC e persiste o lote e itens.
     */
    private ReformaLote executarAuditoriaELog(UUID escritorioId, UUID empresaId, String competencia, String origem, List<NotaFiscalIbsCbs> notas) {
        ReformaLote lote = new ReformaLote();
        lote.setEscritorioId(escritorioId);
        lote.setEmpresaId(empresaId);
        lote.setCompetencia(competencia != null ? competencia : OffsetDateTime.now().toString().substring(0, 7));
        lote.setOrigem(origem);
        lote.setTotalNotas(notas.size());

        lote = loteRepository.save(lote);

        int totalItens = 0;
        int totalOk = 0;
        int totalDivergencias = 0;
        int totalErros = 0;
        int totalSemDestaque = 0;

        BigDecimal somaItens = BigDecimal.ZERO;
        BigDecimal somaCbs = BigDecimal.ZERO;
        BigDecimal somaIbs = BigDecimal.ZERO;

        for (NotaFiscalIbsCbs nota : notas) {
            // Monta requisição para a Calculadora RTC
            OperacaoRtcRequest op = new OperacaoRtcRequest();
            op.setIdOperacao(nota.chaveAcesso());
            op.setDataEmissao(nota.dataEmissao() != null ? nota.dataEmissao().toString() : OffsetDateTime.now().toString());
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

            for (ItemNotaIbsCbs item : nota.itens()) {
                totalItens++;
                somaItens = somaItens.add(item.valorItem());
                somaCbs = somaCbs.add(item.valorCbsInformado());
                somaIbs = somaIbs.add(item.valorIbsTotalInformado());

                ReformaItem ri = new ReformaItem();
                ri.setLote(lote);
                ri.setEscritorioId(escritorioId);
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
                    ri.setMotivoDivergencia("Nota fiscal emitida sem o grupo de tributação IBS/CBS.");
                    if (calc != null) {
                        ri.setCbsCalculado(calc.getValorCbs() != null ? calc.getValorCbs() : BigDecimal.ZERO);
                        ri.setIbsUfCalculado(calc.getValorIbsUf() != null ? calc.getValorIbsUf() : BigDecimal.ZERO);
                        ri.setIbsMunCalculado(calc.getValorIbsMun() != null ? calc.getValorIbsMun() : BigDecimal.ZERO);
                        ri.setCstCalculado(calc.getCstCalculado());
                        ri.setMemoriaCalculo(calc.getMemoriaCalculo());
                        ri.setFundamentacaoLegal(calc.getFundamentacaoLegal());
                        BigDecimal totCalc = ri.getCbsCalculado().add(ri.getIbsUfCalculado()).add(ri.getIbsMunCalculado());
                        ri.setDiferencaTotal(totCalc);
                    }
                    totalSemDestaque++;
                } else if (calc == null) {
                    ri.setClassificacao("ERRO_CALCULO");
                    ri.setMotivoDivergencia("Motor RTC indisponível para cálculo do item.");
                    totalErros++;
                } else if ("INCONSISTENCIA_ENTRADA".equals(calc.getStatus())) {
                    ri.setClassificacao("ENQUADRAMENTO_INVALIDO");
                    ri.setMotivoDivergencia(calc.getMensagemInconsistencia());
                    ri.setCstCalculado(calc.getCstCalculado());
                    ri.setFundamentacaoLegal(calc.getFundamentacaoLegal());
                    ri.setMemoriaCalculo(calc.getMemoriaCalculo());
                    totalErros++;
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
                        totalOk++;
                    } else {
                        ri.setClassificacao("DIVERGENCIA_VALOR");
                        ri.setMotivoDivergencia(String.format("Diferença de cálculo: R$ %s (CBS: %s x %s, IBS UF: %s x %s, IBS Mun: %s x %s)",
                                difTotal, item.valorCbsInformado(), cbsCalc, item.valorIbsUfInformado(), ibsUfCalc, item.valorIbsMunInformado(), ibsMunCalc));
                        totalDivergencias++;
                    }
                }

                itemRepository.save(ri);
            }
        }

        lote.setTotalItens(totalItens);
        lote.setTotalOk(totalOk);
        lote.setTotalDivergencias(totalDivergencias);
        lote.setTotalErros(totalErros);
        lote.setTotalSemDestaque(totalSemDestaque);
        lote.setValorTotalItens(somaItens);
        lote.setValorTotalCbs(somaCbs);
        lote.setValorTotalIbs(somaIbs);
        lote.setAtualizadoEm(OffsetDateTime.now());

        return loteRepository.save(lote);
    }

    public List<ReformaLote> listarLotes(UUID escritorioId) {
        return loteRepository.findByEscritorioIdOrderByCriadoEmDesc(escritorioId);
    }

    public Optional<ReformaLote> obterLote(UUID escritorioId, UUID loteId) {
        return loteRepository.findByIdAndEscritorioId(loteId, escritorioId);
    }

    public Page<ReformaItem> listarItens(UUID escritorioId, UUID loteId, String classificacao, Pageable pageable) {
        if (classificacao != null && !classificacao.isEmpty() && !"TODOS".equalsIgnoreCase(classificacao)) {
            return itemRepository.findByLoteIdAndEscritorioIdAndClassificacao(loteId, escritorioId, classificacao, pageable);
        }
        return itemRepository.findByLoteIdAndEscritorioId(loteId, escritorioId, pageable);
    }

    /**
     * Gera CSV com o relatório de divergências e inconsistências para envio ao cliente.
     */
    public byte[] gerarCsvDivergencias(UUID escritorioId, UUID loteId) {
        List<ReformaItem> itens = itemRepository.findByLoteIdAndEscritorioIdOrderByNItemAsc(loteId, escritorioId);
        StringBuilder sb = new StringBuilder();
        sb.append("Numero Nota;Chave NFe;Item;Codigo;Descricao;NCM;CFOP;CST Informado;CST Calculado;cClassTrib;Valor Item;CBS Destacado;CBS Oficial;IBS Destacado;IBS Oficial;Diferenca;Classificacao;Motivo / Memoria de Calculo\n");

        for (ReformaItem i : itens) {
            BigDecimal ibsDest = i.getIbsUfDestacado().add(i.getIbsMunDestacado());
            BigDecimal ibsCalc = i.getIbsUfCalculado().add(i.getIbsMunCalculado());
            sb.append(String.format("\"%s\";\"%s\";%d;\"%s\";\"%s\";\"%s\";\"%s\";\"%s\";\"%s\";\"%s\";%s;%s;%s;%s;%s;%s;\"%s\";\"%s\"\n",
                    i.getNumeroNota(),
                    i.getChaveNfe(),
                    i.getnItem(),
                    i.getCodigoProduto() != null ? i.getCodigoProduto() : "",
                    i.getDescricao().replace("\"", "\"\""),
                    i.getNcm() != null ? i.getNcm() : "",
                    i.getCfop() != null ? i.getCfop() : "",
                    i.getCstInformado() != null ? i.getCstInformado() : "",
                    i.getCstCalculado() != null ? i.getCstCalculado() : "",
                    i.getcClassTrib() != null ? i.getcClassTrib() : "",
                    i.getValorItem(),
                    i.getCbsDestacado(),
                    i.getCbsCalculado(),
                    ibsDest,
                    ibsCalc,
                    i.getDiferencaTotal(),
                    i.getClassificacao(),
                    (i.getMotivoDivergencia() != null ? i.getMotivoDivergencia() : "").replace("\"", "\"\"")
            ));
        }

        return sb.toString().getBytes(StandardCharsets.ISO_8859_1);
    }
}

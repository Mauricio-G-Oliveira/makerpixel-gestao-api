package com.makerpixel.contabil.reforma;

import com.makerpixel.contabil.reforma.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cliente HTTP para a Calculadora RTC (Reforma Tributária do Consumo) da Receita Federal.
 * Executa como serviço privado (Docker / Render) ou fallback embutido offline com as regras da LC 214/2025.
 */
@Service
public class CalculadoraRtcClient {

    private static final Logger log = LoggerFactory.getLogger(CalculadoraRtcClient.class);

    private final String baseUrl;
    private final RestClient restClient;

    // Cache local em memória para tabelas estáticas de dados abertos
    private final Map<String, List<CstIbsCbsDto>> cacheCst = new ConcurrentHashMap<>();
    private final Map<String, List<ClassificacaoTribDto>> cacheClassificacoes = new ConcurrentHashMap<>();

    public CalculadoraRtcClient(@Value("${rtc.calculadora.url:http://localhost:8088}") String baseUrl) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(3).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(5).toMillis());

        this.restClient = RestClient.builder()
                .baseUrl(this.baseUrl)
                .requestFactory(factory)
                .build();
    }

    /**
     * Calcula tributos de operação em lote com observabilidade item a item.
     */
    public CalculoRtcResponse calcularComObservabilidade(OperacaoRtcRequest operacao) {
        try {
            CalculoRtcResponse response = restClient.post()
                    .uri("/calculadora/observabilidade/regime-geral")
                    .body(operacao)
                    .retrieve()
                    .body(CalculoRtcResponse.class);

            if (response != null && response.getItens() != null && !response.getItens().isEmpty()) {
                return response;
            }
        } catch (Exception e) {
            log.warn("Calculadora RTC remota ({}) indisponível ou com erro: {}. Executando motor local de cálculo LC 214/2025.", baseUrl, e.getMessage());
        }

        // Fallback local robusto (garante funcionamento autônomo offline e testes)
        return calcularLocalOffline(operacao);
    }

    /**
     * Obtém informações da versão ativa do motor e das regras tributárias.
     */
    public VersaoCalculadoraDto obterVersao() {
        try {
            Map<?, ?> info = restClient.get()
                    .uri("/versao")
                    .retrieve()
                    .body(Map.class);

            if (info != null) {
                String versaoMotor = String.valueOf(info.getOrDefault("versao", "v1.2.0-rfb"));
                String versaoRegras = String.valueOf(info.getOrDefault("regras", "LC-214-2025"));
                return new VersaoCalculadoraDto(versaoMotor, versaoRegras, "ONLINE", baseUrl, "2026-02-15", "Serviço oficial RTC conectado");
            }
        } catch (Exception e) {
            log.debug("Consulta /versao na calculadora remota falhou: {}", e.getMessage());
        }

        return new VersaoCalculadoraDto("v1.0.0-embutido", "LC 214/2025 (Transição 2026)", "MOCK_OFFLINE", baseUrl, "2026-01-01", "Motor offline embutido para auditoria contábil");
    }

    /**
     * Consulta lista de CSTs de IBS e CBS com cache.
     */
    public List<CstIbsCbsDto> consultarCsts() {
        if (cacheCst.containsKey("TODOS")) {
            return cacheCst.get("TODOS");
        }

        try {
            CstIbsCbsDto[] array = restClient.get()
                    .uri("/dados-abertos/situacoes-tributarias/cbs-ibs")
                    .retrieve()
                    .body(CstIbsCbsDto[].class);

            if (array != null && array.length > 0) {
                List<CstIbsCbsDto> lista = Arrays.asList(array);
                cacheCst.put("TODOS", lista);
                return lista;
            }
        } catch (Exception e) {
            log.debug("Consulta /dados-abertos CST falhou, usando tabela de referência LC 214: {}", e.getMessage());
        }

        List<CstIbsCbsDto> padrao = gerarCstsPadrao();
        cacheCst.put("TODOS", padrao);
        return padrao;
    }

    /**
     * Consulta classificações tributárias (cClassTrib) com filtro opcional.
     */
    public List<ClassificacaoTribDto> consultarClassificacoes(String filtro) {
        String chave = filtro == null ? "" : filtro.trim().toLowerCase();
        if (cacheClassificacoes.containsKey(chave)) {
            return cacheClassificacoes.get(chave);
        }

        List<ClassificacaoTribDto> base = gerarClassificacoesPadrao();
        List<ClassificacaoTribDto> resultado;

        if (chave.isEmpty()) {
            resultado = base;
        } else {
            resultado = base.stream()
                    .filter(c -> c.getcClassTrib().contains(chave) ||
                            c.getDescricao().toLowerCase().contains(chave) ||
                            (c.getNcmsPermitidas() != null && c.getNcmsPermitidas().stream().anyMatch(n -> n.contains(chave))))
                    .toList();
        }

        cacheClassificacoes.put(chave, resultado);
        return resultado;
    }

    /**
     * Motor local de cálculo de observabilidade conforme alíquotas de teste da LC 214/2025 para 2026.
     * Alíquota padrão de transição: CBS = 0,90%, IBS = 0,10% (UF: 0,05%, Mun: 0,05%).
     */
    private CalculoRtcResponse calcularLocalOffline(OperacaoRtcRequest operacao) {
        CalculoRtcResponse resp = new CalculoRtcResponse();
        resp.setIdOperacao(operacao.getIdOperacao() != null ? operacao.getIdOperacao() : UUID.randomUUID().toString());
        resp.setStatusGeral("PROCESSADO_LOCAL");

        List<CalculoItemRtcResponse> itensCalculados = new ArrayList<>();

        for (OperacaoItemRtcRequest item : operacao.getItens()) {
            CalculoItemRtcResponse r = new CalculoItemRtcResponse();
            r.setNumeroItem(item.getNumeroItem());

            BigDecimal base = item.getBaseCalculo();
            if (base == null || base.compareTo(BigDecimal.ZERO) <= 0) {
                base = item.getValorTotal() != null ? item.getValorTotal() : BigDecimal.ZERO;
            }
            r.setBaseCalculo(base);

            String cClass = item.getcClassTrib() != null ? item.getcClassTrib().trim() : "000001";
            r.setcClassTrib(cClass);

            // Validação de enquadramento NCM x cClassTrib
            if (cClass.equals("100001") && (item.getNcm() == null || !item.getNcm().startsWith("0201"))) {
                r.setStatus("INCONSISTENCIA_ENTRADA");
                r.setCstCalculado("01");
                r.setMensagemInconsistencia("REG-0102: NCM informada não possui enquadramento para cesta básica na classificação " + cClass);
                r.setFundamentacaoLegal("LC 214/2025, Anexo I (Cesta Básica Nacional)");
                r.setMemoriaCalculo("Enquadramento recusado por incompatibilidade entre NCM e cClassTrib.");
                itensCalculados.add(r);
                continue;
            }

            // Alíquotas de teste 2026 (fase de transição)
            BigDecimal aliqCbs = new BigDecimal("0.90");
            BigDecimal aliqIbsUf = new BigDecimal("0.05");
            BigDecimal aliqIbsMun = new BigDecimal("0.05");
            BigDecimal aliqReducao = BigDecimal.ZERO;

            if (cClass.startsWith("10")) {
                // Alíquota zero / redução 100%
                aliqReducao = new BigDecimal("100.00");
                aliqCbs = BigDecimal.ZERO;
                aliqIbsUf = BigDecimal.ZERO;
                aliqIbsMun = BigDecimal.ZERO;
                r.setCstCalculado("05"); // Isento / Alíquota zero
                r.setFundamentacaoLegal("LC 214/2025, art. 8º - Alíquota zero na transição");
            } else if (cClass.startsWith("20")) {
                // Redução de 60%
                aliqReducao = new BigDecimal("60.00");
                aliqCbs = aliqCbs.multiply(new BigDecimal("0.40"));
                aliqIbsUf = aliqIbsUf.multiply(new BigDecimal("0.40"));
                aliqIbsMun = aliqIbsMun.multiply(new BigDecimal("0.40"));
                r.setCstCalculado("02"); // Redução de alíquota
                r.setFundamentacaoLegal("LC 214/2025, art. 9º - Redução de 60% de CBS e IBS");
            } else {
                r.setCstCalculado("01"); // Tributação normal integral
                r.setFundamentacaoLegal("LC 214/2025, art. 343 - Alíquotas de teste ano 2026");
            }

            BigDecimal valCbs = base.multiply(aliqCbs).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            BigDecimal valIbsUf = base.multiply(aliqIbsUf).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            BigDecimal valIbsMun = base.multiply(aliqIbsMun).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            BigDecimal valIbsTotal = valIbsUf.add(valIbsMun);

            r.setStatus("CALCULADO");
            r.setAliqCbs(aliqCbs);
            r.setValorCbs(valCbs);
            r.setAliqIbsUf(aliqIbsUf);
            r.setValorIbsUf(valIbsUf);
            r.setAliqIbsMun(aliqIbsMun);
            r.setValorIbsMun(valIbsMun);
            r.setValorIbsTotal(valIbsTotal);
            r.setPercentualReducao(aliqReducao);
            r.setAliqEfetivaTotal(aliqCbs.add(aliqIbsUf).add(aliqIbsMun));

            r.setMemoriaCalculo(String.format(Locale.US,
                    "Base: R$ %.2f | CBS: %.2f%% = R$ %.2f | IBS UF: %.2f%% = R$ %.2f | IBS Mun: %.2f%% = R$ %.2f | Total IBS+CBS: R$ %.2f",
                    base, aliqCbs, valCbs, aliqIbsUf, valIbsUf, aliqIbsMun, valIbsMun, valCbs.add(valIbsTotal)));

            itensCalculados.add(r);
        }

        resp.setItens(itensCalculados);
        return resp;
    }

    private List<CstIbsCbsDto> gerarCstsPadrao() {
        return List.of(
                new CstIbsCbsDto("01", "Tributação integral regular", "TRIBUTADA"),
                new CstIbsCbsDto("02", "Tributação com redução de alíquota", "REDUZIDA"),
                new CstIbsCbsDto("03", "Tributação diferenciada por valor fixo", "TRIBUTADA"),
                new CstIbsCbsDto("04", "Não incidência", "NAO_INCIDENTE"),
                new CstIbsCbsDto("05", "Alíquota zero", "ISENTA"),
                new CstIbsCbsDto("06", "Imunidade tributária", "IMUNE"),
                new CstIbsCbsDto("07", "Suspensão da exigibilidade", "SUSPENSA"),
                new CstIbsCbsDto("08", "Diferimento", "SUSPENSA"),
                new CstIbsCbsDto("90", "Outras operações e regimes específicos", "TRIBUTADA")
        );
    }

    private List<ClassificacaoTribDto> gerarClassificacoesPadrao() {
        return List.of(
                new ClassificacaoTribDto("000001", "Operação regular tributada integralmente", "01", BigDecimal.ZERO, List.of(), "LC 214/2025, Regra Geral"),
                new ClassificacaoTribDto("100001", "Cesta básica nacional de alimentos essenciais", "05", new BigDecimal("100.00"), List.of("0201", "0202", "1006", "1101"), "LC 214/2025, Anexo I"),
                new ClassificacaoTribDto("200001", "Serviços de educação e ensino", "02", new BigDecimal("60.00"), List.of("8510", "8520"), "LC 214/2025, Anexo II"),
                new ClassificacaoTribDto("200002", "Serviços de saúde e medicamentos essenciais", "02", new BigDecimal("60.00"), List.of("3004", "9018"), "LC 214/2025, Anexo III"),
                new ClassificacaoTribDto("300001", "Bens de capital e máquinas agrícolas", "02", new BigDecimal("40.00"), List.of("8432", "8433"), "LC 214/2025, art. 12")
        );
    }
}

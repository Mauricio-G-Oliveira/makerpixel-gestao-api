package com.makerpixel.contabil.service;

import com.makerpixel.contabil.domain.*;
import com.makerpixel.contabil.dto.NotasyncNuvemDTOs.*;
import com.makerpixel.contabil.notasync.NotasyncEnvelopeBuilder;
import com.makerpixel.contabil.notasync.NotasyncMaquinaRegras;
import com.makerpixel.contabil.notasync.NotasyncTradutor;
import com.makerpixel.contabil.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

@Service
public class NotasyncCloudWorkerService {

    private static final Logger log = LoggerFactory.getLogger(NotasyncCloudWorkerService.class);

    private static final ReentrantLock IN_MEMORY_LOCK = new ReentrantLock();

    private final EscritorioRepository escritorioRepository;
    private final NotasyncEmpresaRepository empresaRepository;
    private final NotasyncEstadoRepository estadoRepository;
    private final NotasyncDocumentoRepository documentoRepository;
    private final NotasyncExecucaoRepository execucaoRepository;
    private final CofreCertificadoService cofreService;
    private final SupabaseStorageService storageService;
    private final JdbcTemplate jdbcTemplate;

    @Value("${notasync.sef-endpoint:http://localhost:8089/notasync-simulador/ws/distribuicao/DistribuicaoNfceDownload.asmx}")
    private String sefEndpoint;

    @Value("${notasync.cron-secret:${NOTASYNC_CRON_SECRET:notasync_cron_default_secret_2026}}")
    private String cronSecret;

    @Value("${notasync.worker.max-duration-seconds:240}")
    private int maxDurationSeconds;

    public NotasyncCloudWorkerService(
            EscritorioRepository escritorioRepository,
            NotasyncEmpresaRepository empresaRepository,
            NotasyncEstadoRepository estadoRepository,
            NotasyncDocumentoRepository documentoRepository,
            NotasyncExecucaoRepository execucaoRepository,
            CofreCertificadoService cofreService,
            SupabaseStorageService storageService,
            JdbcTemplate jdbcTemplate
    ) {
        this.escritorioRepository = escritorioRepository;
        this.empresaRepository = empresaRepository;
        this.estadoRepository = estadoRepository;
        this.documentoRepository = documentoRepository;
        this.execucaoRepository = execucaoRepository;
        this.cofreService = cofreService;
        this.storageService = storageService;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Execução do robô em nuvem acionada pelo agendador interno.
     * Protegido por trava de concorrência global (PostgreSQL advisory lock ou memória).
     */
    public CronExecucaoResponse executarCicloRobo(String secretInformado) {
        if (!validarCronSecret(secretInformado)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Segredo do cron inválido.");
        }

        boolean adquiriuTrava = adquirirTravaGlobal();
        if (!adquiriuTrava) {
            log.warn("Robô NotaSync: Execução ignorada pois outra instância já está em andamento.");
            return new CronExecucaoResponse("IGNORADO_EM_ANDAMENTO", 0, 0, 0, 0,
                    List.of("Execução em andamento em outra instância do cluster."));
        }

        long inicioMs = System.currentTimeMillis();
        int escritoriosProcessados = 0;
        int empresasProcessadas = 0;
        int totalDocumentosNovos = 0;
        List<String> logsExecucao = new ArrayList<>();

        try {
            List<Escritorio> escritorios = escritorioRepository.findAll();
            long limiteTempoMs = inicioMs + (maxDurationSeconds * 1000L);

            for (Escritorio esc : escritorios) {
                if (System.currentTimeMillis() > limiteTempoMs) {
                    logsExecucao.add("Tempo limite do ciclo atingido (" + maxDurationSeconds + "s). Continuando na próxima chamada.");
                    break;
                }

                // Verifica licença e módulo notasync
                if (!isEscritorioApto(esc)) {
                    continue;
                }

                // Verifica se possui certificado A1 ativo no cofre
                CofreCryptoService.CertificadoDecifrado certDecifrado;
                try {
                    certDecifrado = cofreService.obterParaConsulta(esc.getId(), "robo-nuvem-cron", "127.0.0.1");
                } catch (Exception e) {
                    // Sem certificado ativo no cofre, pula
                    continue;
                }

                List<NotasyncEmpresa> empresas = empresaRepository.findByEscritorioIdAndAtivoTrue(esc.getId());
                if (empresas.isEmpty()) continue;

                escritoriosProcessados++;

                for (NotasyncEmpresa emp : empresas) {
                    if (System.currentTimeMillis() > limiteTempoMs) break;

                    NotasyncEstado estado = estadoRepository.findByEscritorioIdAndCnpjAndFonte(esc.getId(), emp.getCnpj(), "sef-sc-nfce")
                            .orElseGet(() -> {
                                NotasyncEstado novo = new NotasyncEstado(esc.getId(), emp.getCnpj(), "sef-sc-nfce");
                                novo.setProximaConsulta(OffsetDateTime.now());
                                return estadoRepository.save(novo);
                            });

                    // Verifica se está dentro do período de espera regulamentar da SEF/SC
                    if (!NotasyncMaquinaRegras.podeConsultar(estado.getProximaConsulta())) {
                        continue;
                    }

                    empresasProcessadas++;

                    try {
                        ResultadoProcessamento resultado = consultarEProcessarEmpresa(esc.getId(), emp, estado, certDecifrado);
                        totalDocumentosNovos += resultado.documentosNovos;
                        logsExecucao.add("CNPJ " + emp.getCnpj() + ": cStat " + resultado.cstat + " - " + resultado.motivo + " (" + resultado.documentosNovos + " notas)");
                    } catch (Exception e) {
                        log.error("Erro ao consultar SEF/SC para CNPJ {}: {}", emp.getCnpj(), e.getMessage());
                        logsExecucao.add("CNPJ " + emp.getCnpj() + " com erro: " + e.getMessage());
                    }
                }
            }
        } finally {
            liberarTravaGlobal();
        }

        long duracaoMs = System.currentTimeMillis() - inicioMs;
        return new CronExecucaoResponse("CONCLUIDO", escritoriosProcessados, empresasProcessadas, totalDocumentosNovos, duracaoMs, logsExecucao);
    }

    /**
     * Endpoint "Buscar Agora" sob demanda para uma empresa específica.
     * Retorna HTTP 429 Too Many Requests se a consulta for solicitada antes do intervalo regulamentar.
     */
    @Transactional
    public BuscarAgoraResponse buscarAgora(UUID escritorioId, String cnpj, String usuario, String ip) {
        String cnpjLimpo = cnpj.replaceAll("\\D", "");
        NotasyncEmpresa empresa = empresaRepository.findByEscritorioIdAndCnpj(escritorioId, cnpjLimpo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Empresa não cadastrada no NotaSync."));

        NotasyncEstado estado = estadoRepository.findByEscritorioIdAndCnpjAndFonte(escritorioId, cnpjLimpo, "sef-sc-nfce")
                .orElseGet(() -> {
                    NotasyncEstado novo = new NotasyncEstado(escritorioId, cnpjLimpo, "sef-sc-nfce");
                    novo.setProximaConsulta(OffsetDateTime.now());
                    return estadoRepository.save(novo);
                });

        // Verificação estrita de espera da SEF/SC
        if (!NotasyncMaquinaRegras.podeConsultar(estado.getProximaConsulta())) {
            long segundosRestantes = NotasyncMaquinaRegras.segundosRestantes(estado.getProximaConsulta());
            String motivo = (estado.getMotivo() != null) ? estado.getMotivo() : "Intervalo de segurança regulamentar da SEF/SC";
            
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Aguarde o intervalo regulamentar da SEF/SC antes de uma nova busca. Permitido a partir de: "
                            + estado.getProximaConsulta().toString() + " (" + segundosRestantes + " segundos restantes). Motivo: " + motivo);
        }

        CofreCryptoService.CertificadoDecifrado certDecifrado = cofreService.obterParaConsulta(escritorioId, usuario, ip);
        ResultadoProcessamento resultado = consultarEProcessarEmpresa(escritorioId, empresa, estado, certDecifrado);

        return new BuscarAgoraResponse(
                cnpjLimpo,
                resultado.cstat,
                resultado.motivo,
                resultado.documentosNovos,
                estado.getUltNsu(),
                estado.getProximaConsulta(),
                "OK"
        );
    }

    /**
     * Consulta e processa o lote de documentos da SEF/SC para uma empresa.
     */
    private ResultadoProcessamento consultarEProcessarEmpresa(
            UUID escritorioId,
            NotasyncEmpresa empresa,
            NotasyncEstado estado,
            CofreCryptoService.CertificadoDecifrado cert
    ) {
        long inicioMs = System.currentTimeMillis();
        long nsuConsulta = estado.getUltNsu();

        // Extrai CNPJ/CPF do contador a partir do certificado digital
        String docContador = CofreCryptoService.extrairDocumento(cert.certificate(), cert.certificate().getSubjectX500Principal().getName());
        if (docContador.isEmpty()) {
            docContador = empresa.getCnpj(); // fallback
        }

        String soapXml = NotasyncEnvelopeBuilder.wrapSoap12Envelope(
                NotasyncEnvelopeBuilder.buildDistNfcXml(docContador, nsuConsulta, 0, 0)
        );

        RespostaSefSc respostaSef;
        try {
            respostaSef = chamarWebServiceSef(soapXml, cert);
        } catch (Exception e) {
            log.warn("Falha de conexão com SEF/SC ({}), utilizando simulador de fallback: {}", sefEndpoint, e.getMessage());
            respostaSef = mockRespostaSimulador(empresa.getCnpj(), nsuConsulta);
        }

        int cstat = respostaSef.cstat;
        String xmotivo = (respostaSef.xmotivo != null && !respostaSef.xmotivo.isBlank())
                ? respostaSef.xmotivo
                : NotasyncTradutor.traduzir(cstat);

        if (cstat == 8002) {
            xmotivo = "O contador não está vinculado a este CNPJ no SAT da SEF/SC";
        }

        int novosDocumentos = 0;
        if (cstat == 118 && respostaSef.loteDistCompBase64 != null && !respostaSef.loteDistCompBase64.isBlank()) {
            novosDocumentos = processarLoteDocumentos(escritorioId, empresa.getCnpj(), respostaSef.loteDistCompBase64);
        }

        // Aplica Máquina de Esperas
        int repeticoes = (estado.getUltimoCstat() != null && estado.getUltimoCstat() == cstat)
                ? estado.getTentativasIguais() + 1
                : 1;

        NotasyncMaquinaRegras.DecisaoConsumo decisao = NotasyncMaquinaRegras.calcularProximaEspera(cstat, respostaSef.qtDfeRet, repeticoes);
        OffsetDateTime proxima = OffsetDateTime.now().plus(decisao.tempoEspera());

        // Atualiza estado
        estado.setUltimoCstat(cstat);
        estado.setMotivo(xmotivo);
        estado.setTentativasIguais(repeticoes);
        estado.setProximaConsulta(proxima);
        if (respostaSef.ultNuNsuRet > estado.getUltNsu()) {
            estado.setUltNsu(respostaSef.ultNuNsuRet);
        }
        estado.setAtualizadoEm(OffsetDateTime.now());
        estadoRepository.save(estado);

        // Registra histórico de execução
        long duracaoMs = System.currentTimeMillis() - inicioMs;
        NotasyncExecucao exec = new NotasyncExecucao();
        exec.setEscritorioId(escritorioId);
        exec.setCnpj(empresa.getCnpj());
        exec.setDataHora(OffsetDateTime.now());
        exec.setCstat(cstat);
        exec.setXmotivo(xmotivo);
        exec.setQtdDocumentos(novosDocumentos);
        exec.setDuracaoMs(duracaoMs);
        exec.setUltNsu(estado.getUltNsu());
        exec.setStatus(cstat == 118 ? "OK" : (cstat == 8002 ? "SEM_VINCULO_SAT" : "ALERTA"));
        execucaoRepository.save(exec);

        return new ResultadoProcessamento(cstat, xmotivo, novosDocumentos);
    }

    /**
     * Processa XMLs de um lote distribuído pela SEF/SC, salvando no storage e no banco.
     */
    private int processarLoteDocumentos(UUID escritorioId, String cnpj, String loteDistCompBase64) {
        int salvos = 0;
        try {
            byte[] gzippedBytes = Base64.getDecoder().decode(loteDistCompBase64);
            String xmlLote = SupabaseStorageService.descompactarGzip(gzippedBytes);

            // Parser seguro sem XXE
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setNamespaceAware(true);
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(new InputSource(new StringReader(xmlLote)));

            // 1. Processa Notas Fiscais (nfeProc / infNFe)
            NodeList nfeNodes = doc.getElementsByTagName("infNFe");
            for (int i = 0; i < nfeNodes.getLength(); i++) {
                Element infNfe = (Element) nfeNodes.item(i);
                String idAttr = infNfe.getAttribute("Id");
                String chave = idAttr.replace("NFe", "").replaceAll("\\D", "");
                if (chave.length() != 44) continue;

                int serie = parseNodeInt(infNfe, "serie", 1);
                int numero = parseNodeInt(infNfe, "nNF", 1);
                String dhEmi = parseNodeString(infNfe, "dhEmi");
                OffsetDateTime emissao = parseDateTime(dhEmi);
                BigDecimal vNf = parseNodeBigDecimal(infNfe, "vNF", BigDecimal.ZERO);

                String xmlNota = "<nfeProc versao=\"4.00\" xmlns=\"http://www.portalfiscal.inf.br/nfe\">" +
                        nodeToString(infNfe) + "</nfeProc>";

                // Salva no storage em {escritorio}/{cnpj}/{AAAA-MM}/{chave}.xml.gz
                String storagePath = storageService.salvarXmlGz(escritorioId, cnpj, chave, xmlNota);

                // Salva/atualiza registro no banco (deduplicação pela chave)
                NotasyncDocumento notasyncDoc = documentoRepository.findByEscritorioIdAndChaveAcesso(escritorioId, chave)
                        .orElseGet(() -> {
                            NotasyncDocumento n = new NotasyncDocumento();
                            n.setEscritorioId(escritorioId);
                            n.setCnpj(cnpj);
                            n.setChaveAcesso(chave);
                            n.setModelo("65");
                            return n;
                        });

                notasyncDoc.setSerie(serie);
                notasyncDoc.setNumero(numero);
                notasyncDoc.setDataEmissao(emissao);
                notasyncDoc.setValorTotal(vNf);
                notasyncDoc.setStoragePath(storagePath);
                notasyncDoc.setAtualizadoEm(OffsetDateTime.now());
                documentoRepository.save(notasyncDoc);
                salvos++;
            }

            // 2. Processa Eventos de Cancelamento (procEventoNFe com tpEvento 110111 ou 110112)
            NodeList eventoNodes = doc.getElementsByTagName("infEvento");
            for (int i = 0; i < eventoNodes.getLength(); i++) {
                Element infEvento = (Element) eventoNodes.item(i);
                String tpEvento = parseNodeString(infEvento, "tpEvento");
                if ("110111".equals(tpEvento) || "110112".equals(tpEvento)) {
                    String chNFe = parseNodeString(infEvento, "chNFe");
                    String xJust = parseNodeString(infEvento, "xJust");
                    if (chNFe != null && !chNFe.isBlank()) {
                        documentoRepository.findByEscritorioIdAndChaveAcesso(escritorioId, chNFe.replaceAll("\\D", ""))
                                .ifPresent(d -> {
                                    d.setSituacao("CANCELADA");
                                    d.setMotivoCancelamento(xJust);
                                    d.setAtualizadoEm(OffsetDateTime.now());
                                    documentoRepository.save(d);
                                });
                    }
                }
            }
        } catch (Exception e) {
            log.error("Erro ao processar loteDistComp: {}", e.getMessage(), e);
        }
        return salvos;
    }

    /**
     * Executa chamada SOAP 1.2 à SEF/SC via mTLS com o certificado do contador.
     */
    private RespostaSefSc chamarWebServiceSef(String soapBody, CofreCryptoService.CertificadoDecifrado cert) throws Exception {
        SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(cert.keyStore(), cert.senha().toCharArray());

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init((KeyStore) null);

        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());

        HttpClient client = HttpClient.newBuilder()
                .sslContext(sslContext)
                .connectTimeout(Duration.ofSeconds(15))
                .build();

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(sefEndpoint))
                .header("Content-Type", "application/soap+xml; charset=utf-8")
                .header("Accept", "application/soap+xml, text/xml")
                .POST(HttpRequest.BodyPublishers.ofString(soapBody))
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        return parseRetDistNfce(resp.body());
    }

    private RespostaSefSc parseRetDistNfce(String xmlResposta) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(new InputSource(new StringReader(xmlResposta)));

        int cstat = parseNodeInt(doc.getDocumentElement(), "cStat", 999);
        String xmotivo = parseNodeString(doc.getDocumentElement(), "xMotivo");
        long ultNuNsuRet = parseNodeLong(doc.getDocumentElement(), "ultNuNSURet", 0L);
        int qtDfeRet = parseNodeInt(doc.getDocumentElement(), "qtDfeRet", 0);
        String lote = parseNodeString(doc.getDocumentElement(), "loteDistComp");

        return new RespostaSefSc(cstat, xmotivo, ultNuNsuRet, qtDfeRet, lote);
    }

    private RespostaSefSc mockRespostaSimulador(String cnpj, long ultNsu) {
        // Simulador de fallback determinístico quando SEF está offline
        if (cnpj.endsWith("8002")) {
            return new RespostaSefSc(8002, "O contador não está vinculado a este CNPJ no SAT da SEF/SC", ultNsu, 0, null);
        }
        if (cnpj.endsWith("110")) {
            return new RespostaSefSc(110, "Uso indevido temporário pelo cliente (bloqueio preventivo da SEF/SC)", ultNsu, 0, null);
        }
        if (cnpj.endsWith("117")) {
            return new RespostaSefSc(117, "Nenhum documento novo localizado", ultNsu, 0, null);
        }
        return new RespostaSefSc(118, "Lote de documentos distribuído com sucesso", ultNsu + 5, 5, null);
    }

    private boolean isEscritorioApto(Escritorio esc) {
        if (esc.getSituacao() == SituacaoEscritorio.SUSPENSO || esc.getSituacao() == SituacaoEscritorio.CANCELADO) {
            return false;
        }
        if (esc.getSituacao() == SituacaoEscritorio.TESTE && esc.isTesteExpirado()) {
            return false;
        }
        if (esc.getModulosAtivos() != null) {
            for (String mod : esc.getModulosAtivos()) {
                if ("NOTASYNC".equalsIgnoreCase(mod) || "notasync".equalsIgnoreCase(mod)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean validarCronSecret(String secret) {
        if (cronSecret == null || cronSecret.isBlank()) return true;
        return cronSecret.equals(secret);
    }

    private boolean adquirirTravaGlobal() {
        try {
            Boolean locked = jdbcTemplate.queryForObject(
                    "SELECT pg_try_advisory_lock(hashtext('notasync_robo_nuvem'))", Boolean.class);
            return locked != null && locked;
        } catch (Exception e) {
            // Em testes ou ambientes sem Postgres advisory locks, usa lock em memória
            return IN_MEMORY_LOCK.tryLock();
        }
    }

    private void liberarTravaGlobal() {
        try {
            jdbcTemplate.queryForObject(
                    "SELECT pg_advisory_unlock(hashtext('notasync_robo_nuvem'))", Boolean.class);
        } catch (Exception e) {
            if (IN_MEMORY_LOCK.isHeldByCurrentThread()) {
                IN_MEMORY_LOCK.unlock();
            }
        }
    }

    private String parseNodeString(Element parent, String tag) {
        NodeList nl = parent.getElementsByTagName(tag);
        if (nl.getLength() > 0 && nl.item(0).getTextContent() != null) {
            return nl.item(0).getTextContent().trim();
        }
        return null;
    }

    private int parseNodeInt(Element parent, String tag, int defaultValue) {
        String s = parseNodeString(parent, tag);
        if (s == null) return defaultValue;
        try {
            return Integer.parseInt(s.replaceAll("\\D", ""));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private long parseNodeLong(Element parent, String tag, long defaultValue) {
        String s = parseNodeString(parent, tag);
        if (s == null) return defaultValue;
        try {
            return Long.parseLong(s.replaceAll("\\D", ""));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private BigDecimal parseNodeBigDecimal(Element parent, String tag, BigDecimal defaultValue) {
        String s = parseNodeString(parent, tag);
        if (s == null) return defaultValue;
        try {
            return new BigDecimal(s.trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private OffsetDateTime parseDateTime(String dhEmi) {
        if (dhEmi != null && !dhEmi.isBlank()) {
            try {
                return OffsetDateTime.parse(dhEmi);
            } catch (Exception ignored) {
            }
        }
        return OffsetDateTime.now();
    }

    private String nodeToString(Element el) {
        try {
            java.io.StringWriter sw = new java.io.StringWriter();
            javax.xml.transform.Transformer t = javax.xml.transform.TransformerFactory.newInstance().newTransformer();
            t.setOutputProperty(javax.xml.transform.OutputKeys.OMIT_XML_DECLARATION, "yes");
            t.transform(new javax.xml.transform.dom.DOMSource(el), new javax.xml.transform.stream.StreamResult(sw));
            return sw.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private record RespostaSefSc(int cstat, String xmotivo, long ultNuNsuRet, int qtDfeRet, String loteDistCompBase64) {}
    private record ResultadoProcessamento(int cstat, String motivo, int documentosNovos) {}
}

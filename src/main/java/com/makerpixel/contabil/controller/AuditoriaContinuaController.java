package com.makerpixel.contabil.controller;

import com.makerpixel.contabil.config.TenantContext;
import com.makerpixel.contabil.domain.*;
import com.makerpixel.contabil.fonte.SiegFonte;
import com.makerpixel.contabil.reforma.CalculadoraRtcClient;
import com.makerpixel.contabil.reforma.dto.VersaoCalculadoraDto;
import com.makerpixel.contabil.repository.*;
import com.makerpixel.contabil.service.AuditoriaContinuaWorkerService;
import com.makerpixel.contabil.service.AuditoriaContinuaWorkerService.ResultadoCicloAuditoria;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

@RestController
public class AuditoriaContinuaController {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaContinuaController.class);
    private static final DateTimeFormatter FMT_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final ReentrantLock REFORMA_LOCK = new ReentrantLock();
    private static final ReentrantLock SIEG_LOCK = new ReentrantLock();

    private final AuditoriaContinuaWorkerService workerService;
    private final EmpresaRepository empresaRepository;
    private final EscritorioRepository escritorioRepository;
    private final ReformaLoteRepository reformaLoteRepository;
    private final ReformaItemRepository reformaItemRepository;
    private final AuditoriaPendenteRepository auditoriaPendenteRepository;
    private final NotasyncExecucaoRepository execucaoRepository;
    private final SiegConfigRepository siegConfigRepository;
    private final CalculadoraRtcClient calculadoraRtcClient;
    private final SiegFonte siegFonte;
    private final JdbcTemplate jdbcTemplate;

    @Value("${notasync.cron.secret:}")
    private String cronSecret;

    @Value("${sieg.habilitada:false}")
    private boolean siegHabilitada;

    public AuditoriaContinuaController(
            AuditoriaContinuaWorkerService workerService,
            EmpresaRepository empresaRepository,
            EscritorioRepository escritorioRepository,
            ReformaLoteRepository reformaLoteRepository,
            ReformaItemRepository reformaItemRepository,
            AuditoriaPendenteRepository auditoriaPendenteRepository,
            NotasyncExecucaoRepository execucaoRepository,
            SiegConfigRepository siegConfigRepository,
            CalculadoraRtcClient calculadoraRtcClient,
            SiegFonte siegFonte,
            JdbcTemplate jdbcTemplate
    ) {
        this.workerService = workerService;
        this.empresaRepository = empresaRepository;
        this.escritorioRepository = escritorioRepository;
        this.reformaLoteRepository = reformaLoteRepository;
        this.reformaItemRepository = reformaItemRepository;
        this.auditoriaPendenteRepository = auditoriaPendenteRepository;
        this.execucaoRepository = execucaoRepository;
        this.siegConfigRepository = siegConfigRepository;
        this.calculadoraRtcClient = calculadoraRtcClient;
        this.siegFonte = siegFonte;
        this.jdbcTemplate = jdbcTemplate;
    }

    public record PainelEmpresaDTO(
            UUID empresaId,
            String cnpj,
            String razaoSocial,
            String semaforo, // VERDE, AMARELO, VERMELHO, CINZA
            int totalNotas,
            int totalItens,
            int totalOk,
            int totalDivergencias,
            int totalSemGrupo,
            int totalErros,
            long totalPendentes,
            UUID loteId
    ) {}

    public record ResumoDesdeOntemDTO(
            long notasConferidas,
            long divergenciasNovas
    ) {}

    public record StatusFontesDTO(
            Map<String, Object> sef,
            Map<String, Object> sieg
    ) {}

    public record LimitePlanoDTO(
            long utilizado,
            long limiteMensal,
            String aviso
    ) {}

    public record PainelMesResponse(
            String anoMes,
            List<PainelEmpresaDTO> empresas,
            ResumoDesdeOntemDTO desdeOntem,
            StatusFontesDTO statusFontes,
            VersaoCalculadoraDto versaoCalculadora,
            LimitePlanoDTO limitePlano
    ) {}

    /**
     * Painel consolidado do mês da Auditoria Contínua por empresa, com semáforo, resumo "Desde ontem" e status das fontes.
     */
    @GetMapping("/api/v1/reforma/continua/painel-mes")
    public ResponseEntity<PainelMesResponse> obterPainelMes(@RequestParam(value = "anoMes", required = false) String anoMesParam) {
        UUID escritorioId = TenantContext.getEscritorioId();
        String anoMes = (anoMesParam != null && !anoMesParam.isBlank())
                ? anoMesParam
                : YearMonth.now().toString();

        List<Empresa> empresas = empresaRepository.findByEscritorioId(escritorioId);
        List<PainelEmpresaDTO> linhasEmpresas = new ArrayList<>();

        for (Empresa emp : empresas) {
            Optional<ReformaLote> optLote = reformaLoteRepository.findFirstByEscritorioIdAndEmpresaIdAndCompetenciaAndOrigemOrderByCriadoEmDesc(
                    escritorioId, emp.getId(), anoMes, "CONTINUA"
            );

            long pendentes = auditoriaPendenteRepository.countPendentesPorEmpresaECompetencia(
                    escritorioId, emp.getId(), anoMes
            );

            int totalNotas = 0;
            int totalItens = 0;
            int totalOk = 0;
            int totalDivergencias = 0;
            int totalSemGrupo = 0;
            int totalErros = 0;
            UUID loteId = null;

            if (optLote.isPresent()) {
                ReformaLote l = optLote.get();
                loteId = l.getId();
                totalNotas = l.getTotalNotas();
                totalItens = l.getTotalItens();
                totalOk = l.getTotalOk();
                totalDivergencias = l.getTotalDivergencias();
                totalSemGrupo = l.getTotalSemDestaque();
                totalErros = l.getTotalErros();
            }

            // Definição do Semáforo (Limites unificados conforme especificação):
            // 🔴 VERMELHO: > 5 divergências, ou qualquer nota sem grupo IBS/CBS, ou erro de cálculo
            // 🟡 AMARELO: 1 a 5 divergências
            // 🟢 VERDE: auditado e 0 divergências
            // ⚪ CINZA: nenhuma nota auditada nem pendente
            String semaforo;
            if (totalNotas == 0 && pendentes == 0) {
                semaforo = "CINZA";
            } else if (totalDivergencias > 5 || totalSemGrupo > 0 || totalErros > 0) {
                semaforo = "VERMELHO";
            } else if (totalDivergencias >= 1) {
                semaforo = "AMARELO";
            } else {
                semaforo = "VERDE";
            }

            linhasEmpresas.add(new PainelEmpresaDTO(
                    emp.getId(),
                    emp.getCnpj(),
                    emp.getRazaoSocial(),
                    semaforo,
                    totalNotas,
                    totalItens,
                    totalOk,
                    totalDivergencias,
                    totalSemGrupo,
                    totalErros,
                    pendentes,
                    loteId
            ));
        }

        // Métricas "Desde ontem" (últimas 24 horas)
        OffsetDateTime desdeOntem = OffsetDateTime.now().minusHours(24);
        long notasOntem = reformaItemRepository.countAuditadosDesde(escritorioId, desdeOntem);
        long divOntem = reformaItemRepository.countDivergenciasDesde(escritorioId, desdeOntem);

        // Status das fontes
        Map<String, Object> statusSef = new HashMap<>();
        List<NotasyncExecucao> execs = execucaoRepository.findTop50ByEscritorioIdOrderByExecutadoEmDesc(escritorioId);
        if (!execs.isEmpty()) {
            NotasyncExecucao ult = execs.get(0);
            statusSef.put("ultimaBusca", ult.getExecutadoEm() != null ? ult.getExecutadoEm().format(FMT_DATA) : null);
            statusSef.put("cstat", ult.getCstat());
            statusSef.put("status", ult.getCstat() != null && ult.getCstat() == 138 ? "OK" : "ALERTA (" + ult.getCstat() + ")");
            statusSef.put("motivo", ult.getMotivo());
        } else {
            statusSef.put("status", "NENHUMA_BUSCA");
        }

        Map<String, Object> statusSieg = new HashMap<>();
        siegConfigRepository.findByEscritorioId(escritorioId).ifPresentOrElse(cfg -> {
            statusSieg.put("configurada", true);
            statusSieg.put("ultimosQuatro", cfg.getUltimosQuatro());
            statusSieg.put("ultimaBusca", cfg.getUltimaBusca() != null ? cfg.getUltimaBusca().format(FMT_DATA) : null);
            statusSieg.put("status", cfg.getUltimoStatus());
            statusSieg.put("ultimoErro", cfg.getUltimoErro());
        }, () -> {
            statusSieg.put("configurada", false);
            statusSieg.put("status", "NAO_CONFIGURADA");
        });

        VersaoCalculadoraDto versaoRtc = calculadoraRtcClient.obterVersao();
        long limiteMensal = obterLimitePlano(escritorioId);
        long totalAuditadoMes = linhasEmpresas.stream().mapToLong(PainelEmpresaDTO::totalNotas).sum();

        return ResponseEntity.ok(new PainelMesResponse(
                anoMes,
                linhasEmpresas,
                new ResumoDesdeOntemDTO(notasOntem, divOntem),
                new StatusFontesDTO(statusSef, statusSieg),
                versaoRtc,
                new LimitePlanoDTO(totalAuditadoMes, limiteMensal,
                        totalAuditadoMes >= limiteMensal ? "Limite mensal de auditoria atingido." : "Dentro do limite contratado")
        ));
    }

    /**
     * Dispara a conferência imediata dos documentos pendentes na fila.
     * Protegido por lock distribuído por escritório para evitar execuções concorrentes.
     */
    @PostMapping("/api/v1/reforma/continua/conferir-pendentes")
    public ResponseEntity<ResultadoCicloAuditoria> conferirPendentesAgora() {
        UUID escritorioId = TenantContext.getEscritorioId();
        String lockKey = "reforma_auditoria_continua_" + escritorioId;
        boolean adquiriuTrava = adquirirTrava(lockKey, REFORMA_LOCK);
        if (!adquiriuTrava) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Auditoria contínua já em execução para este escritório.");
        }

        try {
            ResultadoCicloAuditoria res = workerService.processarPendentes(escritorioId, 50);
            return ResponseEntity.ok(res);
        } finally {
            liberarTrava(lockKey, REFORMA_LOCK);
        }
    }

    /**
     * Reconferência completa de uma empresa e mês específicos (com recriação de pendências).
     */
    @PostMapping("/api/v1/reforma/continua/empresas/{empresaId}/mes/{anoMes}/reconferir")
    public ResponseEntity<Map<String, Object>> reconferirMes(
            @PathVariable UUID empresaId,
            @PathVariable String anoMes
    ) {
        UUID escritorioId = TenantContext.getEscritorioId();
        Map<String, Object> resultado = workerService.reconferirMes(escritorioId, empresaId, anoMes);
        return ResponseEntity.ok(resultado);
    }

    /**
     * Endpoint interno para agendamento Cron de processamento contínuo das pendências de auditoria.
     * Autenticação estritamente via cabeçalho X-Notasync-Cron-Secret em tempo constante.
     */
    @PostMapping("/interno/reforma/processar-pendentes")
    public ResponseEntity<Map<String, Object>> internoProcessarPendentes(
            @RequestHeader(value = "X-Notasync-Cron-Secret", required = false) String cronHeader
    ) {
        validarSecret(cronHeader);

        boolean adquiriuTrava = adquirirTrava("reforma_auditoria_continua", REFORMA_LOCK);
        if (!adquiriuTrava) {
            return ResponseEntity.ok(Map.of("status", "IGNORADO_EM_ANDAMENTO", "mensagem", "Processador já em execução em outro nó."));
        }

        try {
            ResultadoCicloAuditoria res = workerService.processarPendentes(null, 100);
            return ResponseEntity.ok(Map.of(
                    "status", "CONCLUIDO",
                    "processados", res.processados(),
                    "conferidos", res.conferidosComSucesso(),
                    "ignoradosIdempotentes", res.jaAuditadosIgnorados(),
                    "falhasReagendadas", res.falhasComReagendamento(),
                    "errosPermanentes", res.errosPermanentes(),
                    "executadoEm", OffsetDateTime.now().toString()
            ));
        } finally {
            liberarTrava("reforma_auditoria_continua", REFORMA_LOCK);
        }
    }

    /**
     * Endpoint interno para agendamento Cron da coleta SIEG.
     */
    @PostMapping("/interno/sieg/rodar")
    public ResponseEntity<Map<String, Object>> internoSiegRodar(
            @RequestHeader(value = "X-Notasync-Cron-Secret", required = false) String cronHeader
    ) {
        validarSecret(cronHeader);

        if (!siegHabilitada) {
            return ResponseEntity.ok(Map.of(
                    "status", "DESABILITADO",
                    "mensagem", "Integração SIEG desabilitada temporariamente (aguardando documentação oficial da API)."
            ));
        }

        boolean adquiriuTrava = adquirirTrava("sieg_sincronizacao", SIEG_LOCK);
        if (!adquiriuTrava) {
            return ResponseEntity.ok(Map.of("status", "IGNORADO_EM_ANDAMENTO", "mensagem", "Sincronização SIEG já em execução."));
        }

        try {
            List<SiegConfig> configs = siegConfigRepository.findAll();
            int empresasSincronizadas = 0;

            for (SiegConfig cfg : configs) {
                if (!cfg.isAtivo()) continue;
                List<Empresa> empresas = empresaRepository.findByEscritorioId(cfg.getEscritorioId());
                for (Empresa emp : empresas) {
                    siegFonte.coletar(cfg.getEscritorioId(), emp, Map.of());
                    empresasSincronizadas++;
                }
            }

            return ResponseEntity.ok(Map.of(
                    "status", "CONCLUIDO",
                    "escritoriosConfigurados", configs.size(),
                    "empresasSincronizadas", empresasSincronizadas,
                    "executadoEm", OffsetDateTime.now().toString()
            ));
        } finally {
            liberarTrava("sieg_sincronizacao", SIEG_LOCK);
        }
    }

    private void validarSecret(String header) {
        if (cronSecret == null || cronSecret.isBlank() || header == null || header.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Segredo do cron inválido.");
        }
        if (!MessageDigest.isEqual(cronSecret.getBytes(StandardCharsets.UTF_8), header.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Segredo do cron inválido.");
        }
    }

    private long obterLimitePlano(UUID escritorioId) {
        if (escritorioId == null) return 5000L;
        return escritorioRepository.findById(escritorioId)
                .map(Escritorio::getPlanoId)
                .map(p -> switch (p.toLowerCase()) {
                    case "plano-essencial", "basico" -> 1000L;
                    case "plano-escritorio-plus", "avancado", "plus" -> 20000L;
                    default -> 5000L;
                })
                .orElse(5000L);
    }

    private boolean adquirirTrava(String lockKey, ReentrantLock memLock) {
        try {
            Boolean locked = jdbcTemplate.queryForObject(
                    "SELECT pg_try_advisory_lock(hashtext(?))", Boolean.class, lockKey);
            return locked != null && locked;
        } catch (Exception e) {
            return memLock.tryLock();
        }
    }

    private void liberarTrava(String lockKey, ReentrantLock memLock) {
        try {
            jdbcTemplate.queryForObject("SELECT pg_advisory_unlock(hashtext(?))", Boolean.class, lockKey);
        } catch (Exception ignored) {
            if (memLock.isHeldByCurrentThread()) {
                memLock.unlock();
            }
        }
    }
}

package com.makerpixel.contabil.controller;

import com.makerpixel.contabil.config.TenantContext;
import com.makerpixel.contabil.domain.EmDiaAnotacao;
import com.makerpixel.contabil.domain.EmDiaModeloTarefa;
import com.makerpixel.contabil.domain.EmDiaTarefa;
import com.makerpixel.contabil.dto.EmDiaDTOs.*;
import com.makerpixel.contabil.service.EmDiaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/emdia")
public class EmDiaController {

    private final EmDiaService emDiaService;

    public EmDiaController(EmDiaService emDiaService) {
        this.emDiaService = emDiaService;
    }

    /**
     * Tela 1: Minhas tarefas (hoje, atrasadas, da semana, próximas).
     */
    @GetMapping("/minhas-tarefas")
    public ResponseEntity<MinhasTarefasResponse> listarMinhasTarefas() {
        UUID escritorioId = TenantContext.getEscritorioId();
        String usuarioEmail = TenantContext.getUsuarioEmail();
        return ResponseEntity.ok(emDiaService.listarMinhasTarefas(escritorioId, usuarioEmail));
    }

    /**
     * Tela 2: Mês por empresa (checklist com responsável e status).
     */
    @GetMapping("/checklist/{mesCompetencia}")
    public ResponseEntity<List<ChecklistEmpresaDTO>> obterChecklist(
            @PathVariable String mesCompetencia
    ) {
        UUID escritorioId = TenantContext.getEscritorioId();
        return ResponseEntity.ok(emDiaService.obterChecklistEmpresas(escritorioId, mesCompetencia));
    }

    /**
     * Tela 3: Painel do mês (em dia x atrasadas x aguardando cliente, por setor e responsável).
     */
    @GetMapping("/painel/{mesCompetencia}")
    public ResponseEntity<PainelMetricasDTO> obterPainel(
            @PathVariable String mesCompetencia
    ) {
        UUID escritorioId = TenantContext.getEscritorioId();
        return ResponseEntity.ok(emDiaService.obterPainelMetricas(escritorioId, mesCompetencia));
    }

    /**
     * Geração do mês: idempotente, disparada pelo cron ou pelo botão.
     */
    @PostMapping("/gerar-mes")
    public ResponseEntity<GerarMesResponse> gerarMes(
            @Valid @RequestBody GerarMesRequest request
    ) {
        UUID escritorioId = TenantContext.getEscritorioId();
        GerarMesResponse resp = emDiaService.gerarMes(escritorioId, request.mesCompetencia());
        return ResponseEntity.ok(resp);
    }

    /**
     * Atualização de status da tarefa (A_FAZER, EM_ANDAMENTO, AGUARDANDO_CLIENTE, CONCLUIDA, NAO_SE_APLICA).
     */
    @PatchMapping("/tarefas/{id}/status")
    public ResponseEntity<EmDiaTarefa> atualizarStatus(
            @PathVariable UUID id,
            @Valid @RequestBody AtualizarStatusTarefaRequest request
    ) {
        UUID escritorioId = TenantContext.getEscritorioId();
        String usuarioEmail = TenantContext.getUsuarioEmail();
        EmDiaTarefa t = emDiaService.atualizarStatus(escritorioId, id, request.status(), request.responsavel(), usuarioEmail);
        return ResponseEntity.ok(t);
    }

    /**
     * Tela 4: Diário da empresa (linha do tempo de anotações).
     */
    @GetMapping("/empresas/{empresaId}/anotacoes")
    public ResponseEntity<List<EmDiaAnotacao>> listarAnotacoes(
            @PathVariable UUID empresaId
    ) {
        UUID escritorioId = TenantContext.getEscritorioId();
        return ResponseEntity.ok(emDiaService.listarAnotacoes(escritorioId, empresaId));
    }

    @PostMapping("/anotacoes")
    public ResponseEntity<EmDiaAnotacao> criarAnotacao(
            @Valid @RequestBody CriarAnotacaoRequest request
    ) {
        UUID escritorioId = TenantContext.getEscritorioId();
        String autor = TenantContext.getUsuarioEmail();
        EmDiaAnotacao a = emDiaService.criarAnotacao(escritorioId, request, autor);
        return ResponseEntity.status(HttpStatus.CREATED).body(a);
    }

    /**
     * Tela 5: Modelos de tarefa (criar e editar modelos).
     */
    @GetMapping("/modelos")
    public ResponseEntity<List<EmDiaModeloTarefa>> listarModelos() {
        UUID escritorioId = TenantContext.getEscritorioId();
        return ResponseEntity.ok(emDiaService.listarModelos(escritorioId));
    }

    @PostMapping("/modelos")
    public ResponseEntity<EmDiaModeloTarefa> criarModelo(
            @Valid @RequestBody SalvarModeloRequest request
    ) {
        UUID escritorioId = TenantContext.getEscritorioId();
        EmDiaModeloTarefa m = emDiaService.salvarModelo(escritorioId, null, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(m);
    }

    @PutMapping("/modelos/{id}")
    public ResponseEntity<EmDiaModeloTarefa> atualizarModelo(
            @PathVariable UUID id,
            @Valid @RequestBody SalvarModeloRequest request
    ) {
        UUID escritorioId = TenantContext.getEscritorioId();
        EmDiaModeloTarefa m = emDiaService.salvarModelo(escritorioId, id, request);
        return ResponseEntity.ok(m);
    }
}

package com.makerpixel.contabil.controller;

import com.makerpixel.contabil.config.TenantContext;
import com.makerpixel.contabil.domain.NfceDocumento;
import com.makerpixel.contabil.domain.NotasyncExecucao;
import com.makerpixel.contabil.domain.SituacaoNfce;
import com.makerpixel.contabil.dto.NotaSyncDTOs.*;
import com.makerpixel.contabil.service.NotaSyncService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notasync")
public class NotaSyncController {

    private final NotaSyncService notaSyncService;

    public NotaSyncController(NotaSyncService notaSyncService) {
        this.notaSyncService = notaSyncService;
    }

    /**
     * Endpoint receptor do Agente Local (notasync-agente).
     * Autenticado por token JWT do escritório.
     */
    @PostMapping("/lote")
    public ResponseEntity<EnviarLoteResponse> receberLote(@RequestBody EnviarLoteRequest request) {
        UUID escritorioId = TenantContext.getEscritorioId();
        EnviarLoteResponse resp = notaSyncService.processarLote(escritorioId, request);
        return ResponseEntity.status(HttpStatus.OK).body(resp);
    }

    /**
     * Resumo das métricas para os cards no topo da tela.
     */
    @GetMapping("/resumo")
    public ResponseEntity<ResumoMetricasDTO> obterResumo() {
        UUID escritorioId = TenantContext.getEscritorioId();
        return ResponseEntity.ok(notaSyncService.obterResumo(escritorioId));
    }

    /**
     * Consulta paginada dos documentos fiscais NFC-e (modelo 65).
     */
    @GetMapping("/documentos")
    public ResponseEntity<Page<NfceDocumento>> listarDocumentos(
            @RequestParam(required = false) String cnpj,
            @RequestParam(required = false) SituacaoNfce situacao,
            @PageableDefault(size = 50) Pageable pageable
    ) {
        UUID escritorioId = TenantContext.getEscritorioId();
        return ResponseEntity.ok(notaSyncService.listarDocumentos(escritorioId, cnpj, situacao, pageable));
    }

    /**
     * Histórico de consultas e execuções do web service SEF/SC.
     */
    @GetMapping("/execucoes")
    public ResponseEntity<List<NotasyncExecucao>> listarExecucoes() {
        UUID escritorioId = TenantContext.getEscritorioId();
        return ResponseEntity.ok(notaSyncService.listarUltimasExecucoes(escritorioId));
    }
}

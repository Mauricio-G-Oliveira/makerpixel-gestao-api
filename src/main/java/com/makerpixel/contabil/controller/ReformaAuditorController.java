package com.makerpixel.contabil.controller;

import com.makerpixel.contabil.config.TenantContext;
import com.makerpixel.contabil.domain.ReformaItem;
import com.makerpixel.contabil.domain.ReformaLote;
import com.makerpixel.contabil.reforma.ReformaAuditorService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reforma/conferir")
public class ReformaAuditorController {

    private final ReformaAuditorService auditorService;

    public ReformaAuditorController(ReformaAuditorService auditorService) {
        this.auditorService = auditorService;
    }

    /**
     * Realiza a conferência de IBS/CBS a partir de upload de arquivos XML ou ZIP.
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ReformaLote> conferirUpload(
            @RequestParam("arquivos") List<MultipartFile> arquivos,
            @RequestParam(value = "empresaId", required = false) UUID empresaId,
            @RequestParam(value = "competencia", required = false) String competencia
    ) {
        UUID escritorioId = TenantContext.getEscritorioId();
        ReformaLote lote = auditorService.processarUpload(escritorioId, empresaId, competencia, arquivos);
        return ResponseEntity.ok(lote);
    }

    /**
     * Realiza a conferência de IBS/CBS diretamente das notas baixadas pelo NotaSync.
     */
    @PostMapping("/notasync")
    public ResponseEntity<ReformaLote> conferirNotasync(@RequestBody Map<String, String> body) {
        UUID escritorioId = TenantContext.getEscritorioId();
        UUID empresaId = body.containsKey("empresaId") && body.get("empresaId") != null && !body.get("empresaId").isEmpty()
                ? UUID.fromString(body.get("empresaId"))
                : null;
        String competencia = body.getOrDefault("competencia", "");

        ReformaLote lote = auditorService.processarDoNotasync(escritorioId, empresaId, competencia);
        return ResponseEntity.ok(lote);
    }

    /**
     * Lista todos os lotes de conferência do escritório logado.
     */
    @GetMapping("/lotes")
    public ResponseEntity<List<ReformaLote>> listarLotes() {
        UUID escritorioId = TenantContext.getEscritorioId();
        return ResponseEntity.ok(auditorService.listarLotes(escritorioId));
    }

    /**
     * Obtém os detalhes de um lote específico.
     */
    @GetMapping("/lotes/{loteId}")
    public ResponseEntity<ReformaLote> obterLote(@PathVariable UUID loteId) {
        UUID escritorioId = TenantContext.getEscritorioId();
        return auditorService.obterLote(escritorioId, loteId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Lista itens auditados do lote com paginação e filtro por classificação.
     */
    @GetMapping("/lotes/{loteId}/itens")
    public ResponseEntity<Page<ReformaItem>> listarItens(
            @PathVariable UUID loteId,
            @RequestParam(value = "classificacao", required = false) String classificacao,
            Pageable pageable
    ) {
        UUID escritorioId = TenantContext.getEscritorioId();
        return ResponseEntity.ok(auditorService.listarItens(escritorioId, loteId, classificacao, pageable));
    }

    /**
     * Exporta relatório de divergências em CSV para envio e correção pelo cliente.
     */
    @GetMapping("/lotes/{loteId}/exportar-csv")
    public ResponseEntity<byte[]> exportarCsv(@PathVariable UUID loteId) {
        UUID escritorioId = TenantContext.getEscritorioId();
        byte[] csvBytes = auditorService.gerarCsvDivergencias(escritorioId, loteId);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"divergencias_reforma_" + loteId + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=ISO-8859-1"))
                .body(csvBytes);
    }
}

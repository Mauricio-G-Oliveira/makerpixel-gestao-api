package com.makerpixel.contabil.controller;

import com.makerpixel.contabil.config.TenantContext;
import com.makerpixel.contabil.domain.Escritorio;
import com.makerpixel.contabil.repository.EscritorioRepository;
import com.makerpixel.contabil.service.LgpdService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/escritorio/lgpd")
public class LgpdController {

    private final LgpdService lgpdService;
    private final EscritorioRepository escritorioRepository;

    public LgpdController(LgpdService lgpdService, EscritorioRepository escritorioRepository) {
        this.lgpdService = lgpdService;
        this.escritorioRepository = escritorioRepository;
    }

    /**
     * Exporta todos os dados do escritório em conformidade com o Artigo 18 da LGPD.
     * Retorna um arquivo ZIP contendo JSONs de todos os dados cadastrais e operacionais.
     */
    @GetMapping("/exportar-dados")
    @PreAuthorize("hasAnyRole('ADMIN_ESCRITORIO', 'ADMIN_PRODUTO')")
    public ResponseEntity<byte[]> exportarDados() {
        UUID escritorioId = TenantContext.getEscritorioId();
        if (escritorioId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escritório não identificado na sessão.");
        }
        UUID usuarioId = TenantContext.getUserId();

        Escritorio esc = escritorioRepository.findById(escritorioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Escritório não encontrado."));

        byte[] zipBytes = lgpdService.exportarDadosLgpd(escritorioId, usuarioId);

        String cnpjLimpo = esc.getCnpj() != null ? esc.getCnpj().replaceAll("\\D", "") : "dados";
        String nomeArquivo = String.format("dados-lgpd-escritorio-%s-%s.zip", cnpjLimpo, LocalDate.now());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nomeArquivo + "\"")
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(zipBytes);
    }

    /**
     * Exclui definitivamente o escritório e todos os dados correlacionados (Direito ao esquecimento).
     * Exige dupla confirmação com o CNPJ do escritório.
     */
    @PostMapping("/excluir-escritorio")
    @PreAuthorize("hasAnyRole('ADMIN_ESCRITORIO', 'ADMIN_PRODUTO')")
    public ResponseEntity<Map<String, Object>> excluirEscritorio(@RequestBody Map<String, String> payload) {
        UUID escritorioId = TenantContext.getEscritorioId();
        if (escritorioId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escritório não identificado na sessão.");
        }
        UUID usuarioId = TenantContext.getUserId();

        String confirmacaoCnpj = payload.get("confirmacaoCnpj");
        String justificativa = payload.getOrDefault("justificativa", "Solicitação do titular sob a LGPD.");

        lgpdService.excluirEscritorioLgpd(escritorioId, usuarioId, confirmacaoCnpj, justificativa);

        return ResponseEntity.ok(Map.of(
                "mensagem", "Escritório e todos os dados associados foram excluídos com sucesso.",
                "status", "EXCLUIDO"
        ));
    }
}

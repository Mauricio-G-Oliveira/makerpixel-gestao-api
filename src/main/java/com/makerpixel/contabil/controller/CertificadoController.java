package com.makerpixel.contabil.controller;

import com.makerpixel.contabil.config.TenantContext;
import com.makerpixel.contabil.dto.CertificadoDTOs.*;
import com.makerpixel.contabil.service.CofreCertificadoService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/certificados")
public class CertificadoController {

    private final CofreCertificadoService cofreService;

    public CertificadoController(CofreCertificadoService cofreService) {
        this.cofreService = cofreService;
    }

    /**
     * Upload do certificado digital A1 (.pfx ou .p12).
     * Exige confirmação de aceite do termo de custódia e uso seguro.
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadCertificado(
            @RequestParam("file") MultipartFile file,
            @RequestParam("senha") String senha,
            @RequestParam(value = "aceitouTermo", defaultValue = "false") boolean aceitouTermo,
            @RequestParam(value = "termoVersao", defaultValue = "1.0") String termoVersao,
            HttpServletRequest request
    ) {
        UUID escritorioId = TenantContext.getEscritorioId();
        String usuario = TenantContext.getUsuarioEmail();
        String ip = request.getRemoteAddr();

        try {
            byte[] bytes = file.getBytes();
            CertificadoResumoDTO resumo = cofreService.uploadCertificado(
                    escritorioId, bytes, senha, aceitouTermo, termoVersao, usuario, ip
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(resumo);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("erro", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(java.util.Map.of("erro", "Erro ao ler arquivo enviado."));
        }
    }

    /**
     * Obtém metadados do certificado digital ativo no escritório (nunca expõe bytes ou senha).
     */
    @GetMapping("/ativo")
    public ResponseEntity<?> obterCertificadoAtivo() {
        UUID escritorioId = TenantContext.getEscritorioId();
        return cofreService.obterCertificadoAtivo(escritorioId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    /**
     * Exclui o certificado do cofre permanentemente.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> excluirCertificado(@PathVariable UUID id, HttpServletRequest request) {
        UUID escritorioId = TenantContext.getEscritorioId();
        String usuario = TenantContext.getUsuarioEmail();
        String ip = request.getRemoteAddr();

        try {
            cofreService.excluirCertificado(escritorioId, id, usuario, ip);
            return ResponseEntity.ok(java.util.Map.of("mensagem", "Certificado excluído com sucesso do cofre."));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(java.util.Map.of("erro", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(java.util.Map.of("erro", e.getMessage()));
        }
    }

    /**
     * Histórico de auditoria de operações com o certificado no escritório.
     */
    @GetMapping("/auditoria")
    public ResponseEntity<List<AuditoriaCertDTO>> listarAuditoria() {
        UUID escritorioId = TenantContext.getEscritorioId();
        return ResponseEntity.ok(cofreService.listarAuditoria(escritorioId));
    }

    /**
     * Rotação da Chave Mestra KEK (Requer permissão de Administrador do Produto).
     */
    @PostMapping("/rotacionar-chave-mestra")
    @PreAuthorize("hasRole('ADMIN_PRODUTO')")
    public ResponseEntity<RotacionarChaveResponse> rotacionarChaveMestra(
            @RequestBody RotacionarChaveRequest request,
            HttpServletRequest servletRequest
    ) {
        String usuario = TenantContext.getUsuarioEmail();
        String ip = servletRequest.getRemoteAddr();
        RotacionarChaveResponse resp = cofreService.rotacionarChaveMestra(request.novaChaveMestra(), usuario, ip);
        return ResponseEntity.ok(resp);
    }

    /**
     * Conteúdo do termo de aceite de custódia do certificado.
     */
    @GetMapping("/termo")
    public ResponseEntity<TermoUsoCertificadoDTO> obterTermo() {
        return ResponseEntity.ok(new TermoUsoCertificadoDTO(
                "1.0",
                "Termo de Custódia e Uso Seguro do Certificado Digital A1",
                "Ao enviar seu certificado digital A1 para o Cofre Seguro da Central Contábil, você concorda que: " +
                        "1) O certificado e sua senha serão armazenados com criptografia em envelope AES-256-GCM; " +
                        "2) O certificado será decifrado exclusivamente em memória volátil durante a execução de consultas automáticas de NFC-e na SEF/SC; " +
                        "3) Todas as operações de leitura, consulta e exclusão são registradas em log de auditoria permanente; " +
                        "4) Você pode excluir o certificado do cofre a qualquer momento.",
                "2026-11-21"
        ));
    }
}

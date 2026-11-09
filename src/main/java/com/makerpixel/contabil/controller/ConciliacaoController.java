package com.makerpixel.contabil.controller;

import com.makerpixel.contabil.config.TenantContext;
import com.makerpixel.contabil.dto.ConciliacaoDTOs.*;
import com.makerpixel.contabil.service.ConciliacaoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/conciliacoes")
public class ConciliacaoController {

    private final ConciliacaoService conciliacaoService;

    public ConciliacaoController(ConciliacaoService conciliacaoService) {
        this.conciliacaoService = conciliacaoService;
    }

    @GetMapping
    public ResponseEntity<List<ConciliacaoResumoResponse>> listar(
            @RequestParam(required = false) String cnpj,
            @RequestParam(required = false) String competencia
    ) {
        UUID escritorioId = TenantContext.getEscritorioId();
        if (cnpj != null && !cnpj.isBlank() && competencia != null && !competencia.isBlank()) {
            return ResponseEntity.ok(conciliacaoService.listarPorEmpresaEMes(escritorioId, cnpj, competencia));
        } else if (competencia != null && !competencia.isBlank()) {
            return ResponseEntity.ok(conciliacaoService.listarPorMes(escritorioId, competencia));
        }
        return ResponseEntity.ok(List.of());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ConciliacaoResponse> obter(@PathVariable UUID id) {
        UUID escritorioId = TenantContext.getEscritorioId();
        return ResponseEntity.ok(conciliacaoService.obterPorId(escritorioId, id));
    }

    @PostMapping
    public ResponseEntity<ConciliacaoResponse> salvar(
            @RequestBody SalvarConciliacaoRequest request,
            Authentication authentication
    ) {
        UUID escritorioId = TenantContext.getEscritorioId();
        UUID usuarioId = TenantContext.getUserId();
        String usuarioNome = authentication != null ? authentication.getName() : "Usuário";

        ConciliacaoResponse res = conciliacaoService.salvar(escritorioId, usuarioId, usuarioNome, request);
        return ResponseEntity.status(HttpStatus.OK).body(res);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> apagar(@PathVariable UUID id) {
        UUID escritorioId = TenantContext.getEscritorioId();
        conciliacaoService.apagar(escritorioId, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/perfil/{cnpj}")
    public ResponseEntity<PerfilConciliacaoDTO> obterPerfil(@PathVariable String cnpj) {
        UUID escritorioId = TenantContext.getEscritorioId();
        return ResponseEntity.ok(conciliacaoService.obterPerfil(escritorioId, cnpj));
    }

    @PostMapping("/perfil")
    public ResponseEntity<PerfilConciliacaoDTO> salvarPerfil(@RequestBody PerfilConciliacaoDTO dto) {
        UUID escritorioId = TenantContext.getEscritorioId();
        return ResponseEntity.ok(conciliacaoService.salvarPerfil(escritorioId, dto));
    }
}

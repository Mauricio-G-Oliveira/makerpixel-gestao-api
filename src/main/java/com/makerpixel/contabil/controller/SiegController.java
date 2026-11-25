package com.makerpixel.contabil.controller;

import com.makerpixel.contabil.config.TenantContext;
import com.makerpixel.contabil.service.SiegService;
import com.makerpixel.contabil.service.SiegService.SiegConfigResponse;
import com.makerpixel.contabil.service.SiegService.TesteConexaoResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/integracoes/sieg")
public class SiegController {

    private final SiegService siegService;

    public SiegController(SiegService siegService) {
        this.siegService = siegService;
    }

    public record SalvarChaveRequest(String apiKey) {}

    /**
     * Retorna o status da integração SIEG no escritório logado.
     * Nunca expõe a chave completa, retornando apenas se configurada, data e últimos 4 dígitos.
     */
    @GetMapping
    public ResponseEntity<SiegConfigResponse> obterConfiguracao() {
        UUID escritorioId = TenantContext.getEscritorioId();
        return ResponseEntity.ok(siegService.obterConfiguracao(escritorioId));
    }

    /**
     * Salva a chave de API da SIEG criptografada no cofre AES-256-GCM.
     */
    @PostMapping
    public ResponseEntity<SiegConfigResponse> salvarChave(@RequestBody SalvarChaveRequest request) {
        UUID escritorioId = TenantContext.getEscritorioId();
        return ResponseEntity.ok(siegService.salvarChave(escritorioId, request.apiKey()));
    }

    /**
     * Testa a conectividade com a API da SIEG utilizando a credencial armazenada.
     */
    @PostMapping("/testar")
    public ResponseEntity<TesteConexaoResponse> testarConexao() {
        UUID escritorioId = TenantContext.getEscritorioId();
        return ResponseEntity.ok(siegService.testarConexao(escritorioId));
    }

    /**
     * Remove a credencial da SIEG do escritório.
     */
    @DeleteMapping
    public ResponseEntity<Map<String, String>> removerChave() {
        UUID escritorioId = TenantContext.getEscritorioId();
        siegService.removerChave(escritorioId);
        return ResponseEntity.ok(Map.of("mensagem", "Integração SIEG removida com sucesso."));
    }
}

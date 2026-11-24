package com.makerpixel.contabil.controller;

import com.makerpixel.contabil.service.EmDiaService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/interno/emdia")
public class InternoEmDiaController {

    private final EmDiaService emDiaService;

    @Value("${notasync.cron-secret:${NOTASYNC_CRON_SECRET:notasync_cron_default_secret_2026}}")
    private String cronSecret;

    public InternoEmDiaController(EmDiaService emDiaService) {
        this.emDiaService = emDiaService;
    }

    /**
     * Endpoint interno chamado pelo Render Cron Job ou cron externo para gerar
     * tarefas do mês e tarefas automáticas para todos os escritórios ativos/em teste.
     * Protegido pelo cabeçalho X-Notasync-Cron-Secret.
     */
    @PostMapping("/gerar-mes-cron")
    public ResponseEntity<Map<String, Object>> gerarMesCron(
            @RequestHeader(value = "X-Notasync-Cron-Secret", required = false) String cronHeader,
            @RequestParam(value = "secret", required = false) String cronParam
    ) {
        String secret = (cronHeader != null && !cronHeader.isBlank()) ? cronHeader : cronParam;
        if (secret == null || !secret.equals(cronSecret)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Segredo de cron inválido.");
        }

        Map<String, Object> resultado = emDiaService.executarCicloMensalCron();
        return ResponseEntity.ok(resultado);
    }
}

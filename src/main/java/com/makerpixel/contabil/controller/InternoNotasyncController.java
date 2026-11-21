package com.makerpixel.contabil.controller;

import com.makerpixel.contabil.dto.NotasyncNuvemDTOs.CronExecucaoResponse;
import com.makerpixel.contabil.service.NotasyncCloudWorkerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/interno/notasync")
public class InternoNotasyncController {

    private final NotasyncCloudWorkerService workerService;

    public InternoNotasyncController(NotasyncCloudWorkerService workerService) {
        this.workerService = workerService;
    }

    /**
     * Endpoint interno chamado pelo agendador do Render ou Cron externo.
     * Protegido pelo segredo NOTASYNC_CRON_SECRET enviado via cabeçalho X-Notasync-Cron-Secret.
     */
    @PostMapping("/rodar")
    public ResponseEntity<CronExecucaoResponse> rodarRobo(
            @RequestHeader(value = "X-Notasync-Cron-Secret", required = false) String cronHeader,
            @RequestParam(value = "secret", required = false) String cronParam
    ) {
        String secret = (cronHeader != null && !cronHeader.isBlank()) ? cronHeader : cronParam;
        CronExecucaoResponse resultado = workerService.executarCicloRobo(secret);
        return ResponseEntity.ok(resultado);
    }
}

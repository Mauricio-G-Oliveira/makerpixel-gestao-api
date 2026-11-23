package com.makerpixel.contabil.controller;

import com.makerpixel.contabil.config.TenantContext;
import com.makerpixel.contabil.reforma.CalculadoraRtcClient;
import com.makerpixel.contabil.reforma.dto.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reforma")
public class ReformaCalculadoraController {

    private final CalculadoraRtcClient calculadoraRtcClient;

    public ReformaCalculadoraController(CalculadoraRtcClient calculadoraRtcClient) {
        this.calculadoraRtcClient = calculadoraRtcClient;
    }

    /**
     * Retorna a versão e status da Calculadora RTC da Receita Federal.
     */
    @GetMapping("/versao")
    public ResponseEntity<VersaoCalculadoraDto> obterVersao() {
        return ResponseEntity.ok(calculadoraRtcClient.obterVersao());
    }

    /**
     * Consulta lista oficial de CSTs de IBS e CBS.
     */
    @GetMapping("/tabelas/cst")
    public ResponseEntity<List<CstIbsCbsDto>> consultarCsts() {
        return ResponseEntity.ok(calculadoraRtcClient.consultarCsts());
    }

    /**
     * Consulta tabela de Códigos de Classificação Tributária (cClassTrib) com filtro opcional.
     */
    @GetMapping("/tabelas/classificacoes")
    public ResponseEntity<List<ClassificacaoTribDto>> consultarClassificacoes(
            @RequestParam(value = "filtro", required = false) String filtro
    ) {
        return ResponseEntity.ok(calculadoraRtcClient.consultarClassificacoes(filtro));
    }

    /**
     * Calcula tributos de um item avulso para simulação instantânea pelo contador.
     */
    @PostMapping("/calcular-item")
    public ResponseEntity<CalculoItemRtcResponse> calcularItem(@RequestBody OperacaoItemRtcRequest item) {
        if (item.getNumeroItem() == null) {
            item.setNumeroItem(1);
        }

        OperacaoRtcRequest op = new OperacaoRtcRequest();
        op.setIdOperacao(UUID.randomUUID().toString());
        op.setItens(List.of(item));

        CalculoRtcResponse resp = calculadoraRtcClient.calcularComObservabilidade(op);
        if (resp != null && !resp.getItens().isEmpty()) {
            return ResponseEntity.ok(resp.getItens().get(0));
        }

        return ResponseEntity.badRequest().build();
    }
}

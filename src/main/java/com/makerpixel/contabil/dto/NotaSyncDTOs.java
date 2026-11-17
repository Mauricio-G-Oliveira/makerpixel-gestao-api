package com.makerpixel.contabil.dto;

import com.makerpixel.contabil.domain.SituacaoNfce;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public class NotaSyncDTOs {

    public record NfceItemDTO(
        String chave,
        Integer numero,
        Integer serie,
        OffsetDateTime dataEmissao,
        BigDecimal valorTotal,
        SituacaoNfce situacao,
        String motivoCancelamento,
        Long nsu
    ) {}

    public record NotasyncExecucaoDTO(
        String cnpj,
        Integer cstat,
        String xmotivo,
        Integer qtdDocumentos,
        Long duracaoMs,
        Long ultNsu,
        String status
    ) {}

    public record EnviarLoteRequest(
        String cnpjEmitente,
        Long ultNuNSU,
        List<NfceItemDTO> documentos,
        NotasyncExecucaoDTO execucao
    ) {}

    public record EnviarLoteResponse(
        int totalProcessadas,
        int inseridas,
        int atualizadas,
        int canceladas,
        String mensagem
    ) {}

    public record ResumoMetricasDTO(
        long totalNotas,
        BigDecimal totalValor,
        long canceladas,
        long autorizadas,
        long pendenciasSat
    ) {}

    public record TraducaoErroDTO(
        int cStat,
        String motivo,
        String acao,
        String severidade,
        boolean destaque
    ) {}
}

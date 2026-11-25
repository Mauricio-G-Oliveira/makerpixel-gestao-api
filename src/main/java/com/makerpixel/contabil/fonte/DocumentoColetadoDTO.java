package com.makerpixel.contabil.fonte;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record DocumentoColetadoDTO(
        String chaveAcesso,
        String modelo,
        int serie,
        int numero,
        OffsetDateTime dataEmissao,
        BigDecimal valorTotal,
        String situacao, // AUTORIZADA, CANCELADA
        String motivoCancelamento,
        String storagePath,
        String conteudoXml,
        String fonteOrigem
) {}

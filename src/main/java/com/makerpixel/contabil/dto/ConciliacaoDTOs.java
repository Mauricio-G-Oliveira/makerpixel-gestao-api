package com.makerpixel.contabil.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public class ConciliacaoDTOs {

    public record SalvarConciliacaoRequest(
            String cnpj,
            String banco,
            String agencia,
            String conta,
            String competencia,
            String extratoJson,
            String planilhaJson,
            String conferidosJson,
            String juncoesManuaisJson,
            String observacoes,
            String resumoJson,
            Long versao
    ) {}

    public record ConciliacaoResponse(
            UUID id,
            String cnpj,
            String banco,
            String agencia,
            String conta,
            String competencia,
            String extratoJson,
            String planilhaJson,
            String conferidosJson,
            String juncoesManuaisJson,
            String observacoes,
            String resumoJson,
            String ultimoUsuarioNome,
            Long versao,
            OffsetDateTime atualizadoEm
    ) {}

    public record ConciliacaoResumoResponse(
            UUID id,
            String cnpj,
            String banco,
            String agencia,
            String conta,
            String competencia,
            String resumoJson,
            String ultimoUsuarioNome,
            Long versao,
            OffsetDateTime atualizadoEm
    ) {}

    public record PerfilConciliacaoDTO(
            String cnpj,
            Integer toleranciaDias,
            String palavrasChaveJson,
            Boolean conciliarEmLote,
            String mapeamentoColunasJson
    ) {}
}

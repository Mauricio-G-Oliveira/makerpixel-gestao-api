package com.makerpixel.contabil.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public class NotasyncNuvemDTOs {

    public record CadastrarEmpresaRequest(
            String cnpj,
            String razaoSocial
    ) {}

    public record EmpresaStatusDTO(
            UUID id,
            String cnpj,
            String razaoSocial,
            boolean ativo,
            long ultNsu,
            OffsetDateTime proximaConsulta,
            Integer ultimoCstat,
            String motivo,
            long totalNotasMes,
            OffsetDateTime atualizadoEm
    ) {}

    public record ImportarCsvResponse(
            int totalProcessadas,
            int cadastradasComSucesso,
            int ignoradasJaCadastradas,
            int errosDv,
            int limiteAtingido,
            List<String> errosDetalhe
    ) {}

    public record ErroEspera429DTO(
            String erro,
            OffsetDateTime permitidoAPartirDe,
            long esperaSegundos,
            String motivo
    ) {}

    public record BuscarAgoraResponse(
            String cnpj,
            int cstat,
            String motivo,
            int documentosNovos,
            long ultNsu,
            OffsetDateTime proximaConsulta,
            String status
    ) {}

    public record CronExecucaoResponse(
            String status,
            int escritoriosProcessados,
            int empresasProcessadas,
            int documentosNovos,
            long duracaoMs,
            List<String> mensagens
    ) {}
}

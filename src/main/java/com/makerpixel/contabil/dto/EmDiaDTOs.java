package com.makerpixel.contabil.dto;

import com.makerpixel.contabil.domain.SetorTarefa;
import com.makerpixel.contabil.domain.StatusTarefa;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public class EmDiaDTOs {

    public record SalvarModeloRequest(
            @NotBlank(message = "Nome da obrigação é obrigatório")
            String nome,

            @NotNull(message = "Setor é obrigatório")
            SetorTarefa setor,

            @Min(1) @Max(31)
            int diaVencimento,

            boolean aplicaSimples,
            boolean aplicaLucroPresumido,
            boolean aplicaLucroReal,
            boolean aplicaMei,
            boolean exigeFuncionario,
            String tipoEmpresa,
            boolean ativo
    ) {}

    public record GerarMesRequest(
            @NotBlank(message = "Mês de competência (AAAA-MM) é obrigatório")
            String mesCompetencia
    ) {}

    public record GerarMesResponse(
            String mesCompetencia,
            int empresasProcessadas,
            int tarefasCriadas,
            int tarefasJaExistentes,
            String mensagem
    ) {}

    public record AtualizarStatusTarefaRequest(
            @NotNull(message = "Status da tarefa é obrigatório")
            StatusTarefa status,
            String responsavel
    ) {}

    public record CriarAnotacaoRequest(
            @NotNull(message = "ID da empresa é obrigatório")
            UUID empresaId,

            @NotBlank(message = "Texto da anotação é obrigatório")
            String texto,

            String anexoUrl,
            String anexoNome
    ) {}

    public record PainelMetricasDTO(
            long totalTarefas,
            long emDia,
            long atrasadas,
            long aguardandoCliente,
            long concluidas,
            long naoSeAplica,
            java.util.Map<String, Long> porSetor,
            java.util.Map<String, Long> porResponsavel
    ) {}

    public record MinhasTarefasResponse(
            List<TarefaItemDTO> hoje,
            List<TarefaItemDTO> atrasadas,
            List<TarefaItemDTO> destaSemana,
            List<TarefaItemDTO> proximas
    ) {}

    public record TarefaItemDTO(
            UUID id,
            UUID empresaId,
            String empresaNome,
            String empresaCnpj,
            String nome,
            SetorTarefa setor,
            String mesCompetencia,
            LocalDate prazo,
            StatusTarefa status,
            String responsavel,
            boolean atrasada,
            OffsetDateTime concluidaEm,
            String concluidaPor,
            String origemAutomatica
    ) {}

    public record ChecklistEmpresaDTO(
            UUID empresaId,
            String empresaNome,
            String empresaCnpj,
            String regime,
            int totalTarefas,
            int concluidas,
            int pendentes,
            int atrasadas,
            List<TarefaItemDTO> tarefas
    ) {}
}

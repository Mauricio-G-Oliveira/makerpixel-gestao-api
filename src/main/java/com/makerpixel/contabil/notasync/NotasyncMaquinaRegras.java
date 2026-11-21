package com.makerpixel.contabil.notasync;

import java.time.Duration;
import java.time.OffsetDateTime;

public class NotasyncMaquinaRegras {

    public record DecisaoConsumo(
            Duration tempoEspera,
            boolean buscarProximoImediato,
            String acao
    ) {}

    public static DecisaoConsumo calcularProximaEspera(int cstat, int qtdDocumentosRetornados, int repeticoesIguais) {
        if (cstat == 118) {
            if (qtdDocumentosRetornados >= 50) {
                // Lote cheio: buscar o próximo lote na hora
                return new DecisaoConsumo(Duration.ZERO, true, "BUSCAR_PROXIMO_IMEDIATO");
            } else {
                // Lote parcial com fim da fila: esperar 12 horas
                return new DecisaoConsumo(Duration.ofHours(12), false, "AGUARDAR_12H");
            }
        }

        if (cstat == 117) {
            // Nenhum documento novo: esperar 12 horas
            return new DecisaoConsumo(Duration.ofHours(12), false, "AGUARDAR_12H");
        }

        if (cstat == 110) {
            // Uso indevido temporário: aguardar 1 hora
            return new DecisaoConsumo(Duration.ofHours(1), false, "AGUARDAR_1H");
        }

        if (cstat == 657) {
            // Consumo indevido: espera escalonada até 3 repetições
            if (repeticoesIguais <= 1) {
                return new DecisaoConsumo(Duration.ofHours(1), false, "AGUARDAR_1H");
            } else if (repeticoesIguais == 2) {
                return new DecisaoConsumo(Duration.ofHours(2), false, "AGUARDAR_2H");
            } else {
                return new DecisaoConsumo(Duration.ofHours(12), false, "AGUARDAR_12H");
            }
        }

        if (cstat == 8002) {
            // Contador sem vínculo no SAT SEF/SC: esperar 24 horas
            return new DecisaoConsumo(Duration.ofHours(24), false, "AGUARDAR_24H_SEM_VINCULO");
        }

        // Outros códigos: espera padrão de 1 hora
        return new DecisaoConsumo(Duration.ofHours(1), false, "AGUARDAR_PADRAO");
    }

    public static boolean podeConsultar(OffsetDateTime proximaConsulta) {
        if (proximaConsulta == null) return true;
        return OffsetDateTime.now().isAfter(proximaConsulta) || OffsetDateTime.now().isEqual(proximaConsulta);
    }

    public static long segundosRestantes(OffsetDateTime proximaConsulta) {
        if (proximaConsulta == null) return 0;
        Duration diff = Duration.between(OffsetDateTime.now(), proximaConsulta);
        return Math.max(0, diff.getSeconds());
    }
}

package com.makerpixel.contabil.dto;

import com.makerpixel.contabil.domain.TipoCertificadoDigital;
import java.time.OffsetDateTime;
import java.util.UUID;

public class CertificadoDTOs {

    public record CertificadoResumoDTO(
            UUID id,
            UUID escritorioId,
            String titularMascarado,
            String documentoTitular,
            TipoCertificadoDigital tipo,
            String emissor,
            OffsetDateTime validoDe,
            OffsetDateTime validoAte,
            long diasParaVencer,
            String statusVencimento, // OK, AVISO_30_DIAS, ALERTA_15_DIAS, CRITICO_7_DIAS, EXPIRADO
            String sha256Original,
            OffsetDateTime termoAceitoEm,
            String termoAceitoPor,
            String termoVersao,
            String enviadoPor,
            int versaoChaveMestra,
            boolean ativo,
            OffsetDateTime criadoEm
    ) {}

    public record RotacionarChaveRequest(
            String novaChaveMestra
    ) {}

    public record RotacionarChaveResponse(
            int totalCertificadosAtualizados,
            int novaVersao,
            String mensagem
    ) {}

    public record AuditoriaCertDTO(
            UUID id,
            UUID escritorioId,
            UUID certificadoId,
            String operacao,
            String usuario,
            boolean sucesso,
            String detalhe,
            String ip,
            OffsetDateTime executadoEm
    ) {}

    public record TermoUsoCertificadoDTO(
            String versao,
            String titulo,
            String conteudo,
            String dataPublicacao
    ) {}
}

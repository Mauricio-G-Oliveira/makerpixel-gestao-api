package com.makerpixel.contabil.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "notasync_execucao")
public class NotasyncExecucao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "escritorio_id", nullable = false)
    private UUID escritorioId;

    @Column(name = "empresa_id")
    private UUID empresaId;

    @Column(nullable = false, length = 14)
    private String cnpj;

    @Column(name = "data_hora", nullable = false)
    private OffsetDateTime dataHora;

    @Column(nullable = false)
    private Integer cstat;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String xmotivo;

    @Column(name = "qtd_documentos", nullable = false)
    private Integer qtdDocumentos = 0;

    @Column(name = "duracao_ms", nullable = false)
    private Long duracaoMs = 0L;

    @Column(name = "ult_nsu")
    private Long ultNsu = 0L;

    @Column(nullable = false, length = 30)
    private String status = "OK";

    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm;

    @PrePersist
    public void prePersist() {
        if (dataHora == null) dataHora = OffsetDateTime.now();
        if (criadoEm == null) criadoEm = OffsetDateTime.now();
        if (qtdDocumentos == null) qtdDocumentos = 0;
        if (duracaoMs == null) duracaoMs = 0L;
        if (ultNsu == null) ultNsu = 0L;
        if (status == null) status = "OK";
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getEscritorioId() { return escritorioId; }
    public void setEscritorioId(UUID escritorioId) { this.escritorioId = escritorioId; }

    public UUID getEmpresaId() { return empresaId; }
    public void setEmpresaId(UUID empresaId) { this.empresaId = empresaId; }

    public String getCnpj() { return cnpj; }
    public void setCnpj(String cnpj) { this.cnpj = cnpj; }

    public OffsetDateTime getDataHora() { return dataHora; }
    public void setDataHora(OffsetDateTime dataHora) { this.dataHora = dataHora; }

    public Integer getCstat() { return cstat; }
    public void setCstat(Integer cstat) { this.cstat = cstat; }

    public String getXmotivo() { return xmotivo; }
    public void setXmotivo(String xmotivo) { this.xmotivo = xmotivo; }

    public Integer getQtdDocumentos() { return qtdDocumentos; }
    public void setQtdDocumentos(Integer qtdDocumentos) { this.qtdDocumentos = qtdDocumentos; }

    public Long getDuracaoMs() { return duracaoMs; }
    public void setDuracaoMs(Long duracaoMs) { this.duracaoMs = duracaoMs; }

    public Long getUltNsu() { return ultNsu; }
    public void setUltNsu(Long ultNsu) { this.ultNsu = ultNsu; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public OffsetDateTime getCriadoEm() { return criadoEm; }
}

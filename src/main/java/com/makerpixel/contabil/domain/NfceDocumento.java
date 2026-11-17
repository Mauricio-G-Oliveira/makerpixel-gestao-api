package com.makerpixel.contabil.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "nfce_documento", uniqueConstraints = {
    @UniqueConstraint(name = "uk_nfce_documento_chave", columnNames = {"escritorio_id", "chave"})
})
public class NfceDocumento {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "escritorio_id", nullable = false)
    private UUID escritorioId;

    @Column(name = "empresa_id")
    private UUID empresaId;

    @Column(nullable = false, length = 44)
    private String chave;

    @Column(name = "cnpj_emitente", nullable = false, length = 14)
    private String cnpjEmitente;

    @Column(nullable = false)
    private Integer numero;

    @Column(nullable = false)
    private Integer serie = 1;

    @Column(name = "data_emissao", nullable = false)
    private OffsetDateTime dataEmissao;

    @Column(name = "valor_total", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorTotal = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SituacaoNfce situacao = SituacaoNfce.AUTORIZADA;

    @Column(name = "motivo_cancelamento", columnDefinition = "TEXT")
    private String motivoCancelamento;

    private Long nsu;

    @Column(nullable = false, length = 50)
    private String origem = "SEF_SC_WS";

    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private OffsetDateTime atualizadoEm;

    @PrePersist
    public void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        if (criadoEm == null) criadoEm = now;
        atualizadoEm = now;
        if (serie == null) serie = 1;
        if (origem == null) origem = "SEF_SC_WS";
        if (situacao == null) situacao = SituacaoNfce.AUTORIZADA;
    }

    @PreUpdate
    public void preUpdate() {
        atualizadoEm = OffsetDateTime.now();
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getEscritorioId() { return escritorioId; }
    public void setEscritorioId(UUID escritorioId) { this.escritorioId = escritorioId; }

    public UUID getEmpresaId() { return empresaId; }
    public void setEmpresaId(UUID empresaId) { this.empresaId = empresaId; }

    public String getChave() { return chave; }
    public void setChave(String chave) { this.chave = chave; }

    public String getCnpjEmitente() { return cnpjEmitente; }
    public void setCnpjEmitente(String cnpjEmitente) { this.cnpjEmitente = cnpjEmitente; }

    public Integer getNumero() { return numero; }
    public void setNumero(Integer numero) { this.numero = numero; }

    public Integer getSerie() { return serie; }
    public void setSerie(Integer serie) { this.serie = serie; }

    public OffsetDateTime getDataEmissao() { return dataEmissao; }
    public void setDataEmissao(OffsetDateTime dataEmissao) { this.dataEmissao = dataEmissao; }

    public BigDecimal getValorTotal() { return valorTotal; }
    public void setValorTotal(BigDecimal valorTotal) { this.valorTotal = valorTotal; }

    public SituacaoNfce getSituacao() { return situacao; }
    public void setSituacao(SituacaoNfce situacao) { this.situacao = situacao; }

    public String getMotivoCancelamento() { return motivoCancelamento; }
    public void setMotivoCancelamento(String motivoCancelamento) { this.motivoCancelamento = motivoCancelamento; }

    public Long getNsu() { return nsu; }
    public void setNsu(Long nsu) { this.nsu = nsu; }

    public String getOrigem() { return origem; }
    public void setOrigem(String origem) { this.origem = origem; }

    public OffsetDateTime getCriadoEm() { return criadoEm; }
    public OffsetDateTime getAtualizadoEm() { return atualizadoEm; }
}

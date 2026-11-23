package com.makerpixel.contabil.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "reforma_lote")
public class ReformaLote {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "escritorio_id", nullable = false)
    private UUID escritorioId;

    @Column(name = "empresa_id")
    private UUID empresaId;

    @Column(length = 7)
    private String competencia;

    @Column(nullable = false, length = 30)
    private String origem; // UPLOAD_XML, UPLOAD_ZIP, NOTASYNC

    @Column(name = "total_notas", nullable = false)
    private Integer totalNotas = 0;

    @Column(name = "total_itens", nullable = false)
    private Integer totalItens = 0;

    @Column(name = "total_ok", nullable = false)
    private Integer totalOk = 0;

    @Column(name = "total_divergencias", nullable = false)
    private Integer totalDivergencias = 0;

    @Column(name = "total_erros", nullable = false)
    private Integer totalErros = 0;

    @Column(name = "total_sem_destaque", nullable = false)
    private Integer totalSemDestaque = 0;

    @Column(name = "valor_total_itens", nullable = false)
    private BigDecimal valorTotalItens = BigDecimal.ZERO;

    @Column(name = "valor_total_cbs", nullable = false)
    private BigDecimal valorTotalCbs = BigDecimal.ZERO;

    @Column(name = "valor_total_ibs", nullable = false)
    private BigDecimal valorTotalIbs = BigDecimal.ZERO;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm = OffsetDateTime.now();

    @Column(name = "atualizado_em", nullable = false)
    private OffsetDateTime atualizadoEm = OffsetDateTime.now();

    public ReformaLote() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getEscritorioId() { return escritorioId; }
    public void setEscritorioId(UUID escritorioId) { this.escritorioId = escritorioId; }

    public UUID getEmpresaId() { return empresaId; }
    public void setEmpresaId(UUID empresaId) { this.empresaId = empresaId; }

    public String getCompetencia() { return competencia; }
    public void setCompetencia(String competencia) { this.competencia = competencia; }

    public String getOrigem() { return origem; }
    public void setOrigem(String origem) { this.origem = origem; }

    public Integer getTotalNotas() { return totalNotas; }
    public void setTotalNotas(Integer totalNotas) { this.totalNotas = totalNotas; }

    public Integer getTotalItens() { return totalItens; }
    public void setTotalItens(Integer totalItens) { this.totalItens = totalItens; }

    public Integer getTotalOk() { return totalOk; }
    public void setTotalOk(Integer totalOk) { this.totalOk = totalOk; }

    public Integer getTotalDivergencias() { return totalDivergencias; }
    public void setTotalDivergencias(Integer totalDivergencias) { this.totalDivergencias = totalDivergencias; }

    public Integer getTotalErros() { return totalErros; }
    public void setTotalErros(Integer totalErros) { this.totalErros = totalErros; }

    public Integer getTotalSemDestaque() { return totalSemDestaque; }
    public void setTotalSemDestaque(Integer totalSemDestaque) { this.totalSemDestaque = totalSemDestaque; }

    public BigDecimal getValorTotalItens() { return valorTotalItens; }
    public void setValorTotalItens(BigDecimal valorTotalItens) { this.valorTotalItens = valorTotalItens; }

    public BigDecimal getValorTotalCbs() { return valorTotalCbs; }
    public void setValorTotalCbs(BigDecimal valorTotalCbs) { this.valorTotalCbs = valorTotalCbs; }

    public BigDecimal getValorTotalIbs() { return valorTotalIbs; }
    public void setValorTotalIbs(BigDecimal valorTotalIbs) { this.valorTotalIbs = valorTotalIbs; }

    public OffsetDateTime getCriadoEm() { return criadoEm; }
    public void setCriadoEm(OffsetDateTime criadoEm) { this.criadoEm = criadoEm; }

    public OffsetDateTime getAtualizadoEm() { return atualizadoEm; }
    public void setAtualizadoEm(OffsetDateTime atualizadoEm) { this.atualizadoEm = atualizadoEm; }
}

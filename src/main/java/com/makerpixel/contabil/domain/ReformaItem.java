package com.makerpixel.contabil.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "reforma_item")
public class ReformaItem {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lote_id", nullable = false)
    private ReformaLote lote;

    @Column(name = "escritorio_id", nullable = false)
    private UUID escritorioId;

    @Column(name = "chave_nfe", nullable = false, length = 44)
    private String chaveNfe;

    @Column(name = "numero_nota", nullable = false, length = 20)
    private String numeroNota;

    @Column(nullable = false, length = 5)
    private String modelo = "55";

    @Column(name = "data_emissao")
    private OffsetDateTime dataEmissao;

    @Column(name = "n_item", nullable = false)
    private Integer nItem;

    @Column(name = "codigo_produto", length = 60)
    private String codigoProduto;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String descricao;

    @Column(length = 10)
    private String ncm;

    @Column(length = 10)
    private String cfop;

    @Column(name = "cst_informado", length = 5)
    private String cstInformado;

    @Column(name = "cst_calculado", length = 5)
    private String cstCalculado;

    @Column(name = "cclass_trib", length = 10)
    private String cClassTrib;

    @Column(name = "valor_item", nullable = false)
    private BigDecimal valorItem = BigDecimal.ZERO;

    @Column(name = "base_calculo", nullable = false)
    private BigDecimal baseCalculo = BigDecimal.ZERO;

    @Column(name = "cbs_destacado", nullable = false)
    private BigDecimal cbsDestacado = BigDecimal.ZERO;

    @Column(name = "cbs_calculado", nullable = false)
    private BigDecimal cbsCalculado = BigDecimal.ZERO;

    @Column(name = "ibs_uf_destacado", nullable = false)
    private BigDecimal ibsUfDestacado = BigDecimal.ZERO;

    @Column(name = "ibs_uf_calculado", nullable = false)
    private BigDecimal ibsUfCalculado = BigDecimal.ZERO;

    @Column(name = "ibs_mun_destacado", nullable = false)
    private BigDecimal ibsMunDestacado = BigDecimal.ZERO;

    @Column(name = "ibs_mun_calculado", nullable = false)
    private BigDecimal ibsMunCalculado = BigDecimal.ZERO;

    @Column(name = "diferenca_total", nullable = false)
    private BigDecimal diferencaTotal = BigDecimal.ZERO;

    @Column(nullable = false, length = 30)
    private String classificacao; // OK, DIVERGENCIA_VALOR, ENQUADRAMENTO_INVALIDO, SEM_GRUPO_IBSCBS, ERRO_CALCULO

    @Column(name = "motivo_divergencia", columnDefinition = "TEXT")
    private String motivoDivergencia;

    @Column(name = "memoria_calculo", columnDefinition = "TEXT")
    private String memoriaCalculo;

    @Column(name = "fundamentacao_legal", columnDefinition = "TEXT")
    private String fundamentacaoLegal;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm = OffsetDateTime.now();

    public ReformaItem() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public ReformaLote getLote() { return lote; }
    public void setLote(ReformaLote lote) { this.lote = lote; }

    public UUID getEscritorioId() { return escritorioId; }
    public void setEscritorioId(UUID escritorioId) { this.escritorioId = escritorioId; }

    public String getChaveNfe() { return chaveNfe; }
    public void setChaveNfe(String chaveNfe) { this.chaveNfe = chaveNfe; }

    public String getNumeroNota() { return numeroNota; }
    public void setNumeroNota(String numeroNota) { this.numeroNota = numeroNota; }

    public String getModelo() { return modelo; }
    public void setModelo(String modelo) { this.modelo = modelo; }

    public OffsetDateTime getDataEmissao() { return dataEmissao; }
    public void setDataEmissao(OffsetDateTime dataEmissao) { this.dataEmissao = dataEmissao; }

    public Integer getnItem() { return nItem; }
    public void setnItem(Integer nItem) { this.nItem = nItem; }

    public String getCodigoProduto() { return codigoProduto; }
    public void setCodigoProduto(String codigoProduto) { this.codigoProduto = codigoProduto; }

    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }

    public String getNcm() { return ncm; }
    public void setNcm(String ncm) { this.ncm = ncm; }

    public String getCfop() { return cfop; }
    public void setCfop(String cfop) { this.cfop = cfop; }

    public String getCstInformado() { return cstInformado; }
    public void setCstInformado(String cstInformado) { this.cstInformado = cstInformado; }

    public String getCstCalculado() { return cstCalculado; }
    public void setCstCalculado(String cstCalculado) { this.cstCalculado = cstCalculado; }

    public String getcClassTrib() { return cClassTrib; }
    public void setcClassTrib(String cClassTrib) { this.cClassTrib = cClassTrib; }

    public BigDecimal getValorItem() { return valorItem; }
    public void setValorItem(BigDecimal valorItem) { this.valorItem = valorItem; }

    public BigDecimal getBaseCalculo() { return baseCalculo; }
    public void setBaseCalculo(BigDecimal baseCalculo) { this.baseCalculo = baseCalculo; }

    public BigDecimal getCbsDestacado() { return cbsDestacado; }
    public void setCbsDestacado(BigDecimal cbsDestacado) { this.cbsDestacado = cbsDestacado; }

    public BigDecimal getCbsCalculado() { return cbsCalculado; }
    public void setCbsCalculado(BigDecimal cbsCalculado) { this.cbsCalculado = cbsCalculado; }

    public BigDecimal getIbsUfDestacado() { return ibsUfDestacado; }
    public void setIbsUfDestacado(BigDecimal ibsUfDestacado) { this.ibsUfDestacado = ibsUfDestacado; }

    public BigDecimal getIbsUfCalculado() { return ibsUfCalculado; }
    public void setIbsUfCalculado(BigDecimal ibsUfCalculado) { this.ibsUfCalculado = ibsUfCalculado; }

    public BigDecimal getIbsMunDestacado() { return ibsMunDestacado; }
    public void setIbsMunDestacado(BigDecimal ibsMunDestacado) { this.ibsMunDestacado = ibsMunDestacado; }

    public BigDecimal getIbsMunCalculado() { return ibsMunCalculado; }
    public void setIbsMunCalculado(BigDecimal ibsMunCalculado) { this.ibsMunCalculado = ibsMunCalculado; }

    public BigDecimal getDiferencaTotal() { return diferencaTotal; }
    public void setDiferencaTotal(BigDecimal diferencaTotal) { this.diferencaTotal = diferencaTotal; }

    public String getClassificacao() { return classificacao; }
    public void setClassificacao(String classificacao) { this.classificacao = classificacao; }

    public String getMotivoDivergencia() { return motivoDivergencia; }
    public void setMotivoDivergencia(String motivoDivergencia) { this.motivoDivergencia = motivoDivergencia; }

    public String getMemoriaCalculo() { return memoriaCalculo; }
    public void setMemoriaCalculo(String memoriaCalculo) { this.memoriaCalculo = memoriaCalculo; }

    public String getFundamentacaoLegal() { return fundamentacaoLegal; }
    public void setFundamentacaoLegal(String fundamentacaoLegal) { this.fundamentacaoLegal = fundamentacaoLegal; }

    public OffsetDateTime getCriadoEm() { return criadoEm; }
    public void setCriadoEm(OffsetDateTime criadoEm) { this.criadoEm = criadoEm; }
}

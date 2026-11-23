package com.makerpixel.contabil.reforma.dto;

import java.math.BigDecimal;

public class OperacaoItemRtcRequest {
    private Integer numeroItem;
    private String codigoProduto;
    private String descricao;
    private String ncm;
    private String cfop;
    private String cst;
    private String cClassTrib;
    private BigDecimal quantidade;
    private BigDecimal valorTotal;
    private BigDecimal baseCalculo;
    private String ufOrigem;
    private String municipioOrigem;
    private String ufDestino;
    private String municipioDestino;

    public OperacaoItemRtcRequest() {}

    public Integer getNumeroItem() { return numeroItem; }
    public void setNumeroItem(Integer numeroItem) { this.numeroItem = numeroItem; }

    public String getCodigoProduto() { return codigoProduto; }
    public void setCodigoProduto(String codigoProduto) { this.codigoProduto = codigoProduto; }

    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }

    public String getNcm() { return ncm; }
    public void setNcm(String ncm) { this.ncm = ncm; }

    public String getCfop() { return cfop; }
    public void setCfop(String cfop) { this.cfop = cfop; }

    public String getCst() { return cst; }
    public void setCst(String cst) { this.cst = cst; }

    public String getcClassTrib() { return cClassTrib; }
    public void setcClassTrib(String cClassTrib) { this.cClassTrib = cClassTrib; }

    public BigDecimal getQuantidade() { return quantidade; }
    public void setQuantidade(BigDecimal quantidade) { this.quantidade = quantidade; }

    public BigDecimal getValorTotal() { return valorTotal; }
    public void setValorTotal(BigDecimal valorTotal) { this.valorTotal = valorTotal; }

    public BigDecimal getBaseCalculo() { return baseCalculo; }
    public void setBaseCalculo(BigDecimal baseCalculo) { this.baseCalculo = baseCalculo; }

    public String getUfOrigem() { return ufOrigem; }
    public void setUfOrigem(String ufOrigem) { this.ufOrigem = ufOrigem; }

    public String getMunicipioOrigem() { return municipioOrigem; }
    public void setMunicipioOrigem(String municipioOrigem) { this.municipioOrigem = municipioOrigem; }

    public String getUfDestino() { return ufDestino; }
    public void setUfDestino(String ufDestino) { this.ufDestino = ufDestino; }

    public String getMunicipioDestino() { return municipioDestino; }
    public void setMunicipioDestino(String municipioDestino) { this.municipioDestino = municipioDestino; }
}

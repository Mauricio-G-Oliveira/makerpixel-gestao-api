package com.makerpixel.contabil.reforma.dto;

import java.math.BigDecimal;

public class CalculoItemRtcResponse {
    private Integer numeroItem;
    private String status; // CALCULADO | INCONSISTENCIA_ENTRADA | NAO_APLICAVEL | FALHA_TECNICA
    private String cstCalculado;
    private String cClassTrib;
    private BigDecimal baseCalculo;
    private BigDecimal aliqCbs;
    private BigDecimal valorCbs;
    private BigDecimal aliqIbsUf;
    private BigDecimal valorIbsUf;
    private BigDecimal aliqIbsMun;
    private BigDecimal valorIbsMun;
    private BigDecimal valorIbsTotal;
    private BigDecimal aliqEfetivaTotal;
    private BigDecimal percentualReducao;
    private String memoriaCalculo;
    private String fundamentacaoLegal;
    private String mensagemInconsistencia;

    public CalculoItemRtcResponse() {}

    public Integer getNumeroItem() { return numeroItem; }
    public void setNumeroItem(Integer numeroItem) { this.numeroItem = numeroItem; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCstCalculado() { return cstCalculado; }
    public void setCstCalculado(String cstCalculado) { this.cstCalculado = cstCalculado; }

    public String getcClassTrib() { return cClassTrib; }
    public void setcClassTrib(String cClassTrib) { this.cClassTrib = cClassTrib; }

    public BigDecimal getBaseCalculo() { return baseCalculo; }
    public void setBaseCalculo(BigDecimal baseCalculo) { this.baseCalculo = baseCalculo; }

    public BigDecimal getAliqCbs() { return aliqCbs; }
    public void setAliqCbs(BigDecimal aliqCbs) { this.aliqCbs = aliqCbs; }

    public BigDecimal getValorCbs() { return valorCbs; }
    public void setValorCbs(BigDecimal valorCbs) { this.valorCbs = valorCbs; }

    public BigDecimal getAliqIbsUf() { return aliqIbsUf; }
    public void setAliqIbsUf(BigDecimal aliqIbsUf) { this.aliqIbsUf = aliqIbsUf; }

    public BigDecimal getValorIbsUf() { return valorIbsUf; }
    public void setValorIbsUf(BigDecimal valorIbsUf) { this.valorIbsUf = valorIbsUf; }

    public BigDecimal getAliqIbsMun() { return aliqIbsMun; }
    public void setAliqIbsMun(BigDecimal aliqIbsMun) { this.aliqIbsMun = aliqIbsMun; }

    public BigDecimal getValorIbsMun() { return valorIbsMun; }
    public void setValorIbsMun(BigDecimal valorIbsMun) { this.valorIbsMun = valorIbsMun; }

    public BigDecimal getValorIbsTotal() { return valorIbsTotal; }
    public void setValorIbsTotal(BigDecimal valorIbsTotal) { this.valorIbsTotal = valorIbsTotal; }

    public BigDecimal getAliqEfetivaTotal() { return aliqEfetivaTotal; }
    public void setAliqEfetivaTotal(BigDecimal aliqEfetivaTotal) { this.aliqEfetivaTotal = aliqEfetivaTotal; }

    public BigDecimal getPercentualReducao() { return percentualReducao; }
    public void setPercentualReducao(BigDecimal percentualReducao) { this.percentualReducao = percentualReducao; }

    public String getMemoriaCalculo() { return memoriaCalculo; }
    public void setMemoriaCalculo(String memoriaCalculo) { this.memoriaCalculo = memoriaCalculo; }

    public String getFundamentacaoLegal() { return fundamentacaoLegal; }
    public void setFundamentacaoLegal(String fundamentacaoLegal) { this.fundamentacaoLegal = fundamentacaoLegal; }

    public String getMensagemInconsistencia() { return mensagemInconsistencia; }
    public void setMensagemInconsistencia(String mensagemInconsistencia) { this.mensagemInconsistencia = mensagemInconsistencia; }
}

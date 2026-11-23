package com.makerpixel.contabil.reforma.dto;

public class CstIbsCbsDto {
    private String codigo;
    private String descricao;
    private String tipoTributacao; // TRIBUTADA | REDUZIDA | ISENTA | IMUNE | SUSPENSA | NAO_INCIDENTE

    public CstIbsCbsDto() {}

    public CstIbsCbsDto(String codigo, String descricao, String tipoTributacao) {
        this.codigo = codigo;
        this.descricao = descricao;
        this.tipoTributacao = tipoTributacao;
    }

    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }

    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }

    public String getTipoTributacao() { return tipoTributacao; }
    public void setTipoTributacao(String tipoTributacao) { this.tipoTributacao = tipoTributacao; }
}

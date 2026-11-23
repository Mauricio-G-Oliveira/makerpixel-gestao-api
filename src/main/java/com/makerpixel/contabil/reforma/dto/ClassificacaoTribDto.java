package com.makerpixel.contabil.reforma.dto;

import java.math.BigDecimal;
import java.util.List;

public class ClassificacaoTribDto {
    private String cClassTrib;
    private String descricao;
    private String cstPadrao;
    private BigDecimal percentualReducao;
    private List<String> ncmsPermitidas;
    private String fundamentacaoLegal;

    public ClassificacaoTribDto() {}

    public ClassificacaoTribDto(String cClassTrib, String descricao, String cstPadrao, BigDecimal percentualReducao, List<String> ncmsPermitidas, String fundamentacaoLegal) {
        this.cClassTrib = cClassTrib;
        this.descricao = descricao;
        this.cstPadrao = cstPadrao;
        this.percentualReducao = percentualReducao;
        this.ncmsPermitidas = ncmsPermitidas;
        this.fundamentacaoLegal = fundamentacaoLegal;
    }

    public String getcClassTrib() { return cClassTrib; }
    public void setcClassTrib(String cClassTrib) { this.cClassTrib = cClassTrib; }

    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }

    public String getCstPadrao() { return cstPadrao; }
    public void setCstPadrao(String cstPadrao) { this.cstPadrao = cstPadrao; }

    public BigDecimal getPercentualReducao() { return percentualReducao; }
    public void setPercentualReducao(BigDecimal percentualReducao) { this.percentualReducao = percentualReducao; }

    public List<String> getNcmsPermitidas() { return ncmsPermitidas; }
    public void setNcmsPermitidas(List<String> ncmsPermitidas) { this.ncmsPermitidas = ncmsPermitidas; }

    public String getFundamentacaoLegal() { return fundamentacaoLegal; }
    public void setFundamentacaoLegal(String fundamentacaoLegal) { this.fundamentacaoLegal = fundamentacaoLegal; }
}

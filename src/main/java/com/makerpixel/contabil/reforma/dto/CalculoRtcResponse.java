package com.makerpixel.contabil.reforma.dto;

import java.util.ArrayList;
import java.util.List;

public class CalculoRtcResponse {
    private String idOperacao;
    private String statusGeral;
    private List<CalculoItemRtcResponse> itens = new ArrayList<>();

    public CalculoRtcResponse() {}

    public String getIdOperacao() { return idOperacao; }
    public void setIdOperacao(String idOperacao) { this.idOperacao = idOperacao; }

    public String getStatusGeral() { return statusGeral; }
    public void setStatusGeral(String statusGeral) { this.statusGeral = statusGeral; }

    public List<CalculoItemRtcResponse> getItens() { return itens; }
    public void setItens(List<CalculoItemRtcResponse> itens) { this.itens = itens; }
}

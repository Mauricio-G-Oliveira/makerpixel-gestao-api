package com.makerpixel.contabil.reforma.dto;

import java.util.ArrayList;
import java.util.List;

public class OperacaoRtcRequest {
    private String idOperacao;
    private String dataEmissao;
    private String modeloDocumento;
    private List<OperacaoItemRtcRequest> itens = new ArrayList<>();

    public OperacaoRtcRequest() {}

    public String getIdOperacao() { return idOperacao; }
    public void setIdOperacao(String idOperacao) { this.idOperacao = idOperacao; }

    public String getDataEmissao() { return dataEmissao; }
    public void setDataEmissao(String dataEmissao) { this.dataEmissao = dataEmissao; }

    public String getModeloDocumento() { return modeloDocumento; }
    public void setModeloDocumento(String modeloDocumento) { this.modeloDocumento = modeloDocumento; }

    public List<OperacaoItemRtcRequest> getItens() { return itens; }
    public void setItens(List<OperacaoItemRtcRequest> itens) { this.itens = itens; }
}

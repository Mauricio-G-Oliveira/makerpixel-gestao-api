package com.makerpixel.contabil.reforma.dto;

public class VersaoCalculadoraDto {
    private String versaoMotor;
    private String versaoRegras;
    private String statusServico; // ONLINE | MOCK_OFFLINE | INDISPONIVEL
    private String urlServico;
    private String dataAtualizacaoRegras;
    private String observacao;

    public VersaoCalculadoraDto() {}

    public VersaoCalculadoraDto(String versaoMotor, String versaoRegras, String statusServico, String urlServico, String dataAtualizacaoRegras, String observacao) {
        this.versaoMotor = versaoMotor;
        this.versaoRegras = versaoRegras;
        this.statusServico = statusServico;
        this.urlServico = urlServico;
        this.dataAtualizacaoRegras = dataAtualizacaoRegras;
        this.observacao = observacao;
    }

    public String getVersaoMotor() { return versaoMotor; }
    public void setVersaoMotor(String versaoMotor) { this.versaoMotor = versaoMotor; }

    public String getVersaoRegras() { return versaoRegras; }
    public void setVersaoRegras(String versaoRegras) { this.versaoRegras = versaoRegras; }

    public String getStatusServico() { return statusServico; }
    public void setStatusServico(String statusServico) { this.statusServico = statusServico; }

    public String getUrlServico() { return urlServico; }
    public void setUrlServico(String urlServico) { this.urlServico = urlServico; }

    public String getDataAtualizacaoRegras() { return dataAtualizacaoRegras; }
    public void setDataAtualizacaoRegras(String dataAtualizacaoRegras) { this.dataAtualizacaoRegras = dataAtualizacaoRegras; }

    public String getObservacao() { return observacao; }
    public void setObservacao(String observacao) { this.observacao = observacao; }
}

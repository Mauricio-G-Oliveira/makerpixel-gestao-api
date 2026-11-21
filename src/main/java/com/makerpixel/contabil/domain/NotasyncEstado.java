package com.makerpixel.contabil.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "notasync_estado")
public class NotasyncEstado {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "escritorio_id", nullable = false)
    private UUID escritorioId;

    @Column(name = "cnpj", nullable = false, length = 14)
    private String cnpj;

    @Column(name = "fonte", nullable = false, length = 50)
    private String fonte = "sef-sc-nfce";

    @Column(name = "ult_nsu", nullable = false)
    private long ultNsu = 0;

    @Column(name = "proxima_consulta")
    private OffsetDateTime proximaConsulta;

    @Column(name = "tentativas_iguais", nullable = false)
    private int tentativasIguais = 0;

    @Column(name = "ultimo_cstat")
    private Integer ultimoCstat;

    @Column(name = "motivo", columnDefinition = "TEXT")
    private String motivo;

    @Column(name = "atualizado_em", nullable = false)
    private OffsetDateTime atualizadoEm = OffsetDateTime.now();

    public NotasyncEstado() {
    }

    public NotasyncEstado(UUID escritorioId, String cnpj, String fonte) {
        this.escritorioId = escritorioId;
        this.cnpj = cnpj;
        this.fonte = fonte != null ? fonte : "sef-sc-nfce";
        this.ultNsu = 0;
        this.tentativasIguais = 0;
        this.atualizadoEm = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getEscritorioId() {
        return escritorioId;
    }

    public void setEscritorioId(UUID escritorioId) {
        this.escritorioId = escritorioId;
    }

    public String getCnpj() {
        return cnpj;
    }

    public void setCnpj(String cnpj) {
        this.cnpj = cnpj;
    }

    public String getFonte() {
        return fonte;
    }

    public void setFonte(String fonte) {
        this.fonte = fonte;
    }

    public long getUltNsu() {
        return ultNsu;
    }

    public void setUltNsu(long ultNsu) {
        this.ultNsu = ultNsu;
    }

    public OffsetDateTime getProximaConsulta() {
        return proximaConsulta;
    }

    public void setProximaConsulta(OffsetDateTime proximaConsulta) {
        this.proximaConsulta = proximaConsulta;
    }

    public int getTentativasIguais() {
        return tentativasIguais;
    }

    public void setTentativasIguais(int tentativasIguais) {
        this.tentativasIguais = tentativasIguais;
    }

    public Integer getUltimoCstat() {
        return ultimoCstat;
    }

    public void setUltimoCstat(Integer ultimoCstat) {
        this.ultimoCstat = ultimoCstat;
    }

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }

    public OffsetDateTime getAtualizadoEm() {
        return atualizadoEm;
    }

    public void setAtualizadoEm(OffsetDateTime atualizadoEm) {
        this.atualizadoEm = atualizadoEm;
    }
}

package com.makerpixel.contabil.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "sieg_config")
public class SiegConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "escritorio_id", nullable = false, unique = true)
    private UUID escritorioId;

    @Column(name = "api_key_cifrada", nullable = false)
    private byte[] apiKeyCifrada;

    @Column(name = "iv_dados", nullable = false)
    private byte[] ivDados;

    @Column(name = "ultimos_quatro", nullable = false, length = 4)
    private String ultimosQuatro;

    @Column(name = "ativo", nullable = false)
    private boolean ativo = true;

    @Column(name = "ultima_busca")
    private OffsetDateTime ultimaBusca;

    @Column(name = "ultimo_status", length = 50)
    private String ultimoStatus;

    @Column(name = "ultimo_erro", columnDefinition = "TEXT")
    private String ultimoErro;

    @Column(name = "criado_em", nullable = false)
    private OffsetDateTime criadoEm = OffsetDateTime.now();

    @Column(name = "atualizado_em", nullable = false)
    private OffsetDateTime atualizadoEm = OffsetDateTime.now();

    public SiegConfig() {
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

    public byte[] getApiKeyCifrada() {
        return apiKeyCifrada;
    }

    public void setApiKeyCifrada(byte[] apiKeyCifrada) {
        this.apiKeyCifrada = apiKeyCifrada;
    }

    public byte[] getIvDados() {
        return ivDados;
    }

    public void setIvDados(byte[] ivDados) {
        this.ivDados = ivDados;
    }

    public String getUltimosQuatro() {
        return ultimosQuatro;
    }

    public void setUltimosQuatro(String ultimosQuatro) {
        this.ultimosQuatro = ultimosQuatro;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
    }

    public OffsetDateTime getUltimaBusca() {
        return ultimaBusca;
    }

    public void setUltimaBusca(OffsetDateTime ultimaBusca) {
        this.ultimaBusca = ultimaBusca;
    }

    public String getUltimoStatus() {
        return ultimoStatus;
    }

    public void setUltimoStatus(String ultimoStatus) {
        this.ultimoStatus = ultimoStatus;
    }

    public String getUltimoErro() {
        return ultimoErro;
    }

    public void setUltimoErro(String ultimoErro) {
        this.ultimoErro = ultimoErro;
    }

    public OffsetDateTime getCriadoEm() {
        return criadoEm;
    }

    public void setCriadoEm(OffsetDateTime criadoEm) {
        this.criadoEm = criadoEm;
    }

    public OffsetDateTime getAtualizadoEm() {
        return atualizadoEm;
    }

    public void setAtualizadoEm(OffsetDateTime atualizadoEm) {
        this.atualizadoEm = atualizadoEm;
    }
}

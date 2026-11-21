package com.makerpixel.contabil.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "notasync_empresa")
public class NotasyncEmpresa {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "escritorio_id", nullable = false)
    private UUID escritorioId;

    @Column(name = "cnpj", nullable = false, length = 14)
    private String cnpj;

    @Column(name = "razao_social", nullable = false)
    private String razaoSocial;

    @Column(name = "ativo", nullable = false)
    private boolean ativo = true;

    @Column(name = "fontes_ativas")
    private String[] fontesAtivas = new String[]{"sef-sc-nfce"};

    @Column(name = "manifestacao_automatica", nullable = false)
    private boolean manifestacaoAutomatica = false;

    @Column(name = "criado_em", nullable = false)
    private OffsetDateTime criadoEm = OffsetDateTime.now();

    @Column(name = "atualizado_em", nullable = false)
    private OffsetDateTime atualizadoEm = OffsetDateTime.now();

    public NotasyncEmpresa() {
    }

    public NotasyncEmpresa(UUID escritorioId, String cnpj, String razaoSocial, boolean ativo) {
        this.escritorioId = escritorioId;
        this.cnpj = cnpj;
        this.razaoSocial = razaoSocial;
        this.ativo = ativo;
        this.criadoEm = OffsetDateTime.now();
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

    public String getRazaoSocial() {
        return razaoSocial;
    }

    public void setRazaoSocial(String razaoSocial) {
        this.razaoSocial = razaoSocial;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
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

    public String[] getFontesAtivas() {
        return fontesAtivas;
    }

    public void setFontesAtivas(String[] fontesAtivas) {
        this.fontesAtivas = fontesAtivas;
    }

    public boolean isManifestacaoAutomatica() {
        return manifestacaoAutomatica;
    }

    public void setManifestacaoAutomatica(boolean manifestacaoAutomatica) {
        this.manifestacaoAutomatica = manifestacaoAutomatica;
    }
}

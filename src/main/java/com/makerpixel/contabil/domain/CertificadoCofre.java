package com.makerpixel.contabil.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "certificado_cofre")
public class CertificadoCofre {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "escritorio_id", nullable = false)
    private UUID escritorioId;

    @Column(name = "empresa_id")
    private UUID empresaId;

    @Column(name = "titular_mascarado", nullable = false)
    private String titularMascarado;

    @Column(name = "documento_titular")
    private String documentoTitular;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false)
    private TipoCertificadoDigital tipo = TipoCertificadoDigital.E_CNPJ;

    @Column(name = "emissor", nullable = false)
    private String emissor;

    @Column(name = "valido_de", nullable = false)
    private OffsetDateTime validoDe;

    @Column(name = "valido_ate", nullable = false)
    private OffsetDateTime validoAte;

    @Column(name = "sha256_original", nullable = false, length = 64)
    private String sha256Original;

    @Column(name = "dados_cifrados", nullable = false)
    private byte[] dadosCifrados;

    @Column(name = "iv_dados", nullable = false)
    private byte[] ivDados;

    @Column(name = "dek_cifrada", nullable = false)
    private byte[] dekCifrada;

    @Column(name = "iv_dek", nullable = false)
    private byte[] ivDek;

    @Column(name = "versao_chave_mestra", nullable = false)
    private int versaoChaveMestra = 1;

    @Column(name = "termo_aceito_em", nullable = false)
    private OffsetDateTime termoAceitoEm;

    @Column(name = "termo_aceito_por", nullable = false, length = 120)
    private String termoAceitoPor;

    @Column(name = "termo_versao", nullable = false, length = 20)
    private String termoVersao;

    @Column(name = "enviado_por", length = 120)
    private String enviadoPor;

    @Column(name = "ativo", nullable = false)
    private boolean ativo = true;

    @Column(name = "criado_em", nullable = false)
    private OffsetDateTime criadoEm = OffsetDateTime.now();

    @Column(name = "atualizado_em", nullable = false)
    private OffsetDateTime atualizadoEm = OffsetDateTime.now();

    public CertificadoCofre() {
    }

    // Getters and Setters
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

    public String getTitularMascarado() {
        return titularMascarado;
    }

    public void setTitularMascarado(String titularMascarado) {
        this.titularMascarado = titularMascarado;
    }

    public String getDocumentoTitular() {
        return documentoTitular;
    }

    public void setDocumentoTitular(String documentoTitular) {
        this.documentoTitular = documentoTitular;
    }

    public TipoCertificadoDigital getTipo() {
        return tipo;
    }

    public void setTipo(TipoCertificadoDigital tipo) {
        this.tipo = tipo;
    }

    public String getEmissor() {
        return emissor;
    }

    public void setEmissor(String emissor) {
        this.emissor = emissor;
    }

    public OffsetDateTime getValidoDe() {
        return validoDe;
    }

    public void setValidoDe(OffsetDateTime validoDe) {
        this.validoDe = validoDe;
    }

    public OffsetDateTime getValidoAte() {
        return validoAte;
    }

    public void setValidoAte(OffsetDateTime validoAte) {
        this.validoAte = validoAte;
    }

    public String getSha256Original() {
        return sha256Original;
    }

    public void setSha256Original(String sha256Original) {
        this.sha256Original = sha256Original;
    }

    public byte[] getDadosCifrados() {
        return dadosCifrados;
    }

    public void setDadosCifrados(byte[] dadosCifrados) {
        this.dadosCifrados = dadosCifrados;
    }

    public byte[] getIvDados() {
        return ivDados;
    }

    public void setIvDados(byte[] ivDados) {
        this.ivDados = ivDados;
    }

    public byte[] getDekCifrada() {
        return dekCifrada;
    }

    public void setDekCifrada(byte[] dekCifrada) {
        this.dekCifrada = dekCifrada;
    }

    public byte[] getIvDek() {
        return ivDek;
    }

    public void setIvDek(byte[] ivDek) {
        this.ivDek = ivDek;
    }

    public int getVersaoChaveMestra() {
        return versaoChaveMestra;
    }

    public void setVersaoChaveMestra(int versaoChaveMestra) {
        this.versaoChaveMestra = versaoChaveMestra;
    }

    public OffsetDateTime getTermoAceitoEm() {
        return termoAceitoEm;
    }

    public void setTermoAceitoEm(OffsetDateTime termoAceitoEm) {
        this.termoAceitoEm = termoAceitoEm;
    }

    public String getTermoAceitoPor() {
        return termoAceitoPor;
    }

    public void setTermoAceitoPor(String termoAceitoPor) {
        this.termoAceitoPor = termoAceitoPor;
    }

    public String getTermoVersao() {
        return termoVersao;
    }

    public void setTermoVersao(String termoVersao) {
        this.termoVersao = termoVersao;
    }

    public String getEnviadoPor() {
        return enviadoPor;
    }

    public void setEnviadoPor(String enviadoPor) {
        this.enviadoPor = enviadoPor;
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

    public UUID getEmpresaId() {
        return empresaId;
    }

    public void setEmpresaId(UUID empresaId) {
        this.empresaId = empresaId;
    }
}

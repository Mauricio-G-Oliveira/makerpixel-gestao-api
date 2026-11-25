package com.makerpixel.contabil.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "auditoria_pendente")
public class AuditoriaPendente {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "escritorio_id", nullable = false)
    private UUID escritorioId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "documento_id", nullable = false)
    private DocumentoFiscal documento;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "PENDENTE"; // PENDENTE, PROCESSANDO, CONCLUIDO, ERRO

    @Column(name = "tentativas", nullable = false)
    private int tentativas = 0;

    @Column(name = "proxima_tentativa")
    private OffsetDateTime proximaTentativa = OffsetDateTime.now();

    @Column(name = "erro_mensagem", columnDefinition = "TEXT")
    private String erroMensagem;

    @Column(name = "versao_calculadora", length = 50)
    private String versaoCalculadora;

    @Column(name = "criado_em", nullable = false)
    private OffsetDateTime criadoEm = OffsetDateTime.now();

    @Column(name = "atualizado_em", nullable = false)
    private OffsetDateTime atualizadoEm = OffsetDateTime.now();

    public AuditoriaPendente() {
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

    public DocumentoFiscal getDocumento() {
        return documento;
    }

    public void setDocumento(DocumentoFiscal documento) {
        this.documento = documento;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getTentativas() {
        return tentativas;
    }

    public void setTentativas(int tentativas) {
        this.tentativas = tentativas;
    }

    public OffsetDateTime getProximaTentativa() {
        return proximaTentativa;
    }

    public void setProximaTentativa(OffsetDateTime proximaTentativa) {
        this.proximaTentativa = proximaTentativa;
    }

    public String getErroMensagem() {
        return erroMensagem;
    }

    public void setErroMensagem(String erroMensagem) {
        this.erroMensagem = erroMensagem;
    }

    public String getVersaoCalculadora() {
        return versaoCalculadora;
    }

    public void setVersaoCalculadora(String versaoCalculadora) {
        this.versaoCalculadora = versaoCalculadora;
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

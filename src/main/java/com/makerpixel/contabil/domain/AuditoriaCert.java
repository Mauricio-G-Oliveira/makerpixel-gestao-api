package com.makerpixel.contabil.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "auditoria_cert")
public class AuditoriaCert {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "escritorio_id", nullable = false)
    private UUID escritorioId;

    @Column(name = "certificado_id")
    private UUID certificadoId;

    @Column(name = "operacao", nullable = false, length = 50)
    private String operacao; // UPLOAD, DECIFRAGEM_CONSULTA, EXCLUSAO, ROTACAO_CHAVE

    @Column(name = "usuario", length = 120)
    private String usuario;

    @Column(name = "sucesso", nullable = false)
    private boolean sucesso;

    @Column(name = "detalhe", columnDefinition = "TEXT")
    private String detalhe;

    @Column(name = "ip", length = 45)
    private String ip;

    @Column(name = "executado_em", nullable = false)
    private OffsetDateTime executadoEm = OffsetDateTime.now();

    public AuditoriaCert() {
    }

    public AuditoriaCert(UUID escritorioId, UUID certificadoId, String operacao, String usuario, boolean sucesso, String detalhe, String ip) {
        this.escritorioId = escritorioId;
        this.certificadoId = certificadoId;
        this.operacao = operacao;
        this.usuario = usuario;
        this.sucesso = sucesso;
        this.detalhe = detalhe;
        this.ip = ip;
        this.executadoEm = OffsetDateTime.now();
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

    public UUID getCertificadoId() {
        return certificadoId;
    }

    public void setCertificadoId(UUID certificadoId) {
        this.certificadoId = certificadoId;
    }

    public String getOperacao() {
        return operacao;
    }

    public void setOperacao(String operacao) {
        this.operacao = operacao;
    }

    public String getUsuario() {
        return usuario;
    }

    public void setUsuario(String usuario) {
        this.usuario = usuario;
    }

    public boolean isSucesso() {
        return sucesso;
    }

    public void setSucesso(boolean sucesso) {
        this.sucesso = sucesso;
    }

    public String getDetalhe() {
        return detalhe;
    }

    public void setDetalhe(String detalhe) {
        this.detalhe = detalhe;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public OffsetDateTime getExecutadoEm() {
        return executadoEm;
    }

    public void setExecutadoEm(OffsetDateTime executadoEm) {
        this.executadoEm = executadoEm;
    }
}

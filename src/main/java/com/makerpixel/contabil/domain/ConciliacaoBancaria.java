package com.makerpixel.contabil.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "conciliacao_bancaria", uniqueConstraints = {
    @UniqueConstraint(name = "uk_conciliacao", columnNames = {"escritorio_id", "cnpj", "banco", "agencia", "conta", "competencia"})
})
public class ConciliacaoBancaria {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "escritorio_id", nullable = false)
    private UUID escritorioId;

    @Column(name = "empresa_id")
    private UUID empresaId;

    @Column(nullable = false, length = 14)
    private String cnpj;

    @Column(nullable = false, length = 50)
    private String banco;

    @Column(nullable = false, length = 20)
    private String agencia = "";

    @Column(nullable = false, length = 30)
    private String conta;

    @Column(nullable = false, length = 7)
    private String competencia;

    @Column(name = "extrato_json", nullable = false, columnDefinition = "TEXT")
    private String extratoJson = "[]";

    @Column(name = "planilha_json", nullable = false, columnDefinition = "TEXT")
    private String planilhaJson = "[]";

    @Column(name = "conferidos_json", columnDefinition = "TEXT")
    private String conferidosJson = "[]";

    @Column(name = "juncoes_manuais_json", columnDefinition = "TEXT")
    private String juncoesManuaisJson = "[]";

    @Column(columnDefinition = "TEXT")
    private String observacoes;

    @Column(name = "resumo_json", nullable = false, columnDefinition = "TEXT")
    private String resumoJson = "{}";

    @Column(name = "ultimo_usuario_id")
    private UUID ultimoUsuarioId;

    @Column(name = "ultimo_usuario_nome", nullable = false, length = 150)
    private String ultimoUsuarioNome;

    @Column(nullable = false)
    private Long versao = 1L;

    @Column(name = "criado_em", nullable = false)
    private OffsetDateTime criadoEm = OffsetDateTime.now();

    @Column(name = "atualizado_em", nullable = false)
    private OffsetDateTime atualizadoEm = OffsetDateTime.now();

    @PrePersist
    public void prePersist() {
        if (criadoEm == null) criadoEm = OffsetDateTime.now();
        if (atualizadoEm == null) atualizadoEm = OffsetDateTime.now();
        if (versao == null) versao = 1L;
    }

    @PreUpdate
    public void preUpdate() {
        atualizadoEm = OffsetDateTime.now();
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getEscritorioId() { return escritorioId; }
    public void setEscritorioId(UUID escritorioId) { this.escritorioId = escritorioId; }

    public UUID getEmpresaId() { return empresaId; }
    public void setEmpresaId(UUID empresaId) { this.empresaId = empresaId; }

    public String getCnpj() { return cnpj; }
    public void setCnpj(String cnpj) { this.cnpj = cnpj; }

    public String getBanco() { return banco; }
    public void setBanco(String banco) { this.banco = banco; }

    public String getAgencia() { return agencia; }
    public void setAgencia(String agencia) { this.agencia = agencia; }

    public String getConta() { return conta; }
    public void setConta(String conta) { this.conta = conta; }

    public String getCompetencia() { return competencia; }
    public void setCompetencia(String competencia) { this.competencia = competencia; }

    public String getExtratoJson() { return extratoJson; }
    public void setExtratoJson(String extratoJson) { this.extratoJson = extratoJson; }

    public String getPlanilhaJson() { return planilhaJson; }
    public void setPlanilhaJson(String planilhaJson) { this.planilhaJson = planilhaJson; }

    public String getConferidosJson() { return conferidosJson; }
    public void setConferidosJson(String conferidosJson) { this.conferidosJson = conferidosJson; }

    public String getJuncoesManuaisJson() { return juncoesManuaisJson; }
    public void setJuncoesManuaisJson(String juncoesManuaisJson) { this.juncoesManuaisJson = juncoesManuaisJson; }

    public String getObservacoes() { return observacoes; }
    public void setObservacoes(String observacoes) { this.observacoes = observacoes; }

    public String getResumoJson() { return resumoJson; }
    public void setResumoJson(String resumoJson) { this.resumoJson = resumoJson; }

    public UUID getUltimoUsuarioId() { return ultimoUsuarioId; }
    public void setUltimoUsuarioId(UUID ultimoUsuarioId) { this.ultimoUsuarioId = ultimoUsuarioId; }

    public String getUltimoUsuarioNome() { return ultimoUsuarioNome; }
    public void setUltimoUsuarioNome(String ultimoUsuarioNome) { this.ultimoUsuarioNome = ultimoUsuarioNome; }

    public Long getVersao() { return versao; }
    public void setVersao(Long versao) { this.versao = versao; }

    public OffsetDateTime getCriadoEm() { return criadoEm; }
    public void setCriadoEm(OffsetDateTime criadoEm) { this.criadoEm = criadoEm; }

    public OffsetDateTime getAtualizadoEm() { return atualizadoEm; }
    public void setAtualizadoEm(OffsetDateTime atualizadoEm) { this.atualizadoEm = atualizadoEm; }
}

package com.makerpixel.contabil.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "escritorio")
public class Escritorio {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 14)
    private String cnpj;

    @Column(name = "razao_social", nullable = false)
    private String razaoSocial;

    private String contato;

    @Column(nullable = false, length = 180)
    private String email;

    private String telefone;
    private String cidade;
    private String uf;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SituacaoEscritorio situacao = SituacaoEscritorio.TESTE;

    @Column(name = "teste_expira_em")
    private OffsetDateTime testeExpiraEm;

    @Column(name = "modulos_ativos")
    private String[] modulosAtivos = new String[]{
        "CONCILIACAO_BANCARIA", "INTEGRACAO_UNICO", "VENCIMENTOS",
        "EM_DIA", "FISCAL", "CONTROLE_HORAS", "ENCERRAMENTO_LUCROS", "NOTASYNC"
    };

    @Column(name = "max_usuarios", nullable = false)
    private Integer maxUsuarios = 3;

    @Column(name = "max_empresas", nullable = false)
    private Integer maxEmpresas = 20;

    @Column(name = "plano_id", length = 50)
    private String planoId = "plano-escritorio";

    @Column(name = "limite_armazenamento_gb")
    private Integer limiteArmazenamentoGb = 20;

    @Column(name = "acesso_ate")
    private OffsetDateTime acessoAte;

    @Column(name = "observacao")
    private String observacao;

    @Column(name = "suporte_liberado_ate")
    private OffsetDateTime suporteLiberadoAte;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm = OffsetDateTime.now();

    @Column(name = "atualizado_em", nullable = false)
    private OffsetDateTime atualizadoEm = OffsetDateTime.now();

    @PrePersist
    @PreUpdate
    public void onSave() {
        this.atualizadoEm = OffsetDateTime.now();
    }

    public boolean isTesteExpirado() {
        if (this.situacao != SituacaoEscritorio.TESTE || this.testeExpiraEm == null) {
            return false;
        }
        return OffsetDateTime.now().isAfter(this.testeExpiraEm);
    }

    public boolean isAcessoValido() {
        if (this.situacao == SituacaoEscritorio.SUSPENSO || this.situacao == SituacaoEscritorio.ENCERRADO) {
            return false;
        }
        OffsetDateTime fim = this.acessoAte != null ? this.acessoAte : this.testeExpiraEm;
        if (fim == null) return true;
        return OffsetDateTime.now().isBefore(fim);
    }

    public boolean isSuporteAtivo() {
        if (this.suporteLiberadoAte == null) return false;
        return OffsetDateTime.now().isBefore(this.suporteLiberadoAte);
    }

    public boolean hasModulo(String modulo) {
        if (this.modulosAtivos == null) return true;
        for (String m : this.modulosAtivos) {
            if (m != null && m.equalsIgnoreCase(modulo)) return true;
        }
        return false;
    }

    public boolean podeUsarModulo(String modulo) {
        if (!isAcessoValido()) return false;
        return hasModulo(modulo);
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getCnpj() { return cnpj; }
    public void setCnpj(String cnpj) { this.cnpj = cnpj; }

    public String getRazaoSocial() { return razaoSocial; }
    public void setRazaoSocial(String razaoSocial) { this.razaoSocial = razaoSocial; }

    public String getContato() { return contato; }
    public void setContato(String contato) { this.contato = contato; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getTelefone() { return telefone; }
    public void setTelefone(String telefone) { this.telefone = telefone; }

    public String getCidade() { return cidade; }
    public void setCidade(String cidade) { this.cidade = cidade; }

    public String getUf() { return uf; }
    public void setUf(String uf) { this.uf = uf; }

    public SituacaoEscritorio getSituacao() { return situacao; }
    public void setSituacao(SituacaoEscritorio situacao) { this.situacao = situacao; }

    public OffsetDateTime getTesteExpiraEm() { return testeExpiraEm; }
    public void setTesteExpiraEm(OffsetDateTime testeExpiraEm) { this.testeExpiraEm = testeExpiraEm; }

    public String[] getModulosAtivos() { return modulosAtivos; }
    public void setModulosAtivos(String[] modulosAtivos) { this.modulosAtivos = modulosAtivos; }

    public Integer getMaxUsuarios() { return maxUsuarios; }
    public void setMaxUsuarios(Integer maxUsuarios) { this.maxUsuarios = maxUsuarios; }

    public Integer getMaxEmpresas() { return maxEmpresas; }
    public void setMaxEmpresas(Integer maxEmpresas) { this.maxEmpresas = maxEmpresas; }

    public OffsetDateTime getAcessoAte() { return acessoAte; }
    public void setAcessoAte(OffsetDateTime acessoAte) { this.acessoAte = acessoAte; }

    public String getObservacao() { return observacao; }
    public void setObservacao(String observacao) { this.observacao = observacao; }

    public OffsetDateTime getSuporteLiberadoAte() { return suporteLiberadoAte; }
    public void setSuporteLiberadoAte(OffsetDateTime suporteLiberadoAte) { this.suporteLiberadoAte = suporteLiberadoAte; }

    public String getPlanoId() { return planoId; }
    public void setPlanoId(String planoId) { this.planoId = planoId; }

    public Integer getLimiteArmazenamentoGb() { return limiteArmazenamentoGb; }
    public void setLimiteArmazenamentoGb(Integer limiteArmazenamentoGb) { this.limiteArmazenamentoGb = limiteArmazenamentoGb; }

    public boolean checarLimiteUsuarios(int quantidadeAtual) {
        if (maxUsuarios == null) return true;
        return quantidadeAtual < maxUsuarios;
    }

    public boolean checarLimiteEmpresas(int quantidadeAtual) {
        if (maxEmpresas == null) return true;
        return quantidadeAtual < maxEmpresas;
    }

    public boolean checarLimiteArmazenamento(double gbAtual) {
        if (limiteArmazenamentoGb == null) return true;
        return gbAtual < limiteArmazenamentoGb;
    }

    public OffsetDateTime getCriadoEm() { return criadoEm; }
    public OffsetDateTime getAtualizadoEm() { return atualizadoEm; }
}

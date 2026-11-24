package com.makerpixel.contabil.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "emdia_tarefa", uniqueConstraints = {
    @UniqueConstraint(name = "uk_emdia_tarefa_dedup", columnNames = {"escritorio_id", "empresa_id", "modelo_id", "mes_competencia"})
})
public class EmDiaTarefa {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "escritorio_id", nullable = false)
    private UUID escritorioId;

    @Column(name = "empresa_id", nullable = false)
    private UUID empresaId;

    @Column(name = "modelo_id")
    private UUID modeloId;

    @Column(nullable = false)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private SetorTarefa setor = SetorTarefa.GERAL;

    @Column(name = "mes_competencia", nullable = false, length = 7)
    private String mesCompetencia; // AAAA-MM

    @Column(nullable = false)
    private LocalDate prazo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StatusTarefa status = StatusTarefa.A_FAZER;

    @Column(length = 150)
    private String responsavel;

    @Column(name = "concluida_em")
    private OffsetDateTime concluidaEm;

    @Column(name = "concluida_por", length = 150)
    private String concluidaPor;

    @Column(name = "origem_automatica", length = 50)
    private String origemAutomatica;

    @Column(name = "chave_deduplicacao", length = 255)
    private String chaveDeduplicacao;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm = OffsetDateTime.now();

    @Column(name = "atualizado_em", nullable = false)
    private OffsetDateTime atualizadoEm = OffsetDateTime.now();

    @PrePersist
    @PreUpdate
    public void onSave() {
        this.atualizadoEm = OffsetDateTime.now();
    }

    public boolean isAtrasada() {
        if (status == StatusTarefa.CONCLUIDA || status == StatusTarefa.NAO_SE_APLICA) {
            return false;
        }
        return prazo != null && prazo.isBefore(LocalDate.now());
    }

    // Getters e Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getEscritorioId() { return escritorioId; }
    public void setEscritorioId(UUID escritorioId) { this.escritorioId = escritorioId; }

    public UUID getEmpresaId() { return empresaId; }
    public void setEmpresaId(UUID empresaId) { this.empresaId = empresaId; }

    public UUID getModeloId() { return modeloId; }
    public void setModeloId(UUID modeloId) { this.modeloId = modeloId; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public SetorTarefa getSetor() { return setor; }
    public void setSetor(SetorTarefa setor) { this.setor = setor; }

    public String getMesCompetencia() { return mesCompetencia; }
    public void setMesCompetencia(String mesCompetencia) { this.mesCompetencia = mesCompetencia; }

    public LocalDate getPrazo() { return prazo; }
    public void setPrazo(LocalDate prazo) { this.prazo = prazo; }

    public StatusTarefa getStatus() { return status; }
    public void setStatus(StatusTarefa status) { this.status = status; }

    public String getResponsavel() { return responsavel; }
    public void setResponsavel(String responsavel) { this.responsavel = responsavel; }

    public OffsetDateTime getConcluidaEm() { return concluidaEm; }
    public void setConcluidaEm(OffsetDateTime concluidaEm) { this.concluidaEm = concluidaEm; }

    public String getConcluidaPor() { return concluidaPor; }
    public void setConcluidaPor(String concluidaPor) { this.concluidaPor = concluidaPor; }

    public String getOrigemAutomatica() { return origemAutomatica; }
    public void setOrigemAutomatica(String origemAutomatica) { this.origemAutomatica = origemAutomatica; }

    public String getChaveDeduplicacao() { return chaveDeduplicacao; }
    public void setChaveDeduplicacao(String chaveDeduplicacao) { this.chaveDeduplicacao = chaveDeduplicacao; }

    public OffsetDateTime getCriadoEm() { return criadoEm; }
    public OffsetDateTime getAtualizadoEm() { return atualizadoEm; }
}

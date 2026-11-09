package com.makerpixel.contabil.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "perfil_conciliacao", uniqueConstraints = {
    @UniqueConstraint(name = "uk_perfil_conciliacao", columnNames = {"escritorio_id", "cnpj"})
})
public class PerfilConciliacao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "escritorio_id", nullable = false)
    private UUID escritorioId;

    @Column(nullable = false, length = 14)
    private String cnpj;

    @Column(name = "tolerancia_dias", nullable = false)
    private Integer toleranciaDias = 2;

    @Column(name = "palavras_chave_json", nullable = false, columnDefinition = "TEXT")
    private String palavrasChaveJson = "[]";

    @Column(name = "conciliar_em_lote", nullable = false)
    private Boolean conciliarEmLote = true;

    @Column(name = "mapeamento_colunas_json", nullable = false, columnDefinition = "TEXT")
    private String mapeamentoColunasJson = "{}";

    @Column(name = "criado_em", nullable = false)
    private OffsetDateTime criadoEm = OffsetDateTime.now();

    @Column(name = "atualizado_em", nullable = false)
    private OffsetDateTime atualizadoEm = OffsetDateTime.now();

    @PrePersist
    public void prePersist() {
        if (criadoEm == null) criadoEm = OffsetDateTime.now();
        if (atualizadoEm == null) atualizadoEm = OffsetDateTime.now();
        if (toleranciaDias == null) toleranciaDias = 2;
        if (conciliarEmLote == null) conciliarEmLote = true;
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

    public String getCnpj() { return cnpj; }
    public void setCnpj(String cnpj) { this.cnpj = cnpj; }

    public Integer getToleranciaDias() { return toleranciaDias; }
    public void setToleranciaDias(Integer toleranciaDias) { this.toleranciaDias = toleranciaDias; }

    public String getPalavrasChaveJson() { return palavrasChaveJson; }
    public void setPalavrasChaveJson(String palavrasChaveJson) { this.palavrasChaveJson = palavrasChaveJson; }

    public Boolean getConciliarEmLote() { return conciliarEmLote; }
    public void setConciliarEmLote(Boolean conciliarEmLote) { this.conciliarEmLote = conciliarEmLote; }

    public String getMapeamentoColunasJson() { return mapeamentoColunasJson; }
    public void setMapeamentoColunasJson(String mapeamentoColunasJson) { this.mapeamentoColunasJson = mapeamentoColunasJson; }

    public OffsetDateTime getCriadoEm() { return criadoEm; }
    public void setCriadoEm(OffsetDateTime criadoEm) { this.criadoEm = criadoEm; }

    public OffsetDateTime getAtualizadoEm() { return atualizadoEm; }
    public void setAtualizadoEm(OffsetDateTime atualizadoEm) { this.atualizadoEm = atualizadoEm; }
}

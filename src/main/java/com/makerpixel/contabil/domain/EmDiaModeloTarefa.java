package com.makerpixel.contabil.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "emdia_modelo_tarefa")
public class EmDiaModeloTarefa {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "escritorio_id", nullable = false)
    private UUID escritorioId;

    @Column(nullable = false, length = 200)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private SetorTarefa setor = SetorTarefa.GERAL;

    @Column(name = "dia_vencimento", nullable = false)
    private int diaVencimento = 10;

    @Column(name = "aplica_simples", nullable = false)
    private boolean aplicaSimples = true;

    @Column(name = "aplica_lucro_presumido", nullable = false)
    private boolean aplicaLucroPresumido = true;

    @Column(name = "aplica_lucro_real", nullable = false)
    private boolean aplicaLucroReal = true;

    @Column(name = "aplica_mei", nullable = false)
    private boolean aplicaMei = true;

    @Column(name = "exige_funcionario", nullable = false)
    private boolean exigeFuncionario = false;

    @Column(name = "tipo_empresa", length = 50)
    private String tipoEmpresa = "TODOS";

    @Column(nullable = false)
    private boolean ativo = true;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm = OffsetDateTime.now();

    @Column(name = "atualizado_em", nullable = false)
    private OffsetDateTime atualizadoEm = OffsetDateTime.now();

    @PrePersist
    @PreUpdate
    public void onSave() {
        this.atualizadoEm = OffsetDateTime.now();
    }

    public boolean seAplica(Empresa empresa) {
        if (!ativo) return false;

        // Verifica tipo de empresa
        if (tipoEmpresa != null && !tipoEmpresa.isBlank() && !"TODOS".equalsIgnoreCase(tipoEmpresa)) {
            if (empresa.getTipo() == null || !tipoEmpresa.equalsIgnoreCase(empresa.getTipo())) {
                return false;
            }
        }

        // Verifica se exige funcionário
        if (exigeFuncionario && !empresa.getTemFuncionario()) {
            return false;
        }

        // Verifica regime tributário
        RegimeTributario regime = empresa.getRegime();
        if (regime == null) return true;

        return switch (regime) {
            case SIMPLES_NACIONAL -> aplicaSimples;
            case LUCRO_PRESUMIDO -> aplicaLucroPresumido;
            case LUCRO_REAL -> aplicaLucroReal;
            case MEI -> aplicaMei;
        };
    }

    // Getters e Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getEscritorioId() { return escritorioId; }
    public void setEscritorioId(UUID escritorioId) { this.escritorioId = escritorioId; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public SetorTarefa getSetor() { return setor; }
    public void setSetor(SetorTarefa setor) { this.setor = setor; }

    public int getDiaVencimento() { return diaVencimento; }
    public void setDiaVencimento(int diaVencimento) { this.diaVencimento = diaVencimento; }

    public boolean isAplicaSimples() { return aplicaSimples; }
    public void setAplicaSimples(boolean aplicaSimples) { this.aplicaSimples = aplicaSimples; }

    public boolean isAplicaLucroPresumido() { return aplicaLucroPresumido; }
    public void setAplicaLucroPresumido(boolean aplicaLucroPresumido) { this.aplicaLucroPresumido = aplicaLucroPresumido; }

    public boolean isAplicaLucroReal() { return aplicaLucroReal; }
    public void setAplicaLucroReal(boolean aplicaLucroReal) { this.aplicaLucroReal = aplicaLucroReal; }

    public boolean isAplicaMei() { return aplicaMei; }
    public void setAplicaMei(boolean aplicaMei) { this.aplicaMei = aplicaMei; }

    public boolean isExigeFuncionario() { return exigeFuncionario; }
    public void setExigeFuncionario(boolean exigeFuncionario) { this.exigeFuncionario = exigeFuncionario; }

    public String getTipoEmpresa() { return tipoEmpresa; }
    public void setTipoEmpresa(String tipoEmpresa) { this.tipoEmpresa = tipoEmpresa; }

    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }

    public OffsetDateTime getCriadoEm() { return criadoEm; }
    public OffsetDateTime getAtualizadoEm() { return atualizadoEm; }
}

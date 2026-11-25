package com.makerpixel.contabil.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "documento_fiscal")
public class DocumentoFiscal {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "escritorio_id", nullable = false)
    private UUID escritorioId;

    @Column(name = "empresa_id")
    private UUID empresaId;

    @Column(name = "chave_acesso", nullable = false, length = 44)
    private String chaveAcesso;

    @Column(name = "modelo", nullable = false, length = 5)
    private String modelo = "55"; // 55 (NF-e), 65 (NFC-e), 57 (CT-e)

    @Column(name = "serie", nullable = false)
    private int serie = 1;

    @Column(name = "numero", nullable = false)
    private int numero;

    @Column(name = "data_emissao", nullable = false)
    private OffsetDateTime dataEmissao;

    @Column(name = "competencia", nullable = false, length = 7)
    private String competencia; // AAAA-MM

    @Column(name = "valor_total", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorTotal = BigDecimal.ZERO;

    @Column(name = "situacao", nullable = false, length = 20)
    private String situacao = "AUTORIZADA"; // AUTORIZADA, CANCELADA

    @Column(name = "motivo_cancelamento", columnDefinition = "TEXT")
    private String motivoCancelamento;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "documento_fiscal_fontes", joinColumns = @JoinColumn(name = "documento_id"))
    @Column(name = "fonte")
    private List<String> fontes = new ArrayList<>();

    @Column(name = "storage_path", length = 500)
    private String storagePath;

    @Column(name = "conteudo_xml", columnDefinition = "TEXT")
    private String conteudoXml;

    @Column(name = "recebido_em", nullable = false)
    private OffsetDateTime recebidoEm = OffsetDateTime.now();

    @Column(name = "criado_em", nullable = false)
    private OffsetDateTime criadoEm = OffsetDateTime.now();

    @Column(name = "atualizado_em", nullable = false)
    private OffsetDateTime atualizadoEm = OffsetDateTime.now();

    public DocumentoFiscal() {
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

    public UUID getEmpresaId() {
        return empresaId;
    }

    public void setEmpresaId(UUID empresaId) {
        this.empresaId = empresaId;
    }

    public String getChaveAcesso() {
        return chaveAcesso;
    }

    public void setChaveAcesso(String chaveAcesso) {
        this.chaveAcesso = chaveAcesso;
    }

    public String getModelo() {
        return modelo;
    }

    public void setModelo(String modelo) {
        this.modelo = modelo;
    }

    public int getSerie() {
        return serie;
    }

    public void setSerie(int serie) {
        this.serie = serie;
    }

    public int getNumero() {
        return numero;
    }

    public void setNumero(int numero) {
        this.numero = numero;
    }

    public OffsetDateTime getDataEmissao() {
        return dataEmissao;
    }

    public void setDataEmissao(OffsetDateTime dataEmissao) {
        this.dataEmissao = dataEmissao;
    }

    public String getCompetencia() {
        return competencia;
    }

    public void setCompetencia(String competencia) {
        this.competencia = competencia;
    }

    public BigDecimal getValorTotal() {
        return valorTotal;
    }

    public void setValorTotal(BigDecimal valorTotal) {
        this.valorTotal = valorTotal;
    }

    public String getSituacao() {
        return situacao;
    }

    public void setSituacao(String situacao) {
        this.situacao = situacao;
    }

    public String getMotivoCancelamento() {
        return motivoCancelamento;
    }

    public void setMotivoCancelamento(String motivoCancelamento) {
        this.motivoCancelamento = motivoCancelamento;
    }

    public List<String> getFontes() {
        return fontes;
    }

    public void setFontes(List<String> fontes) {
        this.fontes = fontes;
    }

    public void adicionarFonte(String fonte) {
        if (this.fontes == null) {
            this.fontes = new ArrayList<>();
        }
        if (!this.fontes.contains(fonte)) {
            this.fontes.add(fonte);
        }
    }

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    public String getConteudoXml() {
        return conteudoXml;
    }

    public void setConteudoXml(String conteudoXml) {
        this.conteudoXml = conteudoXml;
    }

    public OffsetDateTime getRecebidoEm() {
        return recebidoEm;
    }

    public void setRecebidoEm(OffsetDateTime recebidoEm) {
        this.recebidoEm = recebidoEm;
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

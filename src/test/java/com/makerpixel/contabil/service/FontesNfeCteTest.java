package com.makerpixel.contabil.service;

import com.makerpixel.contabil.notasync.DfeNacionalFonte;
import com.makerpixel.contabil.notasync.SefScNfeFonte;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class FontesNfeCteTest {

    @Test
    @DisplayName("DfeNacionalFonte: identificador, manifestação desligada por padrão e envelope SOAP")
    void deveValidarConfiguracaoDfeNacional() {
        DfeNacionalFonte fonte = new DfeNacionalFonte();

        assertEquals("dfe-nacional", fonte.id());
        assertFalse(fonte.isManifestacaoAutomatica(), "Manifestação do destinatário deve ser desligada por padrão");

        String soap = fonte.montarEnvelopeDistDfe("11222333000181", 100L);
        assertTrue(soap.contains("nfeDistDFeInteresse"));
        assertTrue(soap.contains("distDFeInt"));
        assertTrue(soap.contains("11222333000181"));
        assertTrue(soap.contains("000000000000100"));
    }

    @Test
    @DisplayName("DfeNacionalFonte: interpretação de Resumo de NF-e (resNFe) com chave e valor")
    void deveInterpretarResumoNfe() {
        DfeNacionalFonte fonte = new DfeNacionalFonte();
        String chave = "42261111222333000181550010000000011000000018";
        String xmlResumo =
                "<resNFe versao=\"1.01\" xmlns=\"http://www.portalfiscal.inf.br/nfe\">" +
                "<chNFe>" + chave + "</chNFe>" +
                "<CNPJ>99888777000166</CNPJ>" +
                "<xNome>Fornecedor de Mercadorias Ltda</xNome>" +
                "<vNF>1500.50</vNF>" +
                "<dhEmi>2026-11-20T10:00:00-03:00</dhEmi>" +
                "<cSitNFe>1</cSitNFe>" +
                "<NSU>123</NSU>" +
                "</resNFe>";

        var doc = fonte.interpretarXmlDfe(xmlResumo, "11222333000181");

        assertNotNull(doc);
        assertEquals(chave, doc.chave());
        assertEquals("55", doc.modelo());
        assertEquals("99888777000166", doc.cnpjEmitente());
        assertEquals(new BigDecimal("1500.50"), doc.valor());
        assertTrue(doc.isResumo());
    }

    @Test
    @DisplayName("DfeNacionalFonte: interpretação de Resumo de CT-e (resCTe)")
    void deveInterpretarResumoCte() {
        DfeNacionalFonte fonte = new DfeNacionalFonte();
        String chave = "42261111222333000181570010000000011000000019";
        String xmlResumo =
                "<resCTe versao=\"1.00\" xmlns=\"http://www.portalfiscal.inf.br/cte\">" +
                "<chCTe>" + chave + "</chCTe>" +
                "<CNPJ>88777666000155</CNPJ>" +
                "<xNome>Transportadora Exemplo Ltda</xNome>" +
                "<vTPrest>450.00</vTPrest>" +
                "<dhEmi>2026-11-20T14:30:00-03:00</dhEmi>" +
                "<NSU>456</NSU>" +
                "</resCTe>";

        var doc = fonte.interpretarXmlDfe(xmlResumo, "11222333000181");

        assertNotNull(doc);
        assertEquals(chave, doc.chave());
        assertEquals("57", doc.modelo());
        assertEquals("88777666000155", doc.cnpjEmitente());
        assertEquals(new BigDecimal("450.00"), doc.valor());
        assertTrue(doc.isResumo());
    }

    @Test
    @DisplayName("SefScNfeFonte: identificador e montagem de envelope com nfeDownloadContab")
    void deveValidarConfiguracaoSefScNfe() {
        SefScNfeFonte fonte = new SefScNfeFonte();

        assertEquals("sef-sc-nfe", fonte.id());

        String soap = fonte.montarEnvelopeDistNfe("99888777000100", "11222333000181", 50L);

        assertTrue(soap.contains("nfeDownloadContab"));
        assertTrue(soap.contains("distNFeSC"));
        assertTrue(soap.contains("99888777000100"));
    }
}

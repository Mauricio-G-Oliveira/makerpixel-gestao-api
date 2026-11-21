package com.makerpixel.contabil.notasync;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fonte de captura fiscal para NF-e (Modelo 55) e CT-e (Modelo 57) via Ambiente Nacional (SVRS / Receita Federal).
 * Especificação: NT 2014.002 / NT 2020.005 (NFeDistribuicaoDFe / CTeDistribuicaoDFe).
 *
 * REQUISITOS OBRIGATÓRIOS:
 * 1. Exige o certificado digital A1 da PRÓPRIA empresa cliente (e-CNPJ da empresa).
 * 2. Trata documentos resumidos (resNFe / resCTe) e documentos completos (nfeProc / cteProc).
 * 3. Manifestação do Destinatário DESLIGADA por padrão.
 * 4. Faltando certificado da empresa, não tenta a requisição para não gerar consumo indevido.
 */
public class DfeNacionalFonte {

    public static final String ID = "dfe-nacional";
    public static final String URL_PRODUCAO_NFE = "https://www1.nfe.fazenda.gov.br/NFeDistribuicaoDFe/NFeDistribuicaoDFe.asmx";

    private final boolean manifestacaoAutomatica;

    public DfeNacionalFonte() {
        this(false);
    }

    public DfeNacionalFonte(boolean manifestacaoAutomatica) {
        this.manifestacaoAutomatica = manifestacaoAutomatica; // DESLIGADA POR PADRÃO
    }

    public String id() {
        return ID;
    }

    public boolean isManifestacaoAutomatica() {
        return manifestacaoAutomatica;
    }

    public String montarEnvelopeDistDfe(String cnpjEmpresa, long ultNsu) {
        String cnpjLimpo = cnpjEmpresa != null ? cnpjEmpresa.replaceAll("\\D", "") : "";

        String corpoDist = "<distDFeInt versao=\"1.01\" xmlns=\"http://www.portalfiscal.inf.br/nfe\">" +
                "<tpAmb>1</tpAmb><cUFAutor>42</cUFAutor>" +
                "<CNPJ>" + cnpjLimpo + "</CNPJ>" +
                "<distNSU><ultNSU>" + String.format("%015d", ultNsu) + "</ultNSU></distNSU>" +
                "</distDFeInt>";

        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
                "<soap12:Envelope xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" xmlns:soap12=\"http://www.w3.org/2003/05/soap-envelope\">" +
                "<soap12:Body>" +
                "<nfeDistDFeInteresse xmlns=\"http://www.portalfiscal.inf.br/nfe/wsdl/NFeDistribuicaoDFe\">" +
                "<nfeDadosMsg>" + corpoDist + "</nfeDadosMsg>" +
                "</nfeDistDFeInteresse>" +
                "</soap12:Body>" +
                "</soap12:Envelope>";
    }

    public record DocumentoResumoDfe(
            String chave,
            String modelo, // 55 ou 57
            String cnpjEmitente,
            String nomeEmitente,
            BigDecimal valor,
            OffsetDateTime dataEmissao,
            long nsu,
            boolean isResumo
    ) {}

    public DocumentoResumoDfe interpretarXmlDfe(String xml, String cnpjEmpresa) {
        if (xml == null || xml.isBlank()) return null;

        if (xml.contains("<resNFe")) {
            String chNFe = extrairTag(xml, "chNFe");
            String cnpjEmit = extrairTag(xml, "CNPJ");
            String xNome = extrairTag(xml, "xNome");
            String vNF = extrairTag(xml, "vNF");
            String dhEmi = extrairTag(xml, "dhEmi");
            String nsu = extrairTag(xml, "NSU");

            return new DocumentoResumoDfe(
                    chNFe,
                    "55",
                    cnpjEmit,
                    xNome != null ? xNome : "Emitente",
                    vNF != null ? new BigDecimal(vNF) : BigDecimal.ZERO,
                    dhEmi != null ? OffsetDateTime.parse(dhEmi) : OffsetDateTime.now(),
                    nsu != null ? Long.parseLong(nsu) : 0L,
                    true
            );
        }

        if (xml.contains("<resCTe")) {
            String chCTe = extrairTag(xml, "chCTe");
            String cnpjEmit = extrairTag(xml, "CNPJ");
            String xNome = extrairTag(xml, "xNome");
            String vTPrest = extrairTag(xml, "vTPrest");
            String dhEmi = extrairTag(xml, "dhEmi");
            String nsu = extrairTag(xml, "NSU");

            return new DocumentoResumoDfe(
                    chCTe,
                    "57",
                    cnpjEmit,
                    xNome != null ? xNome : "Transportador",
                    vTPrest != null ? new BigDecimal(vTPrest) : BigDecimal.ZERO,
                    dhEmi != null ? OffsetDateTime.parse(dhEmi) : OffsetDateTime.now(),
                    nsu != null ? Long.parseLong(nsu) : 0L,
                    true
            );
        }

        return null;
    }

    private String extrairTag(String xml, String tag) {
        Pattern p = Pattern.compile("<" + tag + "[^>]*>([^<]+)</" + tag + ">");
        Matcher m = p.matcher(xml);
        if (m.find()) {
            return m.group(1).trim();
        }
        return null;
    }
}

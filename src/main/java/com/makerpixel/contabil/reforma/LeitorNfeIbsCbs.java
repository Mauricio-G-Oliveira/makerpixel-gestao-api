package com.makerpixel.contabil.reforma;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Leitor seguro de XML de NF-e e NFC-e (modelos 55 e 65) para extração de grupos
 * de tributação da Reforma Tributária do Consumo (IBS e CBS - LC 214/2025).
 * Protegido contra ataques XXE (XML External Entity).
 */
public class LeitorNfeIbsCbs {

    private static final Logger log = LoggerFactory.getLogger(LeitorNfeIbsCbs.class);

    public record ItemNotaIbsCbs(
            int numeroItem,
            String codigoProduto,
            String descricao,
            String ncm,
            String cfop,
            BigDecimal quantidade,
            BigDecimal valorItem,
            boolean temGrupoIbsCbs,
            String cstInformado,
            String cClassTrib,
            BigDecimal baseCalculoInformada,
            BigDecimal aliqCbsInformada,
            BigDecimal valorCbsInformado,
            BigDecimal aliqIbsUfInformada,
            BigDecimal valorIbsUfInformado,
            BigDecimal aliqIbsMunInformada,
            BigDecimal valorIbsMunInformado,
            BigDecimal valorIbsTotalInformado
    ) {}

    public record NotaFiscalIbsCbs(
            String chaveAcesso,
            String numeroNota,
            String modelo,
            OffsetDateTime dataEmissao,
            String ufEmitente,
            String municipioEmitente,
            String ufDestinatario,
            String municipioDestinatario,
            List<ItemNotaIbsCbs> itens
    ) {}

    /**
     * Cria DocumentBuilder seguro com proteções OWASP XXE.
     */
    private static DocumentBuilder criarDocumentBuilderSeguro() throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
        dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        dbf.setXIncludeAware(false);
        dbf.setExpandEntityReferences(false);
        return dbf.newDocumentBuilder();
    }

    /**
     * Faz o parse de um XML de NF-e/NFC-e a partir de String.
     */
    public static NotaFiscalIbsCbs lerXml(String xmlConteudo) throws Exception {
        return lerXml(new ByteArrayInputStream(xmlConteudo.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Faz o parse de um XML de NF-e/NFC-e a partir de InputStream.
     */
    public static NotaFiscalIbsCbs lerXml(InputStream is) throws Exception {
        DocumentBuilder db = criarDocumentBuilderSeguro();
        Document doc = db.parse(is);
        doc.getDocumentElement().normalize();

        // Extrai chave de acesso
        String chave = "";
        Element infNFe = (Element) doc.getElementsByTagName("infNFe").item(0);
        if (infNFe != null) {
            chave = infNFe.getAttribute("Id").replaceAll("\\D", "");
        }
        if (chave.isEmpty()) {
            Element chNFe = (Element) doc.getElementsByTagName("chNFe").item(0);
            if (chNFe != null) chave = chNFe.getTextContent().trim();
        }

        // Metadados da nota (ide)
        Element ide = (Element) doc.getElementsByTagName("ide").item(0);
        String numeroNota = ide != null ? obterTextoTag(ide, "nNF", "0") : "0";
        String modelo = ide != null ? obterTextoTag(ide, "mod", "55") : "55";
        String dhEmiStr = ide != null ? obterTextoTag(ide, "dhEmi", "") : "";
        OffsetDateTime dataEmissao = OffsetDateTime.now();
        if (!dhEmiStr.isEmpty()) {
            try {
                dataEmissao = OffsetDateTime.parse(dhEmiStr);
            } catch (Exception ignored) {}
        }

        // Endereços Emitente e Destinatário
        Element emit = (Element) doc.getElementsByTagName("emit").item(0);
        Element enderEmit = emit != null ? (Element) emit.getElementsByTagName("enderEmit").item(0) : null;
        String ufEmit = enderEmit != null ? obterTextoTag(enderEmit, "UF", "SC") : "SC";
        String cMunEmit = enderEmit != null ? obterTextoTag(enderEmit, "cMun", "") : "";

        Element dest = (Element) doc.getElementsByTagName("dest").item(0);
        Element enderDest = dest != null ? (Element) dest.getElementsByTagName("enderDest").item(0) : null;
        String ufDest = enderDest != null ? obterTextoTag(enderDest, "UF", ufEmit) : ufEmit;
        String cMunDest = enderDest != null ? obterTextoTag(enderDest, "cMun", cMunEmit) : cMunEmit;

        // Itens (det)
        NodeList detList = doc.getElementsByTagName("det");
        List<ItemNotaIbsCbs> itens = new ArrayList<>();

        for (int i = 0; i < detList.getLength(); i++) {
            Element det = (Element) detList.item(i);
            int nItem = i + 1;
            try {
                String nItemStr = det.getAttribute("nItem");
                if (!nItemStr.isEmpty()) nItem = Integer.parseInt(nItemStr);
            } catch (Exception ignored) {}

            Element prod = (Element) det.getElementsByTagName("prod").item(0);
            String cProd = prod != null ? obterTextoTag(prod, "cProd", "") : "";
            String xProd = prod != null ? obterTextoTag(prod, "xProd", "Item " + nItem) : "Item " + nItem;
            String ncm = prod != null ? obterTextoTag(prod, "NCM", "") : "";
            String cfop = prod != null ? obterTextoTag(prod, "CFOP", "") : "";
            BigDecimal qCom = prod != null ? obterDecimalTag(prod, "qCom", BigDecimal.ONE) : BigDecimal.ONE;
            BigDecimal vProd = prod != null ? obterDecimalTag(prod, "vProd", BigDecimal.ZERO) : BigDecimal.ZERO;

            // Grupo IBSCBS (NT 2024.002 / LC 214)
            Element imposto = (Element) det.getElementsByTagName("imposto").item(0);
            Element ibscbs = null;
            if (imposto != null) {
                ibscbs = (Element) imposto.getElementsByTagName("IBSCBS").item(0);
                if (ibscbs == null) {
                    ibscbs = (Element) imposto.getElementsByTagName("ibscbs").item(0);
                }
            }

            boolean temIbsCbs = ibscbs != null;
            String cst = "";
            String cClassTrib = "";
            BigDecimal vBC = BigDecimal.ZERO;
            BigDecimal pCbs = BigDecimal.ZERO;
            BigDecimal vCbs = BigDecimal.ZERO;
            BigDecimal pIbsUf = BigDecimal.ZERO;
            BigDecimal vIbsUf = BigDecimal.ZERO;
            BigDecimal pIbsMun = BigDecimal.ZERO;
            BigDecimal vIbsMun = BigDecimal.ZERO;
            BigDecimal vIbsTotal = BigDecimal.ZERO;

            if (temIbsCbs) {
                cst = obterTextoTag(ibscbs, "CST", "");
                if (cst.isEmpty()) cst = obterTextoTag(ibscbs, "cst", "01");
                cClassTrib = obterTextoTag(ibscbs, "cClassTrib", "");
                if (cClassTrib.isEmpty()) cClassTrib = obterTextoTag(ibscbs, "cclasstrib", "");

                vBC = obterDecimalTag(ibscbs, "vBC", vProd);

                // CBS
                Element gCbs = (Element) ibscbs.getElementsByTagName("gCBS").item(0);
                if (gCbs == null) gCbs = (Element) ibscbs.getElementsByTagName("gcbs").item(0);
                if (gCbs != null) {
                    pCbs = obterDecimalTag(gCbs, "pCBS", BigDecimal.ZERO);
                    vCbs = obterDecimalTag(gCbs, "vCBS", BigDecimal.ZERO);
                } else {
                    vCbs = obterDecimalTag(ibscbs, "vCBS", BigDecimal.ZERO);
                }

                // IBS UF
                Element gIbsUf = (Element) ibscbs.getElementsByTagName("gIBSUF").item(0);
                if (gIbsUf == null) gIbsUf = (Element) ibscbs.getElementsByTagName("gibsuf").item(0);
                if (gIbsUf != null) {
                    pIbsUf = obterDecimalTag(gIbsUf, "pIBSUF", BigDecimal.ZERO);
                    vIbsUf = obterDecimalTag(gIbsUf, "vIBSUF", BigDecimal.ZERO);
                }

                // IBS Mun
                Element gIbsMun = (Element) ibscbs.getElementsByTagName("gIBSMun").item(0);
                if (gIbsMun == null) gIbsMun = (Element) ibscbs.getElementsByTagName("gibsmun").item(0);
                if (gIbsMun != null) {
                    pIbsMun = obterDecimalTag(gIbsMun, "pIBSMun", BigDecimal.ZERO);
                    vIbsMun = obterDecimalTag(gIbsMun, "vIBSMun", BigDecimal.ZERO);
                }

                vIbsTotal = obterDecimalTag(ibscbs, "vIBS", vIbsUf.add(vIbsMun));
            }

            itens.add(new ItemNotaIbsCbs(
                    nItem, cProd, xProd, ncm, cfop, qCom, vProd,
                    temIbsCbs, cst, cClassTrib, vBC, pCbs, vCbs,
                    pIbsUf, vIbsUf, pIbsMun, vIbsMun, vIbsTotal
            ));
        }

        return new NotaFiscalIbsCbs(
                chave, numeroNota, modelo, dataEmissao,
                ufEmit, cMunEmit, ufDest, cMunDest, itens
        );
    }

    /**
     * Descompacta um arquivo ZIP em memória e faz o parse de todos os XMLs válidos.
     */
    public static List<NotaFiscalIbsCbs> lerZip(InputStream zipStream) {
        List<NotaFiscalIbsCbs> lista = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(zipStream)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory() && entry.getName().toLowerCase().endsWith(".xml")) {
                    try {
                        byte[] bytes = zis.readAllBytes();
                        NotaFiscalIbsCbs nf = lerXml(new ByteArrayInputStream(bytes));
                        if (nf.itens() != null && !nf.itens().isEmpty()) {
                            lista.add(nf);
                        }
                    } catch (Exception e) {
                        log.debug("Arquivo {} no ZIP não é um XML de NF-e válido: {}", entry.getName(), e.getMessage());
                    }
                }
                zis.closeEntry();
            }
        } catch (Exception e) {
            log.error("Erro ao descompactar ZIP para leitura de notas fiscais: {}", e.getMessage());
        }
        return lista;
    }

    private static String obterTextoTag(Element parent, String tagName, String defaultValue) {
        NodeList nl = parent.getElementsByTagName(tagName);
        if (nl != null && nl.getLength() > 0) {
            String val = nl.item(0).getTextContent();
            return val != null ? val.trim() : defaultValue;
        }
        return defaultValue;
    }

    private static BigDecimal obterDecimalTag(Element parent, String tagName, BigDecimal defaultValue) {
        String val = obterTextoTag(parent, tagName, "");
        if (!val.isEmpty()) {
            try {
                return new BigDecimal(val.trim());
            } catch (Exception ignored) {}
        }
        return defaultValue;
    }
}

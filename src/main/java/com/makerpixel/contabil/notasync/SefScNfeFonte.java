package com.makerpixel.contabil.notasync;

import java.time.Duration;

/**
 * Fonte de captura fiscal para NF-e (Modelo 55) via SEF/SC para o contabilista.
 *
 * NOTA DE ARQUITETURA:
 * A SEF/SC oficializou a distribuição gratuita direta para contabilistas primeiramente
 * para NFC-e (modelo 65 via Boletim Técnico 2026/003). Para NF-e (modelo 55), o canal oficial
 * de distribuição para o contabilista segue o modelo de consulta com autenticação mTLS.
 * 
 * TODO: Conferir o WSDL oficial de NF-e da SEF/SC quando publicado em produção definitiva.
 */
public class SefScNfeFonte {

    public static final String ID = "sef-sc-nfe";
    public static final String URL_PRODUCAO = "https://nfe.sat.sef.sc.gov.br/ws/distribuicao/DistribuicaoNfeDownload.asmx";

    public String id() {
        return ID;
    }

    public String montarEnvelopeDistNfe(String cnpjContador, String cnpjEmpresa, long ultNsu) {
        String cnpjEnvio = (cnpjContador != null && !cnpjContador.isBlank())
                ? cnpjContador.replaceAll("\\D", "")
                : cnpjEmpresa.replaceAll("\\D", "");

        String corpoDist = "<distNFeSC versao=\"1.00\" xmlns=\"http://www.satnfe.sef.sc.gov.br/ws/distribuicao-v1\">" +
                "<tpAmb>1</tpAmb><verAplic>notasync 1.0</verAplic><cUF>42</cUF>" +
                "<CNPJ>" + cnpjEnvio + "</CNPJ>" +
                "<solRel><indXML>0</indXML><indAtor>0</indAtor><ultNuNSU>" + ultNsu + "</ultNuNSU></solRel>" +
                "</distNFeSC>";

        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
                "<soap12:Envelope xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" xmlns:soap12=\"http://www.w3.org/2003/05/soap-envelope\">" +
                "<soap12:Body>" +
                "<nfeDownloadContab xmlns=\"http://www.satnfe.sef.sc.gov.br/ws/distribuicao-v1\">" +
                "<nfeDadosMsg>" + corpoDist + "</nfeDadosMsg>" +
                "</nfeDownloadContab>" +
                "</soap12:Body>" +
                "</soap12:Envelope>";
    }
}

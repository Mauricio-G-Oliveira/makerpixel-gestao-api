package com.makerpixel.contabil.notasync;

/**
 * Montador de envelopes SOAP para o web service de Distribuição de NFC-e da SEF/SC.
 * Especificação: Boletim Técnico SEF/SC 2026/003, v1.00-1 (nfceDownloadContab).
 * 
 * NOTA: O envelope segue estritamente as regras de namespace UTF-8 sem prefixos e
 * sem espaços entre tags. TODO: Conferir o ?WSDL oficial em produção para variações de cabeçalho.
 */
public class NotasyncEnvelopeBuilder {

    public static final String SEF_SC_PROD_URL = "https://dfe.sat.sef.sc.gov.br/nfce/ws/distribuicao/DistribuicaoNfceDownload.asmx";
    public static final String SEF_SC_SIMULADOR_URL = "http://localhost:8089/notasync-simulador/ws/distribuicao/DistribuicaoNfceDownload.asmx";

    public static String buildDistNfcXml(String cnpjContador, long ultNuNsu, int indAtor, int indXml) {
        String cnpjLimpo = (cnpjContador != null ? cnpjContador.replaceAll("\\D", "") : "");
        while (cnpjLimpo.length() < 14) cnpjLimpo = "0" + cnpjLimpo;

        return "<distNFCeSC versao=\"1.00\" xmlns=\"http://www.satnfce.sef.sc.gov.br/ws/distribuicao-v1\">" +
                "<tpAmb>1</tpAmb><verAplic>notasync 1.0</verAplic><cUF>42</cUF>" +
                "<CNPJ>" + cnpjLimpo + "</CNPJ>" +
                "<solRel><indXML>" + indXml + "</indXML><indAtor>" + indAtor + "</indAtor><ultNuNSU>" + ultNuNsu + "</ultNuNSU></solRel>" +
                "</distNFCeSC>";
    }

    public static String buildDistNfcPorChaveXml(String cnpjContador, String chaveAcesso) {
        String cnpjLimpo = (cnpjContador != null ? cnpjContador.replaceAll("\\D", "") : "");
        String chLimpa = (chaveAcesso != null ? chaveAcesso.replaceAll("\\D", "") : "");

        return "<distNFCeSC versao=\"1.00\" xmlns=\"http://www.satnfce.sef.sc.gov.br/ws/distribuicao-v1\">" +
                "<tpAmb>1</tpAmb><verAplic>notasync 1.0</verAplic><cUF>42</cUF>" +
                "<CNPJ>" + cnpjLimpo + "</CNPJ>" +
                "<solDFe><chAcesso>" + chLimpa + "</chAcesso></solDFe>" +
                "</distNFCeSC>";
    }

    public static String wrapSoap12Envelope(String corpoDistNfcXml) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
                "<soap12:Envelope xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" xmlns:soap12=\"http://www.w3.org/2003/05/soap-envelope\">" +
                "<soap12:Body>" +
                "<nfceDownloadContab xmlns=\"http://www.satnfce.sef.sc.gov.br/ws/distribuicao-v1\">" +
                "<nfceDadosMsg>" + corpoDistNfcXml + "</nfceDadosMsg>" +
                "</nfceDownloadContab>" +
                "</soap12:Body>" +
                "</soap12:Envelope>";
    }
}

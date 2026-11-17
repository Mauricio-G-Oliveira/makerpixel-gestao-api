package com.makerpixel.contabil.notasync;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.zip.GZIPOutputStream;

/**
 * Simulador do Web Service da SEF/SC (Boletim Técnico 2026/003, v1.00-1).
 * Responde no formato SOAP 1.2 idêntico ao serviço de produção da Fazenda de SC,
 * retornando lotes gzip+base64 de notas fictícias para testes, demo e homologação.
 */
@RestController
@RequestMapping("/notasync-simulador")
public class NotasyncSimuladorController {

    @PostMapping(
            value = "/ws/distribuicao/DistribuicaoNfceDownload.asmx",
            consumes = {MediaType.APPLICATION_XML_VALUE, MediaType.TEXT_XML_VALUE, "application/soap+xml", "*/*"},
            produces = "application/soap+xml;charset=UTF-8"
    )
    public ResponseEntity<String> responderDownloadNfce(@RequestBody(required = false) String soapRequest) {
        String req = soapRequest != null ? soapRequest : "";

        // Cenário 1: CNPJ sem vínculo SAT/SEF (Erro 8002)
        if (req.contains("12345678000195") || req.contains("8002")) {
            String corpoRet = "<retDistNFCeSC versao=\"1.00\" xmlns=\"http://www.satnfce.sef.sc.gov.br/ws/distribuicao-v1\">" +
                    "<tpAmb>1</tpAmb><verAplic>SEF_SC_SIMULADOR_1.0</verAplic>" +
                    "<cStat>8002</cStat>" +
                    "<xMotivo>Contador transmissor nao e contabilista deste CNPJ no SAT da SEF/SC</xMotivo>" +
                    "<dhResp>" + OffsetDateTime.now() + "</dhResp>" +
                    "<ultNuNSURet>0</ultNuNSURet><qtDfeRet>0</qtDfeRet>" +
                    "</retDistNFCeSC>";
            return ResponseEntity.ok(wrapSoapResponse(corpoRet));
        }

        // Cenário 2: Consulta além do limite de documentos (Fim de lote 117)
        if (req.contains("<ultNuNSU>500</ultNuNSU>") || req.contains("<ultNuNSU>1000</ultNuNSU>")) {
            String corpoRet = "<retDistNFCeSC versao=\"1.00\" xmlns=\"http://www.satnfce.sef.sc.gov.br/ws/distribuicao-v1\">" +
                    "<tpAmb>1</tpAmb><verAplic>SEF_SC_SIMULADOR_1.0</verAplic>" +
                    "<cStat>117</cStat>" +
                    "<xMotivo>Fim do lote de notas para este CNPJ (nenhuma nova nota)</xMotivo>" +
                    "<dhResp>" + OffsetDateTime.now() + "</dhResp>" +
                    "<ultNuNSURet>1050</ultNuNSURet><qtDfeRet>0</qtDfeRet>" +
                    "</retDistNFCeSC>";
            return ResponseEntity.ok(wrapSoapResponse(corpoRet));
        }

        // Cenário 3: Sucesso com lote de documentos fictícios válidos (118)
        String loteXml = gerarXmlLoteFicticio(10, 1001);
        String loteCompBase64 = compactarGzipBase64(loteXml);

        String corpoRet = "<retDistNFCeSC versao=\"1.00\" xmlns=\"http://www.satnfce.sef.sc.gov.br/ws/distribuicao-v1\">" +
                "<tpAmb>1</tpAmb><verAplic>SEF_SC_SIMULADOR_1.0</verAplic>" +
                "<cStat>118</cStat>" +
                "<xMotivo>Lote de documentos localizado com sucesso na SEF/SC</xMotivo>" +
                "<dhResp>" + OffsetDateTime.now() + "</dhResp>" +
                "<ultNuNSURet>1010</ultNuNSURet><qtDfeRet>10</qtDfeRet>" +
                "<loteDistComp>" + loteCompBase64 + "</loteDistComp>" +
                "</retDistNFCeSC>";

        return ResponseEntity.ok(wrapSoapResponse(corpoRet));
    }

    private String wrapSoapResponse(String corpoRetXml) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
                "<soap12:Envelope xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" xmlns:soap12=\"http://www.w3.org/2003/05/soap-envelope\">" +
                "<soap12:Body>" +
                "<nfceDownloadContabResponse xmlns=\"http://www.satnfce.sef.sc.gov.br/ws/distribuicao-v1\">" +
                "<nfceDownloadContabResult>" + corpoRetXml + "</nfceDownloadContabResult>" +
                "</nfceDownloadContabResponse>" +
                "</soap12:Body>" +
                "</soap12:Envelope>";
    }

    private String gerarXmlLoteFicticio(int quantidade, long nsuInicial) {
        StringBuilder sb = new StringBuilder();
        sb.append("<loteDistNFCeSC xmlns=\"http://www.satnfce.sef.sc.gov.br/ws/distribuicao-v1\">");

        for (int i = 1; i <= quantidade; i++) {
            long nsu = nsuInicial + i - 1;
            // Chave fictícia para SC (42), modelo 65 com DV calculado
            String base43 = String.format("4226111122233300018165001%09d11000000%d", i, (i % 9));
            int dv = calcularModulo11(base43);
            String chave = base43 + dv;

            sb.append("<distNFCeSC NSU=\"").append(nsu).append("\" chAcesso=\"").append(chave).append("\">");
            sb.append("<nfeProc versao=\"4.00\"><NFe><infNFe>");
            sb.append("<ide><nNF>").append(i).append("</nNF><serie>1</serie><dhEmi>2026-11-17T10:30:00-03:00</dhEmi></ide>");
            sb.append("<emit><CNPJ>11222333000181</CNPJ></emit>");
            sb.append("<total><ICMSTot><vNF>").append(String.format(java.util.Locale.US, "%.2f", 45.50 + i * 12.30)).append("</vNF></ICMSTot></total>");
            sb.append("</infNFe></NFe></nfeProc>");
            sb.append("</distNFCeSC>");
        }

        sb.append("</loteDistNFCeSC>");
        return sb.toString();
    }

    private int calcularModulo11(String chave43) {
        int soma = 0;
        int peso = 2;
        for (int i = chave43.length() - 1; i >= 0; i--) {
            soma += Character.getNumericValue(chave43.charAt(i)) * peso;
            peso = (peso == 9) ? 2 : peso + 1;
        }
        int resto = soma % 11;
        return (resto == 0 || resto == 1) ? 0 : 11 - resto;
    }

    private String compactarGzipBase64(String conteudoXml) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (GZIPOutputStream gzos = new GZIPOutputStream(baos)) {
                gzos.write(conteudoXml.getBytes(StandardCharsets.UTF_8));
            }
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException("Falha ao compactar lote simulado em gzip", e);
        }
    }
}

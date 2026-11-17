package com.makerpixel.contabil.notasync;

import com.makerpixel.contabil.domain.SituacaoNfce;
import com.makerpixel.contabil.dto.NotaSyncDTOs.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/**
 * Agente Local NotaSync (notasync-agente).
 * Roda no computador do escritório de contabilidade, garantindo que o certificado digital
 * A1 do contador permaneça 100% isolado localmente. Salva os XMLs físicos em disco e envia
 * apenas os metadados contábeis para a Central Contábil.
 */
@Service
public class NotasyncAgenteService {

    @Value("${notasync.xml-saida-dir:xml-saida}")
    private String xmlSaidaDir;

    @Value("${notasync.sef-endpoint:http://localhost:8089/notasync-simulador/ws/distribuicao/DistribuicaoNfceDownload.asmx}")
    private String sefEndpoint;

    /**
     * Salva o arquivo XML físico da NFC-e na estrutura padronizada:
     * xml-saida/{CNPJ}/{AAAA-MM}/{chave}.xml
     */
    public Path salvarXmlLocalmente(String cnpjEmitente, String chaveAcesso, String conteudoXml) throws Exception {
        String cnpjLimpo = cnpjEmitente.replaceAll("\\D", "");
        // Extrai AAAA-MM da chave (posições 2 a 6 são AAMM)
        String ano = "20" + chaveAcesso.substring(2, 4);
        String mes = chaveAcesso.substring(4, 6);
        String pastaPeriodo = ano + "-" + mes;

        Path diretorio = Paths.get(xmlSaidaDir, cnpjLimpo, pastaPeriodo);
        Files.createDirectories(diretorio);

        Path caminhoArquivo = diretorio.resolve(chaveAcesso + ".xml");
        Files.writeString(caminhoArquivo, conteudoXml, StandardCharsets.UTF_8);
        return caminhoArquivo;
    }

    /**
     * Descompacta o loteDistComp retornado pela SEF/SC (base64 de gzip).
     */
    public List<NfceItemDTO> desempacotarLoteGzip(String loteDistCompBase64, String cnpjEmitente) throws Exception {
        byte[] gzippedBytes = Base64.getDecoder().decode(loteDistCompBase64);
        ByteArrayInputStream bais = new ByteArrayInputStream(gzippedBytes);
        GZIPInputStream gzis = new GZIPInputStream(bais);

        String xmlDescompactado = new String(gzis.readAllBytes(), StandardCharsets.UTF_8);
        List<NfceItemDTO> docs = new ArrayList<>();

        Pattern pattern = Pattern.compile("<distNFCeSC\\s+NSU=\"(\\d+)\"\\s+chAcesso=\"(\\d{44})\">([\\s\\S]*?)</distNFCeSC>");
        Matcher matcher = pattern.matcher(xmlDescompactado);

        while (matcher.find()) {
            long nsu = Long.parseLong(matcher.group(1));
            String chave = matcher.group(2);
            String conteudo = matcher.group(3);

            boolean ehCancelada = conteudo.contains("110111") || conteudo.contains("110112") || conteudo.contains("procEventoNFe");

            // Extrai valor
            java.math.BigDecimal valor = java.math.BigDecimal.ZERO;
            Matcher vMatcher = Pattern.compile("<vNF>([\\d.]+)</vNF>").matcher(conteudo);
            if (vMatcher.find()) {
                valor = new java.math.BigDecimal(vMatcher.group(1));
            }

            // Extrai número
            int numero = 1;
            Matcher nMatcher = Pattern.compile("<nNF>(\\d+)</nNF>").matcher(conteudo);
            if (nMatcher.find()) {
                numero = Integer.parseInt(nMatcher.group(1));
            }

            // Salva XML físico no disco local
            try {
                salvarXmlLocalmente(cnpjEmitente, chave, conteudo);
            } catch (Exception ignored) {}

            docs.add(new NfceItemDTO(
                    chave,
                    numero,
                    1,
                    OffsetDateTime.now(),
                    valor,
                    ehCancelada ? SituacaoNfce.CANCELADA : SituacaoNfce.AUTORIZADA,
                    ehCancelada ? "Cancelamento SEF/SC" : null,
                    nsu
            ));
        }

        return docs;
    }
}

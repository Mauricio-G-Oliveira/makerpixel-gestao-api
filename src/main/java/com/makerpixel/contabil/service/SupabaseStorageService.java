package com.makerpixel.contabil.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Serviço de Armazenamento Seguro de Documentos Fiscais (Supabase Storage / Local Fallback).
 * Estrutura: {escritorio}/{cnpj}/{AAAA-MM}/{chave}.xml.gz
 */
@Service
public class SupabaseStorageService {

    private static final Logger log = LoggerFactory.getLogger(SupabaseStorageService.class);

    private final String supabaseUrl;
    private final String supabaseKey;
    private final String storageBucket;
    private final Path localBaseDir;

    public SupabaseStorageService(
            @Value("${supabase.url:}") String supabaseUrl,
            @Value("${supabase.key:}") String supabaseKey,
            @Value("${supabase.storage.bucket:notasync-docs}") String storageBucket,
            @Value("${notasync.storage.local-dir:storage/notasync}") String localDir
    ) {
        this.supabaseUrl = supabaseUrl;
        this.supabaseKey = supabaseKey;
        this.storageBucket = storageBucket;
        this.localBaseDir = Paths.get(localDir);
    }

    public record ArquivoXml(String nomeArquivo, byte[] xmlBytes) {}

    /**
     * Salva o conteúdo XML comprimido em GZIP no caminho padrão:
     * {escritorioId}/{cnpj}/{anoMes}/{chave}.xml.gz
     */
    public String salvarXmlGz(UUID escritorioId, String cnpj, String chaveAcesso, String conteudoXml) throws IOException {
        String cnpjLimpo = cnpj.replaceAll("\\D", "");
        String anoMes = extrairAnoMes(chaveAcesso);
        String relativo = escritorioId.toString() + "/" + cnpjLimpo + "/" + anoMes + "/" + chaveAcesso + ".xml.gz";

        byte[] xmlBytes = conteudoXml.getBytes(StandardCharsets.UTF_8);
        byte[] gzippedBytes = comprimirGzip(xmlBytes);

        if (isSupabaseConfigured()) {
            try {
                enviarParaSupabaseStorage(relativo, gzippedBytes);
                return relativo;
            } catch (Exception e) {
                log.warn("Falha ao enviar ao Supabase Storage. Usando fallback local: {}", e.getMessage());
            }
        }

        // Armazenamento local
        Path destino = localBaseDir.resolve(relativo);
        Files.createDirectories(destino.getParent());
        Files.write(destino, gzippedBytes);
        return relativo;
    }

    /**
     * Recupera o XML descompactado a partir do path de armazenamento.
     */
    public String obterXml(String storagePath) throws IOException {
        if (storagePath == null || storagePath.isBlank()) {
            throw new FileNotFoundException("Caminho do documento não informado.");
        }

        byte[] gzippedBytes;
        if (isSupabaseConfigured()) {
            try {
                gzippedBytes = baixarDoSupabaseStorage(storagePath);
                return descompactarGzip(gzippedBytes);
            } catch (Exception e) {
                log.warn("Falha ao recuperar do Supabase Storage. Tentando local: {}", e.getMessage());
            }
        }

        Path local = localBaseDir.resolve(storagePath);
        if (!Files.exists(local)) {
            throw new FileNotFoundException("Arquivo não localizado no armazenamento: " + storagePath);
        }
        gzippedBytes = Files.readAllBytes(local);
        return descompactarGzip(gzippedBytes);
    }

    /**
     * Escreve um ZIP com todos os XMLs daquele mês em streaming diretamente no OutputStream.
     */
    public void gerarZipMesStreaming(UUID escritorioId, String cnpj, String anoMes, List<Map<String, String>> docs, OutputStream outputStream) throws IOException {
        String cnpjLimpo = cnpj.replaceAll("\\D", "");
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(outputStream))) {
            for (Map<String, String> doc : docs) {
                String chave = doc.get("chave");
                String storagePath = doc.get("storagePath");

                try {
                    String xml;
                    if (storagePath != null && !storagePath.isBlank()) {
                        xml = obterXml(storagePath);
                    } else {
                        // XML simulado se ainda não armazenado
                        xml = "<nfeProc versao=\"4.00\" xmlns=\"http://www.portalfiscal.inf.br/nfe\"><NFe><infNFe Id=\"NFe" + chave + "\"/></NFe></nfeProc>";
                    }

                    ZipEntry entry = new ZipEntry(chave + ".xml");
                    zos.putNextEntry(entry);
                    zos.write(xml.getBytes(StandardCharsets.UTF_8));
                    zos.closeEntry();
                } catch (Exception e) {
                    log.warn("Erro ao adicionar nota {} no ZIP: {}", chave, e.getMessage());
                }
            }
            zos.finish();
            zos.flush();
        }
    }

    public static String extrairAnoMes(String chaveAcesso) {
        if (chaveAcesso != null && chaveAcesso.length() >= 6) {
            String aamm = chaveAcesso.substring(2, 6);
            return "20" + aamm.substring(0, 2) + "-" + aamm.substring(2, 4);
        }
        return "2026-01";
    }

    public static byte[] comprimirGzip(byte[] data) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzos = new GZIPOutputStream(baos)) {
            gzos.write(data);
        }
        return baos.toByteArray();
    }

    public static String descompactarGzip(byte[] gzipped) throws IOException {
        try (GZIPInputStream gzis = new GZIPInputStream(new ByteArrayInputStream(gzipped));
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = gzis.read(buf)) != -1) {
                baos.write(buf, 0, n);
            }
            return baos.toString(StandardCharsets.UTF_8);
        }
    }

    private boolean isSupabaseConfigured() {
        return supabaseUrl != null && !supabaseUrl.isBlank() && supabaseKey != null && !supabaseKey.isBlank();
    }

    private void enviarParaSupabaseStorage(String path, byte[] content) throws Exception {
        // Envio via REST API do Supabase Storage: POST /storage/v1/object/{bucket}/{path}
        java.net.URI uri = java.net.URI.create(supabaseUrl + "/storage/v1/object/" + storageBucket + "/" + path);
        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                .uri(uri)
                .header("Authorization", "Bearer " + supabaseKey)
                .header("apikey", supabaseKey)
                .header("Content-Type", "application/gzip")
                .header("x-upsert", "true")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofByteArray(content))
                .build();

        java.net.http.HttpResponse<String> resp = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 400) {
            throw new IOException("Erro Supabase Storage HTTP " + resp.statusCode() + ": " + resp.body());
        }
    }

    private byte[] baixarDoSupabaseStorage(String path) throws Exception {
        java.net.URI uri = java.net.URI.create(supabaseUrl + "/storage/v1/object/" + storageBucket + "/" + path);
        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                .uri(uri)
                .header("Authorization", "Bearer " + supabaseKey)
                .header("apikey", supabaseKey)
                .GET()
                .build();

        java.net.http.HttpResponse<byte[]> resp = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() >= 400) {
            throw new IOException("Erro Supabase Storage HTTP " + resp.statusCode());
        }
        return resp.body();
    }
}

package com.makerpixel.contabil.controller;

import com.makerpixel.contabil.config.TenantContext;
import com.makerpixel.contabil.domain.NotasyncDocumento;
import com.makerpixel.contabil.domain.NotasyncEmpresa;
import com.makerpixel.contabil.dto.NotasyncNuvemDTOs.*;
import com.makerpixel.contabil.repository.NotasyncDocumentoRepository;
import com.makerpixel.contabil.service.NotasyncCloudWorkerService;
import com.makerpixel.contabil.service.NotasyncEmpresaService;
import com.makerpixel.contabil.service.SupabaseStorageService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/v1/notasync")
public class NotasyncNuvemController {

    private final NotasyncEmpresaService empresaService;
    private final NotasyncCloudWorkerService workerService;
    private final NotasyncDocumentoRepository documentoRepository;
    private final SupabaseStorageService storageService;

    public NotasyncNuvemController(
            NotasyncEmpresaService empresaService,
            NotasyncCloudWorkerService workerService,
            NotasyncDocumentoRepository documentoRepository,
            SupabaseStorageService storageService
    ) {
        this.empresaService = empresaService;
        this.workerService = workerService;
        this.documentoRepository = documentoRepository;
        this.storageService = storageService;
    }

    /**
     * Cadastro manual de empresa para monitoramento.
     */
    @PostMapping("/empresas")
    public ResponseEntity<NotasyncEmpresa> cadastrarEmpresa(@RequestBody CadastrarEmpresaRequest req) {
        UUID escritorioId = TenantContext.getEscritorioId();
        NotasyncEmpresa empresa = empresaService.cadastrarEmpresa(escritorioId, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(empresa);
    }

    /**
     * Importação em lote de empresas por arquivo CSV.
     */
    @PostMapping(value = "/empresas/importar-csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportarCsvResponse> importarCsv(@RequestParam("file") MultipartFile file) throws IOException {
        UUID escritorioId = TenantContext.getEscritorioId();
        ImportarCsvResponse resp = empresaService.importarCsv(escritorioId, file.getInputStream());
        return ResponseEntity.ok(resp);
    }

    /**
     * Lista as empresas monitoradas com status de busca e contagem de notas.
     */
    @GetMapping("/empresas")
    public ResponseEntity<List<EmpresaStatusDTO>> listarEmpresas() {
        UUID escritorioId = TenantContext.getEscritorioId();
        return ResponseEntity.ok(empresaService.listarEmpresas(escritorioId));
    }

    /**
     * Alterna ativação da empresa.
     */
    @PatchMapping("/empresas/{id}/toggle-ativo")
    public ResponseEntity<NotasyncEmpresa> toggleAtivo(@PathVariable UUID id) {
        UUID escritorioId = TenantContext.getEscritorioId();
        return ResponseEntity.ok(empresaService.toggleAtivo(escritorioId, id));
    }

    /**
     * Busca Imediata sob demanda ("Buscar Agora").
     * Retorna HTTP 429 Too Many Requests se a busca for acionada durante o intervalo regulamentar.
     */
    @PostMapping("/buscar-agora/{cnpj}")
    public ResponseEntity<BuscarAgoraResponse> buscarAgora(
            @PathVariable String cnpj,
            HttpServletRequest request
    ) {
        UUID escritorioId = TenantContext.getEscritorioId();
        String usuario = TenantContext.getUsuarioEmail();
        String ip = request.getRemoteAddr();

        BuscarAgoraResponse resp = workerService.buscarAgora(escritorioId, cnpj, usuario, ip);
        return ResponseEntity.ok(resp);
    }

    /**
     * Download em streaming do arquivo ZIP com todos os XMLs de um mês da empresa.
     */
    @GetMapping(value = "/empresas/{cnpj}/mes/{anoMes}/zip", produces = "application/zip")
    public ResponseEntity<StreamingResponseBody> baixarZipMes(
            @PathVariable String cnpj,
            @PathVariable String anoMes
    ) {
        UUID escritorioId = TenantContext.getEscritorioId();
        String cnpjLimpo = cnpj.replaceAll("\\D", "");

        // Converte AAAA-MM para range de datas
        String[] partes = anoMes.split("-");
        int ano = Integer.parseInt(partes[0]);
        int mes = Integer.parseInt(partes[1]);
        OffsetDateTime inicio = OffsetDateTime.of(ano, mes, 1, 0, 0, 0, 0, java.time.ZoneOffset.UTC);
        OffsetDateTime fim = inicio.plusMonths(1);

        List<NotasyncDocumento> docs = documentoRepository.findByEscritorioAndCnpjAndPeriodo(escritorioId, cnpjLimpo, inicio, fim);

        List<Map<String, String>> docsInfo = new ArrayList<>();
        for (NotasyncDocumento d : docs) {
            Map<String, String> m = new HashMap<>();
            m.put("chave", d.getChaveAcesso());
            m.put("storagePath", d.getStoragePath());
            docsInfo.add(m);
        }

        StreamingResponseBody stream = outputStream -> {
            storageService.gerarZipMesStreaming(escritorioId, cnpjLimpo, anoMes, docsInfo, outputStream);
        };

        String filename = "nfce_" + cnpjLimpo + "_" + anoMes + ".zip";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(stream);
    }

    /**
     * Download do XML individual da NFC-e.
     */
    @GetMapping(value = "/documentos/{chave}/xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> obterXmlDocumento(@PathVariable String chave) throws IOException {
        UUID escritorioId = TenantContext.getEscritorioId();
        String chaveLimpa = chave.replaceAll("\\D", "");

        NotasyncDocumento doc = documentoRepository.findByEscritorioIdAndChaveAcesso(escritorioId, chaveLimpa)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND, "Documento não encontrado."));

        String xml = storageService.obterXml(doc.getStoragePath());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + chaveLimpa + ".xml\"")
                .body(xml);
    }
}

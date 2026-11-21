package com.makerpixel.contabil.service;

import com.makerpixel.contabil.domain.*;
import com.makerpixel.contabil.dto.NotasyncNuvemDTOs.CronExecucaoResponse;
import com.makerpixel.contabil.notasync.NotasyncMaquinaRegras;
import com.makerpixel.contabil.notasync.NotasyncTradutor;
import com.makerpixel.contabil.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class NotasyncCloudWorkerTest {

    private EscritorioRepository escritorioRepository;
    private NotasyncEmpresaRepository empresaRepository;
    private NotasyncEstadoRepository estadoRepository;
    private NotasyncDocumentoRepository documentoRepository;
    private NotasyncExecucaoRepository execucaoRepository;
    private CofreCertificadoService cofreService;
    private SupabaseStorageService storageService;
    private JdbcTemplate jdbcTemplate;

    private NotasyncCloudWorkerService workerService;

    @BeforeEach
    void setUp() {
        escritorioRepository = Mockito.mock(EscritorioRepository.class);
        empresaRepository = Mockito.mock(NotasyncEmpresaRepository.class);
        estadoRepository = Mockito.mock(NotasyncEstadoRepository.class);
        documentoRepository = Mockito.mock(NotasyncDocumentoRepository.class);
        execucaoRepository = Mockito.mock(NotasyncExecucaoRepository.class);
        cofreService = Mockito.mock(CofreCertificadoService.class);
        storageService = Mockito.mock(SupabaseStorageService.class);
        jdbcTemplate = Mockito.mock(JdbcTemplate.class);

        workerService = new NotasyncCloudWorkerService(
                escritorioRepository,
                empresaRepository,
                estadoRepository,
                documentoRepository,
                execucaoRepository,
                cofreService,
                storageService,
                jdbcTemplate
        );
    }

    @Test
    @DisplayName("Regras de consumo SEF/SC: 118 cheio, 118 parcial, 117, 110, 657 e 8002")
    void deveAplicarRegrasDeEsperasRegulamentares() {
        // 118 com 50 documentos: buscar na hora
        var d118Cheio = NotasyncMaquinaRegras.calcularProximaEspera(118, 50, 1);
        assertEquals(Duration.ZERO, d118Cheio.tempoEspera());
        assertTrue(d118Cheio.buscarProximoImediato());

        // 118 com menos de 50: esperar 12h
        var d118Parcial = NotasyncMaquinaRegras.calcularProximaEspera(118, 15, 1);
        assertEquals(Duration.ofHours(12), d118Parcial.tempoEspera());
        assertFalse(d118Parcial.buscarProximoImediato());

        // 117: esperar 12h
        var d117 = NotasyncMaquinaRegras.calcularProximaEspera(117, 0, 1);
        assertEquals(Duration.ofHours(12), d117.tempoEspera());

        // 110: esperar 1h
        var d110 = NotasyncMaquinaRegras.calcularProximaEspera(110, 0, 1);
        assertEquals(Duration.ofHours(1), d110.tempoEspera());

        // 657 escalonado: 1h, 2h, 12h
        assertEquals(Duration.ofHours(1), NotasyncMaquinaRegras.calcularProximaEspera(657, 0, 1).tempoEspera());
        assertEquals(Duration.ofHours(2), NotasyncMaquinaRegras.calcularProximaEspera(657, 0, 2).tempoEspera());
        assertEquals(Duration.ofHours(12), NotasyncMaquinaRegras.calcularProximaEspera(657, 0, 3).tempoEspera());

        // 8002: esperar 24h
        var d8002 = NotasyncMaquinaRegras.calcularProximaEspera(8002, 0, 1);
        assertEquals(Duration.ofHours(24), d8002.tempoEspera());
    }

    @Test
    @DisplayName("Tradutor cStat: 8002 em destaque em português")
    void deveTraduzir8002EmDestaque() {
        String msg = NotasyncTradutor.traduzir(8002);
        assertEquals("O contador não está vinculado a este CNPJ no SAT da SEF/SC", msg);
        assertTrue(NotasyncTradutor.isErroVinculoContador(8002));
        assertFalse(NotasyncTradutor.isErroVinculoContador(118));
    }

    @Test
    @DisplayName("Validação de DV do CNPJ (Módulo 11)")
    void deveValidarCnpjModulo11() {
        // CNPJs válidos fictícios
        assertTrue(NotasyncEmpresaService.validarCnpj("11.222.333/0001-81"));
        assertTrue(NotasyncEmpresaService.validarCnpj("11222333000181"));

        // CNPJs inválidos
        assertFalse(NotasyncEmpresaService.validarCnpj("11.222.333/0001-00"));
        assertFalse(NotasyncEmpresaService.validarCnpj("00000000000000"));
        assertFalse(NotasyncEmpresaService.validarCnpj("123"));
        assertFalse(NotasyncEmpresaService.validarCnpj(null));
    }

    @Test
    @DisplayName("Buscar Agora devolve HTTP 429 durante período de espera regulamentar")
    void deveRetornar429SeConsultarDuranteEspera() {
        UUID escritorioId = UUID.randomUUID();
        String cnpj = "11222333000181";

        NotasyncEmpresa empresa = new NotasyncEmpresa(escritorioId, cnpj, "Empresa Teste", true);
        when(empresaRepository.findByEscritorioIdAndCnpj(escritorioId, cnpj)).thenReturn(Optional.of(empresa));

        NotasyncEstado estado = new NotasyncEstado(escritorioId, cnpj, "sef-sc-nfce");
        estado.setProximaConsulta(OffsetDateTime.now().plusHours(4)); // Futuro!
        estado.setMotivo("Aguardando intervalo regulamentar da SEF/SC");
        when(estadoRepository.findByEscritorioIdAndCnpjAndFonte(escritorioId, cnpj, "sef-sc-nfce")).thenReturn(Optional.of(estado));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> workerService.buscarAgora(escritorioId, cnpj, "user@test.com", "127.0.0.1"));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Aguarde o intervalo regulamentar da SEF/SC"));
    }

    @Test
    @DisplayName("Geração de arquivo ZIP em streaming com documentos do mês")
    void deveGerarZipMesStreamingComSucesso() throws Exception {
        SupabaseStorageService realStorage = new SupabaseStorageService("", "", "", "target/storage-test");
        UUID escritorioId = UUID.randomUUID();
        String cnpj = "11222333000181";
        String anoMes = "2026-03";

        List<Map<String, String>> docs = List.of(
                Map.of("chave", "42260311222333000181650010000000011000000018", "storagePath", ""),
                Map.of("chave", "42260311222333000181650010000000021000000023", "storagePath", "")
        );

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        realStorage.gerarZipMesStreaming(escritorioId, cnpj, anoMes, docs, baos);

        byte[] zipBytes = baos.toByteArray();
        assertTrue(zipBytes.length > 0);

        // Verifica que o arquivo ZIP é válido e contém as 2 notas
        List<String> entradas = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entradas.add(entry.getName());
                zis.closeEntry();
            }
        }

        assertEquals(2, entradas.size());
        assertTrue(entradas.contains("42260311222333000181650010000000011000000018.xml"));
        assertTrue(entradas.contains("42260311222333000181650010000000021000000023.xml"));
    }
}

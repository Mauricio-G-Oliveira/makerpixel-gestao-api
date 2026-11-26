package com.makerpixel.contabil.service;

import com.makerpixel.contabil.domain.AuditoriaPendente;
import com.makerpixel.contabil.domain.DocumentoFiscal;
import com.makerpixel.contabil.domain.Escritorio;
import com.makerpixel.contabil.reforma.CalculadoraRtcClient;
import com.makerpixel.contabil.reforma.dto.*;
import com.makerpixel.contabil.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class AuditoriaContinuaWorkerTest {

    private AuditoriaPendenteRepository auditoriaPendenteRepository;
    private DocumentoFiscalRepository documentoFiscalRepository;
    private ReformaLoteRepository reformaLoteRepository;
    private ReformaItemRepository reformaItemRepository;
    private EmpresaRepository empresaRepository;
    private EscritorioRepository escritorioRepository;
    private CalculadoraRtcClient calculadoraRtcClient;
    private SupabaseStorageService storageService;
    private EmDiaService emDiaService;
    private AuditoriaContinuaWorkerService workerService;

    @BeforeEach
    void setUp() {
        auditoriaPendenteRepository = mock(AuditoriaPendenteRepository.class);
        documentoFiscalRepository = mock(DocumentoFiscalRepository.class);
        reformaLoteRepository = mock(ReformaLoteRepository.class);
        reformaItemRepository = mock(ReformaItemRepository.class);
        empresaRepository = mock(EmpresaRepository.class);
        escritorioRepository = mock(EscritorioRepository.class);
        calculadoraRtcClient = mock(CalculadoraRtcClient.class);
        storageService = mock(SupabaseStorageService.class);
        emDiaService = mock(EmDiaService.class);

        workerService = new AuditoriaContinuaWorkerService(
                auditoriaPendenteRepository,
                documentoFiscalRepository,
                reformaLoteRepository,
                reformaItemRepository,
                empresaRepository,
                escritorioRepository,
                calculadoraRtcClient,
                storageService,
                emDiaService
        );
    }

    @Test
    @DisplayName("Idempotência: documento já auditado na mesma versão da calculadora é ignorado e marcado como CONCLUIDO")
    void testIdempotenciaPorVersaoCalculadora() {
        UUID escritorioId = UUID.randomUUID();
        UUID pendenteId = UUID.randomUUID();
        String chave = "42261111222333000181550010000000011000000018";
        String versao = "v1.4.2-rfb";

        when(calculadoraRtcClient.obterVersao()).thenReturn(new VersaoCalculadoraDto(versao, "LC-214", "ONLINE", "http://rtc", "2026-02-15", "OK"));

        DocumentoFiscal doc = new DocumentoFiscal();
        doc.setId(UUID.randomUUID());
        doc.setEscritorioId(escritorioId);
        doc.setChaveAcesso(chave);
        doc.setSituacao("AUTORIZADA");

        AuditoriaPendente pendente = new AuditoriaPendente();
        pendente.setId(pendenteId);
        pendente.setEscritorioId(escritorioId);
        pendente.setDocumento(doc);
        pendente.setStatus("PENDENTE");

        when(auditoriaPendenteRepository.findPendentesPorEscritorioComLock(eq(escritorioId), any(OffsetDateTime.class), anyInt()))
                .thenReturn(List.of(pendente));
        when(auditoriaPendenteRepository.findById(pendenteId)).thenReturn(Optional.of(pendente));

        Escritorio esc = new Escritorio();
        esc.setId(escritorioId);
        esc.setPlanoId("plano-escritorio");
        when(escritorioRepository.findById(escritorioId)).thenReturn(Optional.of(esc));
        when(reformaLoteRepository.sumTotalNotasPorCompetencia(eq(escritorioId), any())).thenReturn(10L);

        // Já existe auditoria com essa chave e essa versão oficial
        when(reformaItemRepository.existsByEscritorioIdAndChaveNfeAndVersaoCalculadora(escritorioId, chave, versao))
                .thenReturn(true);

        var resultado = workerService.processarPendentes(escritorioId, 10);

        assertEquals(1, resultado.processados());
        assertEquals(1, resultado.jaAuditadosIgnorados());
        assertEquals("CONCLUIDO", pendente.getStatus());
        verify(calculadoraRtcClient, never()).calcularComObservabilidade(any());
    }

    @Test
    @DisplayName("Resiliência RTC: Calculadora offline aborta o ciclo sem alterar notas nem gravar versão fictícia")
    void testCalculadoraOfflineAbortaCiclo() {
        UUID escritorioId = UUID.randomUUID();
        when(calculadoraRtcClient.obterVersao()).thenReturn(new VersaoCalculadoraDto(null, null, "OFFLINE", "http://rtc", null, "Indisponível"));

        var resultado = workerService.processarPendentes(escritorioId, 10);

        assertEquals(0, resultado.processados());
        verify(auditoriaPendenteRepository, never()).findPendentesPorEscritorioComLock(any(), any(), anyInt());
    }

    @Test
    @DisplayName("Limite do Plano: quando o limite mensal é atingido, nota é adiada")
    void testLimitePlanoAtingidoAdiaProcessamento() {
        UUID escritorioId = UUID.randomUUID();
        UUID pendenteId = UUID.randomUUID();
        String chave = "42261111222333000181550010000000011000000018";
        String versao = "v1.4.2-rfb";

        when(calculadoraRtcClient.obterVersao()).thenReturn(new VersaoCalculadoraDto(versao, "LC-214", "ONLINE", "http://rtc", "2026-02-15", "OK"));

        DocumentoFiscal doc = new DocumentoFiscal();
        doc.setId(UUID.randomUUID());
        doc.setEscritorioId(escritorioId);
        doc.setChaveAcesso(chave);
        doc.setCompetencia("2026-11");
        doc.setSituacao("AUTORIZADA");

        AuditoriaPendente pendente = new AuditoriaPendente();
        pendente.setId(pendenteId);
        pendente.setEscritorioId(escritorioId);
        pendente.setDocumento(doc);
        pendente.setStatus("PENDENTE");

        when(auditoriaPendenteRepository.findPendentesPorEscritorioComLock(eq(escritorioId), any(OffsetDateTime.class), anyInt()))
                .thenReturn(List.of(pendente));
        when(auditoriaPendenteRepository.findById(pendenteId)).thenReturn(Optional.of(pendente));

        Escritorio esc = new Escritorio();
        esc.setId(escritorioId);
        esc.setPlanoId("basico"); // Limite de 1.000 notas
        when(escritorioRepository.findById(escritorioId)).thenReturn(Optional.of(esc));
        when(reformaLoteRepository.sumTotalNotasPorCompetencia(escritorioId, "2026-11")).thenReturn(1005L); // Limite excedido

        var resultado = workerService.processarPendentes(escritorioId, 10);

        assertEquals(1, resultado.processados());
        assertEquals(1, resultado.limitePlanoAtingido());
        assertEquals("PENDENTE", pendente.getStatus());
        assertTrue(pendente.getErroMensagem().contains("Limite mensal"));
        verify(calculadoraRtcClient, never()).calcularComObservabilidade(any());
    }
}

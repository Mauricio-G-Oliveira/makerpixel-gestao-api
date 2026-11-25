package com.makerpixel.contabil.service;

import com.makerpixel.contabil.domain.AuditoriaPendente;
import com.makerpixel.contabil.domain.DocumentoFiscal;
import com.makerpixel.contabil.reforma.CalculadoraRtcClient;
import com.makerpixel.contabil.reforma.dto.*;
import com.makerpixel.contabil.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class AuditoriaContinuaWorkerTest {

    private AuditoriaPendenteRepository auditoriaPendenteRepository;
    private DocumentoFiscalRepository documentoFiscalRepository;
    private ReformaLoteRepository reformaLoteRepository;
    private ReformaItemRepository reformaItemRepository;
    private EmpresaRepository empresaRepository;
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
        calculadoraRtcClient = mock(CalculadoraRtcClient.class);
        storageService = mock(SupabaseStorageService.class);
        emDiaService = mock(EmDiaService.class);

        workerService = new AuditoriaContinuaWorkerService(
                auditoriaPendenteRepository,
                documentoFiscalRepository,
                reformaLoteRepository,
                reformaItemRepository,
                empresaRepository,
                calculadoraRtcClient,
                storageService,
                emDiaService
        );
    }

    @Test
    @DisplayName("Idempotência: documento já auditado na mesma versão da calculadora é ignorado e marcado como CONCLUIDO")
    void testIdempotenciaPorVersaoCalculadora() {
        UUID escritorioId = UUID.randomUUID();
        String chave = "42261111222333000181550010000000011000000018";
        String versao = "v1.4.2-rfb";

        when(calculadoraRtcClient.obterVersao()).thenReturn(new VersaoCalculadoraDto(versao, "LC-214", "ONLINE", "http://rtc", "2026-02-15", "OK"));

        DocumentoFiscal doc = new DocumentoFiscal();
        doc.setId(UUID.randomUUID());
        doc.setEscritorioId(escritorioId);
        doc.setChaveAcesso(chave);
        doc.setSituacao("AUTORIZADA");

        AuditoriaPendente pendente = new AuditoriaPendente();
        pendente.setId(UUID.randomUUID());
        pendente.setEscritorioId(escritorioId);
        pendente.setDocumento(doc);
        pendente.setStatus("PENDENTE");

        when(auditoriaPendenteRepository.findPendentesPorEscritorio(eq(escritorioId), any(OffsetDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(pendente));

        // Já existe auditoria com essa chave e essa versão
        when(reformaItemRepository.existsByEscritorioIdAndChaveNfeAndVersaoCalculadora(escritorioId, chave, versao))
                .thenReturn(true);

        var resultado = workerService.processarPendentes(escritorioId, 10);

        assertEquals(1, resultado.processados());
        assertEquals(1, resultado.jaAuditadosIgnorados());
        assertEquals("CONCLUIDO", pendente.getStatus());
        verify(auditoriaPendenteRepository, times(1)).save(pendente);
        verify(calculadoraRtcClient, never()).calcularComObservabilidade(any());
    }

    @Test
    @DisplayName("Resiliência: XML indisponível reagenda com backoff e incrementa tentativas")
    void testXmlIndisponivelReagendamento() {
        UUID escritorioId = UUID.randomUUID();
        String chave = "42261111222333000181550010000000031000000030";

        when(calculadoraRtcClient.obterVersao()).thenReturn(new VersaoCalculadoraDto("v1.0.0", "LC-214", "ONLINE", "http://rtc", "2026-01-01", "OK"));

        DocumentoFiscal doc = new DocumentoFiscal();
        doc.setId(UUID.randomUUID());
        doc.setEscritorioId(escritorioId);
        doc.setChaveAcesso(chave);
        doc.setSituacao("AUTORIZADA");
        doc.setStoragePath("arquivos/nao_existe.xml");

        AuditoriaPendente pendente = new AuditoriaPendente();
        pendente.setId(UUID.randomUUID());
        pendente.setEscritorioId(escritorioId);
        pendente.setDocumento(doc);
        pendente.setStatus("PENDENTE");
        pendente.setTentativas(1);

        when(auditoriaPendenteRepository.findPendentesPorEscritorio(eq(escritorioId), any(OffsetDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(pendente));
        when(storageService.baixarArquivo("arquivos/nao_existe.xml")).thenReturn(null);

        var resultado = workerService.processarPendentes(escritorioId, 10);

        assertEquals(1, resultado.processados());
        assertEquals(1, resultado.falhasComReagendamento());
        assertEquals(2, pendente.getTentativas());
        assertNotNull(pendente.getProximaTentativa());
        verify(auditoriaPendenteRepository, times(1)).save(pendente);
    }
}

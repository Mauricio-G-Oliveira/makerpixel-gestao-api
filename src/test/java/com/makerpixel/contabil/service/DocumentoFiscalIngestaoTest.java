package com.makerpixel.contabil.service;

import com.makerpixel.contabil.domain.AuditoriaPendente;
import com.makerpixel.contabil.domain.DocumentoFiscal;
import com.makerpixel.contabil.fonte.DocumentoColetadoDTO;
import com.makerpixel.contabil.repository.AuditoriaPendenteRepository;
import com.makerpixel.contabil.repository.DocumentoFiscalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class DocumentoFiscalIngestaoTest {

    private DocumentoFiscalRepository documentoFiscalRepository;
    private AuditoriaPendenteRepository auditoriaPendenteRepository;
    private DocumentoFiscalIngestaoService ingestaoService;

    @BeforeEach
    void setUp() {
        documentoFiscalRepository = mock(DocumentoFiscalRepository.class);
        auditoriaPendenteRepository = mock(AuditoriaPendenteRepository.class);
        ingestaoService = new DocumentoFiscalIngestaoService(documentoFiscalRepository, auditoriaPendenteRepository);
    }

    @Test
    @DisplayName("Deduplicação de fontes: chave existente recebe nova fonte sem duplicar registro")
    void testDeduplicacaoFontesMultiplas() {
        UUID escritorioId = UUID.randomUUID();
        UUID empresaId = UUID.randomUUID();
        String chave = "42261111222333000181550010000000011000000018";

        DocumentoFiscal docExistente = new DocumentoFiscal();
        docExistente.setEscritorioId(escritorioId);
        docExistente.setEmpresaId(empresaId);
        docExistente.setChaveAcesso(chave);
        docExistente.setModelo("55");
        docExistente.setValorTotal(new BigDecimal("1500.00"));
        docExistente.setSituacao("AUTORIZADA");
        docExistente.setFontes(new ArrayList<>(List.of("NOTASYNC_SEF")));

        when(documentoFiscalRepository.findByEscritorioIdAndChaveAcesso(escritorioId, chave))
                .thenReturn(Optional.of(docExistente));
        when(documentoFiscalRepository.save(any(DocumentoFiscal.class))).thenAnswer(i -> i.getArgument(0));

        DocumentoColetadoDTO dtoSieg = new DocumentoColetadoDTO(
                chave, "55", 1, 1, OffsetDateTime.now(), new BigDecimal("1500.00"),
                "AUTORIZADA", null, "storage/path/nfe.xml", null, "SIEG"
        );

        DocumentoFiscal resultado = ingestaoService.ingerirDocumento(escritorioId, empresaId, dtoSieg);

        assertNotNull(resultado);
        assertEquals(2, resultado.getFontes().size());
        assertTrue(resultado.getFontes().contains("NOTASYNC_SEF"));
        assertTrue(resultado.getFontes().contains("SIEG"));
        verify(documentoFiscalRepository, times(1)).save(docExistente);
    }

    @Test
    @DisplayName("Cancelamento posterior: atualiza situação e motivo do documento existente")
    void testCancelamentoPosterior() {
        UUID escritorioId = UUID.randomUUID();
        UUID empresaId = UUID.randomUUID();
        String chave = "42261111222333000181550010000000011000000018";

        DocumentoFiscal docExistente = new DocumentoFiscal();
        docExistente.setEscritorioId(escritorioId);
        docExistente.setEmpresaId(empresaId);
        docExistente.setChaveAcesso(chave);
        docExistente.setSituacao("AUTORIZADA");
        docExistente.setFontes(new ArrayList<>(List.of("NOTASYNC_SEF")));

        when(documentoFiscalRepository.findByEscritorioIdAndChaveAcesso(escritorioId, chave))
                .thenReturn(Optional.of(docExistente));
        when(documentoFiscalRepository.save(any(DocumentoFiscal.class))).thenAnswer(i -> i.getArgument(0));

        DocumentoColetadoDTO dtoCancelada = new DocumentoColetadoDTO(
                chave, "55", 1, 1, OffsetDateTime.now(), new BigDecimal("1500.00"),
                "CANCELADA", "Cancelamento homologado pelo emitente", null, null, "SIEG"
        );

        DocumentoFiscal resultado = ingestaoService.ingerirDocumento(escritorioId, empresaId, dtoCancelada);

        assertEquals("CANCELADA", resultado.getSituacao());
        assertEquals("Cancelamento homologado pelo emitente", resultado.getMotivoCancelamento());
    }

    @Test
    @DisplayName("Enfileiramento em auditoria_pendente ao gravar novo documento")
    void testEnfileiramentoAuditoriaPendente() {
        UUID escritorioId = UUID.randomUUID();
        UUID empresaId = UUID.randomUUID();
        String chave = "42261111222333000181550010000000021000000029";

        when(documentoFiscalRepository.findByEscritorioIdAndChaveAcesso(escritorioId, chave))
                .thenReturn(Optional.empty());
        when(documentoFiscalRepository.save(any(DocumentoFiscal.class))).thenAnswer(i -> {
            DocumentoFiscal d = i.getArgument(0);
            return d;
        });

        DocumentoColetadoDTO dtoNovo = new DocumentoColetadoDTO(
                chave, "55", 1, 2, OffsetDateTime.now(), new BigDecimal("250.00"),
                "AUTORIZADA", null, null, "<nfeProc></nfeProc>", "UPLOAD"
        );

        ingestaoService.ingerirDocumento(escritorioId, empresaId, dtoNovo);

        ArgumentCaptor<AuditoriaPendente> pendenteCaptor = ArgumentCaptor.forClass(AuditoriaPendente.class);
        verify(auditoriaPendenteRepository, times(1)).save(pendenteCaptor.capture());

        AuditoriaPendente pendenteSalva = pendenteCaptor.getValue();
        assertEquals(escritorioId, pendenteSalva.getEscritorioId());
        assertEquals("PENDENTE", pendenteSalva.getStatus());
        assertEquals(0, pendenteSalva.getTentativas());
    }
}

package com.makerpixel.contabil.service;

import com.makerpixel.contabil.domain.Escritorio;
import com.makerpixel.contabil.domain.SituacaoEscritorio;
import com.makerpixel.contabil.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LgpdServiceTest {

    @Mock
    private EscritorioRepository escritorioRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private EmpresaRepository empresaRepository;

    @Mock
    private EmDiaModeloTarefaRepository modeloTarefaRepository;

    @Mock
    private EmDiaTarefaRepository tarefaRepository;

    @Mock
    private EmDiaAnotacaoRepository anotacaoRepository;

    @Mock
    private CertificadoCofreRepository certificadoRepository;

    @Mock
    private NotasyncDocumentoRepository documentoRepository;

    @Mock
    private AuditoriaEscritorioRepository auditoriaRepository;

    @InjectMocks
    private LgpdService lgpdService;

    @Test
    @DisplayName("Exportação LGPD gera ZIP com arquivos JSON válidos")
    void exportarDadosLgpd_sucesso() throws Exception {
        UUID escritorioId = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();

        Escritorio esc = new Escritorio();
        esc.setId(escritorioId);
        esc.setCnpj("11.222.333/0001-81");
        esc.setRazaoSocial("Contabilidade Piloto Ltda");
        esc.setSituacao(SituacaoEscritorio.ATIVO);

        when(escritorioRepository.findById(escritorioId)).thenReturn(Optional.of(esc));
        when(usuarioRepository.findByEscritorioId(escritorioId)).thenReturn(Collections.emptyList());
        when(empresaRepository.findByEscritorioId(escritorioId)).thenReturn(Collections.emptyList());
        when(modeloTarefaRepository.findByEscritorioId(escritorioId)).thenReturn(Collections.emptyList());
        when(tarefaRepository.findByEscritorioId(escritorioId)).thenReturn(Collections.emptyList());
        when(anotacaoRepository.findByEscritorioId(escritorioId)).thenReturn(Collections.emptyList());
        when(certificadoRepository.findByEscritorioId(escritorioId)).thenReturn(Collections.emptyList());
        when(documentoRepository.findTop500ByEscritorioIdOrderByDataEmissaoDesc(escritorioId)).thenReturn(Collections.emptyList());
        when(auditoriaRepository.findByEscritorioIdOrderByDataHoraDesc(escritorioId)).thenReturn(Collections.emptyList());

        byte[] zipBytes = lgpdService.exportarDadosLgpd(escritorioId, usuarioId);
        assertNotNull(zipBytes);
        assertTrue(zipBytes.length > 0);

        // Verifica que o ZIP contém os arquivos esperados
        Set<String> entries = new HashSet<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            var entry = zis.getNextEntry();
            while (entry != null) {
                entries.add(entry.getName());
                entry = zis.getNextEntry();
            }
        }

        assertTrue(entries.contains("escritorio.json"));
        assertTrue(entries.contains("usuarios.json"));
        assertTrue(entries.contains("empresas.json"));
        assertTrue(entries.contains("emdia_tarefas.json"));
        assertTrue(entries.contains("certificados_metadados.json"));
        assertTrue(entries.contains("auditoria.json"));

        verify(auditoriaRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("Exclusão LGPD rejeita quando confirmação de CNPJ não confere")
    void excluirEscritorioLgpd_cnpjInvalido() {
        UUID escritorioId = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();

        Escritorio esc = new Escritorio();
        esc.setId(escritorioId);
        esc.setCnpj("11.222.333/0001-81");

        when(escritorioRepository.findById(escritorioId)).thenReturn(Optional.of(esc));

        assertThrows(ResponseStatusException.class, () -> {
            lgpdService.excluirEscritorioLgpd(escritorioId, usuarioId, "99.888.777/0001-00", "Cancelamento");
        });

        verify(escritorioRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Exclusão LGPD apaga escritório quando CNPJ é confirmado com sucesso")
    void excluirEscritorioLgpd_sucesso() {
        UUID escritorioId = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();

        Escritorio esc = new Escritorio();
        esc.setId(escritorioId);
        esc.setCnpj("11.222.333/0001-81");

        when(escritorioRepository.findById(escritorioId)).thenReturn(Optional.of(esc));

        lgpdService.excluirEscritorioLgpd(escritorioId, usuarioId, "11.222.333/0001-81", "Solicitação LGPD");

        verify(escritorioRepository, times(1)).delete(esc);
    }
}

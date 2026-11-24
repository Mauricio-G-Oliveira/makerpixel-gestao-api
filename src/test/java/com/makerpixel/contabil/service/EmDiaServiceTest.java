package com.makerpixel.contabil.service;

import com.makerpixel.contabil.domain.*;
import com.makerpixel.contabil.dto.EmDiaDTOs.*;
import com.makerpixel.contabil.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmDiaServiceTest {

    @Mock
    private EmDiaModeloTarefaRepository modeloRepository;

    @Mock
    private EmDiaTarefaRepository tarefaRepository;

    @Mock
    private EmDiaAnotacaoRepository anotacaoRepository;

    @Mock
    private EmpresaRepository empresaRepository;

    @Mock
    private CertificadoCofreRepository certificadoRepository;

    @Mock
    private NotasyncExecucaoRepository execucaoRepository;

    @Mock
    private ReformaLoteRepository reformaLoteRepository;

    @Mock
    private EscritorioRepository escritorioRepository;

    @InjectMocks
    private EmDiaService emDiaService;

    @Test
    @DisplayName("Geração do mês é idempotente: rodar duas vezes não duplica tarefas existentes")
    void gerarMes_idempotencia() {
        UUID escritorioId = UUID.randomUUID();
        String mes = "2026-11";

        Empresa emp = new Empresa();
        emp.setId(UUID.randomUUID());
        emp.setEscritorioId(escritorioId);
        emp.setCnpj("11222333000181");
        emp.setRazaoSocial("Empresa Teste Ltda");
        emp.setRegime(RegimeTributario.SIMPLES_NACIONAL);

        EmDiaModeloTarefa m1 = new EmDiaModeloTarefa();
        m1.setId(UUID.randomUUID());
        m1.setEscritorioId(escritorioId);
        m1.setNome("DAS Simples Nacional");
        m1.setSetor(SetorTarefa.FISCAL);
        m1.setDiaVencimento(20);
        m1.setAplicaSimples(true);
        m1.setAtivo(true);

        when(empresaRepository.findByEscritorioId(escritorioId)).thenReturn(List.of(emp));
        when(modeloRepository.findByEscritorioIdOrderByDiaVencimentoAsc(escritorioId)).thenReturn(List.of(m1));
        when(modeloRepository.findByEscritorioIdAndAtivoTrueOrderByDiaVencimentoAsc(escritorioId)).thenReturn(List.of(m1));

        // Primeira rodada: tarefa não existe
        when(tarefaRepository.existsByEscritorioIdAndEmpresaIdAndModeloIdAndMesCompetencia(
                eq(escritorioId), eq(emp.getId()), eq(m1.getId()), eq(mes)
        )).thenReturn(false);

        GerarMesResponse resp1 = emDiaService.gerarMes(escritorioId, mes);
        assertEquals(1, resp1.tarefasCriadas());
        assertEquals(0, resp1.tarefasJaExistentes());
        verify(tarefaRepository, times(1)).save(any(EmDiaTarefa.class));

        // Segunda rodada: tarefa já existe
        when(tarefaRepository.existsByEscritorioIdAndEmpresaIdAndModeloIdAndMesCompetencia(
                eq(escritorioId), eq(emp.getId()), eq(m1.getId()), eq(mes)
        )).thenReturn(true);

        GerarMesResponse resp2 = emDiaService.gerarMes(escritorioId, mes);
        assertEquals(0, resp2.tarefasCriadas());
        assertEquals(1, resp2.tarefasJaExistentes());
        verify(tarefaRepository, times(1)).save(any(EmDiaTarefa.class)); // total de saves continua 1
    }

    @Test
    @DisplayName("Aplica regras de regime tributário e exigência de funcionário")
    void gerarMes_regrasRegimeEFuncionario() {
        UUID escritorioId = UUID.randomUUID();
        String mes = "2026-11";

        Empresa empSimplesSemFunc = new Empresa();
        empSimplesSemFunc.setId(UUID.randomUUID());
        empSimplesSemFunc.setRegime(RegimeTributario.SIMPLES_NACIONAL);
        empSimplesSemFunc.setTemFuncionario(false);

        EmDiaModeloTarefa modeloFgts = new EmDiaModeloTarefa();
        modeloFgts.setId(UUID.randomUUID());
        modeloFgts.setNome("FGTS Digital");
        modeloFgts.setSetor(SetorTarefa.DP);
        modeloFgts.setDiaVencimento(20);
        modeloFgts.setAplicaSimples(true);
        modeloFgts.setExigeFuncionario(true); // Exige funcionário
        modeloFgts.setAtivo(true);

        assertFalse(modeloFgts.seAplica(empSimplesSemFunc), "Não deve aplicar FGTS a empresa sem funcionário");

        empSimplesSemFunc.setTemFuncionario(true);
        assertTrue(modeloFgts.seAplica(empSimplesSemFunc), "Deve aplicar FGTS quando empresa tem funcionário");
    }

    @Test
    @DisplayName("Cria tarefa automática para erro 8002 no SAT sem duplicar se já existir aberta")
    void gerarTarefasAutomaticas_sat8002Deduplicacao() {
        UUID escritorioId = UUID.randomUUID();

        Empresa emp = new Empresa();
        emp.setId(UUID.randomUUID());
        emp.setCnpj("11222333000181");
        emp.setRazaoSocial("Loja Centro Ltda");

        NotasyncExecucao ex = new NotasyncExecucao();
        ex.setCnpj("11222333000181");
        ex.setCstat(8002);

        when(empresaRepository.findByEscritorioId(escritorioId)).thenReturn(List.of(emp));
        when(execucaoRepository.findTop50ByEscritorioIdOrderByExecutadoEmDesc(escritorioId)).thenReturn(List.of(ex));

        // Primeira vez: não existe aberta
        when(tarefaRepository.existsByEscritorioIdAndChaveDeduplicacaoAndStatusNotIn(
                eq(escritorioId), eq("SAT_8002_11222333000181"), anyList()
        )).thenReturn(false);

        int criadas = emDiaService.gerarTarefasAutomaticas(escritorioId);
        assertEquals(1, criadas);
        verify(tarefaRepository, times(1)).save(any(EmDiaTarefa.class));

        // Segunda vez: já existe aberta
        when(tarefaRepository.existsByEscritorioIdAndChaveDeduplicacaoAndStatusNotIn(
                eq(escritorioId), eq("SAT_8002_11222333000181"), anyList()
        )).thenReturn(true);

        int criadasSegunda = emDiaService.gerarTarefasAutomaticas(escritorioId);
        assertEquals(0, criadasSegunda);
    }

    @Test
    @DisplayName("Atualiza status da tarefa para CONCLUIDA com timestamp e usuário")
    void atualizarStatus_concluir() {
        UUID escritorioId = UUID.randomUUID();
        UUID tarefaId = UUID.randomUUID();

        EmDiaTarefa t = new EmDiaTarefa();
        t.setId(tarefaId);
        t.setEscritorioId(escritorioId);
        t.setStatus(StatusTarefa.A_FAZER);

        when(tarefaRepository.findByIdAndEscritorioId(tarefaId, escritorioId)).thenReturn(Optional.of(t));
        when(tarefaRepository.save(any(EmDiaTarefa.class))).thenAnswer(inv -> inv.getArgument(0));

        EmDiaTarefa atualizada = emDiaService.atualizarStatus(escritorioId, tarefaId, StatusTarefa.CONCLUIDA, "joao@contabil.com", "joao@contabil.com");

        assertEquals(StatusTarefa.CONCLUIDA, atualizada.getStatus());
        assertNotNull(atualizada.getConcluidaEm());
        assertEquals("joao@contabil.com", atualizada.getConcluidaPor());
    }
}

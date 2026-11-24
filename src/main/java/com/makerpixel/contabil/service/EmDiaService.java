package com.makerpixel.contabil.service;

import com.makerpixel.contabil.domain.*;
import com.makerpixel.contabil.dto.EmDiaDTOs.*;
import com.makerpixel.contabil.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class EmDiaService {

    private final EmDiaModeloTarefaRepository modeloRepository;
    private final EmDiaTarefaRepository tarefaRepository;
    private final EmDiaAnotacaoRepository anotacaoRepository;
    private final EmpresaRepository empresaRepository;
    private final CertificadoCofreRepository certificadoRepository;
    private final NotasyncExecucaoRepository execucaoRepository;
    private final ReformaLoteRepository reformaLoteRepository;
    private final EscritorioRepository escritorioRepository;

    public EmDiaService(
            EmDiaModeloTarefaRepository modeloRepository,
            EmDiaTarefaRepository tarefaRepository,
            EmDiaAnotacaoRepository anotacaoRepository,
            EmpresaRepository empresaRepository,
            CertificadoCofreRepository certificadoRepository,
            NotasyncExecucaoRepository execucaoRepository,
            ReformaLoteRepository reformaLoteRepository,
            EscritorioRepository escritorioRepository
    ) {
        this.modeloRepository = modeloRepository;
        this.tarefaRepository = tarefaRepository;
        this.anotacaoRepository = anotacaoRepository;
        this.empresaRepository = empresaRepository;
        this.certificadoRepository = certificadoRepository;
        this.execucaoRepository = execucaoRepository;
        this.reformaLoteRepository = reformaLoteRepository;
        this.escritorioRepository = escritorioRepository;
    }

    /**
     * Geração idempotente de tarefas do mês para todas as empresas ativas do escritório.
     * Rodar duas vezes não duplica tarefas.
     */
    @Transactional
    public GerarMesResponse gerarMes(UUID escritorioId, String mesCompetencia) {
        garantirModelosPadrao(escritorioId);

        YearMonth ym = YearMonth.parse(mesCompetencia);
        List<EmDiaModeloTarefa> modelos = modeloRepository.findByEscritorioIdAndAtivoTrueOrderByDiaVencimentoAsc(escritorioId);
        List<Empresa> empresas = empresaRepository.findByEscritorioId(escritorioId);

        int criadas = 0;
        int existentes = 0;

        for (Empresa emp : empresas) {
            for (EmDiaModeloTarefa modelo : modelos) {
                if (!modelo.seAplica(emp)) {
                    continue;
                }

                boolean jaExiste = tarefaRepository.existsByEscritorioIdAndEmpresaIdAndModeloIdAndMesCompetencia(
                        escritorioId, emp.getId(), modelo.getId(), mesCompetencia
                );

                if (jaExiste) {
                    existentes++;
                    continue;
                }

                int dia = Math.min(modelo.getDiaVencimento(), ym.lengthOfMonth());
                LocalDate prazo = ym.atDay(dia);

                String responsavel = switch (modelo.getSetor()) {
                    case FISCAL -> emp.getResponsavelSetorFiscal();
                    case CONTABIL -> emp.getResponsavelSetorContabil();
                    case DP -> emp.getResponsavelSetorDp();
                    case GERAL -> null;
                };

                EmDiaTarefa t = new EmDiaTarefa();
                t.setEscritorioId(escritorioId);
                t.setEmpresaId(emp.getId());
                t.setModeloId(modelo.getId());
                t.setNome(modelo.getNome());
                t.setSetor(modelo.getSetor());
                t.setMesCompetencia(mesCompetencia);
                t.setPrazo(prazo);
                t.setStatus(StatusTarefa.A_FAZER);
                t.setResponsavel(responsavel);
                t.setOrigemAutomatica("MODELO");

                tarefaRepository.save(t);
                criadas++;
            }
        }

        // Gera também tarefas automáticas pendentes
        gerarTarefasAutomaticas(escritorioId);

        return new GerarMesResponse(
                mesCompetencia,
                empresas.size(),
                criadas,
                existentes,
                String.format("Geração do mês %s concluída: %d tarefas criadas, %d já existiam.", mesCompetencia, criadas, existentes)
        );
    }

    /**
     * Gera tarefas automáticas vindas dos outros módulos (NotaSync 8002, Certificado vencendo, Reforma divergências),
     * sem duplicar se já existir uma aberta.
     */
    @Transactional
    public int gerarTarefasAutomaticas(UUID escritorioId) {
        int criadas = 0;
        List<StatusTarefa> statusIgnorados = List.of(StatusTarefa.CONCLUIDA, StatusTarefa.NAO_SE_APLICA);
        List<Empresa> empresas = empresaRepository.findByEscritorioId(escritorioId);
        if (empresas.isEmpty()) return 0;

        Empresa empresaPadrao = empresas.get(0);
        String mesAtual = YearMonth.now().toString();
        LocalDate hoje = LocalDate.now();

        // 1. NotaSync com erro 8002 no SAT
        try {
            List<NotasyncExecucao> execs = execucaoRepository.findTop50ByEscritorioIdOrderByExecutadoEmDesc(escritorioId);
            for (NotasyncExecucao ex : execs) {
                if (ex.getCstat() != null && ex.getCstat() == 8002) {
                    String chaveDedup = "SAT_8002_" + ex.getCnpj();
                    boolean abertaExiste = tarefaRepository.existsByEscritorioIdAndChaveDeduplicacaoAndStatusNotIn(
                            escritorioId, chaveDedup, statusIgnorados
                    );

                    if (!abertaExiste) {
                        Empresa empAlvo = empresas.stream()
                                .filter(e -> e.getCnpj().equals(ex.getCnpj()))
                                .findFirst()
                                .orElse(empresaPadrao);

                        EmDiaTarefa t = new EmDiaTarefa();
                        t.setEscritorioId(escritorioId);
                        t.setEmpresaId(empAlvo.getId());
                        t.setNome("Vincular contador no SAT – empresa " + (empAlvo.getRazaoSocial() != null ? empAlvo.getRazaoSocial() : ex.getCnpj()));
                        t.setSetor(SetorTarefa.FISCAL);
                        t.setMesCompetencia(mesAtual);
                        t.setPrazo(hoje.plusDays(2));
                        t.setStatus(StatusTarefa.A_FAZER);
                        t.setResponsavel(empAlvo.getResponsavelSetorFiscal());
                        t.setOrigemAutomatica("NOTASYNC_8002");
                        t.setChaveDeduplicacao(chaveDedup);

                        tarefaRepository.save(t);
                        criadas++;
                    }
                }
            }
        } catch (Exception ignored) {}

        // 2. Certificado Digital vencendo em até 30 dias
        try {
            List<CertificadoCofre> certs = certificadoRepository.findByEscritorioIdAndAtivo(escritorioId, true);
            for (CertificadoCofre cert : certs) {
                if (cert.getValidoAte() != null) {
                    long diasRestantes = java.time.Duration.between(OffsetDateTime.now(), cert.getValidoAte()).toDays();
                    if (diasRestantes <= 30) {
                        String chaveDedup = "CERT_VENCENDO_" + cert.getId();
                        boolean abertaExiste = tarefaRepository.existsByEscritorioIdAndChaveDeduplicacaoAndStatusNotIn(
                                escritorioId, chaveDedup, statusIgnorados
                        );

                        if (!abertaExiste) {
                            EmDiaTarefa t = new EmDiaTarefa();
                            t.setEscritorioId(escritorioId);
                            t.setEmpresaId(empresaPadrao.getId());
                            t.setNome("Renovar Certificado Digital A1 (" + (diasRestantes <= 0 ? "Expirado" : "vence em " + diasRestantes + " dias") + ")");
                            t.setSetor(SetorTarefa.GERAL);
                            t.setMesCompetencia(mesAtual);
                            t.setPrazo(cert.getValidoAte().toLocalDate());
                            t.setStatus(StatusTarefa.A_FAZER);
                            t.setOrigemAutomatica("CERTIFICADO_VENCENDO");
                            t.setChaveDeduplicacao(chaveDedup);

                            tarefaRepository.save(t);
                            criadas++;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        // 3. Reforma Tributária com divergências
        try {
            List<ReformaLote> lotes = reformaLoteRepository.findByEscritorioIdOrderByCriadoEmDesc(escritorioId);
            for (ReformaLote lote : lotes) {
                if (lote.getTotalDivergencias() > 0) {
                    String chaveDedup = "REFORMA_DIV_" + lote.getId();
                    boolean abertaExiste = tarefaRepository.existsByEscritorioIdAndChaveDeduplicacaoAndStatusNotIn(
                            escritorioId, chaveDedup, statusIgnorados
                    );

                    if (!abertaExiste) {
                        EmDiaTarefa t = new EmDiaTarefa();
                        t.setEscritorioId(escritorioId);
                        t.setEmpresaId(lote.getEmpresaId() != null ? lote.getEmpresaId() : empresaPadrao.getId());
                        t.setNome(String.format("Revisar %d divergência(s) IBS/CBS da Reforma Tributária (Lote %s)",
                                lote.getTotalDivergencias(), lote.getCompetencia() != null ? lote.getCompetencia() : "recente"));
                        t.setSetor(SetorTarefa.FISCAL);
                        t.setMesCompetencia(mesAtual);
                        t.setPrazo(hoje.plusDays(5));
                        t.setStatus(StatusTarefa.A_FAZER);
                        t.setOrigemAutomatica("REFORMA_DIVERGENCIA");
                        t.setChaveDeduplicacao(chaveDedup);

                        tarefaRepository.save(t);
                        criadas++;
                    }
                }
            }
        } catch (Exception ignored) {}

        return criadas;
    }

    /**
     * Minhas tarefas divididas em: Hoje, Atrasadas, Desta Semana e Próximas.
     */
    public MinhasTarefasResponse listarMinhasTarefas(UUID escritorioId, String usuarioEmail) {
        List<EmDiaTarefa> abertas = (usuarioEmail != null && !usuarioEmail.isBlank())
                ? tarefaRepository.findMinhasTarefasAbertas(escritorioId, usuarioEmail)
                : tarefaRepository.findTodasTarefasAbertas(escritorioId);

        Map<UUID, Empresa> empresaMap = empresaRepository.findByEscritorioId(escritorioId).stream()
                .collect(Collectors.toMap(Empresa::getId, e -> e));

        LocalDate hoje = LocalDate.now();
        LocalDate fimSemana = hoje.plusDays(7);

        List<TarefaItemDTO> hojeList = new ArrayList<>();
        List<TarefaItemDTO> atrasadasList = new ArrayList<>();
        List<TarefaItemDTO> destaSemanaList = new ArrayList<>();
        List<TarefaItemDTO> proximasList = new ArrayList<>();

        for (EmDiaTarefa t : abertas) {
            Empresa emp = empresaMap.get(t.getEmpresaId());
            TarefaItemDTO dto = toDto(t, emp);

            if (t.getPrazo().isBefore(hoje)) {
                atrasadasList.add(dto);
            } else if (t.getPrazo().isEqual(hoje)) {
                hojeList.add(dto);
            } else if (!t.getPrazo().isAfter(fimSemana)) {
                destaSemanaList.add(dto);
            } else {
                proximasList.add(dto);
            }
        }

        return new MinhasTarefasResponse(hojeList, atrasadasList, destaSemanaList, proximasList);
    }

    /**
     * Checklist mensal por empresa com status e responsável.
     */
    public List<ChecklistEmpresaDTO> obterChecklistEmpresas(UUID escritorioId, String mesCompetencia) {
        List<Empresa> empresas = empresaRepository.findByEscritorioId(escritorioId);
        List<EmDiaTarefa> tarefas = tarefaRepository.findByEscritorioIdAndMesCompetenciaOrderByPrazoAsc(escritorioId, mesCompetencia);

        Map<UUID, List<EmDiaTarefa>> tarefasPorEmpresa = tarefas.stream()
                .collect(Collectors.groupingBy(EmDiaTarefa::getEmpresaId));

        List<ChecklistEmpresaDTO> lista = new ArrayList<>();
        for (Empresa emp : empresas) {
            List<EmDiaTarefa> empTarefas = tarefasPorEmpresa.getOrDefault(emp.getId(), Collections.emptyList());
            int total = empTarefas.size();
            int concluidas = (int) empTarefas.stream().filter(t -> t.getStatus() == StatusTarefa.CONCLUIDA).count();
            int atrasadas = (int) empTarefas.stream().filter(EmDiaTarefa::isAtrasada).count();
            int pendentes = total - concluidas;

            List<TarefaItemDTO> tarefasDto = empTarefas.stream()
                    .map(t -> toDto(t, emp))
                    .toList();

            lista.add(new ChecklistEmpresaDTO(
                    emp.getId(),
                    emp.getRazaoSocial(),
                    emp.getCnpj(),
                    emp.getRegime() != null ? emp.getRegime().name() : "SIMPLES_NACIONAL",
                    total,
                    concluidas,
                    pendentes,
                    atrasadas,
                    tarefasDto
            ));
        }

        return lista;
    }

    /**
     * Painel de métricas do mês (em dia x atrasadas x aguardando cliente por setor e responsável).
     */
    public PainelMetricasDTO obterPainelMetricas(UUID escritorioId, String mesCompetencia) {
        List<EmDiaTarefa> tarefas = tarefaRepository.findByEscritorioIdAndMesCompetenciaOrderByPrazoAsc(escritorioId, mesCompetencia);

        long emDia = 0, atrasadas = 0, aguardandoCliente = 0, concluidas = 0, naoSeAplica = 0;
        Map<String, Long> porSetor = new HashMap<>();
        Map<String, Long> porResponsavel = new HashMap<>();

        for (EmDiaTarefa t : tarefas) {
            if (t.getStatus() == StatusTarefa.CONCLUIDA) {
                concluidas++;
            } else if (t.getStatus() == StatusTarefa.NAO_SE_APLICA) {
                naoSeAplica++;
            } else if (t.getStatus() == StatusTarefa.AGUARDANDO_CLIENTE) {
                aguardandoCliente++;
            } else if (t.isAtrasada()) {
                atrasadas++;
            } else {
                emDia++;
            }

            String s = t.getSetor() != null ? t.getSetor().name() : "GERAL";
            porSetor.put(s, porSetor.getOrDefault(s, 0L) + 1);

            String r = (t.getResponsavel() != null && !t.getResponsavel().isBlank()) ? t.getResponsavel() : "Sem responsável";
            porResponsavel.put(r, porResponsavel.getOrDefault(r, 0L) + 1);
        }

        return new PainelMetricasDTO(
                tarefas.size(),
                emDia,
                atrasadas,
                aguardandoCliente,
                concluidas,
                naoSeAplica,
                porSetor,
                porResponsavel
        );
    }

    @Transactional
    public EmDiaTarefa atualizarStatus(UUID escritorioId, UUID tarefaId, StatusTarefa novoStatus, String responsavel, String usuarioEmail) {
        EmDiaTarefa t = tarefaRepository.findByIdAndEscritorioId(tarefaId, escritorioId)
                .orElseThrow(() -> new IllegalArgumentException("Tarefa não encontrada."));

        t.setStatus(novoStatus);
        if (responsavel != null && !responsavel.isBlank()) {
            t.setResponsavel(responsavel);
        }

        if (novoStatus == StatusTarefa.CONCLUIDA) {
            t.setConcluidaEm(OffsetDateTime.now());
            t.setConcluidaPor(usuarioEmail != null ? usuarioEmail : "Usuário");
        } else {
            t.setConcluidaEm(null);
            t.setConcluidaPor(null);
        }

        return tarefaRepository.save(t);
    }

    // --- Modelos ---
    public List<EmDiaModeloTarefa> listarModelos(UUID escritorioId) {
        garantirModelosPadrao(escritorioId);
        return modeloRepository.findByEscritorioIdOrderByDiaVencimentoAsc(escritorioId);
    }

    @Transactional
    public EmDiaModeloTarefa salvarModelo(UUID escritorioId, UUID id, SalvarModeloRequest req) {
        EmDiaModeloTarefa m = (id != null)
                ? modeloRepository.findByIdAndEscritorioId(id, escritorioId).orElseThrow(() -> new IllegalArgumentException("Modelo não encontrado."))
                : new EmDiaModeloTarefa();

        m.setEscritorioId(escritorioId);
        m.setNome(req.nome());
        m.setSetor(req.setor());
        m.setDiaVencimento(req.diaVencimento());
        m.setAplicaSimples(req.aplicaSimples());
        m.setAplicaLucroPresumido(req.aplicaLucroPresumido());
        m.setAplicaLucroReal(req.aplicaLucroReal());
        m.setAplicaMei(req.aplicaMei());
        m.setExigeFuncionario(req.exigeFuncionario());
        m.setTipoEmpresa(req.tipoEmpresa());
        m.setAtivo(req.ativo());

        return modeloRepository.save(m);
    }

    // --- Anotações ---
    @Transactional
    public EmDiaAnotacao criarAnotacao(UUID escritorioId, CriarAnotacaoRequest req, String autor) {
        EmDiaAnotacao a = new EmDiaAnotacao();
        a.setEscritorioId(escritorioId);
        a.setEmpresaId(req.empresaId());
        a.setTexto(req.texto());
        a.setAutor(autor != null ? autor : "Contador");
        a.setAnexoUrl(req.anexoUrl());
        a.setAnexoNome(req.anexoNome());
        return anotacaoRepository.save(a);
    }

    public List<EmDiaAnotacao> listarAnotacoes(UUID escritorioId, UUID empresaId) {
        return anotacaoRepository.findByEscritorioIdAndEmpresaIdOrderByCriadoEmDesc(escritorioId, empresaId);
    }

    private TarefaItemDTO toDto(EmDiaTarefa t, Empresa emp) {
        return new TarefaItemDTO(
                t.getId(),
                t.getEmpresaId(),
                emp != null ? emp.getRazaoSocial() : "Empresa",
                emp != null ? emp.getCnpj() : "",
                t.getNome(),
                t.getSetor(),
                t.getMesCompetencia(),
                t.getPrazo(),
                t.getStatus(),
                t.getResponsavel(),
                t.isAtrasada(),
                t.getConcluidaEm(),
                t.getConcluidaPor(),
                t.getOrigemAutomatica()
        );
    }

    private void garantirModelosPadrao(UUID escritorioId) {
        List<EmDiaModeloTarefa> existentes = modeloRepository.findByEscritorioIdOrderByDiaVencimentoAsc(escritorioId);
        if (!existentes.isEmpty()) return;

        List<EmDiaModeloTarefa> padroes = List.of(
                criarModelo(escritorioId, "DAS - Documento de Arrecadação do Simples", SetorTarefa.FISCAL, 20, true, false, false, true, false),
                criarModelo(escritorioId, "PGDAS-D - Transmissão Mensal", SetorTarefa.FISCAL, 20, true, false, false, false, false),
                criarModelo(escritorioId, "DCTFWeb - Declaração de Débitos e Créditos", SetorTarefa.FISCAL, 15, true, true, true, false, true),
                criarModelo(escritorioId, "FGTS Digital - Guia de Recolhimento", SetorTarefa.DP, 20, true, true, true, false, true),
                criarModelo(escritorioId, "Folha de Pagamento & Pró-labore", SetorTarefa.DP, 5, true, true, true, true, false),
                criarModelo(escritorioId, "EFD-Reinf - Escrituração Fiscal Digital", SetorTarefa.FISCAL, 15, false, true, true, false, false),
                criarModelo(escritorioId, "SPED Fiscal (ICMS/IPI) & DIME SC", SetorTarefa.FISCAL, 10, false, true, true, false, false),
                criarModelo(escritorioId, "Fechamento e Conciliação Mensal", SetorTarefa.CONTABIL, 25, true, true, true, true, false)
        );

        modeloRepository.saveAll(padroes);
    }

    private EmDiaModeloTarefa criarModelo(
            UUID escritorioId, String nome, SetorTarefa setor, int dia,
            boolean simples, boolean presumido, boolean real, boolean mei, boolean exigeFunc
    ) {
        EmDiaModeloTarefa m = new EmDiaModeloTarefa();
        m.setEscritorioId(escritorioId);
        m.setNome(nome);
        m.setSetor(setor);
        m.setDiaVencimento(dia);
        m.setAplicaSimples(simples);
        m.setAplicaLucroPresumido(presumido);
        m.setAplicaLucroReal(real);
        m.setAplicaMei(mei);
        m.setExigeFuncionario(exigeFunc);
        m.setTipoEmpresa("TODOS");
        m.setAtivo(true);
        return m;
    }

    /**
     * Execução periódica via agendador Cron para gerar tarefas de mês e automáticas
     * para todos os escritórios ativos ou em período de avaliação.
     */
    @Transactional
    public Map<String, Object> executarCicloMensalCron() {
        List<Escritorio> escritorios = new ArrayList<>();
        escritorios.addAll(escritorioRepository.findBySituacao(SituacaoEscritorio.ATIVO));
        escritorios.addAll(escritorioRepository.findBySituacao(SituacaoEscritorio.TESTE));

        String mesAtual = YearMonth.now().toString();
        int escritoriosProcessados = 0;
        int totalTarefasModelosCriadas = 0;
        int totalTarefasAutomaticasCriadas = 0;

        for (Escritorio esc : escritorios) {
            try {
                GerarMesResponse respMes = gerarMes(esc.getId(), mesAtual);
                totalTarefasModelosCriadas += respMes.tarefasCriadas();

                GerarTarefasAutomaticasResponse respAuto = gerarTarefasAutomaticas(esc.getId());
                totalTarefasAutomaticasCriadas += respAuto.tarefasCriadas();

                escritoriosProcessados++;
            } catch (Exception ignored) {
                // Segue com os demais escritórios
            }
        }

        return Map.of(
            "mesCompetencia", mesAtual,
            "escritoriosProcessados", escritoriosProcessados,
            "tarefasModelosCriadas", totalTarefasModelosCriadas,
            "tarefasAutomaticasCriadas", totalTarefasAutomaticasCriadas,
            "executadoEm", OffsetDateTime.now().toString()
        );
    }
}

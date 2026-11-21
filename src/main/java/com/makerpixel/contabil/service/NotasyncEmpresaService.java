package com.makerpixel.contabil.service;

import com.makerpixel.contabil.domain.Escritorio;
import com.makerpixel.contabil.domain.NotasyncEmpresa;
import com.makerpixel.contabil.domain.NotasyncEstado;
import com.makerpixel.contabil.dto.NotasyncNuvemDTOs.*;
import com.makerpixel.contabil.repository.EscritorioRepository;
import com.makerpixel.contabil.repository.NotasyncDocumentoRepository;
import com.makerpixel.contabil.repository.NotasyncEmpresaRepository;
import com.makerpixel.contabil.repository.NotasyncEstadoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class NotasyncEmpresaService {

    private final NotasyncEmpresaRepository empresaRepository;
    private final NotasyncEstadoRepository estadoRepository;
    private final NotasyncDocumentoRepository documentoRepository;
    private final EscritorioRepository escritorioRepository;

    public NotasyncEmpresaService(
            NotasyncEmpresaRepository empresaRepository,
            NotasyncEstadoRepository estadoRepository,
            NotasyncDocumentoRepository documentoRepository,
            EscritorioRepository escritorioRepository
    ) {
        this.empresaRepository = empresaRepository;
        this.estadoRepository = estadoRepository;
        this.documentoRepository = documentoRepository;
        this.escritorioRepository = escritorioRepository;
    }

    /**
     * Valida Dígito Verificador de CNPJ (Módulo 11).
     */
    public static boolean validarCnpj(String cnpj) {
        if (cnpj == null) return false;
        String limpo = cnpj.replaceAll("\\D", "");
        if (limpo.length() != 14) return false;

        // Rejeita sequências repetidas como 00000000000000
        if (limpo.chars().distinct().count() == 1) return false;

        try {
            int[] peso1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
            int soma = 0;
            for (int i = 0; i < 12; i++) {
                soma += Character.getNumericValue(limpo.charAt(i)) * peso1[i];
            }
            int r = soma % 11;
            int dv1 = (r < 2) ? 0 : 11 - r;
            if (Character.getNumericValue(limpo.charAt(12)) != dv1) return false;

            int[] peso2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
            soma = 0;
            for (int i = 0; i < 13; i++) {
                soma += Character.getNumericValue(limpo.charAt(i)) * peso2[i];
            }
            r = soma % 11;
            int dv2 = (r < 2) ? 0 : 11 - r;
            return Character.getNumericValue(limpo.charAt(13)) == dv2;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Cadastro manual de empresa monitorada respeitando o limite do plano.
     */
    @Transactional
    public NotasyncEmpresa cadastrarEmpresa(UUID escritorioId, CadastrarEmpresaRequest req) {
        if (req.cnpj() == null || req.cnpj().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CNPJ é obrigatório.");
        }
        String cnpjLimpo = req.cnpj().replaceAll("\\D", "");
        if (!validarCnpj(cnpjLimpo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CNPJ com dígito verificador inválido: " + req.cnpj());
        }

        // Verifica duplicidade no escritório
        if (empresaRepository.findByEscritorioIdAndCnpj(escritorioId, cnpjLimpo).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Esta empresa já está cadastrada no NotaSync deste escritório.");
        }

        // Verifica limite do plano do escritório
        verificarLimiteEmpresas(escritorioId, 1);

        String razao = (req.razaoSocial() != null && !req.razaoSocial().isBlank())
                ? req.razaoSocial().trim()
                : "Empresa " + cnpjLimpo;

        NotasyncEmpresa nova = new NotasyncEmpresa(escritorioId, cnpjLimpo, razao, true);
        NotasyncEmpresa salva = empresaRepository.save(nova);

        // Cria o registro inicial de estado de consulta
        NotasyncEstado estado = new NotasyncEstado(escritorioId, cnpjLimpo, "sef-sc-nfce");
        estado.setProximaConsulta(OffsetDateTime.now());
        estadoRepository.save(estado);

        return salva;
    }

    /**
     * Importação em lote por CSV (espera colunas: cnpj,razao_social ou cnpj;razao_social).
     */
    @Transactional
    public ImportarCsvResponse importarCsv(UUID escritorioId, InputStream inputStream) {
        int total = 0;
        int sucesso = 0;
        int jaCadastradas = 0;
        int errosDv = 0;
        int limiteAtingido = 0;
        List<String> errosDetalhe = new ArrayList<>();

        Escritorio esc = escritorioRepository.findById(escritorioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Escritório não encontrado."));

        int maxEmpresas = esc.getMaxEmpresas() != null ? esc.getMaxEmpresas() : 20;
        long atuais = empresaRepository.countByEscritorioIdAndAtivoTrue(escritorioId);

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String linha;
            boolean primeiraLinha = true;

            while ((linha = reader.readLine()) != null) {
                linha = linha.trim();
                if (linha.isEmpty()) continue;

                // Ignora cabeçalho
                if (primeiraLinha) {
                    primeiraLinha = false;
                    if (linha.toLowerCase().contains("cnpj")) continue;
                }

                total++;
                String[] partes = linha.contains(";") ? linha.split(";") : linha.split(",");
                String rawCnpj = partes[0].trim();
                String rawRazao = (partes.length > 1) ? partes[1].trim() : "Empresa " + rawCnpj;

                String cnpjLimpo = rawCnpj.replaceAll("\\D", "");
                if (!validarCnpj(cnpjLimpo)) {
                    errosDv++;
                    errosDetalhe.add("Linha " + total + ": CNPJ com DV inválido (" + rawCnpj + ")");
                    continue;
                }

                if (empresaRepository.findByEscritorioIdAndCnpj(escritorioId, cnpjLimpo).isPresent()) {
                    jaCadastradas++;
                    continue;
                }

                if (atuais + sucesso >= maxEmpresas) {
                    limiteAtingido++;
                    errosDetalhe.add("Linha " + total + ": Limite do plano atingido (" + maxEmpresas + " empresas). Empresa não importada.");
                    continue;
                }

                NotasyncEmpresa nova = new NotasyncEmpresa(escritorioId, cnpjLimpo, rawRazao, true);
                empresaRepository.save(nova);

                NotasyncEstado estado = new NotasyncEstado(escritorioId, cnpjLimpo, "sef-sc-nfce");
                estado.setProximaConsulta(OffsetDateTime.now());
                estadoRepository.save(estado);

                sucesso++;
            }
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Erro ao processar arquivo CSV: " + e.getMessage());
        }

        return new ImportarCsvResponse(total, sucesso, jaCadastradas, errosDv, limiteAtingido, errosDetalhe);
    }

    /**
     * Lista as empresas monitoradas com status operacional de consulta.
     */
    @Transactional(readOnly = true)
    public List<EmpresaStatusDTO> listarEmpresas(UUID escritorioId) {
        List<NotasyncEmpresa> empresas = empresaRepository.findByEscritorioIdOrderByRazaoSocialAsc(escritorioId);
        List<NotasyncEstado> estados = estadoRepository.findByEscritorioId(escritorioId);

        Map<String, NotasyncEstado> mapaEstados = estados.stream()
                .collect(Collectors.toMap(NotasyncEstado::getCnpj, e -> e, (a, b) -> a));

        List<EmpresaStatusDTO> resultado = new ArrayList<>();
        for (NotasyncEmpresa emp : empresas) {
            NotasyncEstado est = mapaEstados.get(emp.getCnpj());
            long totalNotas = documentoRepository.countByEscritorioIdAndCnpj(escritorioId, emp.getCnpj());

            resultado.add(new EmpresaStatusDTO(
                    emp.getId(),
                    emp.getCnpj(),
                    emp.getRazaoSocial(),
                    emp.isAtivo(),
                    est != null ? est.getUltNsu() : 0,
                    est != null ? est.getProximaConsulta() : null,
                    est != null ? est.getUltimoCstat() : null,
                    est != null ? est.getMotivo() : null,
                    totalNotas,
                    emp.getAtualizadoEm()
            ));
        }

        return resultado;
    }

    /**
     * Alterna o status ativo/inativo da empresa.
     */
    @Transactional
    public NotasyncEmpresa toggleAtivo(UUID escritorioId, UUID empresaId) {
        NotasyncEmpresa emp = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Empresa não encontrada."));

        if (!emp.getEscritorioId().equals(escritorioId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acesso não autorizado.");
        }

        // Se está ativando, confere se cabe no limite
        if (!emp.isAtivo()) {
            verificarLimiteEmpresas(escritorioId, 1);
        }

        emp.setAtivo(!emp.isAtivo());
        emp.setAtualizadoEm(OffsetDateTime.now());
        return empresaRepository.save(emp);
    }

    private void verificarLimiteEmpresas(UUID escritorioId, int quantidadeNovas) {
        Escritorio esc = escritorioRepository.findById(escritorioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Escritório não encontrado."));

        int max = esc.getMaxEmpresas() != null ? esc.getMaxEmpresas() : 20;
        long ativas = empresaRepository.countByEscritorioIdAndAtivoTrue(escritorioId);

        if (ativas + quantidadeNovas > max) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Limite de empresas ativas do plano atingido (" + max + "). Desative empresas ou faça upgrade de plano.");
        }
    }
}

package com.makerpixel.contabil.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.makerpixel.contabil.domain.*;
import com.makerpixel.contabil.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class LgpdService {

    private static final Logger log = LoggerFactory.getLogger(LgpdService.class);

    private final EscritorioRepository escritorioRepository;
    private final UsuarioRepository usuarioRepository;
    private final EmpresaRepository empresaRepository;
    private final EmDiaModeloTarefaRepository modeloTarefaRepository;
    private final EmDiaTarefaRepository tarefaRepository;
    private final EmDiaAnotacaoRepository anotacaoRepository;
    private final CertificadoCofreRepository certificadoRepository;
    private final NotasyncDocumentoRepository documentoRepository;
    private final AuditoriaEscritorioRepository auditoriaRepository;
    private final ObjectMapper objectMapper;

    public LgpdService(
            EscritorioRepository escritorioRepository,
            UsuarioRepository usuarioRepository,
            EmpresaRepository empresaRepository,
            EmDiaModeloTarefaRepository modeloTarefaRepository,
            EmDiaTarefaRepository tarefaRepository,
            EmDiaAnotacaoRepository anotacaoRepository,
            CertificadoCofreRepository certificadoRepository,
            NotasyncDocumentoRepository documentoRepository,
            AuditoriaEscritorioRepository auditoriaRepository
    ) {
        this.escritorioRepository = escritorioRepository;
        this.usuarioRepository = usuarioRepository;
        this.empresaRepository = empresaRepository;
        this.modeloTarefaRepository = modeloTarefaRepository;
        this.tarefaRepository = tarefaRepository;
        this.anotacaoRepository = anotacaoRepository;
        this.certificadoRepository = certificadoRepository;
        this.documentoRepository = documentoRepository;
        this.auditoriaRepository = auditoriaRepository;

        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * Exporta todos os dados do escritório em conformidade com o Artigo 18 da LGPD (Portabilidade e Acesso).
     * O arquivo gerado é um ZIP estruturado com os dados em JSON.
     * Segredos criptográficos (chaves privadas e senhas do certificado) NUNCA são exportados.
     */
    @Transactional(readOnly = true)
    public byte[] exportarDadosLgpd(UUID escritorioId, UUID usuarioSolicitanteId) {
        Escritorio esc = escritorioRepository.findById(escritorioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Escritório não encontrado."));

        List<Usuario> usuarios = usuarioRepository.findByEscritorioId(escritorioId);
        List<Empresa> empresas = empresaRepository.findByEscritorioId(escritorioId);
        List<EmDiaModeloTarefa> modelos = modeloTarefaRepository.findByEscritorioId(escritorioId);
        List<EmDiaTarefa> tarefas = tarefaRepository.findByEscritorioId(escritorioId);
        List<EmDiaAnotacao> anotacoes = anotacaoRepository.findByEscritorioId(escritorioId);
        List<NotasyncDocumento> docs = documentoRepository.findTop500ByEscritorioIdOrderByDataEmissaoDesc(escritorioId);
        List<AuditoriaEscritorio> auditorias = auditoriaRepository.findByEscritorioIdOrderByDataHoraDesc(escritorioId);

        // Metadados de certificados (sem chaves privadas nem senhas)
        List<CertificadoCofre> certs = certificadoRepository.findByEscritorioId(escritorioId);
        List<Map<String, Object>> certsMeta = certs.stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("titularMascarado", c.getTitularMascarado());
            m.put("documentoTitular", c.getDocumentoTitular());
            m.put("tipo", c.getTipo());
            m.put("emissor", c.getEmissor());
            m.put("validoDe", c.getValidoDe());
            m.put("validoAte", c.getValidoAte());
            m.put("sha256Original", c.getSha256Original());
            m.put("termoAceitoEm", c.getTermoAceitoEm());
            m.put("termoAceitoPor", c.getTermoAceitoPor());
            m.put("ativo", c.isAtivo());
            m.put("criadoEm", c.getCriadoEm());
            return m;
        }).toList();

        // Registra evento de auditoria da exportação
        auditoriaRepository.save(new AuditoriaEscritorio(
                escritorioId,
                usuarioSolicitanteId,
                "LGPD_EXPORTAR_DADOS",
                "Exportação de dados do escritório realizada sob os termos da LGPD (Art. 18)."
        ));

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {

            adicionarEntradaZip(zos, "escritorio.json", esc);
            adicionarEntradaZip(zos, "usuarios.json", usuarios);
            adicionarEntradaZip(zos, "empresas.json", empresas);
            adicionarEntradaZip(zos, "emdia_modelos.json", modelos);
            adicionarEntradaZip(zos, "emdia_tarefas.json", tarefas);
            adicionarEntradaZip(zos, "emdia_anotacoes.json", anotacoes);
            adicionarEntradaZip(zos, "certificados_metadados.json", certsMeta);
            adicionarEntradaZip(zos, "notasync_documentos.json", docs);
            adicionarEntradaZip(zos, "auditoria.json", auditorias);

            zos.finish();
            return baos.toByteArray();
        } catch (IOException e) {
            log.error("Erro ao gerar ZIP de exportação LGPD para escritório {}", escritorioId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Erro ao gerar arquivo de exportação de dados.", e);
        }
    }

    /**
     * Exclui definitivamente todos os dados do escritório (Direito ao Esquecimento - Artigo 17/18 LGPD).
     * Exige dupla confirmação com o CNPJ do próprio escritório.
     */
    @Transactional
    public void excluirEscritorioLgpd(UUID escritorioId, UUID usuarioSolicitanteId, String confirmacaoCnpj, String justificativa) {
        Escritorio esc = escritorioRepository.findById(escritorioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Escritório não encontrado."));

        String cnpjCadastradoLimpo = esc.getCnpj().replaceAll("\\D", "");
        String cnpjConfirmacaoLimpo = confirmacaoCnpj != null ? confirmacaoCnpj.replaceAll("\\D", "") : "";

        if (!cnpjCadastradoLimpo.equals(cnpjConfirmacaoLimpo)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "O CNPJ digitado para confirmação não confere com o CNPJ do escritório. Operação cancelada por segurança."
            );
        }

        log.warn("EXCLUSÃO LGPD DEFINITIVA iniciada para escritório ID: {}, CNPJ: {}, solicitante: {}, justificativa: {}",
                esc.getId(), esc.getCnpj(), usuarioSolicitanteId, justificativa);

        // O delete em cascata do banco (ON DELETE CASCADE) garante que certificados, empresas, tarefas, documentos e usuários sejam apagados.
        escritorioRepository.delete(esc);

        log.info("Escritório ID: {} e todos os seus dados correlacionados foram excluídos com sucesso sob a LGPD.", escritorioId);
    }

    private void adicionarEntradaZip(ZipOutputStream zos, String nomeArquivo, Object dados) throws IOException {
        ZipEntry entry = new ZipEntry(nomeArquivo);
        zos.putNextEntry(entry);
        byte[] bytes = objectMapper.writeValueAsBytes(dados);
        zos.write(bytes);
        zos.closeEntry();
    }
}

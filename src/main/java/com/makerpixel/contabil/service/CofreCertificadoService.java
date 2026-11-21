package com.makerpixel.contabil.service;

import com.makerpixel.contabil.domain.AuditoriaCert;
import com.makerpixel.contabil.domain.CertificadoCofre;
import com.makerpixel.contabil.dto.CertificadoDTOs.*;
import com.makerpixel.contabil.repository.AuditoriaCertRepository;
import com.makerpixel.contabil.repository.CertificadoCofreRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CofreCertificadoService {

    public static final String VERSAO_TERMO_USO_ATUAL = "1.0";

    private final CertificadoCofreRepository certificadoRepository;
    private final AuditoriaCertRepository auditoriaRepository;
    private final CofreCryptoService cryptoService;

    public CofreCertificadoService(
            CertificadoCofreRepository certificadoRepository,
            AuditoriaCertRepository auditoriaRepository,
            CofreCryptoService cryptoService
    ) {
        this.certificadoRepository = certificadoRepository;
        this.auditoriaRepository = auditoriaRepository;
        this.cryptoService = cryptoService;
    }

    /**
     * Realiza o upload, validação em memória e encriptação em envelope do certificado digital A1.
     */
    @Transactional
    public CertificadoResumoDTO uploadCertificado(
            UUID escritorioId,
            byte[] pfxBytes,
            String senha,
            boolean aceitouTermo,
            String termoVersao,
            String usuarioLogado,
            String ipOrigem
    ) {
        if (!aceitouTermo) {
            throw new IllegalArgumentException("É obrigatório ler e aceitar o Termo de Custódia e Uso Seguro do Certificado Digital.");
        }

        try {
            // 1. Inspeciona e valida o arquivo PKCS12 em memória
            CofreCryptoService.InspecaoCertificado inspecao = cryptoService.inspecionar(pfxBytes, senha);

            // 2. Desativa certificado anterior do mesmo escritório se houver
            Optional<CertificadoCofre> anterior = certificadoRepository.findByEscritorioIdAndAtivoTrue(escritorioId);
            anterior.ifPresent(c -> {
                c.setAtivo(false);
                c.setAtualizadoEm(OffsetDateTime.now());
                certificadoRepository.save(c);
            });

            // 3. Monta o novo certificado
            CertificadoCofre novo = new CertificadoCofre();
            novo.setEscritorioId(escritorioId);
            novo.setTitularMascarado(inspecao.titularMascarado());
            novo.setDocumentoTitular(inspecao.documento());
            novo.setTipo(inspecao.tipo());
            novo.setEmissor(inspecao.emissor());
            novo.setValidoDe(inspecao.validoDe());
            novo.setValidoAte(inspecao.validoAte());
            novo.setSha256Original(inspecao.sha256Hex());
            novo.setTermoAceitoEm(OffsetDateTime.now());
            novo.setTermoAceitoPor(usuarioLogado != null ? usuarioLogado : "usuario");
            novo.setTermoVersao(termoVersao != null ? termoVersao : VERSAO_TERMO_USO_ATUAL);
            novo.setEnviadoPor(usuarioLogado != null ? usuarioLogado : "usuario");
            novo.setAtivo(true);
            novo.setCriadoEm(OffsetDateTime.now());
            novo.setAtualizadoEm(OffsetDateTime.now());

            // 4. Criptografia em Envelope (AES-256-GCM)
            cryptoService.cifrarEnvelope(novo, pfxBytes, senha, null);

            CertificadoCofre salvo = certificadoRepository.save(novo);

            // 5. Registra auditoria
            registrarAuditoria(escritorioId, salvo.getId(), "UPLOAD", usuarioLogado, true,
                    "Upload e cifragem do certificado A1 titular: " + inspecao.titularMascarado(), ipOrigem);

            return toResumoDTO(salvo);
        } catch (Exception e) {
            registrarAuditoria(escritorioId, null, "UPLOAD", usuarioLogado, false,
                    "Falha no upload do certificado: " + e.getMessage(), ipOrigem);
            throw e;
        }
    }

    /**
     * Retorna metadados do certificado ativo do escritório (sem bytes nem senha).
     */
    @Transactional(readOnly = true)
    public Optional<CertificadoResumoDTO> obterCertificadoAtivo(UUID escritorioId) {
        return certificadoRepository.findByEscritorioIdAndAtivoTrue(escritorioId)
                .map(this::toResumoDTO);
    }

    /**
     * Exclui permanentemente o certificado digital do cofre.
     */
    @Transactional
    public void excluirCertificado(UUID escritorioId, UUID certificadoId, String usuarioLogado, String ipOrigem) {
        CertificadoCofre cert = certificadoRepository.findById(certificadoId)
                .orElseThrow(() -> new IllegalArgumentException("Certificado não encontrado."));

        if (!cert.getEscritorioId().equals(escritorioId)) {
            throw new SecurityException("Acesso negado ao certificado de outro escritório.");
        }

        certificadoRepository.delete(cert);

        registrarAuditoria(escritorioId, certificadoId, "EXCLUSAO", usuarioLogado, true,
                "Exclusão definitiva do certificado titular: " + cert.getTitularMascarado(), ipOrigem);
    }

    /**
     * Decifra o certificado ativo em memória para uso pelo robô de consulta.
     * Registra auditoria de uso.
     */
    public CofreCryptoService.CertificadoDecifrado obterParaConsulta(UUID escritorioId, String usuarioLogado, String ipOrigem) {
        CertificadoCofre cert = certificadoRepository.findByEscritorioIdAndAtivoTrue(escritorioId)
                .orElseThrow(() -> new IllegalStateException("Nenhum certificado A1 ativo no cofre do escritório."));

        try {
            CofreCryptoService.CertificadoDecifrado decifrado = cryptoService.decifrarEnvelope(cert, null);
            registrarAuditoria(escritorioId, cert.getId(), "DECIFRAGEM_CONSULTA", usuarioLogado, true,
                    "Decifração em memória para execução de consulta na SEF/SC", ipOrigem);
            return decifrado;
        } catch (Exception e) {
            registrarAuditoria(escritorioId, cert.getId(), "DECIFRAGEM_CONSULTA", usuarioLogado, false,
                    "Falha na decifração para consulta: " + e.getMessage(), ipOrigem);
            throw e;
        }
    }

    /**
     * Rotação da Chave Mestra KEK em todos os certificados ativos do sistema.
     */
    @Transactional
    public RotacionarChaveResponse rotacionarChaveMestra(String novaChaveMestra, String usuarioLogado, String ipOrigem) {
        List<CertificadoCofre> todos = certificadoRepository.findAllByAtivoTrue();
        int count = 0;
        int novaVersao = 1;

        for (CertificadoCofre cert : todos) {
            cryptoService.rotacionarChaveMestra(cert, null, novaChaveMestra);
            certificadoRepository.save(cert);
            count++;
            novaVersao = cert.getVersaoChaveMestra();

            registrarAuditoria(cert.getEscritorioId(), cert.getId(), "ROTACAO_CHAVE", usuarioLogado, true,
                    "Chave mestra rotacionada com sucesso para versão " + novaVersao, ipOrigem);
        }

        return new RotacionarChaveResponse(count, novaVersao, "Chave mestra rotacionada com sucesso em " + count + " certificados.");
    }

    /**
     * Lista o histórico de auditoria do escritório.
     */
    @Transactional(readOnly = true)
    public List<AuditoriaCertDTO> listarAuditoria(UUID escritorioId) {
        return auditoriaRepository.findByEscritorioIdOrderByExecutadoEmDesc(escritorioId).stream()
                .map(a -> new AuditoriaCertDTO(
                        a.getId(),
                        a.getEscritorioId(),
                        a.getCertificadoId(),
                        a.getOperacao(),
                        a.getUsuario(),
                        a.isSucesso(),
                        a.getDetalhe(),
                        a.getIp(),
                        a.getExecutadoEm()
                ))
                .collect(Collectors.toList());
    }

    private void registrarAuditoria(UUID escritorioId, UUID certificadoId, String operacao, String usuario, boolean sucesso, String detalhe, String ip) {
        AuditoriaCert audit = new AuditoriaCert(escritorioId, certificadoId, operacao, usuario, sucesso, detalhe, ip);
        auditoriaRepository.save(audit);
    }

    public CertificadoResumoDTO toResumoDTO(CertificadoCofre c) {
        long dias = CofreCryptoService.calcularDiasParaVencer(c.getValidoAte());
        String statusVencimento = CofreCryptoService.calcularStatusVencimento(c.getValidoAte());

        return new CertificadoResumoDTO(
                c.getId(),
                c.getEscritorioId(),
                c.getTitularMascarado(),
                c.getDocumentoTitular(),
                c.getTipo(),
                c.getEmissor(),
                c.getValidoDe(),
                c.getValidoAte(),
                dias,
                statusVencimento,
                c.getSha256Original(),
                c.getTermoAceitoEm(),
                c.getTermoAceitoPor(),
                c.getTermoVersao(),
                c.getEnviadoPor(),
                c.getVersaoChaveMestra(),
                c.isAtivo(),
                c.getCriadoEm()
        );
    }
}

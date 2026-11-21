package com.makerpixel.contabil.service;

import com.makerpixel.contabil.domain.CertificadoCofre;
import com.makerpixel.contabil.domain.TipoCertificadoDigital;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Serviço Criptográfico do Cofre de Certificados Digitais A1.
 * Criptografia em Envelope AES-256-GCM com DEK aleatória e KEK mestra.
 * Inspeção e decifração estritamente em memória.
 */
@Service
public class CofreCryptoService {

    public static final int MAX_FILE_SIZE_BYTES = 50 * 1024; // 50 KB limite
    public static final int GCM_TAG_LENGTH_BITS = 128;
    public static final int GCM_IV_LENGTH_BYTES = 12;

    public static final String OID_ICP_CPF = "2.16.76.1.3.1";
    public static final String OID_ICP_CNPJ = "2.16.76.1.3.3";

    private final String masterKeyEnv;

    public CofreCryptoService(@Value("${central.master.key:${CENTRAL_MASTER_KEY:central_contabil_master_key_default_2026}}") String masterKey) {
        this.masterKeyEnv = masterKey;
    }

    public record InspecaoCertificado(
            String titular,
            String titularMascarado,
            String documento,
            TipoCertificadoDigital tipo,
            String emissor,
            OffsetDateTime validoDe,
            OffsetDateTime validoAte,
            String sha256Hex,
            X509Certificate x509Certificate,
            PrivateKey privateKey
    ) {}

    public record CertificadoDecifrado(
            KeyStore keyStore,
            String senha,
            X509Certificate certificate,
            PrivateKey privateKey,
            byte[] pfxBytes
    ) {}

    /**
     * Inspeciona e valida o arquivo PKCS12 em memória sem persistir nada em disco.
     */
    public InspecaoCertificado inspecionar(byte[] pfxBytes, String senha) {
        if (pfxBytes == null || pfxBytes.length == 0) {
            throw new IllegalArgumentException("O arquivo de certificado digital é obrigatório.");
        }
        if (pfxBytes.length > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("Certificado excede o limite máximo permitido de 50 KB.");
        }
        if (senha == null) {
            throw new IllegalArgumentException("A senha do certificado digital é obrigatória.");
        }

        KeyStore keyStore;
        try {
            keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(new ByteArrayInputStream(pfxBytes), senha.toCharArray());
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            if (msg.contains("password") || msg.contains("keystore password") || msg.contains("pad block corrupted") || msg.contains("mac")) {
                throw new IllegalArgumentException("Senha do certificado digital incorreta.");
            }
            throw new IllegalArgumentException("Arquivo inválido ou corrompido. Forneça um certificado digital A1 (.pfx ou .p12).", e);
        }

        Enumeration<String> aliases;
        try {
            aliases = keyStore.aliases();
        } catch (KeyStoreException e) {
            throw new IllegalStateException("Falha ao ler aliases do certificado.", e);
        }

        String aliasChave = null;
        PrivateKey privateKey = null;
        X509Certificate x509Cert = null;

        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            try {
                if (keyStore.isKeyEntry(alias)) {
                    Key key = keyStore.getKey(alias, senha.toCharArray());
                    if (key instanceof PrivateKey pk) {
                        aliasChave = alias;
                        privateKey = pk;
                        x509Cert = (X509Certificate) keyStore.getCertificate(alias);
                        break;
                    }
                }
            } catch (Exception ignored) {
            }
        }

        if (privateKey == null || x509Cert == null) {
            throw new IllegalArgumentException("Certificados do tipo A3 (em cartão ou token físico) não são suportados na nuvem. Utilize um certificado A1 em arquivo (.pfx ou .p12) contendo a chave privada.");
        }

        // Validação de temporalidade
        try {
            x509Cert.checkValidity();
        } catch (CertificateExpiredException e) {
            throw new IllegalArgumentException("Certificado digital expirado em " + x509Cert.getNotAfter() + ".");
        } catch (CertificateNotYetValidException e) {
            throw new IllegalArgumentException("Certificado digital ainda não é válido. Válido a partir de " + x509Cert.getNotBefore() + ".");
        }

        // Extração de Metadados
        String subjectDN = x509Cert.getSubjectX500Principal().getName();
        String emissor = x509Cert.getIssuerX500Principal().getName();
        OffsetDateTime validoDe = x509Cert.getNotBefore().toInstant().atOffset(ZoneOffset.UTC);
        OffsetDateTime validoAte = x509Cert.getNotAfter().toInstant().atOffset(ZoneOffset.UTC);
        String sha256Hex = calcularSha256(pfxBytes);

        String titular = extrairCN(subjectDN);
        String documento = extrairDocumento(x509Cert, subjectDN);
        TipoCertificadoDigital tipo = determinarTipo(documento, x509Cert);
        String titularMascarado = mascararTitular(titular, documento, tipo);

        return new InspecaoCertificado(
                titular,
                titularMascarado,
                documento,
                tipo,
                emissor,
                validoDe,
                validoAte,
                sha256Hex,
                x509Cert,
                privateKey
        );
    }

    /**
     * Criptografa o payload (.pfx + senha) usando Criptografia em Envelope (AES-256-GCM).
     */
    public void cifrarEnvelope(CertificadoCofre cert, byte[] pfxBytes, String senha, String masterKeyOverride) {
        String masterKey = (masterKeyOverride != null && !masterKeyOverride.isBlank()) ? masterKeyOverride : this.masterKeyEnv;
        SecretKeySpec kek = derivarKek(masterKey);

        // 1. Gera DEK aleatória de 256 bits (32 bytes)
        byte[] dekBytes = new byte[32];
        new SecureRandom().nextBytes(dekBytes);
        SecretKeySpec dek = new SecretKeySpec(dekBytes, "AES");

        // 2. Empacota payload: [4 bytes senha length] + [senha UTF-8] + [pfxBytes]
        byte[] senhaBytes = senha.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(4 + senhaBytes.length + pfxBytes.length);
        buffer.putInt(senhaBytes.length);
        buffer.put(senhaBytes);
        buffer.put(pfxBytes);
        byte[] payload = buffer.array();

        // 3. Cifra payload com DEK usando AES-256-GCM
        byte[] ivDados = new byte[GCM_IV_LENGTH_BYTES];
        new SecureRandom().nextBytes(ivDados);
        byte[] dadosCifrados = cifrarAesGcm(payload, dek, ivDados);

        // 4. Cifra DEK com KEK usando AES-256-GCM
        byte[] ivDek = new byte[GCM_IV_LENGTH_BYTES];
        new SecureRandom().nextBytes(ivDek);
        byte[] dekCifrada = cifrarAesGcm(dekBytes, kek, ivDek);

        // Limpa bytes sensíveis da memória
        Arrays.fill(dekBytes, (byte) 0);
        Arrays.fill(payload, (byte) 0);

        // Atribui à entidade
        cert.setDadosCifrados(dadosCifrados);
        cert.setIvDados(ivDados);
        cert.setDekCifrada(dekCifrada);
        cert.setIvDek(ivDek);
        cert.setVersaoChaveMestra(cert.getVersaoChaveMestra() > 0 ? cert.getVersaoChaveMestra() : 1);
    }

    /**
     * Decifra o certificado em envelope estritamente em memória.
     */
    public CertificadoDecifrado decifrarEnvelope(CertificadoCofre cert, String masterKeyOverride) {
        String masterKey = (masterKeyOverride != null && !masterKeyOverride.isBlank()) ? masterKeyOverride : this.masterKeyEnv;
        SecretKeySpec kek = derivarKek(masterKey);

        // 1. Decifra a DEK com a KEK
        byte[] dekBytes = decifrarAesGcm(cert.getDekCifrada(), kek, cert.getIvDek());
        SecretKeySpec dek = new SecretKeySpec(dekBytes, "AES");

        // 2. Decifra os dados com a DEK
        byte[] payload = decifrarAesGcm(cert.getDadosCifrados(), dek, cert.getIvDados());
        Arrays.fill(dekBytes, (byte) 0);

        // 3. Desempacota payload
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        int senhaLen = buffer.getInt();
        byte[] senhaBytes = new byte[senhaLen];
        buffer.get(senhaBytes);
        String senha = new String(senhaBytes, StandardCharsets.UTF_8);

        byte[] pfxBytes = new byte[buffer.remaining()];
        buffer.get(pfxBytes);
        Arrays.fill(payload, (byte) 0);

        // 4. Carrega KeyStore em memória
        KeyStore keyStore;
        try {
            keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(new ByteArrayInputStream(pfxBytes), senha.toCharArray());
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao reconstruir certificado em memória.", e);
        }

        Enumeration<String> aliases;
        try {
            aliases = keyStore.aliases();
        } catch (KeyStoreException e) {
            throw new IllegalStateException("Erro ao acessar aliases.", e);
        }

        X509Certificate x509 = null;
        PrivateKey pk = null;
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            try {
                if (keyStore.isKeyEntry(alias)) {
                    Key key = keyStore.getKey(alias, senha.toCharArray());
                    if (key instanceof PrivateKey pKey) {
                        pk = pKey;
                        x509 = (X509Certificate) keyStore.getCertificate(alias);
                        break;
                    }
                }
            } catch (Exception ignored) {
            }
        }

        return new CertificadoDecifrado(keyStore, senha, x509, pk, pfxBytes);
    }

    /**
     * Rotaciona a Chave Mestra KEK.
     * Re-cifra apenas as DEKs sem precisar re-cifrar os payloads (.pfx).
     */
    public void rotacionarChaveMestra(CertificadoCofre cert, String chaveAntiga, String chaveNova) {
        if (chaveNova == null || chaveNova.isBlank()) {
            throw new IllegalArgumentException("A nova chave mestra não pode ser vazia.");
        }
        SecretKeySpec kekAntiga = derivarKek(chaveAntiga != null ? chaveAntiga : this.masterKeyEnv);
        SecretKeySpec kekNova = derivarKek(chaveNova);

        // 1. Decifra a DEK com a KEK antiga
        byte[] dekBytes = decifrarAesGcm(cert.getDekCifrada(), kekAntiga, cert.getIvDek());

        // 2. Cifra a mesma DEK com a nova KEK e novo IV
        byte[] novoIvDek = new byte[GCM_IV_LENGTH_BYTES];
        new SecureRandom().nextBytes(novoIvDek);
        byte[] novaDekCifrada = cifrarAesGcm(dekBytes, kekNova, novoIvDek);

        Arrays.fill(dekBytes, (byte) 0);

        cert.setDekCifrada(novaDekCifrada);
        cert.setIvDek(novoIvDek);
        cert.setVersaoChaveMestra(cert.getVersaoChaveMestra() + 1);
        cert.setAtualizadoEm(OffsetDateTime.now());
    }

    // --- Métodos de Apoio Criptográfico ---

    private byte[] cifrarAesGcm(byte[] plaintext, SecretKeySpec key, byte[] iv) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, spec);
            return cipher.doFinal(plaintext);
        } catch (Exception e) {
            throw new SecurityException("Erro na criptografia AES-GCM.", e);
        }
    }

    public byte[] decifrarAesGcm(byte[] ciphertext, SecretKeySpec key, byte[] iv) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, spec);
            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            throw new SecurityException("Falha na decifração AES-GCM (integridade violada ou chave incorreta).", e);
        }
    }

    private SecretKeySpec derivarKek(String masterKeyString) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = sha256.digest(masterKeyString.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(keyBytes, "AES");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algoritmo SHA-256 indisponível.", e);
        }
    }

    public static String calcularSha256(byte[] data) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] hash = sha256.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String extrairCN(String subjectDN) {
        Pattern p = Pattern.compile("CN=([^,]+)");
        Matcher m = p.matcher(subjectDN);
        if (m.find()) {
            return m.group(1).trim();
        }
        return subjectDN;
    }

    public static String extrairDocumento(X509Certificate cert, String subjectDN) {
        // 1. Tenta extrair OID ICP-Brasil 2.16.76.1.3.3 (CNPJ) ou 2.16.76.1.3.1 (CPF) via getExtensionValue
        try {
            byte[] extCnpj = cert.getExtensionValue(OID_ICP_CNPJ);
            if (extCnpj != null) {
                String val = new String(extCnpj, StandardCharsets.ISO_8859_1);
                Matcher m = Pattern.compile("(\\d{14})").matcher(val);
                if (m.find()) return m.group(1);
            }
            byte[] extCpf = cert.getExtensionValue(OID_ICP_CPF);
            if (extCpf != null) {
                String val = new String(extCpf, StandardCharsets.ISO_8859_1);
                Matcher m = Pattern.compile("(\\d{11})").matcher(val);
                if (m.find()) return m.group(1);
            }
        } catch (Exception ignored) {
        }

        // 2. Extrai documento do sufixo do Common Name: "NOME:00000000000" ou "RAZAO:00000000000100"
        String cn = extrairCN(subjectDN);
        Matcher mCnpj = Pattern.compile(":(\\d{14})$").matcher(cn);
        if (mCnpj.find()) {
            return mCnpj.group(1);
        }
        Matcher mCpf = Pattern.compile(":(\\d{11})$").matcher(cn);
        if (mCpf.find()) {
            return mCpf.group(1);
        }

        // 3. Procura qualquer sequência de 14 ou 11 dígitos no subject
        Matcher mDig = Pattern.compile("(\\d{14}|\\d{11})").matcher(subjectDN);
        if (mDig.find()) {
            return mDig.group(1);
        }

        return "";
    }

    public static TipoCertificadoDigital determinarTipo(String documento, X509Certificate cert) {
        if (documento != null) {
            String limpo = documento.replaceAll("\\D", "");
            if (limpo.length() == 14) return TipoCertificadoDigital.E_CNPJ;
            if (limpo.length() == 11) return TipoCertificadoDigital.E_CPF;
        }
        if (cert.getExtensionValue(OID_ICP_CNPJ) != null) return TipoCertificadoDigital.E_CNPJ;
        if (cert.getExtensionValue(OID_ICP_CPF) != null) return TipoCertificadoDigital.E_CPF;
        return TipoCertificadoDigital.E_CNPJ;
    }

    public static String mascararTitular(String titular, String documento, TipoCertificadoDigital tipo) {
        String docMascarado = "";
        String docLimpo = (documento != null) ? documento.replaceAll("\\D", "") : "";
        if (docLimpo.length() == 14) {
            // CNPJ: 12.***.***/0001-90
            docMascarado = docLimpo.substring(0, 2) + ".***.***/" + docLimpo.substring(8, 12) + "-" + docLimpo.substring(12, 14);
        } else if (docLimpo.length() == 11) {
            // CPF: ***.456.789-**
            docMascarado = "***." + docLimpo.substring(3, 6) + "." + docLimpo.substring(6, 9) + "-**";
        }

        // Titular: separa o nome do número se houver dois pontos
        String nome = titular;
        if (nome.contains(":")) {
            nome = nome.substring(0, nome.indexOf(":")).trim();
        }

        if (!docMascarado.isEmpty()) {
            return nome + " (" + docMascarado + ")";
        }
        return nome;
    }

    public static String calcularStatusVencimento(OffsetDateTime validoAte) {
        if (validoAte == null) return "DESCONHECIDO";
        long dias = java.time.Duration.between(OffsetDateTime.now(), validoAte).toDays();
        if (dias < 0) return "EXPIRADO";
        if (dias <= 7) return "CRITICO_7_DIAS";
        if (dias <= 15) return "ALERTA_15_DIAS";
        if (dias <= 30) return "AVISO_30_DIAS";
        return "OK";
    }

    public static long calcularDiasParaVencer(OffsetDateTime validoAte) {
        if (validoAte == null) return 0;
        return java.time.Duration.between(OffsetDateTime.now(), validoAte).toDays();
    }
}

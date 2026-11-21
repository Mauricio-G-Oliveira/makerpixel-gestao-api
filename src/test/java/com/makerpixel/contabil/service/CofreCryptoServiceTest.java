package com.makerpixel.contabil.service;

import com.makerpixel.contabil.domain.CertificadoCofre;
import com.makerpixel.contabil.domain.TipoCertificadoDigital;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.time.OffsetDateTime;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class CofreCryptoServiceTest {

    private CofreCryptoService cryptoService;
    private final String masterKey = "minha_chave_mestra_secreta_2026_test_key";

    @BeforeEach
    void setUp() {
        cryptoService = new CofreCryptoService(masterKey);
    }

    @Test
    @DisplayName("Cifrar e decifrar envelope recupera exatamente o mesmo conteúdo")
    void deveCifrarEDecifrarEnvelopeComSucesso() throws Exception {
        // Cria um PKCS12 válido em memória
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);
        String senha = "senhaForteTeste@123";
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ks.store(baos, senha.toCharArray());
        byte[] pfxOriginal = baos.toByteArray();

        CertificadoCofre cofre = new CertificadoCofre();
        cryptoService.cifrarEnvelope(cofre, pfxOriginal, senha, masterKey);

        assertNotNull(cofre.getDadosCifrados());
        assertNotNull(cofre.getIvDados());
        assertNotNull(cofre.getDekCifrada());
        assertNotNull(cofre.getIvDek());
        assertEquals(1, cofre.getVersaoChaveMestra());

        // Decifra em memória
        CofreCryptoService.CertificadoDecifrado decifrado = cryptoService.decifrarEnvelope(cofre, masterKey);

        assertNotNull(decifrado);
        assertEquals(senha, decifrado.senha());
        assertArrayEquals(pfxOriginal, decifrado.pfxBytes());
        assertNotNull(decifrado.keyStore());
    }

    @Test
    @DisplayName("Adulteração no texto cifrado falha na autenticação AES-GCM")
    void deveFalharSeDadosCifradosForemAdulterados() throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);
        String senha = "senhaTesteGCM123";
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ks.store(baos, senha.toCharArray());
        byte[] pfxOriginal = baos.toByteArray();

        CertificadoCofre cofre = new CertificadoCofre();
        cryptoService.cifrarEnvelope(cofre, pfxOriginal, senha, masterKey);

        // Adultera um byte nos dados cifrados
        byte[] adulterados = Arrays.copyOf(cofre.getDadosCifrados(), cofre.getDadosCifrados().length);
        adulterados[0] ^= 0xFF;
        cofre.setDadosCifrados(adulterados);

        assertThrows(SecurityException.class, () -> cryptoService.decifrarEnvelope(cofre, masterKey));
    }

    @Test
    @DisplayName("Adulteração na DEK cifrada falha na autenticação AES-GCM")
    void deveFalharSeDekCifradaForAdulterada() throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);
        String senha = "senhaDekTeste123";
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ks.store(baos, senha.toCharArray());

        CertificadoCofre cofre = new CertificadoCofre();
        cryptoService.cifrarEnvelope(cofre, baos.toByteArray(), senha, masterKey);

        // Adultera um byte na DEK cifrada
        byte[] adulterado = Arrays.copyOf(cofre.getDekCifrada(), cofre.getDekCifrada().length);
        adulterado[0] ^= 0xFF;
        cofre.setDekCifrada(adulterado);

        assertThrows(SecurityException.class, () -> cryptoService.decifrarEnvelope(cofre, masterKey));
    }

    @Test
    @DisplayName("Rotação de chave mestra re-cifra DEK e permite decifração com a nova KEK")
    void deveRotacionarChaveMestraComSucesso() throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);
        String senha = "senhaRotacao123";
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ks.store(baos, senha.toCharArray());
        byte[] pfxOriginal = baos.toByteArray();

        CertificadoCofre cofre = new CertificadoCofre();
        cryptoService.cifrarEnvelope(cofre, pfxOriginal, senha, masterKey);

        String novaChaveMestra = "nova_chave_mestra_rotacionada_super_segura_2026";
        cryptoService.rotacionarChaveMestra(cofre, masterKey, novaChaveMestra);

        assertEquals(2, cofre.getVersaoChaveMestra());

        // Com a chave antiga deve falhar
        assertThrows(SecurityException.class, () -> cryptoService.decifrarEnvelope(cofre, masterKey));

        // Com a nova chave mestra decifra perfeitamente
        CofreCryptoService.CertificadoDecifrado decifrado = cryptoService.decifrarEnvelope(cofre, novaChaveMestra);
        assertNotNull(decifrado);
        assertEquals(senha, decifrado.senha());
        assertArrayEquals(pfxOriginal, decifrado.pfxBytes());
    }

    @Test
    @DisplayName("Rejeita arquivo com tamanho superior a 50 KB")
    void deveRejeitarArquivoMaiorQue50KB() {
        byte[] arquivoGrande = new byte[51 * 1024]; // 51 KB
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> cryptoService.inspecionar(arquivoGrande, "senha"));
        assertTrue(ex.getMessage().contains("limite máximo permitido de 50 KB"));
    }

    @Test
    @DisplayName("Rejeita arquivo PKCS12 vazio ou dados nulos")
    void deveRejeitarDadosNulosOuVazios() {
        assertThrows(IllegalArgumentException.class, () -> cryptoService.inspecionar(null, "senha"));
        assertThrows(IllegalArgumentException.class, () -> cryptoService.inspecionar(new byte[0], "senha"));
        assertThrows(IllegalArgumentException.class, () -> cryptoService.inspecionar(new byte[10], null));
    }

    @Test
    @DisplayName("Rejeita arquivo PKCS12 que não contenha chave privada com mensagem sobre A3")
    void deveRejeitarCertificadoSemChavePrivadaA3() throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);
        String senha = "senha123";
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ks.store(baos, senha.toCharArray());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> cryptoService.inspecionar(baos.toByteArray(), senha));
        assertTrue(ex.getMessage().contains("A3"));
    }

    @Test
    @DisplayName("Rejeita senha incorreta com mensagem amigável")
    void deveRejeitarSenhaIncorreta() throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ks.store(baos, "senhaCerta".toCharArray());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> cryptoService.inspecionar(baos.toByteArray(), "senhaErrada"));
        assertTrue(ex.getMessage().contains("Senha do certificado digital incorreta"));
    }

    @Test
    @DisplayName("Máscaras de CNPJ e CPF são aplicadas corretamente")
    void deveMascararTitularECnpjCpf() {
        String mCnpj = CofreCryptoService.mascararTitular("EMPRESA MODELO LTDA:12345678000190", "12345678000190", TipoCertificadoDigital.E_CNPJ);
        assertEquals("EMPRESA MODELO LTDA (12.***.***/0001-90)", mCnpj);

        String mCpf = CofreCryptoService.mascararTitular("JOAO DA SILVA:12345678901", "12345678901", TipoCertificadoDigital.E_CPF);
        assertEquals("JOAO DA SILVA (***.456.789-**)", mCpf);
    }

    @Test
    @DisplayName("Cálculo de avisos de vencimento em 30, 15 e 7 dias")
    void deveCalcularStatusVencimentoEmIntervalos() {
        OffsetDateTime agora = OffsetDateTime.now();

        assertEquals("EXPIRADO", CofreCryptoService.calcularStatusVencimento(agora.minusDays(1)));
        assertEquals("CRITICO_7_DIAS", CofreCryptoService.calcularStatusVencimento(agora.plusDays(5)));
        assertEquals("ALERTA_15_DIAS", CofreCryptoService.calcularStatusVencimento(agora.plusDays(12)));
        assertEquals("AVISO_30_DIAS", CofreCryptoService.calcularStatusVencimento(agora.plusDays(25)));
        assertEquals("OK", CofreCryptoService.calcularStatusVencimento(agora.plusDays(60)));
    }
}

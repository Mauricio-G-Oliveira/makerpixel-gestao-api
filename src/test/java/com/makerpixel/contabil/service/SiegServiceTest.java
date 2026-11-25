package com.makerpixel.contabil.service;

import com.makerpixel.contabil.domain.SiegConfig;
import com.makerpixel.contabil.repository.SiegConfigRepository;
import com.makerpixel.contabil.service.CofreCryptoService.TextoEncifrado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class SiegServiceTest {

    private SiegConfigRepository siegConfigRepository;
    private CofreCryptoService cofreCryptoService;
    private SiegService siegService;

    @BeforeEach
    void setUp() {
        siegConfigRepository = mock(SiegConfigRepository.class);
        cofreCryptoService = mock(CofreCryptoService.class);
        siegService = new SiegService(siegConfigRepository, cofreCryptoService);
    }

    @Test
    @DisplayName("SIEG: salvar chave criptografa no cofre AES-256-GCM e retorna apenas últimos 4 caracteres")
    void testSalvarChaveSiegCriptografada() {
        UUID escritorioId = UUID.randomUUID();
        String apiKey = "sieg_live_secret_key_9876";

        when(cofreCryptoService.cifrarTexto(apiKey))
                .thenReturn(new TextoEncifrado("cifrado_bytes_base64", "iv_12_bytes"));
        when(siegConfigRepository.findByEscritorioId(escritorioId))
                .thenReturn(Optional.empty());
        when(siegConfigRepository.save(any(SiegConfig.class))).thenAnswer(i -> {
            SiegConfig c = i.getArgument(0);
            when(siegConfigRepository.findByEscritorioId(escritorioId)).thenReturn(Optional.of(c));
            return c;
        });

        var resp = siegService.salvarChave(escritorioId, apiKey);

        assertTrue(resp.configurada());
        assertEquals("9876", resp.ultimosQuatro());
        verify(cofreCryptoService, times(1)).cifrarTexto(apiKey);
        verify(siegConfigRepository, times(1)).save(any(SiegConfig.class));
    }

    @Test
    @DisplayName("SIEG: obter configuração não expõe chave de API em texto puro")
    void testObterConfiguracaoSegura() {
        UUID escritorioId = UUID.randomUUID();
        SiegConfig cfg = new SiegConfig();
        cfg.setEscritorioId(escritorioId);
        cfg.setUltimosQuatro("4321");
        cfg.setUltimoStatus("CONEXAO_OK");
        cfg.setAtivo(true);

        when(siegConfigRepository.findByEscritorioId(escritorioId)).thenReturn(Optional.of(cfg));

        var resp = siegService.obterConfiguracao(escritorioId);

        assertTrue(resp.configurada());
        assertEquals("4321", resp.ultimosQuatro());
        assertEquals("CONEXAO_OK", resp.ultimoStatus());
        verify(cofreCryptoService, never()).decifrarTexto(any(), any());
    }

    @Test
    @DisplayName("SIEG: testar conexão com credencial do cofre")
    void testTestarConexaoSieg() {
        UUID escritorioId = UUID.randomUUID();
        SiegConfig cfg = new SiegConfig();
        cfg.setEscritorioId(escritorioId);
        cfg.setApiKeyCifrada("cifrado");
        cfg.setIvDados("iv");
        cfg.setUltimosQuatro("1234");

        when(siegConfigRepository.findByEscritorioId(escritorioId)).thenReturn(Optional.of(cfg));
        when(cofreCryptoService.decifrarTexto("cifrado", "iv")).thenReturn("sieg_token_valido_1234");

        var resp = siegService.testarConexao(escritorioId);

        assertTrue(resp.sucesso());
        assertTrue(resp.mensagem().contains("sucesso"));
    }
}

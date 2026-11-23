package com.makerpixel.contabil.service;

import com.makerpixel.contabil.domain.ReformaLote;
import com.makerpixel.contabil.reforma.CalculadoraRtcClient;
import com.makerpixel.contabil.reforma.LeitorNfeIbsCbs;
import com.makerpixel.contabil.reforma.LeitorNfeIbsCbs.NotaFiscalIbsCbs;
import com.makerpixel.contabil.reforma.ReformaAuditorService;
import com.makerpixel.contabil.reforma.dto.*;
import com.makerpixel.contabil.repository.NotasyncDocumentoRepository;
import com.makerpixel.contabil.repository.ReformaItemRepository;
import com.makerpixel.contabil.repository.ReformaLoteRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

public class ReformaAuditorTest {

    private final CalculadoraRtcClient calculadoraClient = new CalculadoraRtcClient("http://localhost:8088");

    @Test
    @DisplayName("Calculadora RTC: fallback offline aplica alíquotas de transição 2026 com observabilidade")
    void testCalculadoraRtcOffline() {
        OperacaoRtcRequest op = new OperacaoRtcRequest();
        op.setIdOperacao("OP-TESTE-01");

        OperacaoItemRtcRequest item = new OperacaoItemRtcRequest();
        item.setNumeroItem(1);
        item.setCodigoProduto("PROD-001");
        item.setDescricao("Notebook Profissional");
        item.setNcm("84713012");
        item.setValorTotal(new BigDecimal("5000.00"));
        item.setBaseCalculo(new BigDecimal("5000.00"));
        item.setcClassTrib("000001"); // Regra geral tributada

        op.setItens(List.of(item));

        CalculoRtcResponse resp = calculadoraClient.calcularComObservabilidade(op);

        assertNotNull(resp);
        assertEquals(1, resp.getItens().size());

        CalculoItemRtcResponse calc = resp.getItens().get(0);
        assertEquals("CALCULADO", calc.getStatus());
        assertEquals("01", calc.getCstCalculado());
        // CBS: 0.90% de 5000 = 45.00
        assertEquals(new BigDecimal("45.00"), calc.getValorCbs());
        // IBS UF: 0.05% de 5000 = 2.50
        assertEquals(new BigDecimal("2.50"), calc.getValorIbsUf());
        // IBS Mun: 0.05% de 5000 = 2.50
        assertEquals(new BigDecimal("2.50"), calc.getValorIbsMun());
        assertEquals(new BigDecimal("5.00"), calc.getValorIbsTotal());
        assertNotNull(calc.getMemoriaCalculo());
    }

    @Test
    @DisplayName("Calculadora RTC: redução de 60% para serviços essenciais (cClassTrib 20xxxx)")
    void testCalculadoraRtcReducao60() {
        OperacaoRtcRequest op = new OperacaoRtcRequest();
        OperacaoItemRtcRequest item = new OperacaoItemRtcRequest();
        item.setNumeroItem(1);
        item.setDescricao("Serviço de Educação Superior");
        item.setNcm("8520");
        item.setValorTotal(new BigDecimal("1000.00"));
        item.setBaseCalculo(new BigDecimal("1000.00"));
        item.setcClassTrib("200001"); // Redução 60%

        op.setItens(List.of(item));

        CalculoRtcResponse resp = calculadoraClient.calcularComObservabilidade(op);
        CalculoItemRtcResponse calc = resp.getItens().get(0);

        assertEquals("CALCULADO", calc.getStatus());
        assertEquals("02", calc.getCstCalculado());
        assertEquals(new BigDecimal("60.00"), calc.getPercentualReducao());
        // CBS com 60% de redução = 40% de 0.90% = 0.36% de 1000 = 3.60
        assertEquals(new BigDecimal("3.60"), calc.getValorCbs());
    }

    @Test
    @DisplayName("Leitor XML: parse seguro de NF-e com grupo IBSCBS")
    void testLeitorXmlNfeComIbsCbs() throws Exception {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <nfeProc xmlns="http://www.portalfiscal.inf.br/nfe">
                  <NFe>
                    <infNFe Id="NFe42261012345678000190550010000012341000012345">
                      <ide>
                        <nNF>1234</nNF>
                        <mod>55</mod>
                        <dhEmi>2026-10-15T10:00:00-03:00</dhEmi>
                      </ide>
                      <emit>
                        <enderEmit><UF>SC</UF><cMun>4218707</cMun></enderEmit>
                      </emit>
                      <dest>
                        <enderDest><UF>SC</UF><cMun>4218707</cMun></enderDest>
                      </dest>
                      <det nItem="1">
                        <prod>
                          <cProd>001</cProd>
                          <xProd>Mesa para Escritorio</xProd>
                          <NCM>94031000</NCM>
                          <CFOP>5102</CFOP>
                          <qCom>1.00</qCom>
                          <vProd>1000.00</vProd>
                        </prod>
                        <imposto>
                          <IBSCBS>
                            <CST>01</CST>
                            <cClassTrib>000001</cClassTrib>
                            <vBC>1000.00</vBC>
                            <gCBS>
                              <pCBS>0.90</pCBS>
                              <vCBS>9.00</vCBS>
                            </gCBS>
                            <gIBSUF>
                              <pIBSUF>0.05</pIBSUF>
                              <vIBSUF>0.50</vIBSUF>
                            </gIBSUF>
                            <gIBSMun>
                              <pIBSMun>0.05</pIBSMun>
                              <vIBSMun>0.50</vIBSMun>
                            </gIBSMun>
                            <vIBS>1.00</vIBS>
                          </IBSCBS>
                        </imposto>
                      </det>
                    </infNFe>
                  </NFe>
                </nfeProc>
                """;

        NotaFiscalIbsCbs nf = LeitorNfeIbsCbs.lerXml(xml);

        assertNotNull(nf);
        assertEquals("42261012345678000190550010000012341000012345", nf.chaveAcesso());
        assertEquals("1234", nf.numeroNota());
        assertEquals(1, nf.itens().size());

        var item = nf.itens().get(0);
        assertTrue(item.temGrupoIbsCbs());
        assertEquals("01", item.cstInformado());
        assertEquals("000001", item.cClassTrib());
        assertEquals(new BigDecimal("9.00"), item.valorCbsInformado());
        assertEquals(new BigDecimal("1.00"), item.valorIbsTotalInformado());
    }

    @Test
    @DisplayName("ReformaAuditorService: auditoria em lote detecta OK e tolerância de R$ 0,01")
    void testAuditoriaProcessamentoOk() {
        ReformaLoteRepository mockLoteRepo = Mockito.mock(ReformaLoteRepository.class);
        ReformaItemRepository mockItemRepo = Mockito.mock(ReformaItemRepository.class);
        NotasyncDocumentoRepository mockDocRepo = Mockito.mock(NotasyncDocumentoRepository.class);

        Mockito.when(mockLoteRepo.save(any(ReformaLote.class))).thenAnswer(i -> {
            ReformaLote l = i.getArgument(0);
            if (l.getId() == null) l.setId(UUID.randomUUID());
            return l;
        });

        ReformaAuditorService service = new ReformaAuditorService(
                mockLoteRepo, mockItemRepo, mockDocRepo, null, calculadoraClient
        );

        String xmlOk = """
                <nfeProc xmlns="http://www.portalfiscal.inf.br/nfe">
                  <NFe><infNFe Id="NFe42261012345678000190550010000012341000012345">
                    <ide><nNF>100</nNF><mod>55</mod></ide>
                    <emit><enderEmit><UF>SC</UF></enderEmit></emit>
                    <det nItem="1">
                      <prod><xProd>Serviço</xProd><vProd>1000.00</vProd></prod>
                      <imposto>
                        <IBSCBS>
                          <CST>01</CST>
                          <cClassTrib>000001</cClassTrib>
                          <vBC>1000.00</vBC>
                          <gCBS><vCBS>9.00</vCBS></gCBS>
                          <gIBSUF><vIBSUF>0.50</vIBSUF></gIBSUF>
                          <gIBSMun><vIBSMun>0.50</vIBSMun></gIBSMun>
                        </IBSCBS>
                      </imposto>
                    </det>
                  </infNFe></NFe>
                </nfeProc>
                """;

        MockMultipartFile file = new MockMultipartFile("arquivos", "nota.xml", "application/xml", xmlOk.getBytes());

        UUID escId = UUID.randomUUID();
        ReformaLote lote = service.processarUpload(escId, null, "2026-10", List.of(file));

        assertNotNull(lote);
        assertEquals(1, lote.getTotalNotas());
        assertEquals(1, lote.getTotalItens());
        assertEquals(1, lote.getTotalOk());
        assertEquals(0, lote.getTotalDivergencias());
        assertEquals(0, lote.getTotalErros());
    }
}

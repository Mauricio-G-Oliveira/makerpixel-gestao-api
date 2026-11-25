package com.makerpixel.contabil.fonte;

import com.makerpixel.contabil.domain.Empresa;
import com.makerpixel.contabil.reforma.LeitorNfeIbsCbs;
import com.makerpixel.contabil.reforma.LeitorNfeIbsCbs.NotaFiscalIbsCbs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class UploadFonte implements FonteDocumentos {

    private static final Logger log = LoggerFactory.getLogger(UploadFonte.class);

    @Override
    public String getNomeFonte() {
        return "UPLOAD";
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<DocumentoColetadoDTO> coletar(UUID escritorioId, Empresa empresa, Map<String, Object> parametros) {
        List<DocumentoColetadoDTO> lista = new ArrayList<>();
        if (parametros == null || !parametros.containsKey("arquivosXml")) {
            return lista;
        }

        List<String> xmls = (List<String>) parametros.get("arquivosXml");
        for (String xml : xmls) {
            try {
                NotaFiscalIbsCbs nf = LeitorNfeIbsCbs.lerXml(
                        new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))
                );

                if (nf.chave() != null && !nf.chave().isBlank()) {
                    lista.add(new DocumentoColetadoDTO(
                            nf.chave(),
                            nf.modelo() != null ? nf.modelo() : "55",
                            nf.serie(),
                            nf.numero(),
                            nf.dataEmissao() != null ? nf.dataEmissao() : OffsetDateTime.now(),
                            nf.valorTotal() != null ? nf.valorTotal() : BigDecimal.ZERO,
                            "AUTORIZADA",
                            null,
                            null,
                            xml,
                            getNomeFonte()
                    ));
                }
            } catch (Exception e) {
                log.error("Erro ao converter XML na UploadFonte: {}", e.getMessage());
            }
        }

        return lista;
    }
}

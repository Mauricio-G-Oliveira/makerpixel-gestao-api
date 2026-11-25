package com.makerpixel.contabil.fonte;

import com.makerpixel.contabil.domain.Empresa;
import com.makerpixel.contabil.domain.NotasyncDocumento;
import com.makerpixel.contabil.repository.NotasyncDocumentoRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class NotaSyncSefFonte implements FonteDocumentos {

    private final NotasyncDocumentoRepository notasyncDocumentoRepository;

    public NotaSyncSefFonte(NotasyncDocumentoRepository notasyncDocumentoRepository) {
        this.notasyncDocumentoRepository = notasyncDocumentoRepository;
    }

    @Override
    public String getNomeFonte() {
        return "NOTASYNC_SEF";
    }

    @Override
    public List<DocumentoColetadoDTO> coletar(UUID escritorioId, Empresa empresa, Map<String, Object> parametros) {
        if (empresa == null || empresa.getCnpj() == null) {
            return List.of();
        }

        List<NotasyncDocumento> docs = notasyncDocumentoRepository.findByEscritorioIdAndCnpj(
                escritorioId, empresa.getCnpj()
        );

        List<DocumentoColetadoDTO> coletados = new ArrayList<>();
        for (NotasyncDocumento doc : docs) {
            coletados.add(new DocumentoColetadoDTO(
                    doc.getChaveAcesso(),
                    doc.getModelo(),
                    doc.getSerie(),
                    doc.getNumero(),
                    doc.getDataEmissao(),
                    doc.getValorTotal(),
                    doc.getSituacao(),
                    doc.getMotivoCancelamento(),
                    doc.getStoragePath(),
                    null,
                    getNomeFonte()
            ));
        }

        return coletados;
    }
}

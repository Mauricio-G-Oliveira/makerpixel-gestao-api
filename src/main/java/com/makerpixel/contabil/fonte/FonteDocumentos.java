package com.makerpixel.contabil.fonte;

import com.makerpixel.contabil.domain.Empresa;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface FonteDocumentos {
    String getNomeFonte();
    List<DocumentoColetadoDTO> coletar(UUID escritorioId, Empresa empresa, Map<String, Object> parametros);
}

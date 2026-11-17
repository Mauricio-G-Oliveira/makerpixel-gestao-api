-- ==============================================================================
-- MakerPixel Central Contábil - Migration V3: Módulo NotaSync (NFC-e SEF/SC)
-- ==============================================================================

-- 1. Documentos Fiscais NFC-e (Modelo 65)
CREATE TABLE nfce_documento (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    escritorio_id UUID NOT NULL REFERENCES escritorio(id) ON DELETE CASCADE,
    empresa_id UUID REFERENCES empresa(id) ON DELETE SET NULL,
    chave VARCHAR(44) NOT NULL,
    cnpj_emitente VARCHAR(14) NOT NULL,
    numero INT NOT NULL,
    serie INT NOT NULL DEFAULT 1,
    data_emissao TIMESTAMP WITH TIME ZONE NOT NULL,
    valor_total NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    situacao VARCHAR(20) NOT NULL DEFAULT 'AUTORIZADA', -- AUTORIZADA, CANCELADA
    motivo_cancelamento TEXT,
    nsu BIGINT,
    origem VARCHAR(50) NOT NULL DEFAULT 'SEF_SC_WS',
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    atualizado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_nfce_documento_chave UNIQUE (escritorio_id, chave)
);

CREATE INDEX idx_nfce_escritorio ON nfce_documento(escritorio_id);
CREATE INDEX idx_nfce_cnpj_emitente ON nfce_documento(cnpj_emitente);
CREATE INDEX idx_nfce_data_emissao ON nfce_documento(data_emissao);
CREATE INDEX idx_nfce_situacao ON nfce_documento(situacao);
CREATE INDEX idx_nfce_busca ON nfce_documento(escritorio_id, cnpj_emitente, data_emissao);

-- 2. Histórico de Execuções e Consultas ao Web Service da SEF/SC
CREATE TABLE notasync_execucao (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    escritorio_id UUID NOT NULL REFERENCES escritorio(id) ON DELETE CASCADE,
    empresa_id UUID REFERENCES empresa(id) ON DELETE SET NULL,
    cnpj VARCHAR(14) NOT NULL,
    data_hora TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    cstat INT NOT NULL,
    xmotivo TEXT NOT NULL,
    qtd_documentos INT NOT NULL DEFAULT 0,
    duracao_ms BIGINT NOT NULL DEFAULT 0,
    ult_nsu BIGINT DEFAULT 0,
    status VARCHAR(30) NOT NULL DEFAULT 'OK',
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notasync_exec_escritorio ON notasync_execucao(escritorio_id);
CREATE INDEX idx_notasync_exec_cnpj ON notasync_execucao(cnpj);
CREATE INDEX idx_notasync_exec_data ON notasync_execucao(data_hora);

-- 3. Row Level Security (RLS) para Isolamento Multi-Tenant
ALTER TABLE nfce_documento ENABLE ROW LEVEL SECURITY;
ALTER TABLE notasync_execucao ENABLE ROW LEVEL SECURITY;

CREATE POLICY nfce_documento_tenant_isolation ON nfce_documento
    FOR ALL
    USING (
        escritorio_id = NULLIF(current_setting('app.current_escritorio_id', true), '')::uuid
        OR current_setting('app.is_admin_produto', true) = 'true'
    );

CREATE POLICY notasync_execucao_tenant_isolation ON notasync_execucao
    FOR ALL
    USING (
        escritorio_id = NULLIF(current_setting('app.current_escritorio_id', true), '')::uuid
        OR current_setting('app.is_admin_produto', true) = 'true'
    );

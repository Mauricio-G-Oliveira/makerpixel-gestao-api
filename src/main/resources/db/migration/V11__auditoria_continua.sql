-- ==============================================================================
-- Central Contábil v4.1 - Migration V11: Auditoria Contínua & Hub de Fontes
-- ==============================================================================

-- 1. Tabela unificada de documentos fiscais multi-fonte
CREATE TABLE IF NOT EXISTS documento_fiscal (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    escritorio_id UUID NOT NULL REFERENCES escritorio(id) ON DELETE CASCADE,
    empresa_id UUID REFERENCES empresa(id) ON DELETE CASCADE,
    chave_acesso VARCHAR(44) NOT NULL,
    modelo VARCHAR(5) NOT NULL DEFAULT '55', -- 55 (NF-e), 65 (NFC-e), 57 (CT-e)
    serie INT NOT NULL DEFAULT 1,
    numero INT NOT NULL,
    data_emissao TIMESTAMP WITH TIME ZONE NOT NULL,
    competencia VARCHAR(7) NOT NULL, -- AAAA-MM
    valor_total NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    situacao VARCHAR(20) NOT NULL DEFAULT 'AUTORIZADA', -- AUTORIZADA, CANCELADA
    motivo_cancelamento TEXT,
    fontes TEXT[] NOT NULL DEFAULT ARRAY['NOTASYNC_SEF']::TEXT[],
    storage_path VARCHAR(500),
    conteudo_xml TEXT,
    recebido_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    atualizado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_doc_fiscal_escritorio_chave UNIQUE (escritorio_id, chave_acesso)
);

CREATE INDEX IF NOT EXISTS idx_doc_fiscal_escritorio ON documento_fiscal(escritorio_id);
CREATE INDEX IF NOT EXISTS idx_doc_fiscal_empresa ON documento_fiscal(empresa_id);
CREATE INDEX IF NOT EXISTS idx_doc_fiscal_competencia ON documento_fiscal(escritorio_id, competencia);
CREATE INDEX IF NOT EXISTS idx_doc_fiscal_chave ON documento_fiscal(chave_acesso);
CREATE INDEX IF NOT EXISTS idx_doc_fiscal_situacao ON documento_fiscal(situacao);

-- 2. Fila de auditoria contínua pendente (desacoplada da ingestão)
CREATE TABLE IF NOT EXISTS auditoria_pendente (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    escritorio_id UUID NOT NULL REFERENCES escritorio(id) ON DELETE CASCADE,
    documento_id UUID NOT NULL REFERENCES documento_fiscal(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDENTE', -- PENDENTE, PROCESSANDO, CONCLUIDO, ERRO
    tentativas INT NOT NULL DEFAULT 0,
    proxima_tentativa TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    erro_mensagem TEXT,
    versao_calculadora VARCHAR(50),
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    atualizado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_auditoria_pendente_doc UNIQUE (escritorio_id, documento_id)
);

CREATE INDEX IF NOT EXISTS idx_auditoria_pendente_fila ON auditoria_pendente(status, proxima_tentativa);
CREATE INDEX IF NOT EXISTS idx_auditoria_pendente_escritorio ON auditoria_pendente(escritorio_id);

-- 3. Configuração da integração SIEG por escritório (cofre AES-256-GCM)
CREATE TABLE IF NOT EXISTS sieg_config (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    escritorio_id UUID NOT NULL UNIQUE REFERENCES escritorio(id) ON DELETE CASCADE,
    api_key_cifrada BYTEA NOT NULL,
    iv_dados BYTEA NOT NULL,
    ultimos_quatro VARCHAR(4) NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    ultima_busca TIMESTAMP WITH TIME ZONE,
    ultimo_status VARCHAR(50),
    ultimo_erro TEXT,
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    atualizado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_sieg_config_escritorio ON sieg_config(escritorio_id);

-- 4. Adiciona versão da calculadora e vínculo de documento na auditoria
ALTER TABLE reforma_lote
    ADD COLUMN IF NOT EXISTS versao_calculadora VARCHAR(50) DEFAULT '2026.01';

ALTER TABLE reforma_item
    ADD COLUMN IF NOT EXISTS versao_calculadora VARCHAR(50) DEFAULT '2026.01',
    ADD COLUMN IF NOT EXISTS documento_id UUID REFERENCES documento_fiscal(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_reforma_item_doc ON reforma_item(documento_id);

-- 5. Habilitar RLS (Row Level Security)
ALTER TABLE documento_fiscal ENABLE ROW LEVEL SECURITY;
ALTER TABLE auditoria_pendente ENABLE ROW LEVEL SECURITY;
ALTER TABLE sieg_config ENABLE ROW LEVEL SECURITY;

CREATE POLICY doc_fiscal_tenant_isolation ON documento_fiscal
    FOR ALL
    USING (
        escritorio_id = NULLIF(current_setting('app.current_escritorio_id', true), '')::uuid
        OR current_setting('app.is_admin_produto', true) = 'true'
    );

CREATE POLICY auditoria_pendente_tenant_isolation ON auditoria_pendente
    FOR ALL
    USING (
        escritorio_id = NULLIF(current_setting('app.current_escritorio_id', true), '')::uuid
        OR current_setting('app.is_admin_produto', true) = 'true'
    );

CREATE POLICY sieg_config_tenant_isolation ON sieg_config
    FOR ALL
    USING (
        escritorio_id = NULLIF(current_setting('app.current_escritorio_id', true), '')::uuid
        OR current_setting('app.is_admin_produto', true) = 'true'
    );

-- 6. Garantir remoção de módulos legados dos escritórios
UPDATE escritorio
SET modulos_ativos = array_remove(modulos_ativos, 'CONTROLE_HORAS')
WHERE 'CONTROLE_HORAS' = ANY(modulos_ativos);

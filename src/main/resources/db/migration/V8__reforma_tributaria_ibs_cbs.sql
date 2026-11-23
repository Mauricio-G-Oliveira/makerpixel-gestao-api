-- ==============================================================================
-- Central Contábil v3 - Migration V8: Módulo Reforma Tributária (IBS/CBS e Lotes)
-- ==============================================================================

-- 1. Lotes de Conferência da Reforma Tributária
CREATE TABLE IF NOT EXISTS reforma_lote (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    escritorio_id UUID NOT NULL REFERENCES escritorio(id) ON DELETE CASCADE,
    empresa_id UUID REFERENCES empresa(id) ON DELETE SET NULL,
    competencia VARCHAR(7), -- AAAA-MM
    origem VARCHAR(30) NOT NULL, -- UPLOAD_XML, UPLOAD_ZIP, NOTASYNC
    total_notas INT NOT NULL DEFAULT 0,
    total_itens INT NOT NULL DEFAULT 0,
    total_ok INT NOT NULL DEFAULT 0,
    total_divergencias INT NOT NULL DEFAULT 0,
    total_erros INT NOT NULL DEFAULT 0,
    total_sem_destaque INT NOT NULL DEFAULT 0,
    valor_total_itens NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    valor_total_cbs NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    valor_total_ibs NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    atualizado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_reforma_lote_escritorio ON reforma_lote(escritorio_id);
CREATE INDEX IF NOT EXISTS idx_reforma_lote_empresa ON reforma_lote(empresa_id);
CREATE INDEX IF NOT EXISTS idx_reforma_lote_criado_em ON reforma_lote(criado_em);

-- 2. Itens Auditados na Conferência
CREATE TABLE IF NOT EXISTS reforma_item (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lote_id UUID NOT NULL REFERENCES reforma_lote(id) ON DELETE CASCADE,
    escritorio_id UUID NOT NULL REFERENCES escritorio(id) ON DELETE CASCADE,
    chave_nfe VARCHAR(44) NOT NULL,
    numero_nota VARCHAR(20) NOT NULL,
    modelo VARCHAR(5) NOT NULL DEFAULT '55',
    data_emissao TIMESTAMP WITH TIME ZONE,
    n_item INT NOT NULL,
    codigo_produto VARCHAR(60),
    descricao TEXT NOT NULL,
    ncm VARCHAR(10),
    cfop VARCHAR(10),
    cst_informado VARCHAR(5),
    cst_calculado VARCHAR(5),
    cclass_trib VARCHAR(10),
    valor_item NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    base_calculo NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    cbs_destacado NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    cbs_calculado NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    ibs_uf_destacado NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    ibs_uf_calculado NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    ibs_mun_destacado NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    ibs_mun_calculado NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    diferenca_total NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    classificacao VARCHAR(30) NOT NULL, -- OK, DIVERGENCIA_VALOR, ENQUADRAMENTO_INVALIDO, SEM_GRUPO_IBSCBS, ERRO_CALCULO
    motivo_divergencia TEXT,
    memoria_calculo TEXT,
    fundamentacao_legal TEXT,
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_reforma_item_lote ON reforma_item(lote_id);
CREATE INDEX IF NOT EXISTS idx_reforma_item_escritorio ON reforma_item(escritorio_id);
CREATE INDEX IF NOT EXISTS idx_reforma_item_chave ON reforma_item(chave_nfe);
CREATE INDEX IF NOT EXISTS idx_reforma_item_classificacao ON reforma_item(classificacao);

-- ==============================================================================
-- MakerPixel Contábil - Migration V2: Conciliação Bancária e Perfis de Cliente
-- ==============================================================================

CREATE TABLE conciliacao_bancaria (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    escritorio_id UUID NOT NULL REFERENCES escritorio(id) ON DELETE CASCADE,
    empresa_id UUID REFERENCES empresa(id) ON DELETE SET NULL,
    cnpj VARCHAR(14) NOT NULL,
    banco VARCHAR(50) NOT NULL,
    agencia VARCHAR(20) NOT NULL DEFAULT '',
    conta VARCHAR(30) NOT NULL,
    competencia VARCHAR(7) NOT NULL, -- formato YYYY-MM
    extrato_json TEXT NOT NULL DEFAULT '[]',
    planilha_json TEXT NOT NULL DEFAULT '[]',
    conferidos_json TEXT NOT NULL DEFAULT '[]',
    juncoes_manuais_json TEXT NOT NULL DEFAULT '[]',
    observacoes TEXT,
    resumo_json TEXT NOT NULL DEFAULT '{}',
    ultimo_usuario_id UUID REFERENCES usuario(id) ON DELETE SET NULL,
    ultimo_usuario_nome VARCHAR(150) NOT NULL,
    versao BIGINT NOT NULL DEFAULT 1,
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    atualizado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_conciliacao UNIQUE (escritorio_id, cnpj, banco, agencia, conta, competencia)
);

CREATE INDEX idx_conciliacao_escritorio ON conciliacao_bancaria(escritorio_id);
CREATE INDEX idx_conciliacao_cnpj ON conciliacao_bancaria(cnpj);
CREATE INDEX idx_conciliacao_competencia ON conciliacao_bancaria(competencia);
CREATE INDEX idx_conciliacao_busca ON conciliacao_bancaria(escritorio_id, cnpj, competencia);

CREATE TABLE perfil_conciliacao (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    escritorio_id UUID NOT NULL REFERENCES escritorio(id) ON DELETE CASCADE,
    cnpj VARCHAR(14) NOT NULL,
    tolerancia_dias INT NOT NULL DEFAULT 2,
    palavras_chave_json TEXT NOT NULL DEFAULT '[]',
    conciliar_em_lote BOOLEAN NOT NULL DEFAULT TRUE,
    mapeamento_colunas_json TEXT NOT NULL DEFAULT '{}',
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    atualizado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_perfil_conciliacao UNIQUE (escritorio_id, cnpj)
);

CREATE INDEX idx_perfil_conciliacao_cnpj ON perfil_conciliacao(escritorio_id, cnpj);

-- RLS
ALTER TABLE conciliacao_bancaria ENABLE ROW LEVEL SECURITY;
ALTER TABLE perfil_conciliacao ENABLE ROW LEVEL SECURITY;

CREATE POLICY conciliacao_tenant_isolation ON conciliacao_bancaria
    FOR ALL
    USING (
        escritorio_id = NULLIF(current_setting('app.current_escritorio_id', true), '')::uuid
        OR current_setting('app.is_admin_produto', true) = 'true'
    );

CREATE POLICY perfil_conciliacao_tenant_isolation ON perfil_conciliacao
    FOR ALL
    USING (
        escritorio_id = NULLIF(current_setting('app.current_escritorio_id', true), '')::uuid
        OR current_setting('app.is_admin_produto', true) = 'true'
    );

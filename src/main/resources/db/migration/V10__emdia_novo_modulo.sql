-- ============================================================================
-- V10: Módulo Em Dia — Gestão Diária de Obrigações do Escritório Contábil
-- ============================================================================

-- 1. Campos adicionais na tabela empresa para compartilhamento com Em Dia e NotaSync
ALTER TABLE empresa
    ADD COLUMN IF NOT EXISTS tipo VARCHAR(30) DEFAULT 'LTDA',
    ADD COLUMN IF NOT EXISTS tem_funcionario BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS responsavel_setor_fiscal VARCHAR(150),
    ADD COLUMN IF NOT EXISTS responsavel_setor_contabil VARCHAR(150),
    ADD COLUMN IF NOT EXISTS responsavel_setor_dp VARCHAR(150);

-- 2. Modelos de Tarefa configuráveis pelo escritório
CREATE TABLE IF NOT EXISTS emdia_modelo_tarefa (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    escritorio_id UUID NOT NULL REFERENCES escritorio(id) ON DELETE CASCADE,
    nome VARCHAR(200) NOT NULL,
    setor VARCHAR(50) NOT NULL, -- FISCAL, CONTABIL, DP, GERAL
    dia_vencimento INT NOT NULL CHECK (dia_vencimento BETWEEN 1 AND 31),
    aplica_simples BOOLEAN NOT NULL DEFAULT TRUE,
    aplica_lucro_presumido BOOLEAN NOT NULL DEFAULT TRUE,
    aplica_lucro_real BOOLEAN NOT NULL DEFAULT TRUE,
    aplica_mei BOOLEAN NOT NULL DEFAULT TRUE,
    exige_funcionario BOOLEAN NOT NULL DEFAULT FALSE,
    tipo_empresa VARCHAR(50) DEFAULT 'TODOS',
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_emdia_modelo_escritorio ON emdia_modelo_tarefa(escritorio_id);

-- 3. Tarefas geradas mensalmente por empresa
CREATE TABLE IF NOT EXISTS emdia_tarefa (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    escritorio_id UUID NOT NULL REFERENCES escritorio(id) ON DELETE CASCADE,
    empresa_id UUID NOT NULL REFERENCES empresa(id) ON DELETE CASCADE,
    modelo_id UUID REFERENCES emdia_modelo_tarefa(id) ON DELETE SET NULL,
    nome VARCHAR(255) NOT NULL,
    setor VARCHAR(50) NOT NULL, -- FISCAL, CONTABIL, DP, GERAL
    mes_competencia VARCHAR(7) NOT NULL, -- AAAA-MM
    prazo DATE NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'A_FAZER', -- A_FAZER, EM_ANDAMENTO, AGUARDANDO_CLIENTE, CONCLUIDA, NAO_SE_APLICA
    responsavel VARCHAR(150),
    concluida_em TIMESTAMPTZ,
    concluida_por VARCHAR(150),
    origem_automatica VARCHAR(50), -- MANUAL, MODELO, NOTASYNC_8002, CERTIFICADO_VENCENDO, REFORMA_DIVERGENCIA
    chave_deduplicacao VARCHAR(255),
    criado_em TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_emdia_tarefa_dedup UNIQUE (escritorio_id, empresa_id, modelo_id, mes_competencia)
);

CREATE INDEX IF NOT EXISTS idx_emdia_tarefa_escritorio_mes ON emdia_tarefa(escritorio_id, mes_competencia);
CREATE INDEX IF NOT EXISTS idx_emdia_tarefa_empresa ON emdia_tarefa(empresa_id);
CREATE INDEX IF NOT EXISTS idx_emdia_tarefa_status ON emdia_tarefa(status);
CREATE INDEX IF NOT EXISTS idx_emdia_tarefa_chave_dedup ON emdia_tarefa(chave_deduplicacao);

-- 4. Anotações / Diário da empresa
CREATE TABLE IF NOT EXISTS emdia_anotacao (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    escritorio_id UUID NOT NULL REFERENCES escritorio(id) ON DELETE CASCADE,
    empresa_id UUID NOT NULL REFERENCES empresa(id) ON DELETE CASCADE,
    autor VARCHAR(150) NOT NULL,
    texto TEXT NOT NULL,
    anexo_url VARCHAR(500),
    anexo_nome VARCHAR(255),
    criado_em TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_emdia_anotacao_empresa ON emdia_anotacao(empresa_id, criado_em DESC);

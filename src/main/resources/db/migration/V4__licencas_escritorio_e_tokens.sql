-- ==============================================================================
-- Central Contábil v2.1 - Migration V4: Licenças com Validade, Histórico e Tokens
-- ==============================================================================

-- 1. Campos de validade e observações de contrato na tabela escritorio
ALTER TABLE escritorio
    ADD COLUMN IF NOT EXISTS acesso_ate TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS observacao TEXT;

-- Migra dados existentes: preenche acesso_ate com teste_expira_em ou padrão
UPDATE escritorio
SET acesso_ate = COALESCE(teste_expira_em, NOW() + INTERVAL '30 days')
WHERE acesso_ate IS NULL;

CREATE INDEX IF NOT EXISTS idx_escritorio_acesso_ate ON escritorio(acesso_ate);

-- 2. Tabela de Tokens do Agente NotaSync (armazenamento estrito por Hash SHA-256)
CREATE TABLE IF NOT EXISTS agente_token (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    escritorio_id UUID NOT NULL REFERENCES escritorio(id) ON DELETE CASCADE,
    token_hash VARCHAR(128) NOT NULL UNIQUE,
    prefixo VARCHAR(30) NOT NULL,
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    revogado_em TIMESTAMP WITH TIME ZONE,
    criado_por VARCHAR(120) DEFAULT 'MakerPixel'
);

CREATE INDEX IF NOT EXISTS idx_agente_token_hash ON agente_token(token_hash);
CREATE INDEX IF NOT EXISTS idx_agente_token_escritorio ON agente_token(escritorio_id);

-- 3. Histórico de Eventos de Licença (Trial, Contratos, Renovação, Módulos)
CREATE TABLE IF NOT EXISTS licenca_evento (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    escritorio_id UUID NOT NULL REFERENCES escritorio(id) ON DELETE CASCADE,
    tipo VARCHAR(40) NOT NULL, -- TESTE_CRIADO, TESTE_PRORROGADO, CONTRATO_ATIVADO, CONTRATO_RENOVADO, MODULOS_ALTERADOS, SUSPENSO, REATIVADO, ENCERRADO
    detalhe TEXT,
    modulos TEXT[],
    acesso_ate TIMESTAMP WITH TIME ZONE,
    autor VARCHAR(120) DEFAULT 'MakerPixel',
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_licenca_evento_escritorio ON licenca_evento(escritorio_id);
CREATE INDEX IF NOT EXISTS idx_licenca_evento_tipo ON licenca_evento(tipo);

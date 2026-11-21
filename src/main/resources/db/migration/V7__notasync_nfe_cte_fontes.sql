-- ==============================================================================
-- Central Contábil v3 - Migration V7: Fontes NF-e / CT-e e Certificado por Empresa
-- ==============================================================================

-- 1. Permite associar certificado digital A1 à própria empresa cliente no cofre
ALTER TABLE certificado_cofre
    ADD COLUMN IF NOT EXISTS empresa_id UUID REFERENCES notasync_empresa(id) ON DELETE CASCADE;

CREATE INDEX IF NOT EXISTS idx_cert_cofre_empresa ON certificado_cofre(empresa_id);

-- 2. Permite configurar fontes ativas por empresa (ex.: sef-sc-nfce, sef-sc-nfe, dfe-nacional)
ALTER TABLE notasync_empresa
    ADD COLUMN IF NOT EXISTS fontes_ativas TEXT[] DEFAULT ARRAY['sef-sc-nfce'],
    ADD COLUMN IF NOT EXISTS manifestacao_automatica BOOLEAN DEFAULT FALSE;

-- 3. Atualiza índices para buscas rápidas por fonte e empresa
CREATE INDEX IF NOT EXISTS idx_notasync_emp_ativo ON notasync_empresa(escritorio_id, ativo);

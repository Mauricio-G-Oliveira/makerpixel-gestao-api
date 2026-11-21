-- ==============================================================================
-- Central Contábil v3 - Migration V6: Cofre de Certificados A1 e NotaSync Nuvem
-- ==============================================================================

-- 1. Cofre de Certificados Digitais A1 (Criptografia em Envelope AES-256-GCM)
CREATE TABLE IF NOT EXISTS certificado_cofre (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    escritorio_id UUID NOT NULL REFERENCES escritorio(id) ON DELETE CASCADE,
    titular_mascarado VARCHAR(255) NOT NULL,
    documento_titular VARCHAR(30),
    tipo VARCHAR(30) NOT NULL DEFAULT 'E_CNPJ', -- E_CPF, E_CNPJ
    emissor VARCHAR(255) NOT NULL,
    valido_de TIMESTAMP WITH TIME ZONE NOT NULL,
    valido_ate TIMESTAMP WITH TIME ZONE NOT NULL,
    sha256_original VARCHAR(64) NOT NULL,
    dados_cifrados BYTEA NOT NULL,
    iv_dados BYTEA NOT NULL,
    dek_cifrada BYTEA NOT NULL,
    iv_dek BYTEA NOT NULL,
    versao_chave_mestra INT NOT NULL DEFAULT 1,
    termo_aceito_em TIMESTAMP WITH TIME ZONE NOT NULL,
    termo_aceito_por VARCHAR(120) NOT NULL,
    termo_versao VARCHAR(20) NOT NULL,
    enviado_por VARCHAR(120),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    atualizado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_cert_cofre_escritorio ON certificado_cofre(escritorio_id);
CREATE INDEX IF NOT EXISTS idx_cert_cofre_valido_ate ON certificado_cofre(valido_ate);
CREATE INDEX IF NOT EXISTS idx_cert_cofre_ativo ON certificado_cofre(ativo);

-- 2. Auditoria de Operações com Certificados Digitais
CREATE TABLE IF NOT EXISTS auditoria_cert (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    escritorio_id UUID NOT NULL REFERENCES escritorio(id) ON DELETE CASCADE,
    certificado_id UUID REFERENCES certificado_cofre(id) ON DELETE SET NULL,
    operacao VARCHAR(50) NOT NULL, -- UPLOAD, DECIFRAGEM_CONSULTA, EXCLUSAO, ROTACAO_CHAVE
    usuario VARCHAR(120),
    sucesso BOOLEAN NOT NULL,
    detalhe TEXT,
    ip VARCHAR(45),
    executado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_audit_cert_escritorio ON auditoria_cert(escritorio_id);
CREATE INDEX IF NOT EXISTS idx_audit_cert_data ON auditoria_cert(executado_em);

-- 3. Empresas Monitoradas pelo NotaSync
CREATE TABLE IF NOT EXISTS notasync_empresa (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    escritorio_id UUID NOT NULL REFERENCES escritorio(id) ON DELETE CASCADE,
    cnpj VARCHAR(14) NOT NULL,
    razao_social VARCHAR(255) NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    atualizado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_notasync_empresa_escritorio_cnpj UNIQUE (escritorio_id, cnpj)
);

CREATE INDEX IF NOT EXISTS idx_notasync_emp_escritorio ON notasync_empresa(escritorio_id);
CREATE INDEX IF NOT EXISTS idx_notasync_emp_cnpj ON notasync_empresa(cnpj);

-- 4. Estado de Consulta por Escritório x CNPJ x Fonte
CREATE TABLE IF NOT EXISTS notasync_estado (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    escritorio_id UUID NOT NULL REFERENCES escritorio(id) ON DELETE CASCADE,
    cnpj VARCHAR(14) NOT NULL,
    fonte VARCHAR(50) NOT NULL DEFAULT 'sef-sc-nfce',
    ult_nsu BIGINT NOT NULL DEFAULT 0,
    proxima_consulta TIMESTAMP WITH TIME ZONE,
    tentativas_iguais INT NOT NULL DEFAULT 0,
    ultimo_cstat INT,
    motivo TEXT,
    atualizado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_notasync_estado_fonte UNIQUE (escritorio_id, cnpj, fonte)
);

CREATE INDEX IF NOT EXISTS idx_notasync_estado_busca ON notasync_estado(escritorio_id, cnpj, fonte);
CREATE INDEX IF NOT EXISTS idx_notasync_estado_proxima ON notasync_estado(proxima_consulta);

-- 5. Documentos Armazenados na Nuvem (NotaSync Documento)
CREATE TABLE IF NOT EXISTS notasync_documento (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    escritorio_id UUID NOT NULL REFERENCES escritorio(id) ON DELETE CASCADE,
    cnpj VARCHAR(14) NOT NULL,
    chave_acesso VARCHAR(44) NOT NULL,
    modelo VARCHAR(10) NOT NULL DEFAULT '65',
    serie INT NOT NULL DEFAULT 1,
    numero INT NOT NULL,
    data_emissao TIMESTAMP WITH TIME ZONE NOT NULL,
    valor_total NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    situacao VARCHAR(20) NOT NULL DEFAULT 'AUTORIZADA', -- AUTORIZADA, CANCELADA
    motivo_cancelamento TEXT,
    nsu BIGINT DEFAULT 0,
    storage_path VARCHAR(500),
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    atualizado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_notasync_doc_escritorio_chave UNIQUE (escritorio_id, chave_acesso)
);

CREATE INDEX IF NOT EXISTS idx_notasync_doc_escritorio ON notasync_documento(escritorio_id);
CREATE INDEX IF NOT EXISTS idx_notasync_doc_cnpj ON notasync_documento(cnpj);
CREATE INDEX IF NOT EXISTS idx_notasync_doc_data ON notasync_documento(data_emissao);
CREATE INDEX IF NOT EXISTS idx_notasync_doc_chave ON notasync_documento(chave_acesso);

-- 6. Habilitar RLS (Row Level Security) para Multi-Tenant Isolado
ALTER TABLE certificado_cofre ENABLE ROW LEVEL SECURITY;
ALTER TABLE auditoria_cert ENABLE ROW LEVEL SECURITY;
ALTER TABLE notasync_empresa ENABLE ROW LEVEL SECURITY;
ALTER TABLE notasync_estado ENABLE ROW LEVEL SECURITY;
ALTER TABLE notasync_documento ENABLE ROW LEVEL SECURITY;

CREATE POLICY cert_cofre_tenant_isolation ON certificado_cofre
    FOR ALL
    USING (
        escritorio_id = NULLIF(current_setting('app.current_escritorio_id', true), '')::uuid
        OR current_setting('app.is_admin_produto', true) = 'true'
    );

CREATE POLICY auditoria_cert_tenant_isolation ON auditoria_cert
    FOR ALL
    USING (
        escritorio_id = NULLIF(current_setting('app.current_escritorio_id', true), '')::uuid
        OR current_setting('app.is_admin_produto', true) = 'true'
    );

CREATE POLICY notasync_emp_tenant_isolation ON notasync_empresa
    FOR ALL
    USING (
        escritorio_id = NULLIF(current_setting('app.current_escritorio_id', true), '')::uuid
        OR current_setting('app.is_admin_produto', true) = 'true'
    );

CREATE POLICY notasync_estado_tenant_isolation ON notasync_estado
    FOR ALL
    USING (
        escritorio_id = NULLIF(current_setting('app.current_escritorio_id', true), '')::uuid
        OR current_setting('app.is_admin_produto', true) = 'true'
    );

CREATE POLICY notasync_doc_tenant_isolation ON notasync_documento
    FOR ALL
    USING (
        escritorio_id = NULLIF(current_setting('app.current_escritorio_id', true), '')::uuid
        OR current_setting('app.is_admin_produto', true) = 'true'
    );

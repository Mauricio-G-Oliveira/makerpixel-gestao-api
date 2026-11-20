-- ==============================================================================
-- Central Contábil v3 - Migration V5: Planos de Assinatura, Limites e Storage
-- ==============================================================================

CREATE TABLE IF NOT EXISTS plano (
    id VARCHAR(50) PRIMARY KEY,
    nome VARCHAR(100) NOT NULL,
    descricao TEXT,
    preco_mensal NUMERIC(10, 2) DEFAULT 0.00,
    limite_usuarios INT NOT NULL,
    limite_empresas INT NOT NULL,
    limite_armazenamento_gb INT NOT NULL,
    modulos TEXT[] NOT NULL,
    destaque BOOLEAN DEFAULT FALSE,
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Inserção dos 3 planos padrão do Central Contábil v3
INSERT INTO plano (id, nome, descricao, preco_mensal, limite_usuarios, limite_empresas, limite_armazenamento_gb, modulos, destaque)
VALUES
('plano-essencial', 'Plano Essencial', 'Para escritórios contábeis individuais ou pequenas equipes em estruturação.', 297.00, 2, 20, 5, ARRAY['notasync', 'conciliacao', 'reforma', 'danfse', 'utilitarios', 'vencimentos', 'emdia', 'fiscal', 'horas', 'lucros'], FALSE),
('plano-escritorio', 'Plano Escritório', 'Ideal para escritórios consolidados com carteira ativa e equipe colaborativa.', 597.00, 5, 60, 20, ARRAY['notasync', 'conciliacao', 'reforma', 'danfse', 'utilitarios', 'vencimentos', 'emdia', 'fiscal', 'horas', 'lucros'], TRUE),
('plano-escritorio-plus', 'Plano Escritório+', 'Para médias e grandes operações contábeis com alto volume de notas e clientes.', 1197.00, 15, 180, 100, ARRAY['notasync', 'conciliacao', 'reforma', 'danfse', 'utilitarios', 'vencimentos', 'emdia', 'fiscal', 'horas', 'lucros'], FALSE)
ON CONFLICT (id) DO UPDATE SET
    limite_usuarios = EXCLUDED.limite_usuarios,
    limite_empresas = EXCLUDED.limite_empresas,
    limite_armazenamento_gb = EXCLUDED.limite_armazenamento_gb,
    modulos = EXCLUDED.modulos;

-- Adiciona campos de plano e armazenamento em escritorio
ALTER TABLE escritorio
    ADD COLUMN IF NOT EXISTS plano_id VARCHAR(50) REFERENCES plano(id),
    ADD COLUMN IF NOT EXISTS limite_armazenamento_gb INT DEFAULT 5;

-- Preenche plano padrão para escritórios já cadastrados
UPDATE escritorio
SET plano_id = 'plano-escritorio',
    limite_armazenamento_gb = 20
WHERE plano_id IS NULL;

-- ==============================================================================
-- Central Contábil v4.1.1 - Migration V12: Remover Default Fictício da Versão Calculadora
-- ==============================================================================

-- Remove default fictício '2026.01' das colunas de versão da calculadora
-- O valor deve provir exclusivamente da resposta oficial do motor RTC
ALTER TABLE reforma_lote ALTER COLUMN versao_calculadora DROP DEFAULT;
ALTER TABLE reforma_item ALTER COLUMN versao_calculadora DROP DEFAULT;

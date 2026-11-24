-- ==============================================================================
-- MakerPixel Contábil - Migration V9: Remoção de Tabelas de Módulos Descontinuados
-- Central Contábil v4 (Enxuto para Produção)
-- ==============================================================================

DROP TABLE IF EXISTS conciliacao_bancaria CASCADE;
DROP TABLE IF EXISTS perfil_conciliacao CASCADE;
DROP TABLE IF EXISTS faturamento_mensal CASCADE;
DROP TABLE IF EXISTS limite_faturamento_config CASCADE;
DROP TABLE IF EXISTS socio CASCADE;

# Changelog — Central Contábil API (makerpixel-gestao-api)

Todas as modificações e melhorias relevantes da API são registradas neste documento.

---

## [4.0.0] - 2026-11-24

### 🚀 Central Contábil API v4: Produto Enxuto, Em Dia Reescrito, Cron Agendado, LGPD e Deploy

#### Limpeza de Módulos Descontinuados
- **Remoção de Entidades e Endpoints:** Exclusão de controladores, serviços e repositórios de conciliação bancária legada, limites de faturamento e módulos legados.
- **Migration Flyway V9:** Criação de `V9__remover_tabelas_descontinuadas.sql` para remoção segura das tabelas obsoletas no banco Supabase Postgres.

#### Melhorias no NotaSync & Reforma Tributária
- **Cofre Seguro de Certificados A1:** Criptografia de envelope AES-256-GCM.
- **Exportação de Planilhas via API:** Adicionado endpoint `GET /api/v1/notasync/empresas/{cnpj}/mes/{anoMes}/excel` para geração e download de planilha estruturada de notas do mês diretamente pelo servidor.
- **Auditoria IBS/CBS (LC 214/2025):** Integração com a Calculadora RTC como microsserviço privado.

#### Novo Módulo Em Dia (Reescrito do Zero)
- **Migration Flyway V10:** Criação de `V10__emdia_novo_modulo.sql` com as tabelas `emdia_modelo_tarefa`, `emdia_tarefa`, `emdia_anotacao` e colunas estendidas em `empresa` (`tipo`, `tem_funcionario`, responsáveis por setor).
- **Geração Idempotente de Tarefas:** Endpoint `POST /api/v1/emdia/gerar-mes` com regras de aplicação por regime tributário e exigência de funcionário.
- **Tarefas Automáticas Preventivas:** Deduplicação inteligente de eventos:
  - SAT 8002: *Vincular contador no SAT – empresa X*;
  - Certificado A1 vencendo em até 30 dias: *Renovar Certificado Digital A1*;
  - Divergência da Reforma: *Revisar divergências IBS/CBS – mês Y*.
- **Endpoint Agendado para Cron:** Adicionado `POST /interno/emdia/gerar-mes-cron` protegido pelo cabeçalho `X-Notasync-Cron-Secret` para geração autônoma de rotinas mensais.

#### Garantias LGPD (Artigos 17 e 18)
- **Portabilidade de Dados (`GET /api/v1/escritorio/lgpd/exportar-dados`):** Exporta arquivo compactado ZIP contendo todos os dados do escritório (empresas, tarefas, usuários, metadados de certificados e notas) em formato JSON.
- **Direito ao Esquecimento (`POST /api/v1/escritorio/lgpd/excluir-escritorio`):** Exclusão permanente de todo o acervo do escritório, com dupla confirmação exigindo o CNPJ cadastrado.

#### Deploy em Produção (Render & Supabase)
- **Render Blueprint (`render.yaml`):** Especificação completa para deploy automatizado da API Java 21 Web Service, Calculadora RTC Private Service e Cron Jobs periódicos.
- **Health Check (`/health` e `/api/health`):** Rota pública retornando status 200 UP e versão da API para monitoramento gratuito via UptimeRobot.
- **CORS Configurável:** Restrição estrita para origens de produção (`https://app.makerpixeltech.com.br`).

# Central Contábil API v4.0.0 — MakerPixel

Backend RESTful corporativo desenvolvido em **Java 21** e **Spring Boot 3.3.4** para escritórios de contabilidade, fornecendo autenticação JWT, gestão multi-tenant com PostgreSQL Row Level Security (RLS), buscador de DF-e na SEF/SC (**NotaSync**), cofre seguro de certificados A1 (AES-256-GCM), auditoria da Reforma Tributária (IBS/CBS - LC 214/2025), gestão diária de rotinas (**Em Dia**) e conformidade com a LGPD.

---

## 🌐 Links Oficiais
- **Site Institucional & Apresentação:** [makerpixeltech.com.br](https://makerpixeltech.com.br)
- **Aplicação Web (SPA):** [app.makerpixeltech.com.br](https://app.makerpixeltech.com.br)
- **Documentação de Deploy:** [docs/deploy-guia.md](file:///Users/mauricio/Desktop/gestao%20financeira/makerpixel-gestao-api/docs/deploy-guia.md)
- **Roteiro do Piloto:** [docs/roteiro-piloto.md](file:///Users/mauricio/Desktop/gestao%20financeira/makerpixel-gestao-api/docs/roteiro-piloto.md)

---

## 🚦 Status do Produto (Transparência Operacional)

| Módulo / Recurso | Status | Descrição |
| :--- | :---: | :--- |
| **NotaSync Nuvem & Cofre A1** | **Produção** | Upload de certificados A1 (AES-256-GCM), monitoramento de empresas, busca contínua na SEF/SC, download em lote de XMLs (ZIP) e diagnósticos com tradução de erros (ex.: cStat 8002). |
| **Auditor Reforma Tributária (LC 214/2025)** | **Produção** | Conferência item a item de IBS/CBS, alíquotas de teste de 2026, modal lado a lado com fundamentação legal e integração com a Calculadora RTC. |
| **DANFSe Nacional (WASM)** | **Produção** | Emissão e visualização de Documento Auxiliar da NFS-e 100% no navegador (zero envio de XML a servidores externos). |
| **XML → Excel Universal** | **Produção** | Conversor de notas e itens para planilhas XLSX estruturadas, direto no navegador ou via endpoint de streaming da API. |
| **Em Dia (Rotinas e Obrigações)** | **Piloto** | Geração mensal idempotente de tarefas, modelos customizáveis, diário da empresa e criação de tarefas preventivas automáticas. |
| **Admin MakerPixel** | **Produção** | Criação de testes de 30 dias, gestão de planos e limites, prorrogações, conversões e suspensão imediata de escritórios. |
| **Garantias LGPD (Artigos 17 e 18)** | **Produção** | Exportação completa dos dados do escritório em arquivo ZIP estruturado e expurgo definitivo com dupla confirmação do CNPJ. |
| **Modo Demonstração** | **Demonstração** | Flag `VITE_MODO_DEMO=true` no front para visualização com sementes fictícias em memória sem conexão obrigatória à API. |

---

## 🛠️ Tecnologias Utilizadas

- **Linguagem:** Java 21 (LTS)
- **Framework:** Spring Boot 3.3.4 (Spring Web, Security, Data JPA, Validation)
- **Segurança:** Spring Security 6 + JWT (jjwt 0.12.6), senhas com BCrypt e cofre AES-256-GCM
- **Persistência:** PostgreSQL 16 gerenciado no Supabase com Flyway Migrations (V1 a V10)
- **Integrações Oficiais:**
  - Calculadora RTC (Receita Federal / LC 214/2025) via microsserviço privado em container Docker
  - BrasilAPI (consulta pública de CNPJ com cache de 24h)
  - SEF/SC Web Service para Contadores (Boletim Técnico 2026/003)
- **Deploy:** Render Web Service (API) e Render Private Service (RTC) via `render.yaml`

---

## 📋 Endpoints Principais

### Autenticação & Sessão (`/api/auth`)
- `POST /api/auth/login` — Autenticação de usuário com email e senha; retorna Bearer Token JWT.
- `GET /api/auth/me` — Dados do usuário logado e permissões de acesso.
- `POST /api/auth/trocar-senha` — Troca de senha provisória ou redefinição de credencial.

### Módulo NotaSync — DF-e & Cofre A1 (`/api/v1/notasync`, `/api/v1/certificados`)
- `POST /api/v1/certificados/upload` — Upload multipart de certificado A1 criptografado com termo aceito.
- `GET /api/v1/certificados/ativo` — Metadados do certificado ativo (CNPJ, validade, titular mascarado).
- `DELETE /api/v1/certificados/{id}` — Remoção segura do certificado.
- `GET /api/v1/notasync/empresas` — Lista e gerencia empresas com monitoramento de DF-e ativo.
- `POST /api/v1/notasync/buscar-agora/{cnpj}` — Disparo manual de busca com controle de rate limit (429).
- `GET /api/v1/notasync/documentos` — Listagem com filtros por empresa, competência e canceladas.
- `GET /api/v1/notasync/empresas/{cnpj}/mes/{anoMes}/zip` — Stream de download do ZIP com os XMLs do mês.
- `GET /api/v1/notasync/empresas/{cnpj}/mes/{anoMes}/excel` — Stream de exportação da planilha XLSX/CSV do mês.
- `GET /api/v1/notasync/execucoes` — Histórico de consultas com cStat traduzido e diagnóstico.

### Módulo Reforma Tributária — IBS/CBS (`/api/v1/reforma`)
- `POST /api/v1/reforma/conferir/notasync` — Auditoria em lote de itens de documentos da competência contra LC 214/2025.
- `GET /api/v1/reforma/conferir/lotes` — Listagem dos lotes auditados por competência.
- `GET /api/v1/reforma/versao` — Versão ativa do motor da Calculadora RTC da Receita Federal.
- `GET /api/v1/reforma/conferir/lotes/{loteId}/exportar-csv` — Exportação em planilha das divergências de cálculo.

### Módulo Em Dia — Rotinas e Obrigações (`/api/v1/emdia`)
- `POST /api/v1/emdia/gerar-mes` — Geração mensal idempotente de tarefas a partir dos modelos do escritório.
- `GET /api/v1/emdia/tarefas` — Listagem com filtros por competência, status e responsável.
- `GET /api/v1/emdia/minhas-tarefas` — Painel do usuário logado (Hoje, Atrasadas, Da Semana).
- `GET /api/v1/emdia/painel-mes` — Métricas consolidadas (em dia x atrasadas x aguardando cliente) por setor e responsável.
- `GET /api/v1/emdia/modelos` — Listagem e edição dos modelos de obrigações do escritório.
- `POST /api/v1/emdia/anotacoes` — Criação de notas no Diário da Empresa.

### Endpoints Agendados (Cron Jobs Internos)
- `POST /interno/notasync/rodar` — Ciclo periódico do coletor NotaSync (protegido por `X-Notasync-Cron-Secret`).
- `POST /interno/emdia/gerar-mes-cron` — Ciclo mensal do gerador de tarefas do Em Dia (protegido por `X-Notasync-Cron-Secret`).

### Privacidade & LGPD (`/api/v1/escritorio/lgpd`)
- `GET /api/v1/escritorio/lgpd/exportar-dados` — Exportação completa em ZIP (Art. 18 LGPD) contendo JSONs de todos os dados do escritório.
- `POST /api/v1/escritorio/lgpd/excluir-escritorio` — Exclusão permanente do escritório e expurgo atômico de dados (Art. 17 LGPD), com confirmação obrigatória de CNPJ.

### Monitoramento de Saúde
- `GET /health` e `GET /api/health` — Endpoint público de health check (retorna 200 UP e versão 4.0.0).

---

## 🧭 Roadmap

- Conciliação bancária e importação de planilhas: em estudo, a serem desenvolvidas de forma genérica.

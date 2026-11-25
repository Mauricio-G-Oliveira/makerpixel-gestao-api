# Central Contábil API v4.1.0 — MakerPixel

Backend RESTful corporativo desenvolvido em **Java 21** e **Spring Boot 3.3.4** para escritórios de contabilidade, fornecendo autenticação JWT, gestão multi-tenant com PostgreSQL Row Level Security (RLS), buscador de DF-e na SEF/SC (**NotaSync**), integração **SIEG** (NF-e 55 e CT-e 57), cofre seguro AES-256-GCM, **Auditoria Contínua** da Reforma Tributária (IBS/CBS - LC 214/2025), gestão diária de rotinas (**Em Dia**) e conformidade com a LGPD.

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
| **Auditoria Contínua IBS/CBS** | **Produção** | As notas entram automaticamente por múltiplas fontes, enfileiram em segundo plano e são auditadas de forma autônoma e idempotente pela Calculadora RTC. Painel com semáforo (🟢🟡🔴) e resumo das últimas 24h. |
| **Hub Multi-Fontes (`documento_fiscal`)** | **Produção** | Deduplicação por chave de acesso única por escritório (`NOTASYNC_SEF`, `SIEG`, `UPLOAD`) com atualização em caso de cancelamento posterior. |
| **Integração SIEG** | **Produção** | Coleta de NF-e (55) e CT-e (57) com chave de API criptografada no cofre AES-256-GCM, sem exposição de credenciais em logs ou tela. |
| **NotaSync Nuvem & Cofre A1** | **Produção** | Upload de certificados A1 (AES-256-GCM), monitoramento de empresas, busca contínua na SEF/SC, download em lote de XMLs (ZIP) e diagnóstico de erro SAT 8002. |
| **Em Dia (Rotinas e Obrigações)** | **Piloto** | Geração mensal idempotente de tarefas, modelos customizáveis, diário da empresa e criação de tarefas preventivas automáticas de divergência IBS/CBS. |
| **DANFSe Nacional (WASM)** | **Produção** | Emissão e visualização de Documento Auxiliar da NFS-e 100% no navegador (zero envio de XML a servidores externos). |
| **XML → Excel Universal** | **Produção** | Conversor de notas e itens para planilhas XLSX estruturadas, direto no navegador ou via endpoint de streaming da API. |
| **Admin MakerPixel** | **Produção** | Criação de testes de 30 dias, gestão de planos e limites, prorrogações, conversões e suspensão imediata de escritórios. |
| **Garantias LGPD (Artigos 17 e 18)** | **Produção** | Exportação completa dos dados do escritório em arquivo ZIP estruturado e expurgo definitivo com dupla confirmação do CNPJ. |
| **Modo Demonstração** | **Demonstração** | Flag `VITE_MODO_DEMO=true` no front para visualização com sementes fictícias em memória sem conexão obrigatória à API. |

---

## ⚙️ O que é Automático vs. o que Depende de Configuração

### 🤖 O que roda 100% automático:
1. **Coleta de NFC-e na SEF/SC:** execução diária às 04:00 via NotaSync Nuvem.
2. **Coleta de NF-e e CT-e na SIEG:** execução diária às 04:30 via pipeline agendado.
3. **Deduplicação de Documentos:** notas recebidas de mais de uma fonte viram um único registro com lista de fontes agregada.
4. **Auditoria Contínua:** conferência diária às 05:00 pela Calculadora RTC dos itens novos (com idempotência por chave + versão).
5. **Geração e Atualização de Tarefas no Em Dia:** às 06:00, tarefas preventivas de divergências IBS/CBS e erro SAT 8002 são criadas ou atualizadas sem duplicações.

### 🔑 O que depende de configuração do escritório:
- **Certificado Digital A1 no Cofre:** necessário para o NotaSync autenticar perante a SEF/SC.
- **Vínculo SAT na SEF/SC:** o contador precisa estar cadastrado e autorizado pela empresa no portal SAT de Santa Catarina (caso contrário, a SEF retorna cStat 8002).
- **Chave de API da SIEG:** informada pelo escritório na tela *Integrações > SIEG* (armazenada de forma cifrada no cofre AES-256-GCM).

### 📊 Limites por Plano:
- Documentos conferidos automaticamente no mês (limite padrão: 5.000 documentos/mês).
- Ao atingir o limite mensal, as pendências permanecem na fila aguardando o próximo ciclo ou upgrade de plano.

---

## 🔒 Variáveis de Ambiente

| Variável | Obrigatória | Finalidade |
| :--- | :---: | :--- |
| `SPRING_DATASOURCE_URL` | Sim | URL de conexão PostgreSQL/Supabase com SSL (`sslmode=require`) |
| `SPRING_DATASOURCE_USERNAME` | Sim | Usuário do banco de dados |
| `SPRING_DATASOURCE_PASSWORD` | Sim | Senha do banco de dados |
| `CENTRAL_MASTER_KEY` | Sim | Chave-mestra AES-256 de 32 bytes em Base64 para cifrar o cofre (certificados e chaves de API da SIEG) |
| `JWT_SECRET` | Sim | Segredo criptográfico para assinatura e validação dos tokens JWT |
| `NOTASYNC_CRON_SECRET` | Sim | Segredo compartilhado via header `X-Notasync-Cron-Secret` para autorização dos endpoints internos do Render |
| `CORS_ALLOWED_ORIGINS` | Não | Lista de origens autorizadas (ex.: `https://app.makerpixeltech.com.br`) |
| `RTC_CALCULADORA_URL` | Não | URL do serviço privado da Calculadora RTC (padrão `http://central-contabil-calculadora-rtc:8088`) |
| `TZ` | Não | Fuso horário padrão da aplicação (`America/Sao_Paulo`) |

---

## 🗄️ Histórico de Migrations Flyway

1. `V1__init_makerpixel.sql` — Estrutura base de escritórios, usuários e empresas.
2. `V2__adicionar_indices_performance.sql` — Otimização de consultas frequentes.
3. `V3__modulos_v3_reforma_danfse.sql` — Suporte aos módulos da Reforma e DANFSe.
4. `V4__notasync_fase2_tabelas.sql` — Tabelas de controle de lotes e estados do NotaSync.
5. `V5__notasync_fase3_certificados.sql` — Cofre de certificados com AES-256-GCM.
6. `V6__notasync_fase4_empresas.sql` — Gestão e monitoramento de empresas clientes.
7. `V7__reforma_lote_itens.sql` — Persistência de lotes e itens auditados da Reforma.
8. `V8__notasync_fase6_fontes.sql` — Suporte a fontes NF-e (55) e CT-e (57).
9. `V9__remover_tabelas_descontinuadas.sql` — Expurgado acervo de módulos descontinuados.
10. `V10__emdia_novo_modulo.sql` — Novo módulo Em Dia reescrito com geração idempotente.
11. `V11__auditoria_continua.sql` — Hub multi-fonte (`documento_fiscal`), fila `auditoria_pendente`, integração `sieg_config`, RLS e limpeza de resíduos legados.

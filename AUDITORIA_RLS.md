# Auditoria e Arquitetura de Isolamento Multi-Tenant (RLS)

**Central Contábil v4.1.1**  
*Documento de Conformidade e Segurança Técnica*

---

## 1. Visão Geral do Isolamento Multi-Tenant

O **Central Contábil** foi desenhado com arquitetura multi-tenant por escritório contábil (`escritorio_id`). A integridade e a segregação dos dados entre escritórios é garantida por meio de um modelo de **defesa em profundidade**.

---

## 2. Status Honesto da Implementação do Row Level Security (RLS)

| Camada | Mecanismo | Status Atual (v4.1.1) | Descrição |
|---|---|---|---|
| **Aplicação (Spring Boot)** | Queries Parametrizadas (JPQL / JPA / Native) | **100% Ativo & Auditado** | 100% dos repositórios e serviços filtram explicitamente por `escritorio_id` obtido do JWT autenticado via `TenantContext`. |
| **Banco de Dados (PostgreSQL)** | Políticas RLS (`ENABLE ROW LEVEL SECURITY`) | **Configurado no DDL** | Tabelas como `documento_fiscal`, `auditoria_pendente` e `sieg_config` possuem `ENABLE ROW LEVEL SECURITY` e políticas `USING (escritorio_id = NULLIF(current_setting('app.current_escritorio_id', true), '')::uuid)`. |
| **Pool de Conexões (HikariCP)** | Interceptor de Sessão `SET LOCAL` | **Em Implementação (v4.2)** | As conexões do pool Spring Boot utilizam um usuário comum de banco (`makerpixel`). A execução de `SET LOCAL app.current_escritorio_id` por transação está planejada para a versão v4.2. |

> [!NOTE]
> **Transparência Técnica:** No estado atual (v4.1.1), o isolamento entre escritórios na aplicação é garantido rigorosamente na camada de serviços e consultas SQL/JPQL (`escritorio_id = :escritorioId`). As políticas RLS no PostgreSQL atuam como camada de defesa complementar para acessos diretos administrativos (ex.: DBeaver, psql ou dumps).

---

## 3. Armazenamento Seguro de Documentos Fiscais

1. **Metadados no PostgreSQL:**  
   As tabelas `documento_fiscal` e `reforma_item` armazenam estritamente chaves de acesso, números de série, valores e divergências detectadas.
2. **XML Exclusivo no Storage Privado (Supabase Storage):**  
   O arquivo XML bruto da NF-e/CT-e é enviado para bucket privado protegido, sendo a coluna `conteudo_xml` mantida como `NULL` para economia de volume no banco relacional.
3. **Cofre de Credenciais (AES-256-GCM):**  
   Chaves de terceiros (ex.: SIEG, SEF) são criptografadas em cofre simétrico antes da gravação no banco, nunca sendo expostas em logs nem transitando em texto claro.

---

## 4. Próximos Passos (Roadmap v4.2)

- Interceptor Spring TransactionSynchronization ou Hikari ConnectionCustomizer para emissão automática de `SET LOCAL app.current_escritorio_id = :escritorioId` em cada transação de tenant.

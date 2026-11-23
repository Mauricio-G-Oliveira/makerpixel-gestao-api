# Central Contábil API — by MakerPixel

Backend RESTful corporativo desenvolvido em **Java 21** e **Spring Boot 3.3.4** para escritórios de contabilidade, fornecendo autenticação JWT, gestão multi-tenant com PostgreSQL Row Level Security (RLS), buscador automatizado de NFC-e SEF/SC (**NotaSync**), conciliação bancária inteligente, auditoria tributária e controle fiscal.

---

## 🛠️ Tecnologias Utilizadas

- **Linguagem:** Java 21 (LTS)
- **Framework:** Spring Boot 3.3.4 (Spring Web, Security, Data JPA, Validation)
- **Segurança:** Spring Security 6 + JWT (jjwt 0.12.6) com senhas criptografadas em BCrypt
- **Persistência:** Spring Data JPA / Hibernate com PostgreSQL 16
- **Migrações de Banco:** Flyway (V1 a V8 — incluindo Cofre Seguro A1, Licenças, Planos e Auditoria IBS/CBS da Reforma Tributária)
- **Integrações Oficiais:**
  - Calculadora RTC (Receita Federal / LC 214/2025) via serviço desacoplado
  - BrasilAPI (consulta pública de CNPJ com cache local de 24h)
  - SEF/SC Web Service para Contadores (Boletim Técnico 2026/003, v1.00-1)
- **Containerização:** Docker e Docker Compose

---

## 📋 Endpoints Principais

### Autenticação & Sessão (`/api/v1/auth`)
- `POST /api/v1/auth/login` — Autenticação de usuário com email e senha; retorna Bearer Token JWT.
- `GET /api/v1/auth/me` — Dados do usuário logado e permissões de acesso.
- `POST /api/v1/auth/trocar-senha` — Troca de senha provisória ou redefinição.
- `POST /api/v1/auth/usuarios` — Cadastro de novo usuário do escritório (acesso administrativo).

### Módulo Reforma Tributária — IBS/CBS (`/api/v1/reforma`)
- `POST /api/v1/reforma/auditar` — Auditoria em lote de itens de NF-e, NFC-e e NFS-e contra as regras oficiais da LC 214/2025 e NT 2025.002.
- `GET /api/v1/reforma/lotes` — Listagem dos lotes auditados por competência.
- `GET /api/v1/reforma/lotes/{id}` — Detalhamento do lote com comparativo lado a lado e status de auditoria.
- `GET /api/v1/reforma/versao-rtc` — Versão ativa do motor da Calculadora RTC.

### Módulo NotaSync — NFC-e SEF/SC (`/api/v1/notasync`)
- `POST /api/v1/notasync/lote` — Endpoint receptor de metadados fiscais enviados pelo agente local, autenticado com token do escritório e com **deduplicação estrita pela chave de acesso**.
- `GET /api/v1/notasync/resumo` — Resumo de métricas (total de notas, faturamento emitido, canceladas, pendências SAT).
- `GET /api/v1/notasync/documentos` — Listagem paginada de NFC-e filtradas por CNPJ emitente e situação.
- `GET /api/v1/notasync/execucoes` — Histórico de consultas do agente à SEF/SC com tempo de resposta e cStat traduzido.
- `POST /notasync-simulador/ws/distribuicao/DistribuicaoNfceDownload.asmx` — Simulador SOAP da SEF/SC que devolve lotes compactados em gzip+base64 de notas fiscais fictícias para testes e demonstrações.

### Conciliação Bancária (`/api/v1/conciliacoes`)
- `GET /api/v1/conciliacoes` — Lista conciliações por empresa (CNPJ) e competência.
- `GET /api/v1/conciliacoes/{id}` — Carrega o resumo, extrato e lançamentos conferidos.
- `POST /api/v1/conciliacoes` — Salva ou atualiza conciliação com controle de versão otimista (`version`). Em caso de conflito concorrente, retorna `409 Conflict`.
- `DELETE /api/v1/conciliacoes/{id}` — Remove conciliação salva.

### Escritórios Contábeis & Assinaturas (`/api/v1/escritorios`)
- `GET /api/v1/escritorios` — Listagem de escritórios contábeis cadastrados.
- `POST /api/v1/escritorios/trial` — Criação de acesso de teste gratuito (trial) com expiração programada.
- `POST /api/v1/escritorios/{id}/converter` — Conversão de escritório em teste para cliente ativo.
- `POST /api/v1/escritorios/{id}/suspender` — Suspensão de acesso do escritório.
- `POST /api/v1/escritorios/{id}/reativar` — Reativação de acesso do escritório.

### Empresas & Clientes (`/api/v1/empresas`)
- `GET /api/v1/empresas` — Lista as empresas clientes do escritório ativo.
- `POST /api/v1/empresas` — Cadastra ou atualiza empresa cliente com dados societários.
- `GET /api/v1/empresas/{id}` — Ficha cadastral detalhada da empresa.
- `GET /api/v1/receita/cnpj/{cnpj}` — Consulta dados oficiais do CNPJ na BrasilAPI com cache de 24 horas.

---

## 🚀 Como Rodar Localmente

### 1. Pré-requisitos
- JDK 21 instalado
- Docker e Docker Compose (para subir o banco PostgreSQL)

### 2. Configurar Variáveis de Ambiente
Copie o arquivo de exemplo e ajuste se necessário:
```bash
cp .env.example .env
```

### 3. Subir o Banco de Dados com Docker
```bash
docker compose up -d
```
O PostgreSQL iniciará na porta `5432` com banco `makerpixel_contabil`. As migrações do Flyway (`V1`, `V2`, `V3__notasync_nfce.sql`) serão executadas automaticamente na inicialização da aplicação.

### 4. Executar a Aplicação
No Linux/macOS:
```bash
./mvnw spring-boot:run
```
No Windows:
```bash
mvnw.cmd spring-boot:run
```

A API estará acessível em: `http://localhost:8080/`

---

## ☁️ Deploy no Supabase e Render

### 1. Banco de Dados no Supabase
1. Crie um projeto gratuito no [Supabase](https://supabase.com/);
2. Em **Project Settings > Database**, copie a URI de conexão (Pooler ou Direct connection):
   ```
   jdbc:postgresql://db.<PROJECT_REF>.supabase.co:5432/postgres?sslmode=require
   ```
3. O Flyway executará as migrações `V1`, `V2` e `V3` automaticamente ao inicializar a API.

### 2. Deploy da API no Render
1. Conecte o repositório `makerpixel-gestao-api` no [Render](https://render.com/) como **Web Service**;
2. Configure o runtime como **Docker** (utilizando o `Dockerfile` existente no projeto) ou **Java / Maven**:
   - **Build Command:** `./mvnw clean package -DskipTests`
   - **Start Command:** `java -jar target/makerpixel-gestao-api-0.0.1-SNAPSHOT.jar`
3. Configure as variáveis de ambiente no painel do Render:
   - `SPRING_DATASOURCE_URL`: a URL JDBC do Supabase
   - `SPRING_DATASOURCE_USERNAME`: `postgres`
   - `SPRING_DATASOURCE_PASSWORD`: sua senha do Supabase
   - `JWT_SECRET`: uma chave de pelo menos 256 bits para assinatura dos tokens
   - `PORT`: `8080`

---

## 🔒 Segurança & LGPD

- Senhas nunca são armazenadas em texto plano (BCrypt com salt);
- Chaves privadas e certificados A1 **permanecem no agente local**;
- Isolamento multi-tenant gerenciado pelo contexto de requisição (`TenantContext`) e PostgreSQL RLS;
- Dados 100% fictícios no demo e testes.

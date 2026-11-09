# MakerPixel Gestão API

Backend RESTful desenvolvido em **Java 21** e **Spring Boot 3.3.4** para escritórios de contabilidade, fornecendo autenticação JWT, gestão multi-tenant de escritórios e empresas, consulta de CNPJ via BrasilAPI, controle de limites fiscais para MEI e Simples Nacional e armazenamento de conciliações bancárias com controle de versão otimista.

---

## 🛠️ Tecnologias Utilizadas

- **Linguagem:** Java 21 (LTS)
- **Framework:** Spring Boot 3.3.4
- **Segurança:** Spring Security 6 + JWT (jjwt 0.12.6) com senhas criptografadas em BCrypt
- **Persistência:** Spring Data JPA / Hibernate com PostgreSQL
- **Migrações de Banco:** Flyway
- **Integrações Externas:** BrasilAPI (consulta pública de CNPJ com cache local)
- **Containerização:** Docker e Docker Compose

---

## 📋 Endpoints Principais

### Autenticação & Sessão (`/api/v1/auth`)
- `POST /api/v1/auth/login` — Autenticação de usuário com email e senha; retorna Bearer Token JWT.
- `GET /api/v1/auth/me` — Dados do usuário logado e permissões de acesso.
- `POST /api/v1/auth/trocar-senha` — Troca de senha provisória ou redefinição.
- `POST /api/v1/auth/usuarios` — Cadastro de novo usuário do escritório (acesso administrativo).

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

### Consulta Receita / BrasilAPI (`/api/v1/receita`)
- `GET /api/v1/receita/cnpj/{cnpj}` — Consulta dados oficiais do CNPJ na BrasilAPI com cache no banco local de 24 horas.

### Limites MEI e Simples Nacional (`/api/v1/limites`)
- `GET /api/v1/limites/alertas` — Relatório consolidado de empresas do MEI ou Simples com faturamento no limite ou com risco de estouro proporcional.

### Conciliação Bancária (`/api/v1/conciliacoes`)
- `GET /api/v1/conciliacoes` — Lista conciliações por empresa (CNPJ) e competência.
- `GET /api/v1/conciliacoes/{id}` — Carrega o resumo, extrato e lançamentos conferidos.
- `POST /api/v1/conciliacoes` — Salva ou atualiza conciliação com controle de versão otimista (`version`). Em caso de conflito concorrente, retorna `409 Conflict`.
- `DELETE /api/v1/conciliacoes/{id}` — Remove conciliação salva.

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
O PostgreSQL iniciará na porta `5432` com banco `makerpixel_contabil`. As migrações do Flyway (`V1__init_makerpixel.sql`) serão executadas automaticamente na inicialização da aplicação.

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
Documentação de saúde: `http://localhost:8080/actuator/health`

---

## 🧪 Como Executar os Testes

Para rodar os testes unitários e de integração:
```bash
./mvnw test
```

---

## 🔒 Segurança & Boas Práticas

- Senhas nunca são armazenadas em texto plano (BCrypt com salt).
- Nenhuma chave secreta ou credencial de banco é mantida estática no código; todas são injetadas por variáveis de ambiente (`SPRING_DATASOURCE_*`, `JWT_SECRET`).
- O isolamento multi-tenant por escritório é gerenciado no contexto da requisição (`TenantContext`).

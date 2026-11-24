# Guia de Deploy e Produção — Central Contábil v4.0.0 (MakerPixel)

Este guia orienta o deploy completo da infraestrutura da **Central Contábil v4.0.0** em ambiente de produção em nuvem, utilizando serviços gerenciados de alta confiabilidade, sem expor chaves ou segredos.

---

## 1. Visão Geral da Arquitetura

```
                      [ Internet / Clientes ]
                                 │
                 ┌───────────────┴───────────────┐
                 │                               │
        (Site Institucional)             (Aplicação Web SPA)
     makerpixeltech.com.br             app.makerpixeltech.com.br
    [ Vercel / Cloudflare ]           [ Vercel / Render Static ]
                 │                               │ (HTTPS + CORS)
                 │                               ▼
                 │                 ┌───────────────────────────┐
                 │                 │    API Central Contábil   │
                 │                 │  Render Web Service Java  │
                 │                 │       Porta 8080          │
                 │                 └───────┬───────────┬───────┘
                 │                         │           │ (HTTP Interno)
                 │     (JDBC SSL)          │           ▼
                 │   ┌─────────────────────┘   ┌───────────────────────────┐
                 │   ▼                         │      Calculadora RTC      │
         ┌───────────────┐                     │  Render Private Service   │
         │   Supabase    │                     │  (Motor Receita Federal)  │
         │   Postgres    │                     │        Porta 8088         │
         │  + Storage    │                     └───────────────────────────┘
         │  (Cofre RLS)  │
         └───────────────┘
```

---

## 2. Componentes da Infraestrutura

### 2.1 API Central Contábil (Render Web Service)
- **Runtime:** Docker (Multi-stage build com Java 21 JRE Jammy).
- **Porta:** 8080.
- **Health Check Path:** `/health` (retorna `status: UP`, `versao: 4.0.0`, código HTTP 200).
- **Auto Deploy:** Ativado a partir do branch `main` do repositório `makerpixel-gestao-api`.
- **Migrations:** Aplicadas automaticamente pelo Flyway no startup da aplicação.

### 2.2 Calculadora RTC — Receita Federal (Render Private Service)
- **Tipo:** Private Service (acessível **exclusivamente** na rede interna do Render via `http://central-contabil-calculadora-rtc:8088`).
- **Dockerfile:** `infra/calculadora-rtc/Dockerfile`.
- **Versão:** Motor oficial da Receita Federal em modo offline estático (SQLite pré-compilado).
- **Segurança:** Não possui porta pública ou rota exposta para a internet.

### 2.3 Banco de Dados e Storage (Supabase)
- **Postgres:** Instância gerenciada PostgreSQL 16+.
- **Storage:** Bucket privado `central-contabil-cofre` para guarda de comprovantes e anexos.
  - O bucket deve ser configurado como **Privado (Private)**.
  - Apenas a API autenticada acessa via credencial do backend.
- **Políticas RLS:** Multi-tenant estrito com isolamento por `escritorio_id`.

### 2.4 Frontend SPA (Recomendação de Hospedagem)
- **Recomendação:** **Vercel** (ou **Render Static Site**).
- **Por que a Vercel?**
  1. **CDN Global Anycast:** Distribuição estática ultrarrápida no Brasil (São Paulo - GRU), garantindo carregamento instantâneo do SPA e PWA.
  2. **SSL Automático com Wildcard:** Gestão nativa e renovação automática de certificado TLS para `app.makerpixeltech.com.br` e `makerpixeltech.com.br`.
  3. **Zero Manutenção de Servidor:** Deploy atômico via `git push origin develop` / `main`.
- **Subdomínios:**
  - `makerpixeltech.com.br` e `www.makerpixeltech.com.br` -> Site institucional público e funil de vendas.
  - `app.makerpixeltech.com.br` -> Central Contábil (Aplicação SPA autenticada).

### 2.5 CORS (Cross-Origin Resource Sharing)
A API libera requisições exclusivamente para as origens configuradas em `CORS_ALLOWED_ORIGINS`:
```
https://app.makerpixeltech.com.br,https://makerpixeltech.com.br,https://www.makerpixeltech.com.br
```
Todas as requisições em produção devem utilizar estritamente **HTTPS**.

---

## 3. Variáveis de Ambiente e Geração de Segredos

### 3.1 Tabela de Variáveis

| Variável | Onde Configurar | Finalidade |
| :--- | :--- | :--- |
| `SPRING_DATASOURCE_URL` | Render (API) | URL JDBC de conexão ao Postgres Supabase (`jdbc:postgresql://db.<ref>.supabase.co:5432/postgres?sslmode=require`) |
| `SPRING_DATASOURCE_USERNAME` | Render (API) | Usuário do banco (`postgres`) |
| `SPRING_DATASOURCE_PASSWORD` | Render (API) | Senha forte da instância Supabase |
| `CENTRAL_MASTER_KEY` | Render (API) | Chave-mestra AES-256 (32 bytes em Base64) que decifra as DEKs do cofre de certificados A1 |
| `JWT_SECRET` | Render (API) | Segredo HMAC-SHA256 para assinatura dos tokens JWT da aplicação |
| `NOTASYNC_CRON_SECRET` | Render (API & Crons) | Segredo de autenticação enviado no cabeçalho `X-Notasync-Cron-Secret` para os disparadores agendados |
| `CORS_ALLOWED_ORIGINS` | Render (API) | Lista de domínios autorizados separados por vírgula (`https://app.makerpixeltech.com.br`) |
| `RTC_CALCULADORA_URL` | Render (API) | Endereço interno do motor RTC (`http://central-contabil-calculadora-rtc:8088`) |
| `TZ` | Render (Todos) | Fuso horário operacional (`America/Sao_Paulo`) |
| `VITE_API_URL` | Vercel (Front) | URL pública da API (`https://central-contabil-api.onrender.com`) |
| `VITE_MODO_DEMO` | Vercel (Front) | `false` em produção |

---

### 3.2 Como Gerar os Segredos Criptográficos via PowerShell (Windows)

Abra o **PowerShell** no seu Windows e execute os comandos abaixo para gerar chaves seguras e aleatórias de 256 bits:

#### Gerar `CENTRAL_MASTER_KEY` (Chave AES-256 de 32 bytes em Base64):
```powershell
$bytes = New-Object byte[] 32
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
[Convert]::ToBase64String($bytes)
```
> **Exemplo de saída:** `kL9f2W...==`

#### Gerar `JWT_SECRET` (Chave de 64 bytes em Base64):
```powershell
$bytes = New-Object byte[] 64
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
[Convert]::ToBase64String($bytes)
```

#### Gerar `NOTASYNC_CRON_SECRET` (Segredo forte de 32 bytes em Hexadecimal):
```powershell
$bytes = New-Object byte[] 32
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
-join ($bytes | ForEach-Object { "{0:x2}" -f $_ })
```

---

## 4. Agendamento de Tarefas (Cron Jobs)

O agendamento é configurado no Render através do arquivo `render.yaml` ou via agendador externo (ex.: cron-job.org / GitHub Actions):

### 4.1 Coletor NotaSync Nuvem
- **Frequência:** A cada 2 horas (`0 */2 * * *`).
- **Endpoint:** `POST https://central-contabil-api.onrender.com/interno/notasync/rodar`
- **Cabeçalho:** `X-Notasync-Cron-Secret: <NOTASYNC_CRON_SECRET>`

### 4.2 Geração Mensal Em Dia
- **Frequência:** Todo dia 1º de cada mês às 06:00 (`0 6 1 * *`).
- **Endpoint:** `POST https://central-contabil-api.onrender.com/interno/emdia/gerar-mes-cron`
- **Cabeçalho:** `X-Notasync-Cron-Secret: <NOTASYNC_CRON_SECRET>`

---

## 5. Política de Backup e Alerta de Segurança da Chave-Mestra

> [!CAUTION]
> **ALERTA CRÍTICO DE SEGURANÇA SOBRE A `CENTRAL_MASTER_KEY`**
>
> O cofre de certificados da Central Contábil utiliza criptografia de envelope (envelope encryption) com algoritmo **AES-256-GCM**. Cada certificado possui sua própria DEK (Data Encryption Key), a qual é cifrada pela `CENTRAL_MASTER_KEY`.
>
> Se você perder a `CENTRAL_MASTER_KEY`:
> 1. **Nenhum certificado A1 armazenado poderá ser decifrado novamente.**
> 2. O NotaSync nuvem interromperá a busca para todos os escritórios clientes.
> 3. Os clientes terão que reenviar e reconfigurar todos os seus certificados.
>
> **Procedimento obrigatório:**
> Armazene uma cópia offline da `CENTRAL_MASTER_KEY` em um cofre de senhas seguro (ex.: 1Password, Bitwarden ou cofre corporativo físico). **Nunca versione esta chave no Git.**

### Ativação de Backup no Supabase:
1. Acesse o dashboard do projeto no Supabase -> **Database** -> **Backups**.
2. O plano Pro do Supabase realiza backups diários automáticos com retenção de 7 a 30 dias e Point-in-time Recovery (PITR).
3. Para testar o restore: clone a base em um projeto temporário de staging periodicamente a cada 90 dias.

---

## 6. Monitoramento Simples e Alertas

1. **Uptime Check Gratuito:**
   - Cadastre uma verificação HTTP GET gratuita no [BetterStack](https://betterstack.com) ou [UptimeRobot](https://uptimerobot.com).
   - URL: `https://central-contabil-api.onrender.com/health`
   - Intervalo: 1 minuto.
   - Alerta: Notificação via e-mail e SMS caso a API retorne status diferente de 200.

2. **Log de Execução do Robô:**
   - A tabela `notasync_execucao` registra todas as tentativas de coleta da SEF/SC com tempo de resposta, cStat e mensagens traduzidas.

3. **Alerta de Falhas Consecutivas:**
   - Se o robô registrar 3 falhas consecutivas para o mesmo escritório (ex.: cStat 8002 de SAT desvinculado), a API gera automaticamente uma tarefa no **Em Dia**:
     *"Vincular contador no SAT – empresa X"*.

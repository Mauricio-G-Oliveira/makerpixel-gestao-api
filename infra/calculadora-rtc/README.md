# Calculadora RTC — Serviço de Apuração da Reforma Tributária (Receita Federal)

Este serviço empacota o motor oficial de cálculo do **Regime Regular da Reforma Tributária do Consumo** (CBS, IBS e Imposto Seletivo), desenvolvido pela Receita Federal do Brasil sob a **Licença MIT**.

- **Repositório oficial**: `https://github.com/ReceitaFederal/calculadora-rtc-regime-regular`
- **Licenciamento**: MIT License (Secretaria Especial da Receita Federal do Brasil)
- **Perfil de execução**: `offline` (opera de forma autônoma sem requisições externas à internet)

---

## 1. Execução Local via Docker

### Build manual da imagem:
```bash
docker build -t makerpixel/calculadora-rtc:latest -f infra/calculadora-rtc/Dockerfile .
```

### Rodar container localmente:
```bash
docker run -d --name calculadora-rtc -p 8088:8088 makerpixel/calculadora-rtc:latest
```

Teste no navegador ou terminal:
```bash
curl http://localhost:8088/actuator/health
curl http://localhost:8088/versao
```

---

## 2. Deploy no Render como Private Service

No Render, a Calculadora RTC deve rodar como um **Private Service** (Serviço Privado), garantindo segurança máxima (não recebe tráfego da internet pública e só aceita conexões diretas da API Central Contábil):

1. Acesse o painel do **Render** (`dashboard.render.com`).
2. Clique em **New + > Private Service**.
3. Conecte o repositório `makerpixel-central-contabil` (ou repositório dedicado da calculadora).
4. Em **Dockerfile Path**, informe: `infra/calculadora-rtc/Dockerfile`.
5. Em **Instance Type**, selecione `Starter` ou `Free`.
6. Variáveis de ambiente:
   - `SPRING_PROFILES_ACTIVE=offline`
   - `PORT=8088`
7. Após o deploy, o Render exibirá o endereço de rede interna, por exemplo:
   `http://calculadora-rtc:8088`
8. Na API Central Contábil (`makerpixel-gestao-api`), configure a variável de ambiente:
   ```bash
   RTC_CALCULADORA_URL=http://calculadora-rtc:8088
   ```

---

## 3. Endpoints Utilizados pela Central Contábil

- `POST /calculadora/observabilidade/regime-geral`: Apuração e validação item a item com memória de cálculo detalhada e fundamentação legal.
- `GET /dados-abertos/situacoes-tributarias/cbs-ibs`: Lista oficial de CSTs da Reforma Tributária.
- `GET /dados-abertos/classificacoes-tributarias`: Tabela de Códigos de Classificação Tributária (`cClassTrib`) e NCMs vinculadas.
- `GET /versao`: Identificação da versão das regras e tabelas da Receita Federal em execução.

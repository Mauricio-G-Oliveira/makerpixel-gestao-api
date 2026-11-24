# Roteiro de Teste de Ponta a Ponta — Piloto Central Contábil v4.0.0

Este documento descreve os 6 passos práticos para validação completa da **Central Contábil v4.0.0** com escritórios de contabilidade parceiros. O roteiro pode ser executado primeiro em **Ambiente Simulado** (demonstração com dados fictícios) e posteriormente em **Produção Real**.

---

## 📋 Pré-requisitos
- Acesso de administrador do produto (`ADMIN_PRODUTO`).
- Um certificado digital A1 de teste (em formato `.pfx` ou `.p12`) ou arquivo de teste fornecido pela SEF/SC.
- Três CNPJs válidos de empresas de Santa Catarina (para teste em SC) ou CNPJs fictícios de teste.

---

## 🚀 Passo a Passo do Piloto

### 1. Criar um Escritório de Teste no Admin MakerPixel
1. Acesse o sistema como `ADMIN_PRODUTO` e vá para a aba **Admin MakerPixel** (`/admin`).
2. Clique no botão **"Novo Teste"** ou **"Criar Acesso Teste"**.
3. Preencha os dados cadastrais do escritório parceiro:
   - **Razão Social:** `Contabilidade Exemplo & Associados Ltda`
   - **CNPJ:** `11.222.333/0001-81`
   - **Responsável:** `João da Silva`
   - **E-mail:** `joao@contabilidadeexemplo.com.br`
   - **Telefone:** `(48) 99999-8888`
   - **Dias de Teste:** `30`
   - **Plano:** `Intermediário` ou `Avançado` (com módulos NotaSync, Reforma, DANFSe, XML->Excel e Em Dia).
4. O sistema gera uma senha provisória aleatória e monta a mensagem de convite pronta para envio via WhatsApp ou E-mail.
5. Copie a mensagem e confirme a criação.

---

### 2. Primeiro Acesso do Escritório e Upload do Certificado A1
1. Abra uma janela anônima e acesse `https://app.makerpixeltech.com.br` (ou `http://localhost:5173` em ambiente local).
2. Faça login com o e-mail cadastrado e a senha provisória.
3. Crie a sua senha definitiva no primeiro acesso.
4. Acesse a aba **NotaSync** (`/notasync`).
5. Clique em **"Enviar Certificado A1"**:
   - Leia e marque o aceite do **Termo de Custódia e Uso Seguro do Certificado**.
   - Selecione o arquivo `.pfx` / `.p12` e informe a senha do certificado.
   - Clique em **Salvar Certificado no Cofre**.
6. **Verificação de Segurança:**
   - Observe que a API cifra o certificado com envelope **AES-256-GCM** e armazena no cofre seguro.
   - O navegador **não retém** a senha nem o arquivo nos cookies, localStorage ou memória.
   - A tela exibe o card verde com o titular mascarado (ex.: `CONTABILIDADE EX***`), emissor e data de validade.

---

### 3. Cadastrar/Importar Empresas e Rodar a Busca do NotaSync
1. Na aba **Empresas** do NotaSync:
   - Clique em **"Cadastrar Empresa"** ou **"Importar Planilha (CSV)"**.
   - Adicione 3 CNPJs clientes do escritório (ex.: empresas do Simples Nacional e Lucro Presumido).
2. Clique no botão **"Buscar Agora"** em uma das empresas.
3. **Comportamento Esperado:**
   - O robô se comunica com a SEF/SC utilizando o certificado do cofre.
   - Se o contador estiver devidamente vinculado no SAT, os documentos (NFC-e mod 65, NF-e mod 55, CT-e mod 57) são sincronizados e listados.
   - Caso o contador ainda não esteja credenciado perante a SEFAZ, o robô traduz o código:
     *"cStat 8002: Rejeição: Contador não vinculado no cadastro SAT para o CNPJ"*.
   - Se o usuário clicar em "Buscar Agora" repetidamente, a API responde HTTP 429 com mensagem de espera regulamentar (ex.: *"Aguarde X minutos para nova consulta"*).

---

### 4. Baixar o ZIP do Mês, Conferir IBS/CBS e Exportar Excel
1. Na tela de **Documentos** do NotaSync:
   - Selecione o mês de competência desejado (ex.: `2026-11`).
   - Clique em **"Baixar ZIP do Mês"**: o navegador recebe o arquivo `.zip` compactado em streaming diretamente da API contendo todos os XMLs do período.
2. Clique no botão **"Conferir IBS/CBS"** na linha de documentos:
   - A tela de **Reforma Tributária** abre automaticamente carregando os XMLs daquele mês.
   - Visualize a memória de cálculo comparando o imposto destacado no documento contra o valor recalculado pelo motor oficial RTC.
   - Abra o **Modal Lado a Lado** para verificar fundamentação legal, alíquotas de teste de 2026 e divergências.
3. Clique em **"Exportar Excel/CSV"** para salvar a planilha estruturada de auditoria.

---

### 5. Verificar Tarefas Automáticas no Módulo Em Dia
1. Acesse o menu **Em Dia** (`/em-dia`).
2. Selecione a competência atual e clique em **"Gerar Mês"**:
   - O sistema gera de forma **idempotente** as tarefas rotineiras de cada empresa (DAS, Folha, EFD-Reinf, SPED, Conciliação) de acordo com o regime tributário e porte.
3. Observe as **Tarefas Automáticas** geradas automaticamente pelo cruzamento dos módulos:
   - Se ocorreu o erro 8002 no NotaSync: a tarefa *"Vincular contador no SAT – empresa X"* estará aberta no setor Fiscal.
   - Se o certificado A1 estiver a menos de 30 dias do vencimento: a tarefa *"Renovar Certificado Digital A1"* estará aberta no setor Geral.
   - Se o lote da Reforma acusou divergência tributária: a tarefa *"Revisar divergências IBS/CBS – mês X"* estará gerada.
4. Alterne para as abas **"Painel do Mês"** e **"Diário da Empresa"** para acompanhar métricas e incluir uma nota operacional.

---

### 6. Teste de Suspensão no Admin e Bloqueio de Acesso
1. Retorne à sessão do `ADMIN_PRODUTO` na aba **Admin MakerPixel**.
2. Localize o escritório de teste e clique na opção **"Suspender Escritório"**.
3. Na janela do escritório piloto, tente navegar ou executar qualquer ação na API:
   - A API retorna imediatamente HTTP 403 / 402 com a mensagem padronizada:
     *"Escritório suspenso. Entre em contato com o suporte MakerPixel para reativar seu acesso."*
   - O front renderiza a tela amigável de bloqueio sem travar a aplicação.
4. No admin, clique em **"Reativar Escritório"** e confirme que o acesso volta a operar instantaneamente.

---

## 🔒 7. Validação das Garantias LGPD (Artigos 17 e 18)
1. Acesse **Configurações / Usuários** (`/usuarios`).
2. Localize a seção **"Privacidade e Conformidade LGPD"**:
   - **Exportar Meus Dados:** Clique em *"Baixar Arquivo dos Dados (ZIP)"*. O sistema entrega um arquivo ZIP com todos os dados cadastrais, empresas, tarefas e metadados de certificados em formato JSON limpo.
   - **Direito ao Esquecimento:** Clique em *"Excluir Escritório Definitivamente"*. Digite o CNPJ do escritório para confirmação dupla. Ao confirmar, todos os dados, certificados e usuários são expurgados em definitivo do banco de dados e a sessão é encerrada.

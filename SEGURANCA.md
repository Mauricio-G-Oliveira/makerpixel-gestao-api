# Diretrizes de Segurança e Privacidade — Central Contábil v3

**MakerPixel Tech** — *Plataforma de Gestão Contábil, Inteligência Fiscal e Busca Automatizada*  
*Domínio Oficial: makerpixeltech.com.br*

---

## 1. Princípios Fundamentais de Segurança

A Central Contábil v3 adota o modelo de segurança preventiva e privacidade por padrão (*Privacy by Design*):
1. **Isolamento Estrito Multi-Tenant**: Dados fiscais, cadastros e acessos de cada escritório contábil são isolados por políticas ativas no banco de dados (Row Level Security - RLS).
2. **Zero Telemetria em Documentos Fiscais Sensíveis**: O processamento e geração de DANFSe (NFS-e) e conversão para planilhas Excel ocorrem inteiramente na memória do navegador do cliente via WebAssembly e engines locais.
3. **Cofre Blindado para Certificados A1**: Proteção de chaves criptográficas com modelo de chave em camadas e trilha de auditoria completa.

---

## 2. Arquitetura do Cofre de Certificados A1

Para o módulo **NotaSync** e consultas SEF/SC / DF-e Nacional:
- **Criptografia em Repouso**: Os certificados PFX/P12 e suas respectivas senhas são armazenados cifrados com algoritmo **AES-256-GCM** (Galois/Counter Mode), garantindo sigilo e integridade criptográfica.
- **Envelope Encryption**:
  - Cada arquivo é cifrado com uma chave de dados exclusiva (DEK - Data Encryption Key) e um Initialization Vector (IV/Nonce) aleatório de 96 bits.
  - A chave de dados é envelopada por uma Chave Mestra de Criptografia (KEK - Key Encryption Key) gerenciada por variável de ambiente segura no servidor backend.
- **Mascaramento Protetivo**: O titular (CNPJ ou CPF) é mascarado em qualquer tela ou listagem pública da interface (`11.***.***/0001-**`), exibindo somente emissor, período de validade e hash SHA-256 de auditoria.

---

## 3. Rotação de Chaves e Política de Retenção

- **Identificador de Versão de Chave (`versaoChaveMestra`)**: Todo registro no cofre identifica a versão da chave que o cifrou, permitindo reencriptação transparente em caso de rotação preventiva de chaves.
- **Exclusão Segura e Imediata**: O botão "Excluir Certificado" executa a remoção definitiva dos bytes cifrados e das senhas do banco de dados, cancelando buscas automatizadas atreladas àquele documento.
- **Trilha de Auditoria Imutável**: Todas as operações críticas (Upload, Consulta à Fazenda, Exportação de Lote e Exclusão) registram data/hora, endereço IP e identificador do operador na tabela de auditoria (`notasync_auditoria_acessos`).

---

## 4. Conformidade com a LGPD (Lei Geral de Proteção de Dados)

- Não há armazenamento de dados pessoais além do estritamente necessário para cumprimento de obrigações fiscais e contábeis acessórias.
- O termo de autorização de uso do certificado digital deve ser aceito pelo administrador do escritório antes da primeira carga no cofre.

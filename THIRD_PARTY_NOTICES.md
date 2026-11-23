# Third-Party Notices — Central Contábil v3

Este projeto integra e referencia os seguintes componentes de código aberto:

## 1. Calculadora RTC e Gerador DANFSe
- **Provedor:** Receita Federal do Brasil
- **Repositórios Oficiais:**
  - `https://github.com/ReceitaFederal/calculadora-rtc-regime-regular`
  - `https://github.com/ReceitaFederal/calculadora-rtc-simples-nacional`
  - `https://github.com/ReceitaFederal/calculadora-rtc-gerador-danfse`
- **Licença:** MIT License
- **Uso no Sistema:**
  - A Calculadora RTC executa como serviço de apoio desacoplado via container Docker (`infra/calculadora-rtc`).
  - O Gerador DANFSe executa de forma 100% client-side via WebAssembly (`public/danfse/`), sem envio de XMLs de clientes para serviços externos.

## 2. Bibliotecas de Interface e Processamento
- **React & React DOM:** Licença MIT (Meta Platforms, Inc.)
- **React Router:** Licença MIT (Shopify / Remix Software)
- **Vite:** Licença MIT (Evan You & Vite Contributors)
- **Tailwind CSS:** Licença MIT (Tailwind Labs, Inc.)
- **SheetJS (xlsx):** Licença Apache 2.0
- **JSZip:** Licença MIT

---
*MakerPixel Tech — Central Contábil (makerpixeltech.com.br)*

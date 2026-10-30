# Estado das funcionalidades

Uma linha por funcionalidade: o que é, se está pronta e em que versão entrou. Onde fica no código está no [mapa](../mapa/); o desenho, na [especificação](../especificacao.md); o que falta, em [pendencias.md](pendencias.md).

Legenda: ✅ pronto e testado · 🟡 parcial · ⬜ não começado

## Motor e essencial

| Funcionalidade | Estado | Versão | Resumo |
| --- | --- | --- | --- |
| Projeto NeoForge 1.21.1 | ✅ | — | Build, setup e CI |
| Gerenciador com orçamento de tempo | ✅ | — | Rotas por rede e tipo, laço com cursor entre ticks, origens e destinos dormindo |
| Roteador direcional | ✅ | — | Acessa a máquina pelas seis faces; gira (spin) sem perder a configuração |
| Configuração por face | ✅ | — | Modo, prioridade, redstone, filtro e rede por tipo e lado |
| Redes | ✅ | 1.9.0 (por face) | Rede por face, rede ativa, round-robin, grupos de redes, roteador nasce sem rede |
| Acesso às redes privadas | ✅ | 2.7.0 | Convidados e times do FTB Teams (opcional) |
| Filtros e Cartão de Filtro | ✅ | — | Sem limite de entradas, estoque em dois limites, tags, mod, propriedades, JEI |
| Tiers e Cartões de Upgrade | ✅ | — | Oito tiers (três com o Allthemodium), ×8 por degrau, subir tier pelo anel ou pela bancada |
| Profiler e benchmark | ✅ | — | `/wa profile`, `/wa bench`, `scripts/bench.sh` e o comparativo com o Logistics Network |
| Upgrade de Chunk Loading | ✅ | — | Mantém o roteador trabalhando com o jogador longe |

## Ferramentas e telas

| Funcionalidade | Estado | Versão | Resumo |
| --- | --- | --- | --- |
| Telas (GUI) | ✅ | 2.0.0 (tema escuro) | Tema claro e escuro, telas redimensionáveis, roteador com visor 3D e painel do nó |
| Vinculador | ✅ | 2.7.0 (modos atuais) | Modos Vincular e Abrir, área com limites |
| Configurador | ✅ | 1.6.0 (roda) | Pincel e área, sem tela, colar por tipo |
| Tablet de Rede | ✅ | 1.0.0 (Fluxos e a Lista com máquinas da 2.2.0 à 2.4.0) | Lista (roteadores e máquinas), Fluxos, Mapa e Redes |
| Roteador de alcance | 🟡 | 1.9.0 | Várias máquinas por roteador, grupos com perfil, lista e destaque no mundo; faltam pausar grupo, perfis no Configurador e a página no guia |
| Editor de receitas | ✅ | 1.7.0 | `/wa recipes` e tela para operadores |
| Livro-guia (GuideME) | ✅ | — | 25 páginas pt e en geradas por `scripts/guide/`; reescritas mais simples em 30/10 |

## Armazenamentos

| Funcionalidade | Estado | Versão | Resumo |
| --- | --- | --- | --- |
| Baú, Tanque, Bateria, Tanque Químico e Tanque de Source | ✅ | 1.0 | Capacidade por tier, tela em lista, filtro de entrada, quebrar e levar com o conteúdo |
| Upgrades do Baú (Mesa e Compactação) | ✅ | 2.13.0 | Grade de mesa que se repõe e compactação por cadeias de receitas |

## Energia e cristais

| Funcionalidade | Estado | Versão | Resumo |
| --- | --- | --- | --- |
| Ressonador de Sinal | ✅ | 2.1.0 (rebalanceado na 2.4.0, câmara na 2.8.3) | Gerador multibloco movido a cristais e sintonia |
| Energia ligada ao Ressonador | ✅ | 2.8.0 | Energia do roteador e capacidade da Bateria pela geração do Ressonador do mesmo tier |
| Reparador de Cristais | ✅ | 2.9.0 | Conserta cristais rachados, oito de uma vez |

## Painel

| Funcionalidade | Estado | Versão | Resumo |
| --- | --- | --- | --- |
| Tela de Sinal | ✅ | 2.11.0 (etapas 1 a 3), 2.12.0 (etapa 4) | Painel na parede com peças, armazenamentos, pixel art, animação e imagens |

## Integrações

| Funcionalidade | Estado | Versão | Resumo |
| --- | --- | --- | --- |
| JEI | ✅ | — | Filtros, receitas, estrutura do Ressonador, "+" da Mesa (opcional) |
| Químicos do Mekanism | ✅ | — | Aba Químicos e Tanque Químico (opcional) |
| Source do Ars Nouveau | ✅ | — | Aba Source e Tanque de Source (opcional) |
| FTB Teams | ✅ | 2.7.0 | Times nas redes privadas (opcional) |
| Allthemodium | ✅ | — | Três tiers a mais, só com o mod |
| Occultism | ✅ | 2.8.1 | Os mineiros dão Cristais de Sinal |
| AE2 e RS2 | ⬜ | — | Não começado |
| Balanceamento das receitas | ⬜ | — | Não começado |

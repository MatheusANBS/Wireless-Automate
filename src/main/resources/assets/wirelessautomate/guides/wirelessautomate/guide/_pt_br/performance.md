---
navigation:
  title: Desempenho e config
  icon: minecraft:clock
  parent: index.md
  position: 12
---


# Desempenho e config

O mod não faz tick por bloco: um gerenciador central move tudo, dentro de um **orçamento de tempo
por tick** (padrão: 1 ms, de 50 ms do tick). O que não cabe continua no tick seguinte, sem perder
nada; com muitas máquinas, a vazão se divide, mas o lag não passa do teto.

- **Orçamento adaptativo:** se o servidor passar de 40 ms por tick, o mod reduz o próprio teto
  (até 25% a partir de 50 ms) e volta sozinho quando alivia.
- **Quem dorme não pesa:** origens vazias e destinos cheios dormem e quase não custam.
- **`/wa profile`** (operador): ms por tick do mod e de cada rede, operações por segundo e origens e
  destinos acordados ou dormindo.

## Config do servidor

Arquivo `config/wirelessautomate-server.toml`:

- `tickBudgetMs` e `adaptiveBudget`: o orçamento.
- `tiers.<tier>`: vazão e alcance de cada tier (0 = sem limite).
- `chunkLoading`: liga o upgrade e o limite de chunks por jogador.
- `linker`: tamanho e distância máximos da área do Vinculador e do Configurador.

---
navigation:
  title: Desempenho e config
  icon: minecraft:clock
  parent: index.md
  position: 12
---


# Desempenho e config

O mod não faz tick por bloco: um gerenciador central move tudo, dentro de um **orçamento de tempo
por tick**. O que não cabe continua no tick seguinte, sem perder nada. Com muitas máquinas, a vazão
se divide, mas o lag nunca passa do teto.

## Como o mod se protege

| Mecanismo | O que faz |
| --- | --- |
| **Orçamento por tick** | Teto de tempo do mod: padrão de 1 ms, de 50 ms do tick. |
| **Orçamento adaptativo** | Se o servidor passar de 40 ms por tick, o teto cai aos poucos (até 25% a partir de 50 ms) e volta sozinho. |
| **Quem dorme não pesa** | Origens vazias e destinos cheios dormem e quase não custam. |
| **Revezamento** | Com o orçamento esgotado, todas as origens continuam movendo, cada uma um pouco menos. |

## Medir

| Comando | Mostra |
| --- | --- |
| `/wa profile` (operador) | ms por tick do mod e de cada rede, operações por segundo, origens e destinos acordados e dormindo. |
| Tablet › Estatísticas | Vazão por tipo e tempo do mod por tick, sem precisar ser operador. |

## Config do servidor

Arquivo `config/wirelessautomate-server.toml`:

| Chave | Padrão | O que faz |
| --- | --- | --- |
| `performance.tickBudgetMs` | `1.0` | Teto de tempo do mod por tick, em ms. Mais = mais vazão com muitas máquinas. |
| `performance.adaptiveBudget` | `true` | Reduz o teto quando o servidor está pesado. |
| `tiers.<tier>.itemsPerSecond` | por tier | Itens por segundo, por face e tipo (0 = sem limite). |
| `tiers.<tier>.fluidPerSecond` | por tier | Fluido e químico em mB/s (0 = sem limite). |
| `tiers.<tier>.energyPerTick` | por tier | Energia em FE/t (0 = sem limite). |
| `tiers.<tier>.range` | por tier | Alcance em blocos (0 = a dimensão inteira). |
| `tiers.<tier>.crossDimension` | só Ultimate | Permite rotas entre dimensões. |
| `chunkLoading.*` | | Veja [Upgrade de Chunk Loading](chunk-loading.md). |
| `linker.maxAreaVolume` / `maxDistance` | `262144` / `64` | Área do Vinculador e do Configurador. |

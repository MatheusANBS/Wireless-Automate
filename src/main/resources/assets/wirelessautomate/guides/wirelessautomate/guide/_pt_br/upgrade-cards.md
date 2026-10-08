---
navigation:
  title: Cartões de Upgrade
  icon: wirelessautomate:tier_core_advanced
  parent: index.md
  position: 3
item_ids:
  - wirelessautomate:tier_core_advanced
  - wirelessautomate:tier_core_elite
  - wirelessautomate:tier_core_ultimate
---


# Cartões de Upgrade

<Row gap="8">
  <ItemImage id="wirelessautomate:tier_core_advanced" scale="2" />
  <ItemImage id="wirelessautomate:tier_core_elite" scale="2" />
  <ItemImage id="wirelessautomate:tier_core_ultimate" scale="2" />
</Row>

Todo roteador nasce **Básico**. Cada cartão sobe **um** tier: Básico → Avançado → Elite → Ultimate.
A configuração do roteador (faces, filtros, redes) não se perde.

## O que cada cartão aumenta

Por face e por tipo. O tooltip do cartão mostra os valores do seu servidor. Químicos usam o
limite de fluido. Source só com o Ars Nouveau.

| Cartão | Itens/s | Fluido e químico (mB/s) | Energia (FE/t) | Source/s | Alcance |
| --- | --- | --- | --- | --- | --- |
| <ItemImage id="wirelessautomate:tier_core_advanced" /> **Avançado** | 512 → **8.192** | 32.000 → **512.000** | 16.000 → **256.000** | 1.000 → **16.000** | 128 → **1.024 blocos** |
| <ItemImage id="wirelessautomate:tier_core_elite" /> **Elite** | 8.192 → **131.072** | 512.000 → **8.000.000** | 256.000 → **4.000.000** | 16.000 → **256.000** | 1.024 → **a dimensão inteira** |
| <ItemImage id="wirelessautomate:tier_core_ultimate" /> **Ultimate** | 131.072 → **sem limite** | 8.000.000 → **sem limite** | 4.000.000 → **sem limite** | 256.000 → **sem limite** | **todas as dimensões** |

Cada passo multiplica a vazão por 16. Alcance e dimensões contam pelo tier de quem **envia**.

## Como usar

| Onde | Como |
| --- | --- |
| **No mundo** | Clique com o cartão do tier seguinte num roteador já colocado. |
| **Na bancada** | Um roteador + o cartão do tier seguinte, em qualquer posição. O nome do roteador continua. |

Não dá para pular tier: um roteador Básico não aceita o cartão Elite.

Os mesmos cartões sobem os armazenamentos ([Baú](wireless-chest.md), [Tanque](wireless-tank.md),
[Bateria](wireless-battery.md), [Tanque Químico](wireless-chemical-tank.md) e
[Tanque de Source](wireless-source-tank.md)), do mesmo jeito e sem
perder o conteúdo. A capacidade de cada tier está na página de cada um.

## Receitas

<RecipeFor id="wirelessautomate:tier_core_advanced" />
<RecipeFor id="wirelessautomate:tier_core_elite" />
<RecipeFor id="wirelessautomate:tier_core_ultimate" />

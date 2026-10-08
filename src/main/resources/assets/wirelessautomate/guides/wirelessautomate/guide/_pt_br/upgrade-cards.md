---
navigation:
  title: Cartões de Upgrade
  icon: wirelessautomate:tier_core_advanced
  parent: index.md
  position: 3
item_ids:
  - wirelessautomate:tier_core_advanced
  - wirelessautomate:tier_core_elite
  - wirelessautomate:tier_core_emerald
  - wirelessautomate:tier_core_allthemodium
  - wirelessautomate:tier_core_vibranium
  - wirelessautomate:tier_core_unobtainium
  - wirelessautomate:tier_core_ultimate
---


# Cartões de Upgrade

<Row gap="8">
  <ItemImage id="wirelessautomate:tier_core_advanced" scale="2" />
  <ItemImage id="wirelessautomate:tier_core_elite" scale="2" />
  <ItemImage id="wirelessautomate:tier_core_emerald" scale="2" />
  <ItemImage id="wirelessautomate:tier_core_allthemodium" scale="2" />
  <ItemImage id="wirelessautomate:tier_core_vibranium" scale="2" />
  <ItemImage id="wirelessautomate:tier_core_unobtainium" scale="2" />
  <ItemImage id="wirelessautomate:tier_core_ultimate" scale="2" />
</Row>

Todo roteador nasce **Básico**. Cada cartão sobe **um** tier: Básico → Avançado → Elite →
Esmeralda → Ultimate. Com o mod **Allthemodium**, entram três degraus entre a Esmeralda e o
Ultimate: Allthemodium → Vibranium → Unobtainium. A configuração do roteador (faces, filtros, redes)
não se perde.

## O que cada cartão aumenta

Por face e por tipo. O tooltip do cartão mostra os valores do seu servidor. Químicos usam o
limite de fluido. Source só com o Ars Nouveau.

| Cartão | Itens/s | Fluido e químico (mB/s) | Energia (FE/t) | Source/s | Alcance |
| --- | --- | --- | --- | --- | --- |
| <ItemImage id="wirelessautomate:tier_core_advanced" /> **Avançado** | 32 → **256** | 2.000 → **16.000** | 1.000 → **8.000** | 100 → **800** | 64 blocos → **512 blocos** |
| <ItemImage id="wirelessautomate:tier_core_elite" /> **Elite** | 256 → **2.048** | 16.000 → **128.000** | 8.000 → **64.000** | 800 → **6.400** | 512 blocos → **a dimensão inteira** |
| <ItemImage id="wirelessautomate:tier_core_emerald" /> **Esmeralda** | 2.048 → **16.384** | 128.000 → **1.024.000** | 64.000 → **512.000** | 6.400 → **51.200** | a dimensão inteira → **todas as dimensões** |
| <ItemImage id="wirelessautomate:tier_core_allthemodium" /> **Allthemodium¹** | 16.384 → **131.072** | 1.024.000 → **8.192.000** | 512.000 → **4.096.000** | 51.200 → **409.600** | todas as dimensões |
| <ItemImage id="wirelessautomate:tier_core_vibranium" /> **Vibranium¹** | 131.072 → **1.048.576** | 8.192.000 → **65.536.000** | 4.096.000 → **32.768.000** | 409.600 → **3.276.800** | todas as dimensões |
| <ItemImage id="wirelessautomate:tier_core_unobtainium" /> **Unobtainium¹** | 1.048.576 → **8.388.608** | 65.536.000 → **524.288.000** | 32.768.000 → **262.144.000** | 3.276.800 → **26.214.400** | todas as dimensões |
| <ItemImage id="wirelessautomate:tier_core_ultimate" /> **Ultimate** | 8.388.608 → **Sem limite** | 524.288.000 → **Sem limite** | 262.144.000 → **Sem limite** | 26.214.400 → **Sem limite** | todas as dimensões |

¹ Só com o mod Allthemodium.

Cada passo multiplica a vazão por 8. Alcance e dimensões contam pelo tier de quem **envia**.

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
<RecipeFor id="wirelessautomate:tier_core_emerald" />
<RecipeFor id="wirelessautomate:tier_core_ultimate" />

Com o Allthemodium, os cartões dele pedem o cartão anterior no centro e os materiais do próprio
metal: lingotes e blocos de Allthemodium; lingotes, blocos e liga Vibranium-Allthemodium; lingotes,
blocos e liga Unobtainium-Vibranium. O Ultimate passa a pedir o Cartão Unobtainium e, no ATM10 (com
o All The Tweaks), fragmentos de ATM Star, ovo do dragão e blocos de liga Unobtainium-Allthemodium.
O JEI mostra as receitas do seu pack.

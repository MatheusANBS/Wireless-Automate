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

Os cartões sobem o tier: Básico → Avançado → Elite → Esmeralda → Ultimate. Com o **Allthemodium**,
entram Allthemodium → Vibranium → Unobtainium antes do Ultimate.

## Como usar

| Onde | Como |
| --- | --- |
| **No mundo** | Shift + clique com o cartão no roteador ou armazenamento. Toda a configuração continua. |
| **Na bancada, com o cartão** | O bloco + o cartão, em qualquer posição. |
| **Na bancada, sem cartão** | O bloco no centro do anel do tier seguinte. |

O cartão só vale no tier escrito no nome ("Básico → Elite"). Em outro tier, ele não é gasto e um
aviso diz de qual tier ele sobe.

Também sobem os armazenamentos ([Baú](wireless-chest.md), [Tanque](wireless-tank.md),
[Bateria](wireless-battery.md), [Tanque Químico](wireless-chemical-tank.md) e
[Tanque de Source](wireless-source-tank.md)) sem perder o conteúdo.

## O que cada cartão aumenta

Por face e por tipo. O tooltip do cartão mostra os valores do seu servidor.

| Cartão | Itens/s | Fluido e químico (mB/s) | Energia (FE/t) | Source/s | Alcance |
| --- | --- | --- | --- | --- | --- |
| <ItemImage id="wirelessautomate:tier_core_advanced" /> **Avançado** | 32 → **256** | 2.000 → **16.000** | 454 → **29.100** | 100 → **800** | 64 blocos → **512 blocos** |
| <ItemImage id="wirelessautomate:tier_core_elite" /> **Elite** | 256 → **2.048** | 16.000 → **128.000** | 29.100 → **63.000** | 800 → **6.400** | 512 blocos → **a dimensão inteira** |
| <ItemImage id="wirelessautomate:tier_core_emerald" /> **Esmeralda** | 2.048 → **16.384** | 128.000 → **1.024.000** | 63.000 → **1.350.000** | 6.400 → **51.200** | a dimensão inteira → **todas as dimensões** |
| <ItemImage id="wirelessautomate:tier_core_allthemodium" /> **Allthemodium¹** | 16.384 → **131.072** | 1.024.000 → **8.192.000** | 1.350.000 → **67.200.000** | 51.200 → **409.600** | todas as dimensões |
| <ItemImage id="wirelessautomate:tier_core_vibranium" /> **Vibranium¹** | 131.072 → **1.048.576** | 8.192.000 → **65.536.000** | 67.200.000 → **84.500.000** | 409.600 → **3.276.800** | todas as dimensões |
| <ItemImage id="wirelessautomate:tier_core_unobtainium" /> **Unobtainium¹** | 1.048.576 → **8.388.608** | 65.536.000 → **524.288.000** | 84.500.000 → **158.000.000** | 3.276.800 → **26.214.400** | todas as dimensões |
| <ItemImage id="wirelessautomate:tier_core_ultimate" /> **Ultimate** | 8.388.608 → **Sem limite** | 524.288.000 → **Sem limite** | 158.000.000 → **Sem limite** | 26.214.400 → **Sem limite** | todas as dimensões |

¹ Só com o mod Allthemodium. Sem ele, o Cartão Ultimate vem depois do Cartão Esmeralda (16.384 itens/s → sem limite).

Cada passo multiplica a vazão por 8 (a energia do Unobtainium, por 64). Alcance e dimensões contam pelo tier de quem **envia**.

## Receitas

Cada degrau tem um **anel** de oito itens na bancada. O centro decide o resultado:

| Centro do anel | Resultado |
| --- | --- |
| **Redstone** | O cartão de um degrau, por exemplo **Avançado → Elite**. |
| **Um cartão** (ex.: Básico → Avançado) | O cartão que pula: **Básico → Elite**. Junta os anéis dos degraus pulados. |
| **O roteador ou armazenamento** do degrau anterior | O mesmo bloco, já no tier novo. |

<RecipeFor id="wirelessautomate:tier_core_advanced" />
<RecipeFor id="wirelessautomate:tier_core_elite" />
<RecipeFor id="wirelessautomate:tier_core_emerald" />
<RecipeFor id="wirelessautomate:tier_core_ultimate" />

As receitas mostram a Redstone no centro; o JEI mostra os três caminhos do seu pack.

Os anéis do Allthemodium usam o próprio metal. No ATM10, o do Ultimate leva fragmentos de ATM Star,
ovo do dragão e blocos de liga Unobtainium-Allthemodium. Um cartão feito antes de o Allthemodium
entrar no pack (ex.: Esmeralda → Ultimate) continua valendo e pula esses degraus.

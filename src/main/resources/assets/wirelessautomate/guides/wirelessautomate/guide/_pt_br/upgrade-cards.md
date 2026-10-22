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

Todo roteador nasce **Básico**. A escada é Básico → Avançado → Elite → Esmeralda → Ultimate. Com
o mod **Allthemodium**, entram três degraus entre a Esmeralda e o Ultimate: Allthemodium → Vibranium
→ Unobtainium.

Cada degrau tem um **anel**: oito itens em volta do centro da bancada. Subir um degrau custa o anel
daquele tier, por qualquer um dos três caminhos:

| Centro do anel | Resultado |
| --- | --- |
| **Redstone** | O cartão de um degrau, por exemplo **Avançado → Elite**. |
| **Um cartão** (por exemplo Básico → Avançado) | O cartão que pula: **Básico → Elite**. Junta os anéis dos degraus que ele pula. |
| **O roteador ou o armazenamento** do degrau anterior | O mesmo bloco, já no tier novo (a receita direta). |

O cartão tem **origem**: o nome diz de onde ele sobe ("Cartão de Upgrade: Básico → Elite"), e ele só
vale num bloco daquele tier. Cartões antigos, sem origem, continuam subindo de qualquer tier abaixo.

## O que cada cartão aumenta

Por face e por tipo. O tooltip do cartão mostra os valores do seu servidor. Químicos usam o
limite de fluido. Source só com o Ars Nouveau.

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

Cada passo multiplica a vazão por 8; só a energia do Unobtainium sobe 64 vezes sobre o Vibranium. Alcance e dimensões contam pelo tier de quem **envia**.

## Como usar

| Onde | Como |
| --- | --- |
| **No mundo** | Shift + clique com o cartão num roteador ou armazenamento já colocado, do tier da origem do cartão. Toda a configuração (faces, filtros, redes, cartões) continua. |
| **Na bancada, com o cartão** | O bloco + o cartão, em qualquer posição. |
| **Na bancada, sem cartão** | O bloco no centro do anel do tier seguinte. |

Na bancada, o bloco sobe como item: o roteador mantém o nome, e o armazenamento, o nome e o conteúdo.
Num bloco de outro tier, o cartão não é gasto e a barra acima do inventário avisa de qual tier ele sobe.

Se o mod Allthemodium entrar no pack depois, um cartão feito antes dele (por exemplo Esmeralda →
Ultimate) continua valendo e pula os degraus do Allthemodium.

Os mesmos cartões sobem os armazenamentos ([Baú](wireless-chest.md), [Tanque](wireless-tank.md),
[Bateria](wireless-battery.md), [Tanque Químico](wireless-chemical-tank.md) e
[Tanque de Source](wireless-source-tank.md)), do mesmo jeito e sem
perder o conteúdo. A capacidade de cada tier está na página de cada um.

## Receitas

<RecipeFor id="wirelessautomate:tier_core_advanced" />
<RecipeFor id="wirelessautomate:tier_core_elite" />
<RecipeFor id="wirelessautomate:tier_core_emerald" />
<RecipeFor id="wirelessautomate:tier_core_ultimate" />

As receitas mostram o anel com a Redstone no centro. Troque a Redstone por um cartão ou pelo bloco
do degrau anterior para os outros dois caminhos.

Com o Allthemodium, os anéis dele usam os materiais do próprio metal: lingotes e blocos de
Allthemodium; lingotes, blocos e liga Vibranium-Allthemodium; lingotes, blocos e liga
Unobtainium-Vibranium. O degrau antes do Ultimate passa a ser o Unobtainium e, no ATM10 (com o All
The Tweaks), o anel do Ultimate leva fragmentos de ATM Star, ovo do dragão e blocos de liga
Unobtainium-Allthemodium. O JEI mostra os três caminhos de cada anel do seu pack.

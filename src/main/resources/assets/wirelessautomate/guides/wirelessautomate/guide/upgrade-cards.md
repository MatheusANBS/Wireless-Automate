---
navigation:
  title: Upgrade Cards
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


# Upgrade Cards

<Row gap="8">
  <ItemImage id="wirelessautomate:tier_core_advanced" scale="2" />
  <ItemImage id="wirelessautomate:tier_core_elite" scale="2" />
  <ItemImage id="wirelessautomate:tier_core_emerald" scale="2" />
  <ItemImage id="wirelessautomate:tier_core_allthemodium" scale="2" />
  <ItemImage id="wirelessautomate:tier_core_vibranium" scale="2" />
  <ItemImage id="wirelessautomate:tier_core_unobtainium" scale="2" />
  <ItemImage id="wirelessautomate:tier_core_ultimate" scale="2" />
</Row>

Every router starts as **Basic**. The ladder is Basic → Advanced → Elite → Emerald → Ultimate.
With the **Allthemodium** mod, three steps go between Emerald and Ultimate: Allthemodium →
Vibranium → Unobtainium. Each card takes the router straight to its tier from **any lower tier**: a
Basic router with the Emerald Card becomes Emerald. The router's configuration (faces, filters,
networks) is kept.

## What each card raises

Per face and per type. The card's tooltip shows your server's values. Chemicals use the fluid
limit. Source only with Ars Nouveau.

| Card | Items/s | Fluid and chemical (mB/s) | Energy (FE/t) | Source/s | Range |
| --- | --- | --- | --- | --- | --- |
| <ItemImage id="wirelessautomate:tier_core_advanced" /> **Advanced** | 32 → **256** | 2,000 → **16,000** | 1,000 → **8,000** | 100 → **800** | 64 blocks → **512 blocks** |
| <ItemImage id="wirelessautomate:tier_core_elite" /> **Elite** | 256 → **2,048** | 16,000 → **128,000** | 8,000 → **64,000** | 800 → **6,400** | 512 blocks → **the whole dimension** |
| <ItemImage id="wirelessautomate:tier_core_emerald" /> **Emerald** | 2,048 → **16,384** | 128,000 → **1,024,000** | 64,000 → **512,000** | 6,400 → **51,200** | the whole dimension → **every dimension** |
| <ItemImage id="wirelessautomate:tier_core_allthemodium" /> **Allthemodium¹** | 16,384 → **131,072** | 1,024,000 → **8,192,000** | 512,000 → **4,096,000** | 51,200 → **409,600** | every dimension |
| <ItemImage id="wirelessautomate:tier_core_vibranium" /> **Vibranium¹** | 131,072 → **1,048,576** | 8,192,000 → **65,536,000** | 4,096,000 → **32,768,000** | 409,600 → **3,276,800** | every dimension |
| <ItemImage id="wirelessautomate:tier_core_unobtainium" /> **Unobtainium¹** | 1,048,576 → **8,388,608** | 65,536,000 → **524,288,000** | 32,768,000 → **262,144,000** | 3,276,800 → **26,214,400** | every dimension |
| <ItemImage id="wirelessautomate:tier_core_ultimate" /> **Ultimate** | 8,388,608 → **Unlimited** | 524,288,000 → **Unlimited** | 262,144,000 → **Unlimited** | 26,214,400 → **Unlimited** | every dimension |

¹ Only with the Allthemodium mod. Without it, the Ultimate Card comes after Emerald (16,384 items/s → unlimited).

Each step multiplies throughput by 8. Range and dimensions follow the **sender's** tier.

## How to use

| Where | How |
| --- | --- |
| **In the world** | Use the card of the tier you want on a placed router. |
| **In a crafting table** | A router + the card of the tier you want, in any slots. The router's name is kept. |

Cards only go up: they won't take a router of the same tier or a higher one. Since each card takes the
previous one in its recipe, going straight to the final tier is the cheapest path.

The same cards raise the storages ([Chest](wireless-chest.md), [Tank](wireless-tank.md),
[Battery](wireless-battery.md), [Chemical Tank](wireless-chemical-tank.md) and
[Source Tank](wireless-source-tank.md)), the same way and
keeping their contents. Each tier's capacity is on each one's page.

## Recipes

<RecipeFor id="wirelessautomate:tier_core_advanced" />
<RecipeFor id="wirelessautomate:tier_core_elite" />
<RecipeFor id="wirelessautomate:tier_core_emerald" />
<RecipeFor id="wirelessautomate:tier_core_ultimate" />

With Allthemodium, its cards take the previous card in the middle and the metal's own materials:
Allthemodium ingots and blocks; Vibranium ingots, blocks and Vibranium-Allthemodium alloy;
Unobtainium ingots, blocks and Unobtainium-Vibranium alloy. Ultimate then takes the Unobtainium Card
and, in ATM10 (with All The Tweaks), ATM Star shards, a dragon egg and Unobtainium-Allthemodium alloy
blocks. JEI shows your pack's recipes.

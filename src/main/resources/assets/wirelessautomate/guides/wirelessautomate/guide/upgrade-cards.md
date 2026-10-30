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

Cards raise the tier: Basic → Advanced → Elite → Emerald → Ultimate. With **Allthemodium**,
Allthemodium → Vibranium → Unobtainium come before Ultimate.

## How to use it

| Where | How |
| --- | --- |
| **In the world** | Shift + use the card on the router or storage. All its configuration is kept. |
| **In a crafting table, with the card** | The block + the card, in any slots. |
| **In a crafting table, no card** | The block in the middle of the next tier's ring. |

A card only works on the tier in its name ("Basic → Elite"). On another tier, it isn't used up and
a message says which tier it raises.

They also raise the storages ([Chest](wireless-chest.md), [Tank](wireless-tank.md),
[Battery](wireless-battery.md), [Chemical Tank](wireless-chemical-tank.md) and
[Source Tank](wireless-source-tank.md)) keeping their contents.

## What each card raises

Per face and per type. The card's tooltip shows your server's values.

| Card | Items/s | Fluid and chemical (mB/s) | Energy (FE/t) | Source/s | Range |
| --- | --- | --- | --- | --- | --- |
| <ItemImage id="wirelessautomate:tier_core_advanced" /> **Advanced** | 32 → **256** | 2,000 → **16,000** | 454 → **29,100** | 100 → **800** | 64 blocks → **512 blocks** |
| <ItemImage id="wirelessautomate:tier_core_elite" /> **Elite** | 256 → **2,048** | 16,000 → **128,000** | 29,100 → **63,000** | 800 → **6,400** | 512 blocks → **the whole dimension** |
| <ItemImage id="wirelessautomate:tier_core_emerald" /> **Emerald** | 2,048 → **16,384** | 128,000 → **1,024,000** | 63,000 → **1,350,000** | 6,400 → **51,200** | the whole dimension → **every dimension** |
| <ItemImage id="wirelessautomate:tier_core_allthemodium" /> **Allthemodium¹** | 16,384 → **131,072** | 1,024,000 → **8,192,000** | 1,350,000 → **67,200,000** | 51,200 → **409,600** | every dimension |
| <ItemImage id="wirelessautomate:tier_core_vibranium" /> **Vibranium¹** | 131,072 → **1,048,576** | 8,192,000 → **65,536,000** | 67,200,000 → **84,500,000** | 409,600 → **3,276,800** | every dimension |
| <ItemImage id="wirelessautomate:tier_core_unobtainium" /> **Unobtainium¹** | 1,048,576 → **8,388,608** | 65,536,000 → **524,288,000** | 84,500,000 → **158,000,000** | 3,276,800 → **26,214,400** | every dimension |
| <ItemImage id="wirelessautomate:tier_core_ultimate" /> **Ultimate** | 8,388,608 → **Unlimited** | 524,288,000 → **Unlimited** | 158,000,000 → **Unlimited** | 26,214,400 → **Unlimited** | every dimension |

¹ Only with the Allthemodium mod. Without it, the Ultimate Card comes after Emerald (16,384 items/s → unlimited).

Each step multiplies throughput by 8 (Unobtainium energy, by 64). Range and dimensions follow the **sender's** tier.

## Recipes

Each step has a **ring** of eight items in the crafting grid. The middle decides the result:

| Middle of the ring | Result |
| --- | --- |
| **Redstone** | The one-step card, for example **Advanced → Elite**. |
| **A card** (e.g. Basic → Advanced) | The skipping card: **Basic → Elite**. It adds up the rings of the skipped steps. |
| **The router or storage** one step below | The same block, already at the new tier. |

<RecipeFor id="wirelessautomate:tier_core_advanced" />
<RecipeFor id="wirelessautomate:tier_core_elite" />
<RecipeFor id="wirelessautomate:tier_core_emerald" />
<RecipeFor id="wirelessautomate:tier_core_ultimate" />

The recipes show Redstone in the middle; JEI shows the three paths in your pack.

Allthemodium's rings use the metal itself. In ATM10, Ultimate's takes ATM Star shards, a dragon egg
and Unobtainium-Allthemodium alloy blocks. A card made before Allthemodium joined the pack (e.g.
Emerald → Ultimate) still works and skips those steps.

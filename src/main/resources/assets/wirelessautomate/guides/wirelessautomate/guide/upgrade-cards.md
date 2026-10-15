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
Vibranium → Unobtainium.

Each step has a **ring**: eight items around the middle of the crafting grid. Going up one step costs
that tier's ring, by any of three paths:

| Middle of the ring | Result |
| --- | --- |
| **Redstone** | The one-step card, for example **Advanced → Elite**. |
| **A card** (for example Basic → Advanced) | The skipping card: **Basic → Elite**. It adds up the rings of the steps it skips. |
| **The router or storage** one step below | The same block, already at the new tier (the direct recipe). |

A card has an **origin**: its name says where it starts ("Upgrade Card: Basic → Elite"), and it only
works on a block of that tier. Old cards, with no origin, still raise any lower tier.

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
| <ItemImage id="wirelessautomate:tier_core_unobtainium" /> **Unobtainium¹** | 1,048,576 → **8,388,608** | 65,536,000 → **524,288,000** | 32,768,000 → **2,097,152,000** | 3,276,800 → **26,214,400** | every dimension |
| <ItemImage id="wirelessautomate:tier_core_ultimate" /> **Ultimate** | 8,388,608 → **Unlimited** | 524,288,000 → **Unlimited** | 2,097,152,000 → **Unlimited** | 26,214,400 → **Unlimited** | every dimension |

¹ Only with the Allthemodium mod. Without it, the Ultimate Card comes after Emerald (16,384 items/s → unlimited).

Each step multiplies throughput by 8. Range and dimensions follow the **sender's** tier.

## How to use

| Where | How |
| --- | --- |
| **In the world** | Shift + use the card on a placed router or storage of the card's origin tier. All of its configuration (faces, filters, networks, cards) is kept. |
| **In a crafting table, with the card** | The block + the card, in any slots. |
| **In a crafting table, no card** | The block in the middle of the next tier's ring. |

In a crafting table, the block goes up as an item: a router keeps its name, and a storage its name and
contents. On a block of another tier, the card isn't used up and the bar above the hotbar says which tier
it raises.

If the Allthemodium mod is added to the pack later, a card made before it (for example Emerald →
Ultimate) still works and skips the Allthemodium steps.

The same cards raise the storages ([Chest](wireless-chest.md), [Tank](wireless-tank.md),
[Battery](wireless-battery.md), [Chemical Tank](wireless-chemical-tank.md) and
[Source Tank](wireless-source-tank.md)), the same way and
keeping their contents. Each tier's capacity is on each one's page.

## Recipes

<RecipeFor id="wirelessautomate:tier_core_advanced" />
<RecipeFor id="wirelessautomate:tier_core_elite" />
<RecipeFor id="wirelessautomate:tier_core_emerald" />
<RecipeFor id="wirelessautomate:tier_core_ultimate" />

The recipes show the ring with Redstone in the middle. Swap the Redstone for a card or for the block one
step below for the other two paths.

With Allthemodium, its rings use the metal's own materials: Allthemodium ingots and blocks; Vibranium
ingots, blocks and Vibranium-Allthemodium alloy; Unobtainium ingots, blocks and Unobtainium-Vibranium
alloy. The step before Ultimate becomes Unobtainium and, in ATM10 (with All The Tweaks), the Ultimate
ring takes ATM Star shards, a dragon egg and Unobtainium-Allthemodium alloy blocks. JEI shows the three
paths of each ring in your pack.

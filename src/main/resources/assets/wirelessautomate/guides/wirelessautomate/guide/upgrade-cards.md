---
navigation:
  title: Upgrade Cards
  icon: wirelessautomate:tier_core_advanced
  parent: index.md
  position: 3
item_ids:
  - wirelessautomate:tier_core_advanced
  - wirelessautomate:tier_core_elite
  - wirelessautomate:tier_core_ultimate
---


# Upgrade Cards

<Row gap="8">
  <ItemImage id="wirelessautomate:tier_core_advanced" scale="2" />
  <ItemImage id="wirelessautomate:tier_core_elite" scale="2" />
  <ItemImage id="wirelessautomate:tier_core_ultimate" scale="2" />
</Row>

Every router starts as **Basic**. Each card raises **one** tier: Basic → Advanced → Elite →
Ultimate. The router's configuration (faces, filters, networks) is kept.

## What each card raises

Per face and per type. The card's tooltip shows your server's values. Chemicals use the fluid
limit.

| Card | Items/s | Fluid and chemical (mB/s) | Energy (FE/t) | Range |
| --- | --- | --- | --- | --- |
| <ItemImage id="wirelessautomate:tier_core_advanced" /> **Advanced** | 512 → **8,192** | 32,000 → **512,000** | 16,000 → **256,000** | 128 → **1,024 blocks** |
| <ItemImage id="wirelessautomate:tier_core_elite" /> **Elite** | 8,192 → **131,072** | 512,000 → **8,000,000** | 256,000 → **4,000,000** | 1,024 → **the whole dimension** |
| <ItemImage id="wirelessautomate:tier_core_ultimate" /> **Ultimate** | 131,072 → **unlimited** | 8,000,000 → **unlimited** | 4,000,000 → **unlimited** | **every dimension** |

Each step multiplies throughput by 16. Range and dimensions follow the **sender's** tier.

## How to use

| Where | How |
| --- | --- |
| **In the world** | Use the next tier's card on a placed router. |
| **In a crafting table** | A router + the next tier's card, in any slots. The router's name is kept. |

Tiers can't be skipped: a Basic router won't take the Elite card.

The same cards raise the storages ([Chest](wireless-chest.md), [Tank](wireless-tank.md),
[Battery](wireless-battery.md) and [Chemical Tank](wireless-chemical-tank.md)), the same way and
keeping their contents. Each tier's capacity is on each one's page.

## Recipes

<RecipeFor id="wirelessautomate:tier_core_advanced" />
<RecipeFor id="wirelessautomate:tier_core_elite" />
<RecipeFor id="wirelessautomate:tier_core_ultimate" />

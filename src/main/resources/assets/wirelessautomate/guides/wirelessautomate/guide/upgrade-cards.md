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

Every router starts as **Basic**. Cards raise one tier at a time: Basic → Advanced → Elite →
Ultimate. The router's configuration (faces, filters, networks) is kept.

## What each card raises

Default values, **per face and per type** (the server can change them in the config; the card's
tooltip shows your server's values). Chemicals use the fluid limit.

| Card | Items/s | Fluid and chemical (mB/s) | Energy (FE/t) | Range |
| --- | --- | --- | --- | --- |
| <ItemImage id="wirelessautomate:tier_core_advanced" /> Advanced (Basic → Advanced) | 512 → **8,192** (16×) | 32,000 → **512,000** (16×) | 16,000 → **256,000** (16×) | 128 → **1,024** blocks |
| <ItemImage id="wirelessautomate:tier_core_elite" /> Elite (Advanced → Elite) | 8,192 → **131,072** (16×) | 512,000 → **8,000,000** (16×) | 256,000 → **4,000,000** (16×) | 1,024 blocks → **the whole dimension** |
| <ItemImage id="wirelessautomate:tier_core_ultimate" /> Ultimate (Elite → Ultimate) | 131,072 → **unlimited** | 8,000,000 → **unlimited** | 4,000,000 → **unlimited** | the dimension → **every dimension** |

"Unlimited" means only the mod's time budget limits throughput (see
[Performance and config](performance.md)). Range and dimensions follow the **source's** tier.

## How to use

- **In the world:** use the next tier's card on a placed router.
- **In a crafting table:** a router + the next tier's card, in any slots, give the router in the
  new tier (its custom name is kept).

Tiers can't be skipped: a Basic router won't take the Elite card.

## Recipes

<RecipeFor id="wirelessautomate:tier_core_advanced" />
<RecipeFor id="wirelessautomate:tier_core_elite" />
<RecipeFor id="wirelessautomate:tier_core_ultimate" />

What each tier gives is in the [Router](router.md) table.

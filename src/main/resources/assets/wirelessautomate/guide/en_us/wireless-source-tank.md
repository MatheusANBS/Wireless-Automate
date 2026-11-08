---
group: armazenamentos
navigation:
  title: Wireless Source Tank
  icon: wirelessautomate:storage_source_tank
  parent: index.md
  position: 17
item_ids:
  - wirelessautomate:storage_source_tank
---


# Wireless Source Tank

<ItemImage id="wirelessautomate:storage_source_tank" scale="2" float="left" />

Holds far more **Source** than a Source Jar. It only exists with Ars Nouveau.

<br clear="all" />

<GameScene zoom="4" interactive={true}>
  <Block id="wirelessautomate:storage_source_tank" x="0" y="0" z="0" p:tier="elite" />
  <Block id="wirelessautomate:router" x="0" y="1" z="0" p:facing="up" p:tier="ultimate" />
  <Block id="wirelessautomate:storage_source_tank" x="4" y="0" z="0" p:tier="ultimate" />
  <Block id="wirelessautomate:router" x="4" y="1" z="0" p:facing="up" p:tier="ultimate" />
  <LineAnnotation from="0.5 1.5 0.5" to="4.5 1.5 0.5" color="#a46cff" thickness="0.08">
    From one Tank to another through the Source tab
  </LineAnnotation>
  <IsometricCamera yaw="200" pitch="30" />
</GameScene>

## How to use it

- **Sourcelinks** within 5 blocks deposit into it.
- **Ars machines** nearby (Enchanting Apparatus, Imbuement Chamber, rituals, Spell Turrets and deposit Relays)
  draw from it as from a Source Jar, without touching it.
- **Relays** and other Ars blocks connect to it directly.
- **Router:** on the **Source** tab, it works like any face. Between two tanks, everything moves at once.

Click the Tank to see how much it holds and the rate per second; the block's glass column also shows the level.
It has no filter: Source is one thing.

## Capacity

| Tier | Source |
| --- | --- |
| **Basic** | 3,600,000 |
| **Advanced** | 28,800,000 |
| **Elite** | 230,400,000 |
| **Emerald** | 1,843,200,000 |
| **Allthemodium¹** | 14,745,600,000 |
| **Vibranium¹** | 117,964,800,000 |
| **Unobtainium¹** | 943,718,400,000 |
| **Ultimate** | Unlimited |

¹ Only with the Allthemodium mod.

## Shared with the Chest

- **Tiers:** raised with the [Upgrade Cards](upgrade-cards.md), in the world or in a crafting table, keeping the contents.
- **Breaking:** the item takes the contents. A full one always becomes an item (in creative, without a pickaxe or in an explosion).
- **Comparator:** the signal rises with how full it is.
- **Faces:** all six are the same.

## Recipe

A Wireless Tank with Source gems and iron (only with Ars Nouveau).

<RecipeFor id="wirelessautomate:storage_source_tank" />

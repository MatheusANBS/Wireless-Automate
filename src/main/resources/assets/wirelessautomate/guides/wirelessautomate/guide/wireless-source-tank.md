---
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

Stores Ars Nouveau **Source** in large amounts, far beyond a Source Jar. It only exists with Ars
Nouveau installed. Attached to a router on the **Source** tab, it takes and gives Source like the
other storages.

<br clear="all" />

<GameScene zoom="4" interactive={true}>
  <Block id="wirelessautomate:storage_source_tank" x="0" y="0" z="0" p:tier="elite" />
  <Block id="wirelessautomate:router" x="0" y="1" z="0" p:facing="up" p:tier="ultimate" />
  <Block id="wirelessautomate:storage_source_tank" x="4" y="0" z="0" p:tier="ultimate" />
  <Block id="wirelessautomate:router" x="4" y="1" z="0" p:facing="up" p:tier="ultimate" />
  <LineAnnotation from="0.5 1.5 0.5" to="4.5 1.5 0.5" color="#a46cff" thickness="0.08">
    Source from one Tank to another through the Source tab
  </LineAnnotation>
  <IsometricCamera yaw="200" pitch="30" />
</GameScene>

## For Ars

- **Sourcelinks** within 5 blocks deposit into it.
- Ars machines nearby (Enchanting Apparatus, Imbuement Chamber, rituals, Spell Turrets and deposit Relays) draw from it, as from a Source Jar.

## With the router

On the **Source** tab it works like any other face. Between two tanks, everything moves at once.

## The screen

Click the Tank: the purple bar shows how much it holds, the percentage and the rate in Source per
second. The block's glass column also shows the level, from empty to full. The Tank has no
filter: Source is one thing.

## Capacity

| Tier | Source |
| --- | --- |
| **Basic** | 160,000 |
| **Advanced** | 2,560,000 |
| **Elite** | 40,960,000 |
| **Ultimate** | Unlimited |

## Shared with the Chest

- **Tiers:** raised with the same [Upgrade Cards](upgrade-cards.md), in the world or in a crafting table, keeping the contents.
- **Breaking:** the item takes the contents, and the tooltip shows the total. A full one always becomes an item (in creative, without a pickaxe or in an explosion).
- **Comparator:** the signal rises with how full it is.
- **Other mods** see it as a regular tank.
- **Faces:** all six are the same. It doesn't push or pull by itself: the router moves things, or another mod's cable or pipe. A machine just placed against it gets nothing.

## Recipe

A Wireless Tank with Source gems and iron (only with Ars Nouveau).

<RecipeFor id="wirelessautomate:storage_source_tank" />

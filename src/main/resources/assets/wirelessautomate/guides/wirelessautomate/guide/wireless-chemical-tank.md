---
navigation:
  title: Wireless Chemical Tank
  icon: wirelessautomate:storage_chemical_tank
  parent: index.md
  position: 14
item_ids:
  - wirelessautomate:storage_chemical_tank
---


# Wireless Chemical Tank

<ItemImage id="wirelessautomate:storage_chemical_tank" scale="2" float="left" />

The [Wireless Tank](wireless-tank.md) for **Mekanism chemicals**, many at once. It only exists with Mekanism. On
the router's **Chemicals** tab, a whole type moves at once.

<br clear="all" />

<GameScene zoom="4" interactive={true}>
  <Block id="wirelessautomate:storage_chemical_tank" x="0" y="0" z="0" p:tier="elite" />
  <Block id="wirelessautomate:router" x="0" y="1" z="0" p:facing="up" p:tier="ultimate" />
  <Block id="wirelessautomate:storage_chemical_tank" x="4" y="0" z="0" p:tier="ultimate" />
  <Block id="wirelessautomate:router" x="4" y="1" z="0" p:facing="up" p:tier="ultimate" />
  <LineAnnotation from="0.5 1.5 0.5" to="4.5 1.5 0.5" color="#a46cff" thickness="0.08">
    From one Tank to another through the Chemicals tab
  </LineAnnotation>
  <IsometricCamera yaw="200" pitch="30" />
</GameScene>

## Capacity

The [Tank](wireless-tank.md)'s, in mB, all chemicals added up.

| Tier | mB |
| --- | --- |
| **Basic** | 256,000 |
| **Advanced** | 2,048,000 |
| **Elite** | 16,384,000 |
| **Emerald** | 131,072,000 |
| **Allthemodium¹** | 1,048,576,000 |
| **Vibranium¹** | 8,388,608,000 |
| **Unobtainium¹** | 67,108,864,000 |
| **Ultimate** | Unlimited |

¹ Only with the Allthemodium mod.

## Mekanism containers

| Gesture | What it does |
| --- | --- |
| **Click** a chemical, with a tank or canister on the cursor | Fills the container (or empties it, if full). |
| **Right-click** | Empties one. |
| **Shift + click** the container in the inventory | Empties it into the Tank. |

## Input filter

**Filter** opens the chemical filter (by chemical or by mod). The **stock** means "store up to N mB".

## Shared with the Chest

- **Tiers:** raised with the same [Upgrade Cards](upgrade-cards.md), in the world or in a crafting table, keeping the contents.
- **Breaking:** the item takes the contents and the filter, and the tooltip shows the total. A full one always becomes an item (in creative, without a pickaxe or in an explosion).
- **Comparator:** the signal rises with how full it is.
- **Other mods** see it as a regular chemical tank.
- **Faces:** all six are the same. It doesn't push or pull by itself: the router moves things, or another mod's cable or pipe. A machine just placed against it gets nothing.

## Recipe

A Wireless Tank with glass bottles and iron (only with Mekanism).

<RecipeFor id="wirelessautomate:storage_chemical_tank" />

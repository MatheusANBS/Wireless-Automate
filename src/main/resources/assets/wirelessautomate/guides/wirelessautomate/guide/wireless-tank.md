---
navigation:
  title: Wireless Tank
  icon: wirelessautomate:storage_tank
  parent: index.md
  position: 12
item_ids:
  - wirelessautomate:storage_tank
---


# Wireless Tank

<ItemImage id="wirelessautomate:storage_tank" scale="2" float="left" />

A tank for **many fluids at once**, in a list like the [Wireless Chest](wireless-chest.md)'s.
Attached to a router, it trades billions of mB at once.

<br clear="all" />

<GameScene zoom="4" interactive={true}>
  <Block id="wirelessautomate:storage_tank" x="0" y="0" z="0" p:tier="elite" />
  <Block id="wirelessautomate:router" x="0" y="1" z="0" p:facing="up" p:tier="ultimate" />
  <Block id="wirelessautomate:storage_tank" x="4" y="0" z="0" p:tier="ultimate" />
  <Block id="wirelessautomate:router" x="4" y="1" z="0" p:facing="up" p:tier="ultimate" />
  <LineAnnotation from="0.5 1.5 0.5" to="4.5 1.5 0.5" color="#a46cff" thickness="0.08">
    Fluid from one Tank to another through the Fluids tab
  </LineAnnotation>
  <IsometricCamera yaw="200" pitch="30" />
</GameScene>

## Capacity

Total in mB, all fluids added up.

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

## Buckets and containers

| Gesture | What it does |
| --- | --- |
| **Click** the Tank with a full bucket | Empties the bucket (or Shift + click it, on the screen). |
| **Click** the Tank with an empty bucket | Fills it with the first fluid. |
| On the screen, **click** a fluid with a container on the cursor | Fills the container, or empties it if it's already full. Right click: empties one. |

Tanks and cells from other mods work the same way, through the screen.

## Input filter

The **Filter** button opens the fluid [filter screen](filters.md): only what it accepts gets in, and
**stock** means "store up to N mB".

## Shared with the Chest

- **Tiers:** raised with the same [Upgrade Cards](upgrade-cards.md), in the world or in a crafting table, keeping the contents.
- **Breaking:** the item takes the contents and the filter, and the tooltip shows the total. A full one always becomes an item (in creative, without a pickaxe or in an explosion).
- **Comparator:** the signal rises with how full it is.
- **Other mods** see it as a regular tank.
- **Faces:** all six are the same. It doesn't push or pull by itself: the router moves things, or another mod's cable or pipe. A machine just placed against it gets nothing.

## Recipe

<RecipeFor id="wirelessautomate:storage_tank" />

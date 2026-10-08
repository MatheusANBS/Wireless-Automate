---
navigation:
  title: Wireless Battery
  icon: wirelessautomate:storage_battery
  parent: index.md
  position: 13
item_ids:
  - wirelessautomate:storage_battery
---


# Wireless Battery

<ItemImage id="wirelessautomate:storage_battery" scale="2" float="left" />

Stores energy (FE) far beyond an `int`: on Ultimate, unlimited. Attached to a router on the
**Energy** tab, it charges and discharges billions of FE per tick.

<br clear="all" />

<GameScene zoom="4" interactive={true}>
  <Block id="wirelessautomate:storage_battery" x="0" y="0" z="0" p:tier="elite" />
  <Block id="wirelessautomate:router" x="0" y="1" z="0" p:facing="up" p:tier="ultimate" />
  <Block id="wirelessautomate:storage_battery" x="4" y="0" z="0" p:tier="ultimate" />
  <Block id="wirelessautomate:router" x="4" y="1" z="0" p:facing="up" p:tier="ultimate" />
  <LineAnnotation from="0.5 1.5 0.5" to="4.5 1.5 0.5" color="#a46cff" thickness="0.08">
    Energy from one Battery to another through the Energy tab
  </LineAnnotation>
  <IsometricCamera yaw="200" pitch="30" />
</GameScene>

## Capacity

| Tier | FE |
| --- | --- |
| **Basic** | 16,000,000 |
| **Advanced** | 1,000,000,000 |
| **Elite** | 64,000,000,000 |
| **Ultimate** | Unlimited |

## The screen

Click the Battery: the bar shows the charge and the percentage, and below it the change per tick
(**charging**, **draining** or **idle**). Hover the bar for the exact value. The Battery has no
filter: energy is just one thing.

## Shared with the Chest

- **Tiers:** raised with the same [Upgrade Cards](upgrade-cards.md), in the world or in a crafting table, keeping the contents.
- **Breaking:** the item takes the contents, and the tooltip shows the total. A full one always becomes an item (in creative, without a pickaxe or in an explosion).
- **Comparator:** the signal rises with how full it is.
- **Other mods** see it as a regular battery.

## Recipe

<RecipeFor id="wirelessautomate:storage_battery" />

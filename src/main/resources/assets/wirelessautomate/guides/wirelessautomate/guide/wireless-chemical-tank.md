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

The [Wireless Tank](wireless-tank.md) for **Mekanism chemicals** (gases, infuse types, pigments,
slurries): many at once, within the tier's capacity. It only exists with Mekanism installed.
Attached to a router on the **Chemicals** tab, a whole type moves at once.

<br clear="all" />

<GameScene zoom="4" interactive={true}>
  <Block id="wirelessautomate:storage_chemical_tank" x="0" y="0" z="0" p:tier="elite" />
  <Block id="wirelessautomate:router" x="0" y="1" z="0" p:facing="up" p:tier="ultimate" />
  <Block id="wirelessautomate:storage_chemical_tank" x="4" y="0" z="0" p:tier="ultimate" />
  <Block id="wirelessautomate:router" x="4" y="1" z="0" p:facing="up" p:tier="ultimate" />
  <LineAnnotation from="0.5 1.5 0.5" to="4.5 1.5 0.5" color="#a46cff" thickness="0.08">
    Chemical from one Tank to another through the Chemicals tab
  </LineAnnotation>
  <IsometricCamera yaw="200" pitch="30" />
</GameScene>

## Capacity

The same as the Tank: 1,000,000, 64,000,000 and 4,000,000,000 mB, and unlimited on Ultimate.

## Mekanism containers

On the screen, with a Mekanism chemical tank or canister on the cursor: clicking a chemical fills
the container with it, or empties it if it's already full; right click empties one. Shift + click
a container in the inventory empties it into the Tank.

## Input filter

The **Filter** button opens the chemical filter screen (by chemical or by mod); the **stock** means
"store up to N mB".

## Shared with the Chest

- **Tiers:** raised with the same [Upgrade Cards](upgrade-cards.md), in the world or in a crafting table, keeping the contents.
- **Breaking:** the item takes the contents and the filter, and the tooltip shows the total. A full one always becomes an item (in creative, without a pickaxe or in an explosion).
- **Comparator:** the signal rises with how full it is.
- **Other mods** see it as a regular chemical tank.

## Recipe

A Wireless Tank with glass bottles and iron (only with Mekanism).

<RecipeFor id="wirelessautomate:storage_chemical_tank" />

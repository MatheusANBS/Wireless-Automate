---
navigation:
  title: Wireless Chest
  icon: wirelessautomate:storage_chest
  parent: index.md
  position: 11
item_ids:
  - wirelessautomate:storage_chest
---


# Wireless Chest

<ItemImage id="wirelessautomate:storage_chest" scale="2" float="left" />

Stores **millions** of items, of as many types as you like. Between two Wireless Chests, a whole
type moves **at once**.

<br clear="all" />

<GameScene zoom="4" interactive={true}>
  <Block id="wirelessautomate:storage_chest" x="0" y="0" z="0" p:tier="elite" />
  <Block id="wirelessautomate:router" x="0" y="1" z="0" p:facing="up" p:tier="ultimate" />
  <Block id="wirelessautomate:storage_chest" x="4" y="0" z="0" p:tier="ultimate" />
  <Block id="wirelessautomate:router" x="4" y="1" z="0" p:facing="up" p:tier="ultimate" />
  <LineAnnotation from="0.5 1.5 0.5" to="4.5 1.5 0.5" color="#a46cff" thickness="0.08">
    A whole type at a time
  </LineAnnotation>
  <BlockAnnotation x="0" y="0" z="0" color="#45d6cc">
    Elite chest · top face on **Extract**
  </BlockAnnotation>
  <BlockAnnotation x="4" y="0" z="0" color="#a46cff">
    Ultimate chest · top face on **Insert**
  </BlockAnnotation>
  <IsometricCamera yaw="200" pitch="30" />
</GameScene>

## How to use it

1. Place the chest and attach a router to it.
2. Set up the face on the router's Items tab.
3. Click the chest with an empty hand to see what's inside.

## Capacity

Total items, all types added up.

| Tier | Items |
| --- | --- |
| **Basic** | 32,768 |
| **Advanced** | 262,144 |
| **Elite** | 2,097,152 |
| **Emerald** | 16,777,216 |
| **Allthemodium¹** | 134,217,728 |
| **Vibranium¹** | 1,073,741,824 |
| **Unobtainium¹** | 8,589,934,592 |
| **Ultimate** | Unlimited |

¹ Only with the Allthemodium mod.

Raise the tier with [Upgrade Cards](upgrade-cards.md), in the world or in a crafting table, **keeping the contents**.

## The screen

| Gesture | What it does |
| --- | --- |
| Type in the search box | **@mod** searches by mod (e.g. **@mekanism**); **#** by tag and tooltip (e.g. **#ingots**). |
| Button next to the search | Changes the order: amount, name or mod. |
| **Click** an item | Takes a stack. Right click: half. |
| **Shift + click** an item | Sends the stack to your inventory. Dragging: one of each. |
| **Shift + double-click** with an item on the cursor | Fills your inventory with that item. |
| **Wheel** over an item | Down takes one, up stores one. |
| **Click** the list with an item on the cursor | Stores it all. Right click: just one. |
| **Shift + click** in your inventory | Stores the item. |

## Input filter

The **Filter** button opens a [filter](filters.md) that applies to **everything that gets in**:
router, hopper, another mod or the screen.

| In the filter entry | In the chest |
| --- | --- |
| Whitelist | Only what's on the list gets in. |
| Blacklist | Everything gets in except what's on the list. |
| **Stock** N | Stores **up to N** of that item; the rest stays where it was. |

## Upgrades

Two slots, to the left of the list. Click a slot with the upgrade on the cursor; the tab next to it opens the panel.

| Upgrade | Recipe |
| --- | --- |
| <ItemImage id="wirelessautomate:crafting_upgrade" /> **Crafting Upgrade** | Crafting table + Signal Crystal. |
| <ItemImage id="wirelessautomate:compacting_upgrade" /> **Compacting Upgrade** | Piston + Signal Crystal + iron block (shapeless). |

### Crafting

A 3×3 crafting table inside the chest.

- The grid **refills itself** from what the chest has.
- **Shift** + click the result: repeats up to one stack.
- In **JEI**, **+** sets up the recipe from the chest first, then your inventory.

### Compacting

It packs and unpacks chains like nugget, ingot, block (or cobblestone up to AllTheCompressed's 9x)
by itself. Enchanted or named items are left out.

1. The upgrade starts **Off**.
2. In the panel, pick **Compress** (highest form) or **Decompress** (the base) for all chains.
3. For a single chain, use the arrows on its row. **Global** goes back to the general mode.

With the target on ingot, nuggets **and** blocks become ingots.

- The leftover stays in the lower form: 10 ingots with the target on block give 1 block and 1 ingot.
- Decompressing converts **only what fits**; the rest stays in the high form.
- The router only sees the stored form: to pull ingots, leave the target on ingot.
- **Stock** counts the whole chain (11 blocks and 1 ingot are worth 100 ingots).
- The same material from different mods is a single row: click the mod name to choose which one to gather into.
- A chain with its own rule stays in the list, dimmed, even when empty.
- With Compacting, the crafting grid steps down one form when the exact item is missing.

## Break and carry

The item takes **the contents, the filter, the upgrades and the crafting grid**. A full chest always
becomes an item, even in creative or in an explosion.

## With other blocks

- **Hoppers, AE2, Refined Storage and other mods** see it as a regular inventory, one slot per type.
- **Comparator**: the signal rises with how full it is (on Ultimate, 1 with anything inside).

## Recipe

<RecipeFor id="wirelessautomate:storage_chest" />

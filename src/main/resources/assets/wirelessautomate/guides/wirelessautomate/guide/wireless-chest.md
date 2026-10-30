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

A chest without slots: it stores **millions** of items, by type, as many types as you like. The
only limit is the tier's total item count. Attached to a router it's the network's best friend:
between two Wireless Chests, a whole type moves **at once**, with no one-stack-at-a-time cap like
regular chests.

<br clear="all" />

<GameScene zoom="4" interactive={true}>
  <Block id="wirelessautomate:storage_chest" x="0" y="0" z="0" p:tier="elite" />
  <Block id="wirelessautomate:router" x="0" y="1" z="0" p:facing="up" p:tier="ultimate" />
  <Block id="wirelessautomate:storage_chest" x="4" y="0" z="0" p:tier="ultimate" />
  <Block id="wirelessautomate:router" x="4" y="1" z="0" p:facing="up" p:tier="ultimate" />
  <LineAnnotation from="0.5 1.5 0.5" to="4.5 1.5 0.5" color="#a46cff" thickness="0.08">
    A whole type at a time: millions of items in an instant
  </LineAnnotation>
  <BlockAnnotation x="0" y="0" z="0" color="#45d6cc">
    Elite chest · router with the top face on **Extract**
  </BlockAnnotation>
  <BlockAnnotation x="4" y="0" z="0" color="#a46cff">
    Ultimate chest · router with the top face on **Insert**
  </BlockAnnotation>
  <IsometricCamera yaw="200" pitch="30" />
</GameScene>

## Capacity

Total items, all types added up. The item's tooltip shows your server's value.

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

Raise the tier with the same [Upgrade Cards](upgrade-cards.md) as the router, in the world or in a
crafting table, **keeping the contents**.

## The screen

Click the chest with an empty hand. The list shows each type with its amount; hover to see the
exact number.

| I want to... | How |
| --- | --- |
| Find an item | Type in the search box. **@mod** searches by mod (e.g. **@mekanism**) and **#** by tags and tooltip text (e.g. **#ingots**, **#not yet eaten**). The search starts empty every time you open it. |
| Change the order | The button next to the search box cycles: amount, name and mod. |
| Take a stack | Click the item. Right click: half a stack (with less than a stack stored, half of what is there). |
| Send it straight to the inventory | **Shift** + click the item. Hold **Shift** and drag across the list: one stack of each item you pass over. While **Shift** is held, items keep their places. |
| Fill the inventory with an item | With any item on the cursor, **Shift** + double-click the item. |
| Move one item at a time | Mouse wheel over the item: down takes one, up stores one from your inventory. Over an item in your inventory: down stores one, up pulls one from the chest. |
| Store what's on the cursor | Click anywhere on the list. Right click: just one. |
| Store from the inventory | **Shift** + click the item in your inventory. |

The bar under the list shows how much of the tier is used.

## Input filter

The **Filter** button, at the top of the screen, opens the same [filter](filters.md) screen as the
router. It decides what can **get in** the chest, by any path: router, hopper, another mod or the
screen.

| In the filter entry | In the chest |
| --- | --- |
| Whitelist | Only what's on the list gets in. |
| Blacklist | Everything gets in except what's on the list. |
| **Stock** N | Stores **up to N** of that item; the rest stays where it was. |

The button's dot lights up in the tier color when there's a filter. Filter Cards work too: import
and export them from the filter screen.

## Upgrades

The chest has **two upgrade slots**, to the left of the list. Each upgrade fits once. Click a slot
with the upgrade on the cursor to place it; click the filled slot to open its panel, to the right
of the list.

| Upgrade | Recipe |
| --- | --- |
| <ItemImage id="wirelessautomate:crafting_upgrade" /> **Crafting Upgrade** | Crafting table + Signal Crystal. |
| <ItemImage id="wirelessautomate:compacting_upgrade" /> **Compacting Upgrade** | Piston + Signal Crystal + iron block (shapeless). |

### Crafting

A 3×3 crafting table inside the chest. Set up the recipe in the grid and take the result.

- The grid **refills itself** from what the chest has.
- **Shift** + click the result: repeats the recipe up to one stack and sends it to your inventory.
- **Return**: the grid goes back into the chest.
- In **JEI**, the **+** button sets up the recipe from the chest first, then your inventory.

### Compacting

It packs and unpacks item chains by itself: nugget, ingot, block; or cobblestone up to
AllTheCompressed's 9x. A chain only counts if the crafting table can also undo it. Enchanted or
named items don't compact.

1. Placing the upgrade changes nothing: the mode starts **Off**.
2. In the panel, pick the mode for all chains: **Compress** (the highest form) or **Decompress** (the base).
3. For a single chain, use the arrows or the wheel on its row; click the icon to pick the form from a list. **Global** goes back to the general mode.

The target works both ways: what is **below** it is compressed up to it, and what is **above** it is
decompressed down to it. With the target on ingot, nuggets become ingots and blocks become ingots.

- The leftover that doesn't make a full factor stays in the lower form: 10 ingots with the target on block give 1 block and 1 ingot.
- Decompressing converts **only what fits** in the chest; the rest stays in the high form, nothing is lost.
- The router only sees the stored form: to pull ingots, leave the chain's target on ingot.
- The **Stock** limit of a chain item counts the whole chain (11 blocks and 1 ingot are worth 100 ingots).
- With Compacting, the crafting grid steps down one form when the exact item is missing.

## Break and carry

Break the chest: the item takes **all the contents and the filter** with it, and the tooltip shows
the total and the types. Place it again and everything is there. A full chest always becomes an
item: in creative, without a pickaxe or in an explosion, nothing is lost.

The **upgrades and the crafting grid items** also go in the item, even with an empty chest, and come
back when it is placed. If the block disappears without becoming an item (a command, another mod),
they drop on the ground.

## With other blocks

- **Hoppers, AE2, Refined Storage and other mods** see the chest as a regular inventory, one slot per type.
- **Comparator**: the signal rises with how full it is (on Ultimate, 1 with anything inside).
- Between a Wireless Chest and a regular chest the router is fast too, but the regular chest still
  moves one stack at a time.

## Recipe

<RecipeFor id="wirelessautomate:storage_chest" />

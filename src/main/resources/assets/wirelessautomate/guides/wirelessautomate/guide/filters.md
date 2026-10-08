---
navigation:
  title: Filters
  icon: minecraft:hopper
  parent: index.md
  position: 5
---


# Filters

Each machine face, for each type, has a **built-in filter** with no entry limit, plus two
[Filter Card](filter-card.md) slots. Open it with the **Edit** button on the router screen.

## Golden rule

| Situation | Result |
| --- | --- |
| No filter has entries | **Everything passes.** |
| The built-in filter or any card accepts | It passes. |
| Whitelist | Only what matches an entry passes. |
| Blacklist | Everything passes except what matches. |

At the source, the filter decides what **leaves**; at the destination, what **enters**.

## Entry types

| Rule | Example | Items | Fluids | Chemicals |
| --- | --- | --- | --- | --- |
| **Exact** | Iron ingot, water, hydrogen | Yes | Yes | Yes |
| **Tag** | `#c:ingots`, `#c:ores` | Yes | Yes | No |
| **Mod** | `@mekanism` | Yes | Yes | Yes |
| **Rule** | Any enchanted item | Yes | No | No |

## The screen

The entries are on the left, one per row, with the search on top and your inventory below. On the
right, four tabs. The window grows from its edges and the corner grip, like the Chest's.

| Tab | What for |
| --- | --- |
| **Entry** | The selected entry: what it matches, stock, Remove and, on an item, **See tags**. |
| **Tags** | Find and check tags and mods (see below). For chemicals it becomes **Add**: id or `@mod`. |
| **Rule** | Build a property rule (items only). |
| **More** | Filter Card, components and Clear. |

## How to add

| Way | How |
| --- | --- |
| **Inventory** | Shift + click the item (for fluids and chemicals, a full bucket or tank). |
| **JEI** | Drag from the JEI list onto the entry list, or Shift + click it. You don't need the item. |
| **An item's tags** | In the **Tags** tab, click the inspector slot with the item on the cursor (you keep it), or Ctrl + click the item in your inventory. Check the tags and click **Add**. |
| **Searching** | In the **Tags** tab, type part of the name (`ingots`) to see every tag in the game, or `@` for mods. Hover a row to see its items. |

## Property rules

A rule matches an item by what it **is**, not by which item it is. Each condition has
**- / Yes / No**, and the rule matches items that meet **all** the set ones. Your inventory lights up
on what it matches before you add it.

| Condition | Example |
| --- | --- |
| **Enchanted** | Any item with an enchantment, enchanted books included. |
| **Damaged** | Has some wear. **No** = undamaged. |
| **Renamed** | Named on an anvil. |
| **Has potion** | Potions, tipped arrows. |
| **Stackable** | **No** = tools, armor and anything that sits alone in a slot. |
| **Has contents** | A shulker box or bundle with something inside. |
| **Enchantment** | One enchantment with a minimum level: Fortune ≥ III. |
| **Durability** | Remaining ≥ or < a percentage: < 25% sends it to repair. |
| **Only in** | Limits it to a tag or mod: Enchanted + `#c:armors` = enchanted armor only. |

To edit a rule, select it in the list and click **Edit**.

## Options

| Option | What it does |
| --- | --- |
| **Whitelist / blacklist** | Inverts the filter. |
| **Components** (items) | Ignore: enchanted pickaxe = pickaxe. Require: only identical. To match only enchanted ones, use a rule. |
| **Stock** | At a destination, accept only up to N. At a source, always keep N. Select the entry to set it. |

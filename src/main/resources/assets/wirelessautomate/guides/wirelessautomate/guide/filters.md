---
navigation:
  title: Filters
  icon: minecraft:hopper
  parent: index.md
  position: 5
---


# Filters

Each face, for each type, has a **built-in filter** with no entry limit and two
[Filter Card](filter-card.md) slots.

## How to use it

1. On the router screen, pick the face and click **Edit**.
2. Shift + click an item in your inventory to add it (fluids and chemicals: a full bucket or tank).

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

## Other ways to add

- **JEI:** drag onto the entry list, or Shift + click. You don't need the item.
- **Tags:** in the **Tags** tab, Ctrl + click an item in your inventory, check the tags and click
  **Add**. Or type part of the name (`ingots`), or `@` for mods. For chemicals, the tab becomes
  **Add**: id or `@mod`.

## Property rules

In the **Rule** tab (items only). Each condition has **- / Yes / No**; the item must meet **all** the
set ones.

| Condition | Example |
| --- | --- |
| **Enchanted** | Enchanted books included. |
| **Damaged** | Has wear. **No** = undamaged. |
| **Renamed** | Named on an anvil. |
| **Has potion** | Potions, tipped arrows. |
| **Stackable** | **No** = tools, armor etc. |
| **Has contents** | A shulker box or bundle with something inside. |
| **Enchantment** | One enchantment with a minimum level (1 to 255): Fortune ≥ III. |
| **Durability** | Remaining ≥ or < a percentage: < 25% sends it to repair. |
| **Only in** | Limits it to a tag or mod: Enchanted + `#c:armors` = enchanted armor only. |

## Options

| Option | What it does |
| --- | --- |
| **Components** (items) | Ignore: enchanted pickaxe = pickaxe. Require: only identical. To match only enchanted ones, use a rule. |
| **Stock** | At a destination, accept only up to N. At a source, always keep N. Select the entry to set it. |

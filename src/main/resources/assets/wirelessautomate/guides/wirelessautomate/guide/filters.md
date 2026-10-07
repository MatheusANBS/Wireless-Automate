---
navigation:
  title: Filters
  icon: minecraft:hopper
  parent: index.md
  position: 5
---


# Filters

Each machine face, for each type, has a **built-in filter** with no entry limit, plus two
[Filter Card](filter-card.md) slots. A resource passes if the built-in filter **or** any card
accepts it. An empty filter lets everything through.

Open it with the **Edit** button on the router screen.

## Entries

| Rule | Example | Applies to |
| --- | --- | --- |
| Exact | Iron ingot, water, hydrogen | Items, fluids, chemicals |
| Tag | `#c:ingots`, `#c:ores` | Items, fluids |
| Mod | `@mekanism` | Items, fluids, chemicals |

- **Add:** Shift + click an item in your inventory (a bucket or tank, for fluids and chemicals),
  drag from JEI or Shift + click in the JEI list (you don't need the item), or type the rule in
  the **More** box (`#tag`, `@mod` or, for chemicals, the id).
- **Whitelist or blacklist**, per filter.
- **Components** (items): ignore (enchanted pickaxe = pickaxe) or require them to match.
- **Stock:** at a destination, accept only up to N; at a source, always keep N.

Checking an item costs the same with 9 or thousands of entries: the filter is compiled.

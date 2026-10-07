---
navigation:
  title: Chemicals (Mekanism)
  icon: minecraft:glass_bottle
  parent: index.md
  position: 11
---


# Mekanism chemicals

With **Mekanism** installed, the router gets a **Chemicals** tab: gases, infuse types, pigments
and slurries. Without Mekanism the tab doesn't show and the rest of the mod works the same.

## What works

| Feature | Chemicals |
| --- | --- |
| Mode, priority and redstone per face | Yes, like the other tabs. |
| Its own network on the tab | Yes. |
| Throughput | The tier's fluid limit (see [Router](router.md)). |
| Exact and mod (`@mod`) filter | Yes, with stock. |
| Tag filter | No. |
| Filter Card | No: use the face's built-in filter. |

## Adding chemicals to the filter

| Way | How |
| --- | --- |
| **Inventory** | Shift + click a tank holding the chemical. |
| **JEI** | Drag the chemical from the list onto the grid. |
| **Typing** | Under **More**, the id: `mekanism:hydrogen`, `mekanism:oxygen`... |

## Important: Mekanism machine faces

Mekanism machines and tanks come with their faces **disabled** in Mekanism's own side
configuration. Enable the face the router will use with Mekanism's configurator tool, just like
you would for a tube; otherwise the router can't see the chemical and the face mode shows as
unavailable.

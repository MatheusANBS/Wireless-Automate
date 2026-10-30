---
navigation:
  title: Configurator
  icon: wirelessautomate:configurator
  parent: index.md
  position: 8
item_ids:
  - wirelessautomate:configurator
---


# Configurator

<ItemImage id="wirelessautomate:configurator" scale="2" float="left" />

Copies a router's configuration and pastes it on another.

<br clear="all" />

## How to use it

1. Shift + click the router that's already set up: copies faces, filters, priorities, redstone and
   networks.
2. Click another router: pastes on it.

The item holds a single copy. The tooltip shows what's copied.

| Gesture | What it does |
| --- | --- |
| Shift + click a router | Copy |
| Click a router | Paste on it |
| Shift + click a block without a router | Clear the wand |
| Shift + mouse wheel | Change the pasted type |
| Hold Left Alt | Open the pasted type wheel |

## Pasted type

Copying always copies everything. The **pasted type** picks what goes to the router: **All**
(default), Items, Fluids, Energy, Chemicals (with Mekanism) or Source (with Ars Nouveau). On a single
type, the router's other tabs stay as they were.

Example: copy a fluids router, switch to **Fluids** and paste on the others without touching their
items or energy.

On the wheel (Left Alt; change the key in Controls), release the key over a slice to pick it.
Releasing in the center or Esc closes without changing anything.

## Good to know

- The copy is relative to the router: it works with the router on any face.
- A network is only pasted if you can use it, and a face only leaves a network you manage (your own,
  or any if you are an operator). Otherwise the face keeps its own. A face with no network in the copy leaves the target's network alone.
- The network applies to the attached machine; Range Upgrade machines stay as they are.
- To paste on many routers at once, use the [Tablet](network-tablet.md) selection.

## Recipe

<RecipeFor id="wirelessautomate:configurator" />

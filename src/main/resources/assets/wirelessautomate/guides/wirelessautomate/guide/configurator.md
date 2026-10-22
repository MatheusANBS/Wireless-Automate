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

## Spec sheet

| | |
| --- | --- |
| **Copies** | Faces, filters, priorities, redstone and each face's network. |
| **Holds** | A single copy, in the item itself. The tooltip shows what's copied and the actions. |
| **Pasted type** | All (default), Items, Fluids, Energy, Chemicals (with Mekanism) or Source (with Ars Nouveau). |

## Actions

| Action | What it does |
| --- | --- |
| Shift + click a router | Copy |
| Click a router | Paste on it |
| Shift + click a block without a router | Clear the wand |
| Shift + mouse wheel | Change the pasted type |
| Hold Left Alt | Open the wheel |

## The wheel

With the Configurator in hand, **hold Left Alt** (change the key in Controls, under Wireless
Automate): a wheel opens in the middle of the screen with the **pasted type**. The current choice has an
orange border and the center tells what the slice under the mouse does.

| | |
| --- | --- |
| **Release the key** | Picks the slice under the mouse and closes. |
| **Click** | Picks without closing. |
| **Release in the center or Esc** | Closes without changing anything. |

Copying always copies everything; the **pasted type** picks what goes to the router. On **All**,
every tab. On a single type, only that tab's faces (with their networks): the router's other tabs stay as they
were. For example, copy a router set up only for fluids, switch to **Fluids** and paste on the others
without touching their items or energy.

## Good to know

| | |
| --- | --- |
| **Orientation** | The copy is relative to the router: it works with the router on any face. |
| **Networks** | A face's network is only pasted if you can use it, and a face only leaves a network you manage (your own, or any if you are an operator); otherwise the face keeps its own. A face with no network in the copy leaves the target's network alone. The network applies to the attached machine's faces; Range Upgrade machines stay as they are. |
| **Many at once** | To paste on several routers, use the [Tablet](network-tablet.md) selection. |

## Recipe

<RecipeFor id="wirelessautomate:configurator" />

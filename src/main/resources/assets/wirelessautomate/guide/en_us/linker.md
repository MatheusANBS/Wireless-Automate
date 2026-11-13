---
group: ferramentas
navigation:
  title: Linker
  icon: wirelessautomate:linker
  parent: index.md
  position: 7
item_ids:
  - wirelessautomate:linker
---


# Linker

<ItemImage id="wirelessautomate:linker" scale="2" float="left" />

Links **machines** to a range router, one at a time or all the ones of the same block. It also opens
the router that reaches a machine.

<br clear="all" />

## How to use it

1. Put the Range Upgrade in a router and click it with the Linker. Click it again to release it.
2. Click a machine to link it. Click it again to unlink it. If its group ends up empty, the group's
   cards go back to your inventory. A machine belongs to one router only: if it already belongs to
   another (linked or attached to it), the action bar says which.
3. To link all the matching machines at once, hold **Ctrl** (**Cmd** on Mac) and click one of them:
   the ones of the same block **touching it or each other** (by a face, an edge or a corner), like a
   row or a wall, and within the router's radius go in, nearest **to the clicked one** first, up to the
   tier's limit. The action bar says how many, for example
   **Linked 7 Furnaces**; if the limit cut it, **Linked 32 Furnaces · tier limit**. While sprinting, Ctrl is
   the sprint key and the click counts as a normal click.
4. To unlink the touching ones at once, do the same gesture on a machine that is already linked.
5. Click the air to see the chosen router. **Shift** + click the air releases it.
6. Switch to **Open** mode (Shift + mouse wheel) and click a machine to set up its face in the
   router.

The Linker doesn't touch networks: pick each face's network in the router screen's **Network** row or
with the [Tablet](network-tablet.md).

## Gestures

| Gesture | Link | Open |
| --- | --- | --- |
| Click a router | Picks the router | — |
| Click a machine | Links or unlinks it | Opens the router that reaches it |
| Ctrl (Cmd on Mac) + click a machine | Links the ones of the same block touching it, in range; if it is already linked, unlinks the touching ones | — |
| Click a Signal Screen | Picks the Screen | — |
| Click a mod Chest, Battery or Tank | With a Screen picked, links or unlinks it | Opens the router that reaches it |
| Click the air | Shows the chosen router | Shows the mode's hint |
| Shift + click the air | Releases the chosen router and Screen | Shows the mode's hint |
| Shift + mouse wheel | Goes to **Open** | Goes to **Link** |

## Open mode

If more than one router reaches the machine (the one attached to it and the range ones), a list shows
the name, whether it is **attached** or reaches by **range**, and the distance. Click a row to open it.
Only routers within your reach that you can touch are listed.

## Linking storages to a Screen

1. Click a [Signal Screen](signal-screen.md). The action bar shows how many storages it already has.
2. Click a mod **Chest, Battery or Tank** to link it. Click it again to unlink.

No limit on distance or dimension. You must be allowed to edit the Screen. If the storage's chunk is
unloaded, the Screen keeps the last number, dimmed.

## Recipe

<RecipeFor id="wirelessautomate:linker" />

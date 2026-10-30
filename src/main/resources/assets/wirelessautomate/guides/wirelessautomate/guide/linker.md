---
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

Links **machines** to a range router, one at a time or a whole area. It also opens the router that
reaches a machine.

<br clear="all" />

## How to use it

1. Put the Range Upgrade in a router and click it with the Linker.
2. Click a machine to link it. Click it again to unlink.
3. For a whole room: holding Shift, click two corners, click the air to open the screen and click
   **Link area**.
4. Switch to **Open** mode (Shift + mouse wheel) and click a machine to set up its face in the
   router.

The Linker doesn't touch networks: pick each face's network in the router screen's **Network** row or
with the [Tablet](network-tablet.md).

## Gestures

| Gesture | Link | Open |
| --- | --- | --- |
| Click a router | Picks the router | — |
| Click a machine | Links or unlinks it | Opens the router that reaches it |
| Click a Signal Screen | Picks the Screen | — |
| Click a mod Chest, Battery or Tank | With a Screen picked, links or unlinks it | Opens the router that reaches it |
| Click the air | Opens the screen | Shows the mode's hint |
| Shift + click two blocks | Marks the area corners | — |
| Shift + mouse wheel | Goes to **Open** | Goes to **Link** |

## The screen (Link mode)

- Shows the chosen router and the slots used (for example **7/32 machines**). If it doesn't link yet
  (no Range Upgrade), the reason shows there.
- **Link area** links the area's machines the router reaches, nearest first, up to the tier's limit.
- **Unlink**, in the header, makes the clicks and the area button take machines out instead of
  putting them in.
- **Clear** erases the corners.

The area can be up to 262,144 blocks (for example 64 × 64 × 64), and you need to be within 64
blocks of it.

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

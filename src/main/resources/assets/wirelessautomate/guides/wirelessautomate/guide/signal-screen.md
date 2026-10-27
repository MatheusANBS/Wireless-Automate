---
navigation:
  title: Signal Screen
  icon: wirelessautomate:signal_screen
  parent: index.md
  position: 20
item_ids:
  - wirelessautomate:signal_screen
---


# Signal Screen

<ItemImage id="wirelessautomate:signal_screen" scale="2" float="left" />

A panel you attach to a wall and stretch to any size, from **1 × 1 up to 32 × 32 blocks**. It shows your
[networks'](networks.md) numbers live: what comes in, what goes out and how many faces are moving.

<br clear="all" />

## Placing it

1. Click with the Screen on the **side of a solid block**: it sits flush against the wall, facing you. It can't go on
   the floor or the ceiling, and it drops if the wall behind it goes away.
2. Click it with an **empty hand** to open its settings. Only whoever placed it (and operators) can configure it;
   everyone else just watches.

## Choosing the size

In the **Size** tab:

| Part | How to use |
| --- | --- |
| **Width** and **Height** | ‹ and › change it 1 block at a time; with **Shift**, 4 at a time. |
| **Grows from the block toward** | ↗ up and right, ↖ up and left or ✥ center: where the block you placed sits inside the screen. |
| **Preview** | The wall around it, with the screen's area. The red cell is the one in the way. |
| **Apply** | Stretches the screen. It stays off while something is in the way. |

The big screen **takes no blocks**: it is drawn over the wall. To fit, every block of the area needs a **solid wall
behind it** and **free space in front** (air or grass, for example; a torch is in the way), and the area can't cross
another Screen.

## What to show

In the **Content** tab, pick a **network** (only the ones you can use are listed) and a **type** (Items, Fluids,
Energy...) and click **Apply the Network panel**. The Screen then shows the network's name, what **comes in** (▲),
what **goes out** (▼) and how many faces are **moving** (⇄), in the type's unit. **Clear** (two clicks) wipes the content.

The numbers arrive once a second while someone is nearby. A private network that whoever placed the Screen lost access
to shows **—**.

## Recipe

<RecipeFor id="wirelessautomate:signal_screen" />

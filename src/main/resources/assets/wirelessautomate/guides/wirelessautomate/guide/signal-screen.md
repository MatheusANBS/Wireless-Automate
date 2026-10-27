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

## What to show: the editor

In the **Edit** tab you build what the Screen shows out of **pieces**. The window grows to fill the game screen; if it
doesn't fit, lower the interface scale.

| Piece | What for |
| --- | --- |
| **Value** | A live number from a [network](networks.md): what comes in, what goes out, what is stored, how many faces move... |
| **Text** | A fixed phrase. |
| **Shape** | A rectangle with a fill, border and corners: card, frame, divider. |

- **Picking and moving:** click a piece to pick it (on a grouped piece, the whole group; two clicks enter the group).
  **Shift** + click adds or removes from the pick; click the empty space to let go. Drag to move and pull the corner
  handle to resize. Everything snaps to a 4 px grid; hold **Alt** to adjust 1 px at a time.
- **Groups and layers:** **Group** joins the picked pieces to move and resize together; **Ungroup** lets them go.
  Below, **Layers** lists the pieces from top to bottom: drag a row to change the order, use the eye to hide, and
  **[** and **]** to raise and lower.
- **Tabs on the right:** **Data** links a Value to a network (and picks the type and what to read); **Format** sets
  decimals, abbreviating (k, M, G), sign, prefix, suffix and unit; **Style** changes position, size, colors, border,
  corners, opacity and font; **Rules** changes the color, hides or blinks when the number crosses a value.
- **Saving:** nothing reaches the wall until you click **Save** (or **Ctrl+S**). Closing with changes takes two clicks
  on the ✕ (**Discard?**). The numbers of a new link only show up after saving.

| Shortcut | What it does |
| --- | --- |
| **Ctrl+Z**, **Ctrl+Y** (or **Ctrl+Shift+Z**) | Undo and redo. |
| **Ctrl+C**, **Ctrl+X**, **Ctrl+V** | Copy, cut and paste, even from one Screen to another. |
| **Ctrl+D**, **Delete** | Duplicate and remove. |
| **Ctrl+A**, **Ctrl+G**, **Ctrl+Shift+G** | Pick all, group and ungroup. |
| **Arrow keys** | Move 4 px; with **Shift**, 32 px; with **Alt**, 1 px. |

### Themes and background

With **nothing picked**, the right column shows the Screen's **base**. The **Theme** is a palette of six colors
(background, card, text, muted, high and low): there are four ready ones (Graphite, Phosphor, Amber and Porcelain) and
your own. The colors of pieces set "from the theme" (the round dots) follow the swap; the square ones are fixed. Edit
the six colors by their `#RRGGBB` code and use **Save theme** to keep the palette under a name. The **Background** can
be the theme's, a fixed color or a gradient, and you can turn on the scan **lines** and the preview **grid**. The
Screen keeps the whole palette: deleting one of your themes later changes no Screen.

### Components

In the left column, **Components** are groups of pieces ready to insert: **Network panel**, **Number card** and
**Title** come built in, and you keep your own. Click a component to insert it, in a new group, in the first free
spot. To keep your own, pick the pieces, click **Save as component** (⊕) and name it; the ✎ renames and the ✕ removes,
in two clicks. Components and themes live in your library and work on any Screen of yours. A component linked to a
network you can no longer use comes in with **—**.

The numbers arrive once a second while someone is nearby. A private network that whoever placed the Screen lost access
to shows **—**.

## Recipe

<RecipeFor id="wirelessautomate:signal_screen" />

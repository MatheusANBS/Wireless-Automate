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

Links **machines** to a range router, one at a time or a whole area, and opens the router that reaches a
machine.

<br clear="all" />

## Spec sheet

| | |
| --- | --- |
| **Modes** | **Link** (default) and **Open**. Shift + mouse wheel switches between them. |
| **Link** | Pick a router with the Range Upgrade and click machines: each click links the machine to it (clicking one that is already linked unlinks it). The screen links a whole area at once. |
| **Open** | Click a machine: opens the screen of the router that reaches it, already on the machine. If several reach it (the one attached to it and the range ones), a short list lets you choose. |
| **Networks** | The Linker doesn't touch them: pick each face's network in the router screen's **Network** row or with the [Tablet](network-tablet.md). |
| **Signal Screen** | Click a [Signal Screen](signal-screen.md) to pick it, then a mod Chest, Battery or Tank: the storage becomes linked to the Screen (clicking it again unlinks). No limit on distance or dimension. |

## Actions

| Action | Link | Open |
| --- | --- | --- |
| Click a router | Picks the router | — |
| Click a Signal Screen | Picks the Screen | — |
| Click a mod Chest, Battery or Tank | With a Screen picked, links or unlinks the storage to it | Opens the router that reaches it |
| Click a machine | Links or unlinks the machine | Opens the router that reaches it |
| Click the air | Opens the screen | Shows the mode's hint |
| Shift + mouse wheel | Goes to **Open** | Goes to **Link** |
| Shift + click two blocks | Marks the area corners | — |

## On the screen (Link mode)

| Part | How to use it |
| --- | --- |
| **Chosen router** | The name, the position and the slots used (for example **7/32 machines**). If the router doesn't link yet (no Range Upgrade), the reason shows here. |
| **Link / Unlink** | In the header. On **Unlink**, the clicks and the area button take machines out of the router instead of putting them in. |
| **Preview** | The marked area seen from above, with the corners and the router's machines inside it. |
| **Link area** | Links the area's machines the router reaches, nearest first, up to the tier's limit. On **Unlink**, it becomes **Unlink N** and takes out the ones inside. The result shows below the preview. |
| **Clear** | Erases the corners. |

Drag the edge or the corner to make the screen bigger; the preview grows.

The area can be up to 262,144 blocks (for example 64 × 64 × 64), and you need to be within 64
blocks of it.

## The Open mode list

When more than one router reaches the machine, the list shows a row per router: its name, whether it is
**attached** to the machine or reaches it by **range**, and the distance. Click a row to open it. Only
routers within your reach that you can touch are listed.

## Linking storages to a Screen

Click a [Signal Screen](signal-screen.md) with the Linker: it becomes the picked one and the action bar shows how many storages it already has. Then click a mod **Chest, Battery or Tank** to link it to the Screen; click it again to unlink. There is no limit on distance or dimension. You must be allowed to edit the Screen. Once linked, the Screen shows the storage's contents live; if its chunk is unloaded, the Screen keeps the last number, dimmed.

## Example: link the machines of a room

| Step | What to do |
| --- | --- |
| **1** | Put the Range Upgrade in a router and click it with the Linker. |
| **2** | Holding Shift, click two corners of the room. |
| **3** | Click the air to open the screen and **Link area**: the machines within range join, up to the tier's limit. |
| **4** | In **Open** mode, click one of them to set up its face in the router. |

## Recipe

<RecipeFor id="wirelessautomate:linker" />

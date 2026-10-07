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

Copies a router's configuration and pastes it on others, one at a time or over a whole area.

<br clear="all" />

## Spec sheet

| | |
| --- | --- |
| **Copies** | Faces, filters, priorities, redstone and each tab's network. |
| **Holds** | A single copy, in the item itself. The tooltip shows what's copied and the mode's actions. |
| **Modes** | Brush (default) and Area. |

## Actions

| Action | Brush | Area |
| --- | --- | --- |
| Shift + click a router | Copy | Copy |
| Click a router | Paste on it | Mark a corner |
| Click a block | — | Mark a corner (the 3rd starts over) |
| Click the air | — | Paste into the area's routers attached to the **same machine** as the copy |
| Shift + click a block without a router | Clear the wand | Clear the wand |
| Shift + click the air | Switch to Area | Switch to Brush |

<GameScene zoom="3" interactive={true}>
  <Block id="minecraft:furnace" x="0" y="0" z="0" />
  <Block id="wirelessautomate:router" x="0" y="1" z="0" p:facing="up" p:tier="basic" />
  <Block id="minecraft:furnace" x="2" y="0" z="0" />
  <Block id="wirelessautomate:router" x="2" y="1" z="0" p:facing="up" p:tier="basic" />
  <Block id="minecraft:furnace" x="4" y="0" z="0" />
  <Block id="wirelessautomate:router" x="4" y="1" z="0" p:facing="up" p:tier="basic" />
  <Block id="minecraft:chest" x="2" y="0" z="2" />
  <Block id="wirelessautomate:router" x="2" y="1" z="2" p:facing="up" p:tier="basic" />
  <BoxAnnotation min="0 0 0" max="5 2 3" color="#45d6cc" thickness="0.05">
    Area marked with the Configurator
  </BoxAnnotation>
  <BlockAnnotation x="2" y="1" z="2" color="#ff6b5e">
    Left unchanged: it sits on a chest, and the copy came from a furnace
  </BlockAnnotation>
  <IsometricCamera yaw="200" pitch="35" />
</GameScene>

## Good to know

| | |
| --- | --- |
| **Orientation** | The copy is relative to the router: it works with the router on any face. |
| **Networks** | A tab's network is only pasted if you can use it; otherwise the tab keeps its own. |
| **Replicating a line** | Copy the router of each machine type and paste over an area covering the whole line. |

## Recipe

<RecipeFor id="wirelessautomate:configurator" />

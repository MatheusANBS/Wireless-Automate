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

Copies a router's configuration (faces, filters, priorities, redstone and each tab's network) and
pastes it on others. It holds a single copy, in the item itself; the tooltip shows what is copied
and the current mode's actions.

<br clear="all" />

<RecipeFor id="wirelessautomate:configurator" />

| Action | Brush (default) | Area |
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

The copy is relative to the router's orientation: it works with the router on any face. A tab's
network is only pasted if you're allowed to use it.

**Tip:** to replicate a line of machines, copy the router of each machine type and paste over an
area covering the whole line.

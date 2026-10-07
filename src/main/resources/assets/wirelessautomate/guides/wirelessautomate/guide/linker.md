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

Picks your **active network** and puts routers in it, one at a time or by area.

<br clear="all" />

<RecipeFor id="wirelessautomate:linker" />

| Action | What it does |
| --- | --- |
| Click a router | Puts the router in the active network (creates one if you have none) |
| Click the air | Opens the screen: active network, type, mode and, in Area, the preview and the Link button |
| Shift + click the air | Switches between **Single** and **Area** |
| Shift + mouse wheel | Changes the type: All, Items, Fluids or Energy |
| Shift + click two blocks (Area mode) | Marks the area corners |

With the type on **All**, the router joins the network on every tab; with a type, only that tab.

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
    Marked area: every loaded router inside joins the network
  </BoxAnnotation>
  <BlockAnnotation x="2" y="1" z="2" color="#ff6b5e">
    Joins too (for the Linker, the machine does not matter)
  </BlockAnnotation>
  <IsometricCamera yaw="200" pitch="35" />
</GameScene>

The area can be up to 262,144 blocks and up to 64 blocks away from you (configurable on the server).

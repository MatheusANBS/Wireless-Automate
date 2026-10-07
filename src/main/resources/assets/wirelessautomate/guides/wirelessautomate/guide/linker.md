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

Picks your **active network** and puts routers in it, one at a time or a whole area at once.

<br clear="all" />

## Spec sheet

| | |
| --- | --- |
| **Modes** | Single and Area. |
| **Type** | All, Items, Fluids or Energy. |
| **Stacks to** | 1 |

## Actions

| Action | What it does |
| --- | --- |
| Click a router | Puts the router in the active network (creates one if you have none). |
| Click the air | Opens the screen: active network, type, mode and, in Area, the preview and the **Link** button. |
| Shift + click the air | Switches between **Single** and **Area**. |
| Shift + mouse wheel | Changes the type. On **All**, every tab joins the network; with a type, only that tab. |
| Shift + click two blocks (Area) | Marks the area corners. |

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
  <BlockAnnotation x="2" y="1" z="2" color="#3fc36b">
    Joins too: for the Linker, the machine does not matter
  </BlockAnnotation>
  <IsometricCamera yaw="200" pitch="35" />
</GameScene>

## Area limits

| Limit | Default |
| --- | --- |
| Maximum size | 262,144 blocks |
| Maximum distance from you | 64 blocks |

The server can change both in the config (`linker`).

## Recipe

<RecipeFor id="wirelessautomate:linker" />

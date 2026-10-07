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

Picks your **active network** and puts routers in it, one at a time or a whole area at once. It
also takes routers out of their network.

<br clear="all" />

## Spec sheet

| | |
| --- | --- |
| **Modes** | Single and Area. |
| **Tabs** | Items, Fluids, Energy and, with Mekanism, Chemicals. Check as many as you like. |
| **Network** | One of your networks, or **None (unlink)**. |

## Actions

| Action | What it does |
| --- | --- |
| Click a router | Puts the router's checked tabs in the active network (creates one if you have none). On **None (unlink)**, takes those tabs out of their network. |
| Click the air | Opens the screen: network, tabs, mode and, in Area, the preview and the **Link** (or **Unlink**) button. |
| Shift + click the air | Switches between **Single** and **Area**. |
| Shift + mouse wheel | Changes the tabs through the shortcuts: **All**, Items, Fluids, Energy, Chemicals (with Mekanism). A combination checked on the screen goes back to **All**. |
| Shift + click two blocks (Area) | Marks the area corners. |

## On the screen

| Part | How to use it |
| --- | --- |
| **Network** | Click a network to make it active, or **+ New network** to create one. |
| **None (unlink)** | The first row of the list. While it's chosen, the same actions take the checked tabs out of their network instead. Pick a network to go back to linking. |
| **Tabs** | One checkbox per tab. Only the checked tabs change; the others stay as they are. At least one stays checked. |

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

The area can be up to 262,144 blocks (for example 64 × 64 × 64), and you need to be within 64
blocks of it.

## Example: unlink an area, keep the energy

| Step | What to do |
| --- | --- |
| **1** | Click the air to open the screen and pick **None (unlink)**. |
| **2** | Keep **Items**, **Fluids** and **Chemicals** checked and uncheck **Energy**. |
| **3** | Shift + click the air for **Area** mode and mark both corners with Shift + click. |
| **4** | Open the screen again and click **Unlink**: the items, fluids and chemicals tabs leave their network and the energy stays connected. |

## Recipe

<RecipeFor id="wirelessautomate:linker" />

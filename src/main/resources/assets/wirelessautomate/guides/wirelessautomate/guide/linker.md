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
| **Modes** | Single, Area and Machines. In **Machines** mode, pick a router with the Range Upgrade and click the machines (clicking a linked one unlinks it) or mark an area. A machine of a new kind joins with all its faces on your active network. With **None (unlink)** chosen on the screen, the area button unlinks the machines inside it from the router. |
| **Tabs** | Items, Fluids, Energy and, with Mekanism, Chemicals; with Ars Nouveau, Source. Check as many as you like. |
| **Faces** | All six (default) or just some: Front, Back, Left, Right, Top and Bottom, the sides of each router's machine. |
| **Network** | One of your networks, or **None (unlink)**. |

## Actions

| Action | What it does |
| --- | --- |
| Click a router | Puts the router's checked faces, on the checked tabs, in the active network (creates one if you have none). On **None (unlink)**, takes those faces out of their network. |
| Click the air | Opens the screen: network, tabs, faces, mode and, in Area, the preview and the **Link** (or **Unlink**) button. |
| Shift + click the air | Goes to the next mode: **Single**, **Area**, **Machines** and back to **Single**. |
| Shift + mouse wheel | Changes the tabs through the shortcuts: **All**, Items, Fluids, Energy, Chemicals (with Mekanism), Source (with Ars Nouveau). A combination checked on the screen goes back to **All**. |
| Shift + click two blocks (Area) | Marks the area corners. |

## On the screen

| Part | How to use it |
| --- | --- |
| **Network** | Click a network to make it active, or **+ New network** to create one. |
| **None (unlink)** | The first row of the list. While it's chosen, the same actions take the checked faces and tabs out of their network instead. Pick a network to go back to linking. |
| **Tabs** | One colored button per type: click to mark or unmark it (marked = border and tint in the type's color). **All** marks every type; when all are marked, it leaves only the first. Only the marked tabs change; the others stay as they are. At least one stays marked. |
| **Faces** | **All** and one letter per machine side (F, Bk, L, R, T, Bt), seen facing it: click to check or uncheck. Only the checked faces change. The button shows which ones go, like **Link 3 · Bk, Bt**. Machine without a front: Front = North. Hidden in Machines mode. |
| **More tips ›** | In Single mode, when the tips don't fit on the screen: hover it to see the ones left out. |

Drag the edge or the corner to make the screen bigger; the area map grows.

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

## Example: input and output on different networks

| Step | What to do |
| --- | --- |
| **1** | On each furnace's router screen, set the **Top** face to **Insert** and the **Bottom** one to **Extract**. |
| **2** | In the Linker, pick the **Mine** network and, under Faces, keep only **T** (Top) checked. Click the furnaces' routers. |
| **3** | Pick the **Storage** network, check only **Bt** (Bottom) and click the routers again. |
| **4** | Ore comes in through Mine and ingots leave through Storage. Go back to **All** when you're done. |

## Example: unlink an area, keep the energy

| Step | What to do |
| --- | --- |
| **1** | Click the air to open the screen and pick **None (unlink)**. |
| **2** | Keep **Items**, **Fluids** and **Chemicals** checked and uncheck **Energy**. |
| **3** | Shift + click the air for **Area** mode and mark both corners with Shift + click. |
| **4** | Open the screen again and click **Unlink**: the items, fluids and chemicals faces leave their network and the energy stays connected. |

## Recipe

<RecipeFor id="wirelessautomate:linker" />

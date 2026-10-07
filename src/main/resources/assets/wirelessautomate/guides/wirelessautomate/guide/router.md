---
navigation:
  title: Wireless Router
  icon: wirelessautomate:router
  parent: index.md
  position: 2
item_ids:
  - wirelessautomate:router
---


# Wireless Router

<BlockImage id="wirelessautomate:router" p:facing="up" p:tier="basic" scale="3" float="left" />

The router attaches to a face of any block (the machine) and gives the network access to it, like
a pipe touching it would, but on **all six faces at once**: you choose on its screen what each
machine face does.

<br clear="all" />

## Spec sheet

| | |
| --- | --- |
| **Attaches to** | Any face of any block (it needs a block behind it). |
| **Opens with** | Right-click the router, with an empty hand or a regular item. |
| **Types** | Items, Fluids, Energy and, with Mekanism, Chemicals. |
| **Configurable faces** | All six machine faces, each with mode, priority, redstone and filter per type. |
| **Starting tier** | Basic. Raised with [Upgrade Cards](upgrade-cards.md). |
| **Stacks to** | 64 (the tier stays on the item when broken). |

## Recipe

<RecipeFor id="wirelessautomate:router" />

## Where to attach it

<GameScene zoom="4" interactive={true}>
  <Block id="minecraft:furnace" x="0" y="0" z="0" />
  <Block id="wirelessautomate:router" x="0" y="1" z="0" p:facing="up" p:tier="basic" />
  <Block id="wirelessautomate:router" x="0" y="0" z="1" p:facing="south" p:tier="basic" />
  <BlockAnnotation x="0" y="1" z="0" color="#4a8cff">
    Attached on top of the furnace
  </BlockAnnotation>
  <BlockAnnotation x="0" y="0" z="1" color="#ff9a3c">
    Attached to the side: works the same
  </BlockAnnotation>
  <IsometricCamera yaw="200" pitch="30" />
</GameScene>

It doesn't matter which face it sits on: from its screen it reaches all six. Breaking the block
behind it drops the router.

## The screen

| Part | What it does |
| --- | --- |
| **Tabs** | Items, Fluids, Energy and Chemicals. Each tab has its own [network](networks.md), picked in the selector next to the tabs. |
| **3D viewer** | Drag to rotate, scroll to zoom and click a machine face to select it. |
| **U N E D S W buttons** | Pick the face: Up, North, East, Down, South and West. |
| **Mode** | What the selected face does (table below). |
| **More** | The face's priority and redstone. |
| **Filter** | The **Edit** button opens the face's [filter](filters.md); next to it are two [Filter Card](filter-card.md) slots. |
| **Header** | Name, current throughput, tier and the [Chunk Loading Upgrade](chunk-loading.md) slot. |

## Face modes

| Mode | What it does | Use it for |
| --- | --- | --- |
| ![extract](images/port_extract.png) **Extract** | Takes resources out of the machine through this face. | Outputs: finished furnace, generator, miner. |
| ![insert](images/port_insert.png) **Insert** | Puts resources into the machine through this face. | Inputs: furnace, processing machine, final chest. |
| ![both](images/port_both.png) **Storage** | Receives from extractors and delivers to inserters, but doesn't trade with another Storage. | Buffer and storage chests. |
| ![none](images/port_none.png) **None** | The face is left out. | Faces you don't need. |

## Priority and redstone

| Setting | Values | Effect |
| --- | --- | --- |
| **Priority** | −999 to 999 (default 0) | Higher-priority destinations receive first; ties take turns. |
| **Redstone: Ignore** | Default | The face always works. |
| **Redstone: With signal** | | The face only works with a redstone signal on the router. |
| **Redstone: No signal** | | The face only works without a signal. |

## Specs per tier

Throughput is **per face and per type**, counted at the sender. Chemicals use the fluid limit.

| Tier | Items/s | Fluid and chemical (mB/s) | Energy (FE/t) | Range |
| --- | --- | --- | --- | --- |
| **Basic** | 512 | 32,000 | 16,000 | 128 blocks |
| **Advanced** | 8,192 | 512,000 | 256,000 | 1,024 blocks |
| **Elite** | 131,072 | 8,000,000 | 4,000,000 | The whole dimension |
| **Ultimate** | Unlimited | Unlimited | Unlimited | Every dimension |

<GameScene zoom="3" interactive={true}>
  <Block id="minecraft:chest" x="0" y="0" z="0" />
  <Block id="wirelessautomate:router" x="0" y="1" z="0" p:facing="up" p:tier="basic" />
  <Block id="minecraft:furnace" x="2" y="0" z="0" />
  <Block id="wirelessautomate:router" x="2" y="1" z="0" p:facing="up" p:tier="advanced" />
  <Block id="minecraft:barrel" x="4" y="0" z="0" p:facing="up" />
  <Block id="wirelessautomate:router" x="4" y="1" z="0" p:facing="up" p:tier="elite" />
  <Block id="minecraft:blast_furnace" x="6" y="0" z="0" />
  <Block id="wirelessautomate:router" x="6" y="1" z="0" p:facing="up" p:tier="ultimate" />
  <BlockAnnotation x="0" y="1" z="0" color="#c8ccd2">
    Basic
  </BlockAnnotation>
  <BlockAnnotation x="2" y="1" z="0" color="#e2b347">
    Advanced
  </BlockAnnotation>
  <BlockAnnotation x="4" y="1" z="0" color="#45d6cc">
    Elite
  </BlockAnnotation>
  <BlockAnnotation x="6" y="1" z="0" color="#a06bff">
    Ultimate
  </BlockAnnotation>
  <IsometricCamera yaw="200" pitch="30" />
</GameScene>

In creative, middle-click picks the router in the block's tier.

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

The router attaches to a face of any block (the machine) and gives the network access to it. It
doesn't need to sit on a specific face: from its screen you configure **all six faces of the
machine**, and the router reaches each one like a pipe touching it would.

<br clear="all" />

<RecipeFor id="wirelessautomate:router" />

## Where to attach it

<GameScene zoom="4" interactive={true}>
  <Block id="minecraft:furnace" x="0" y="0" z="0" />
  <Block id="wirelessautomate:router" x="0" y="1" z="0" p:facing="up" p:tier="basic" />
  <Block id="wirelessautomate:router" x="0" y="0" z="1" p:facing="south" p:tier="basic" />
  <Block id="wirelessautomate:router" x="-1" y="0" z="0" p:facing="west" p:tier="basic" />
  <BlockAnnotation x="0" y="1" z="0" color="#4a8cff">
    Attached on top of the furnace
  </BlockAnnotation>
  <BlockAnnotation x="0" y="0" z="1" color="#ff9a3c">
    Attached to the side: works the same
  </BlockAnnotation>
  <IsometricCamera yaw="200" pitch="30" />
</GameScene>

It needs a block behind it (it can't float): breaking that block drops the router.

## The screen

Right-click the router (without a Linker, Configurator or upgrade card in hand):

- **Tabs:** Items, Fluids, Energy and Chemicals (Chemicals only with Mekanism). Each tab picks its own
  [network](networks.md) in the selector next to the tabs.
- **3D viewer:** drag to rotate, scroll to zoom and click a machine face to select it. The buttons
  below (U, N, E, D, S, W) do the same.
- **Face mode:**
  - <Color color="#4a8cff">Extract</Color>: takes resources out of the machine through this face.
  - <Color color="#ff9a3c">Insert</Color>: puts resources into the machine through this face.
  - <Color color="#3fc36b">Storage</Color>: receives from extractors and delivers to inserters, but
    doesn't trade with another Storage. Great for buffer chests.
  - None: the face is left out.
- **More:** priority (−999 to 999; higher-priority destinations receive first; ties take turns) and
  redstone (Ignore, With signal, No signal).
- **Filter:** the Edit button opens the face's [filter](filters.md); next to it are two
  [Filter Card](filter-card.md) slots.
- **Header:** name, current throughput, tier and the [Chunk Loading Upgrade](chunk-loading.md) slot.

## Specs per tier

Throughput is **per face and per type**, counted at the source. Chemicals use the fluid limit.

| Tier | Items/s | Fluid and chemical (mB/s) | Energy (FE/t) | Range |
| --- | --- | --- | --- | --- |
| Basic | 512 | 32,000 | 16,000 | 128 blocks |
| Advanced | 8,192 | 512,000 | 256,000 | 1,024 blocks |
| Elite | 131,072 | 8,000,000 | 4,000,000 | The whole dimension |
| Ultimate | Unlimited | Unlimited | Unlimited | Every dimension |

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

To raise the tier, see [Upgrade Cards](upgrade-cards.md). In creative, middle-click picks the
router in the block's tier.

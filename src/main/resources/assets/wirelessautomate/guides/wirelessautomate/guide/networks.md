---
navigation:
  title: Networks
  icon: wirelessautomate:linker
  parent: index.md
  position: 4
---


# Networks

Routers don't link to each other: each **tab** of a router joins a **network**, and everything of
the same type on the same network trades. Who sends and who receives comes from the face modes.

- **Active network:** every player has an active network (the first one is named after them). A
  placed router joins it, on every tab.
- **Network per tab:** a furnace's items can go to the "Ore line" network and its energy to the
  "Base" network. If you don't need to split anything, you won't notice.
- **Delivery order:** higher priority first; ties take turns (round-robin).
- **Storage:** receives from extractors and delivers to inserters, without bouncing between two
  Storage faces.

<GameScene zoom="3" interactive={true}>
  <Block id="minecraft:furnace" x="0" y="0" z="0" />
  <Block id="wirelessautomate:router" x="0" y="1" z="0" p:facing="up" p:tier="advanced" />
  <Block id="minecraft:barrel" x="3" y="0" z="0" p:facing="up" />
  <Block id="wirelessautomate:router" x="3" y="1" z="0" p:facing="up" p:tier="advanced" />
  <Block id="minecraft:barrel" x="3" y="0" z="3" p:facing="up" />
  <Block id="wirelessautomate:router" x="3" y="1" z="3" p:facing="up" p:tier="advanced" />
  <Block id="minecraft:chest" x="0" y="0" z="3" />
  <Block id="wirelessautomate:router" x="0" y="1" z="3" p:facing="up" p:tier="advanced" />
  <LineAnnotation from="0.5 1.5 0.5" to="3.5 1.5 0.5" color="#4a8cff" thickness="0.08">
    The furnace (Extract) delivers to the barrels (Storage)
  </LineAnnotation>
  <LineAnnotation from="0.5 1.5 0.5" to="3.5 1.5 3.5" color="#4a8cff" thickness="0.08">
    The furnace (Extract) delivers to the barrels (Storage)
  </LineAnnotation>
  <LineAnnotation from="3.5 1.5 0.5" to="0.5 1.5 3.5" color="#3fc36b" thickness="0.08">
    The barrels (Storage) deliver to the chest (Insert)
  </LineAnnotation>
  <LineAnnotation from="3.5 1.5 3.5" to="0.5 1.5 3.5" color="#3fc36b" thickness="0.08">
    The barrels (Storage) deliver to the chest (Insert)
  </LineAnnotation>
  <BlockAnnotation x="0" y="1" z="0" color="#4a8cff">
    Furnace · Extract
  </BlockAnnotation>
  <BlockAnnotation x="3" y="1" z="0" color="#3fc36b">
    Barrel · Storage
  </BlockAnnotation>
  <BlockAnnotation x="3" y="1" z="3" color="#3fc36b">
    Barrel · Storage
  </BlockAnnotation>
  <BlockAnnotation x="0" y="1" z="3" color="#ff9a3c">
    Chest · Insert
  </BlockAnnotation>
  <IsometricCamera yaw="210" pitch="35" />
</GameScene>

## Changing the network

- With each tab's network selector, on the router screen.
- With the <ItemLink id="wirelessautomate:linker" />, one router or one area at a time.
- From the <ItemLink id="wirelessautomate:network_tablet" />, many nodes at once.
- By pasting a configuration with the <ItemLink id="wirelessautomate:configurator" /> (it carries the network).

## Range and chunks

The maximum distance depends on the source's tier (see [Router](router.md)). A source or destination
in an unloaded chunk just pauses that route, at no cost. To keep a router working far away, use the
[Chunk Loading Upgrade](chunk-loading.md).

Networks belong to whoever created them: only the owner (or an operator) can put routers in them.

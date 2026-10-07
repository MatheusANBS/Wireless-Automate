---
navigation:
  title: Getting started
  icon: minecraft:chest
  parent: index.md
  position: 1
---


# Getting started

Let's move items from one chest to another, without pipes.

<GameScene zoom="4" interactive={true}>
  <Block id="minecraft:chest" x="0" y="0" z="0" />
  <Block id="wirelessautomate:router" x="0" y="1" z="0" p:facing="up" p:tier="basic" />
  <Block id="minecraft:chest" x="4" y="0" z="0" />
  <Block id="wirelessautomate:router" x="4" y="1" z="0" p:facing="up" p:tier="basic" />
  <LineAnnotation from="0.5 1.5 0.5" to="4.5 1.5 0.5" color="#45d6cc" thickness="0.08">
    Same network: items go from A to B with no wire at all
  </LineAnnotation>
  <BlockAnnotation x="0" y="1" z="0" color="#4a8cff">
    Router A · top face set to **Extract**
  </BlockAnnotation>
  <BlockAnnotation x="4" y="1" z="0" color="#ff9a3c">
    Router B · top face set to **Insert**
  </BlockAnnotation>
  <IsometricCamera yaw="200" pitch="30" />
</GameScene>

1. Craft two routers (recipe below).
2. Use a router on a face of a chest: it **attaches** to that machine and joins your active
   network, on every tab.
3. Right-click router A to open its screen. On the **Items** tab, pick the **Up** face and the
   **Extract** mode.
4. On router B, same face, **Insert** mode. Done: whatever goes into chest A moves to chest B.

<RecipeFor id="wirelessautomate:router" />

## What else

- Each machine face, for each type (items, fluids, energy, chemicals), has its own mode, priority,
  redstone and [filter](filters.md). See [Router](router.md).
- Routers far apart still work: the range depends on the [tier](upgrade-cards.md).
- To keep factories apart, create different [networks](networks.md).

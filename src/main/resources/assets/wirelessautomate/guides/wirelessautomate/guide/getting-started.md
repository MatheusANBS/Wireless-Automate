---
navigation:
  title: Getting started
  icon: minecraft:chest
  parent: index.md
  position: 1
---


# Getting started

Items from one chest to another, without pipes, in a minute.

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

## How to do it

1. Craft two routers (recipe below).
2. Use one router on chest A and the other on chest B. They attach, with no network yet.
3. On router A, **Items** tab, **Up** face: on the **Network** row, create a network and pick it.
4. On router B, same face, pick the same network.
5. Set the **Up** face to **Extract** on A and to **Insert** on B.
6. Put items in chest A: they show up in chest B.

The [Configurator](configurator.md) copies the setup, networks included, to other routers.

The moon button, in the top-right corner, switches the screens to dark mode.

## Recipes

<RecipeFor id="wirelessautomate:router" />

## Next steps

| I want to... | Read |
| --- | --- |
| Understand the screen | [Wireless Router](router.md) |
| Send only some items | [Filters](filters.md) |
| Keep factories apart | [Networks](networks.md) |
| More throughput or range | [Upgrade Cards](upgrade-cards.md) |
| It didn't work | [Troubleshooting](troubleshooting.md) |

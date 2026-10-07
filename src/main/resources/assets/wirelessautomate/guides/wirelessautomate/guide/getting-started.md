---
navigation:
  title: Getting started
  icon: minecraft:chest
  parent: index.md
  position: 1
---


# Getting started

Let's move items from one chest to another, without pipes. It takes a minute.

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

| Step | What to do |
| --- | --- |
| **1** | Craft two routers and a [Linker](linker.md) (recipes below). |
| **2** | Use a router on any face of chest A. It **attaches** to the chest, with **no network** yet. Do the same on chest B. |
| **3** | Holding the Linker, click router A and then router B: both join your active network (the first click creates one if you have none). |
| **4** | Right-click router A. On the **Items** tab, pick the **Up** face and the **Extract** mode. |
| **5** | On router B, same face, **Insert** mode. |
| **6** | Put items in chest A: they show up in chest B. |

Once the first router of a line is set up, use the [Configurator](configurator.md) to copy its
configuration (and networks) to the others.

## Recipes

<RecipeFor id="wirelessautomate:router" />

<RecipeFor id="wirelessautomate:linker" />

## Next steps

| I want to... | Read |
| --- | --- |
| Understand every part of the router screen | [Wireless Router](router.md) |
| Send only some items | [Filters](filters.md) |
| Keep different factories apart | [Networks](networks.md) |
| More throughput or more range | [Upgrade Cards](upgrade-cards.md) |
| Find out why it didn't work | [Troubleshooting](troubleshooting.md) |

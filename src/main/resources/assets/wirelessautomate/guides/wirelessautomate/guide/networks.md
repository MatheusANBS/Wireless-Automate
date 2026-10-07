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

## How it works

| Rule | Explanation |
| --- | --- |
| **Active network** | Every player has an active network (the first one is named after them). A placed router joins it, on every tab. |
| **Network per tab** | A furnace's items can go to the "Ore line" network and its energy to the "Base" network. |
| **Delivery order** | Higher priority first; ties take turns (round-robin). |
| **Storage** | Receives from extractors and delivers to inserters, without bouncing between two Storage faces. |
| **Owner** | Only the network's owner (or an operator) can put routers in it. |

## Changing the network

| Way | How many at once |
| --- | --- |
| The tab's network selector, on the router screen | One tab of one router |
| <ItemLink id="wirelessautomate:linker" />, Single mode | One router (every tab or one type) |
| <ItemLink id="wirelessautomate:linker" />, Area mode | Every loaded router in an area |
| <ItemLink id="wirelessautomate:network_tablet" />, Select | The nodes you check in the list |
| <ItemLink id="wirelessautomate:configurator" /> | The network goes with the pasted configuration |

## Range and chunks

| Situation | What happens |
| --- | --- |
| Destination too far | Left out for that source. Range depends on the sender's tier ([Router](router.md)). |
| Another dimension | Only from an **Ultimate** source. |
| Unloaded chunk | The route pauses and resumes on its own when the chunk loads. |
| Keep it working far away | Use the [Chunk Loading Upgrade](chunk-loading.md). |

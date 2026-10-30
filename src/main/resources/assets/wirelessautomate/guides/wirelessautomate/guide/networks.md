---
navigation:
  title: Networks
  icon: wirelessautomate:linker
  parent: index.md
  position: 4
---


# Networks

Routers don't link to each other: each **face**, on each tab, joins a **network**. Everything of the
same type on the same network trades; the face mode says who sends and who receives.

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

## How to use it

1. A new router isn't in any network. Open its screen and pick the tab and the face.
2. On the **Network** row, pick the network.
3. Repeat on the other machines.

## Rules

| Rule | Explanation |
| --- | --- |
| **Network per face** | A furnace's **Top** face can be on the "Mine" network and its **Bottom** face on "Storage". The energy can go to "Base". |
| **Delivery order** | Higher priority first; ties take turns. |
| **Active network** | The one the [Tablet](network-tablet.md) uses as the target of **Move**. Every player has their own. |
| **Who can use it** | Public: everyone. Private: the owner, operators, **guests** and, with FTB Teams, the added teams. Only the owner (or an operator) renames, invites and removes. |

## Changing the network

| Way | How many at once |
| --- | --- |
| The face's **Network** row, on the router screen | One face, on one tab |
| <ItemLink id="wirelessautomate:network_tablet" />, Select | All faces of the checked nodes, in the chosen type (or every type) |
| <ItemLink id="wirelessautomate:configurator" /> | The network goes with the pasted configuration |

## Range and chunks

| Situation | What happens |
| --- | --- |
| Destination too far | Left out. Range depends on the sender's tier ([Router](router.md)). |
| Another dimension | Only from an **Emerald** or higher source. |
| Unloaded chunk | The route pauses and resumes on its own when the chunk loads. |
| Keep it working far away | Use the [Chunk Loading Upgrade](chunk-loading.md). |

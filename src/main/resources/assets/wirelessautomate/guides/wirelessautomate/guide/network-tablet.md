---
navigation:
  title: Network Tablet
  icon: wirelessautomate:network_tablet
  parent: index.md
  position: 9
item_ids:
  - wirelessautomate:network_tablet
---


# Network Tablet

<ItemImage id="wirelessautomate:network_tablet" scale="2" float="left" />

An overview of all your networks, from anywhere.

<br clear="all" />

## Spec sheet

| | |
| --- | --- |
| **Opens with** | Right-click, or the **Open Network Tablet** key with the tablet in your inventory (unbound by default: set it in Options › Controls). |

## Tabs

| Tab | What it shows and does |
| --- | --- |
| **List** | Every node, with search and a role filter (extracts, inserts...). **Select** moves many nodes between networks at once: all their faces of the chosen type (or of every type). A node with faces on several networks shows "faces in N networks"; hover to see which. Click a node to open its screen remotely. |
| **Map** | Top-down view colored by status. Click a point to open the node. |
| **Statistics** | How much each network moves per type, full destinations and routers in unloaded chunks. |
| **Networks** | Create networks, color, members and privacy. |
| **Groups** | Bundle the networks of one system to pause and resume them all at once. |

The Statistics tab has one card per type. Click a card to list only the routers of that type; the "Only …" chip in the List removes the filter.

If a network is deleted, the faces that were on it are left without a network and the node shows the warning **faces without network: network removed**: pick another network for them.

## Recipe

<RecipeFor id="wirelessautomate:network_tablet" />

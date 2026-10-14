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
| **List** | Every node, grouped by machine, with search, quick filters and the panel of the chosen node (see below). **Select** moves many nodes between networks at once: all their faces of the chosen type (or of every type). A node with faces on several networks shows "faces in N networks"; hover to see which. |
| **Map** | Top-down view colored by status. Click a point to open the node. |
| **Statistics** | How much each network moves per type, full destinations and routers in unloaded chunks. |
| **Networks** | Create networks, color, members and privacy. |
| **Groups** | Bundle the networks of one system to pause and resume them all at once. |

The Statistics tab has one card per type. Click a card to list only the routers of that type; the "Only …" chip in the List removes the filter.

If a network is deleted, the faces that were on it are left without a network and the node shows the warning **faces without network: network removed**: pick another network for them.

## Searching the List

Type in the search box: suggestions are split into **Machines**, **Networks** and **Names**. Picking a machine or a network turns it into a **label** (for example "Machine: Furnace ✕"); the ✕ removes it. You can combine several labels and some text.

Next to the search sit the **quick filters**, each with its count: **With problems**, **No network** and **Unloaded**. A filter with zero nodes disappears. At the bottom, "N of M nodes" and **Clear** bring back the whole list.

Each machine is a group you collapse and open from its header. Each node's row states its condition in words (**unloaded**, **no network**, **destination full**, **paused**, or the network and rate) and six small squares, one per machine side, in the color of the selected tab's mode.

| Key | What it does |
| --- | --- |
| **Type** | Searches. |
| **↑ ↓** | Moves through the open suggestions or the nodes. |
| **Enter** | Accepts the suggestion, searches, or opens the chosen node's full screen. |
| **← →** | Collapses and opens the chosen node's group (with an empty search). |
| **Esc** | Closes the suggestions; again, clears the search. |
| **Backspace** | With an empty search, removes the last label. |
| **Double-click a node** | Opens the router's full screen. |

## The node panel

Click a node in the list: the panel on the right is the same as the [router screen](router.md), with the unfolded face cube, mode, network, filter, priority and redstone, and every change takes effect at once. From the Tablet you edit a node **within the Tablet tier's range**; with the node unloaded, or out of range, the panel shows its last state read-only and says why.

**Edit** on the filter opens the filter screen even from a distance (across dimensions too, if the tier allows it), and **Back** returns you to the Tablet, on the same node and the same search. The Tablet remembers its tab and node when you reopen it.

## Copying and pasting a profile

With a node chosen, **Copy profile** saves its face settings in the Tablet (the **Profile of …** strip shows the source; **forget ✕** drops it). Then:

| To | How |
| --- | --- |
| **One node** | Choose it and click **Paste profile**. |
| **A whole group** | On the group's header, **Paste on N** (the source is left out). |
| **Several nodes** | In **Select**, check the nodes and click **Paste on N marked**. |

Nodes already equal to the profile say "same as the profile"; the others show "N different from the profile" and the first setting that is missing. The notice after pasting has an **Undo** button. Shortcuts: **Ctrl+C** copies the chosen node's profile, **Ctrl+V** pastes and **Ctrl+Z** undoes. Networks follow the usual rule: only the ones you can use are applied.

## Recipe

<RecipeFor id="wirelessautomate:network_tablet" />

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
| **List** | Every node, grouped by machine, with search, quick filters and the panel of the chosen node (see below). A router with linked machines opens as a tree, with its machines. **Select** moves many nodes between networks at once: all their faces of the chosen type (or of every type). A node with faces on several networks shows "faces in N networks"; hover to see which. |
| **Map** | Top-down view colored by status. Click a point to choose the node. The chosen router shows the machines linked to it, each joined to it by a line (see below). |
| **Flows** | How much of each type comes in and goes out, per type and per network, the balance of Storage faces and what needs attention (see below). |
| **Networks** | Create networks, color, members and privacy. |
| **Groups** | Bundle the networks of one system to pause and resume them all at once. |

If a network is deleted, the faces that were on it are left without a network and the node shows the warning **faces without network: network removed**: pick another network for them.

## Flows

Each row shows **in → out**, the average of the last 5 seconds in the type's unit (items/s, mB/s or B/s, FE/t, Source/s):

- **in** is what the **Extract** faces deliver: the network's production;
- **out** is what the **Insert** faces receive: the consumption;
- on the right, the **balance** of Storage faces: **+N filling** when they receive more than they give, **-N draining** when they give more.

**By type** shows one row per type, adding up the networks; the arrow opens that type's networks, from the one that moves most to the one that moves least. **By network** shows each network with one row per type. A network paused in a group is greyed out and left out of the totals.

Next to it, only what needs attention: **waiting** (Extract faces with something to give and no destination), **full** (destinations with no room), **by redstone** (faces stopped by the signal) and **without network** (faces with a mode and no network). Hover a row for the full count. A network that has just started shows the average of the seconds it has so far.

Click a row to list only the routers of that type, that network or both; the "Only …" chip and the network label in the List remove the filter.

## Searching the List

Type in the search box: suggestions are split into **Machines**, **Networks** and **Names**. Picking a machine or a network turns it into a **label** (for example "Machine: Furnace ✕"); the ✕ removes it. You can combine several labels and some text.

Next to the search sit the **quick filters**, each with its count: **With problems**, **No network** and **Unloaded**. A filter with zero nodes disappears. At the bottom, "N of M nodes" and **Clear** bring back the whole list.

Each machine is a group you collapse and open from its header. Each node's row states its condition in words (**unloaded**, **no network**, **destination full**, **paused**, or the network and rate) and six small squares, one per machine side, in the color of the selected tab's mode.

| Key | What it does |
| --- | --- |
| **Type** | Searches. |
| **↑ ↓** | Moves through the open suggestions or the rows of the list: the nodes and, in an open router, its machines. |
| **Enter** | Accepts the suggestion, searches, chooses the machine marked by the arrows, or opens the chosen node's full screen. |
| **← →** | With an empty search: on a node's row, → opens the router as a tree and ← closes it (or collapses its group); on a machine group, collapses and opens it; on a machine, ← goes up to the group; on "… N more", → shows them all. |
| **Esc** | Closes the suggestions; again, clears the search. |
| **Backspace** | With an empty search, removes the last label. |
| **Double-click a node** | Opens the router's full screen. |

## A router's machines

A router with the Range Upgrade and linked machines has a **▸** in front of its name, and its row says how many machines it serves. Click the **▸** to open the router as a tree; the **▾** closes it. Below it you see:

| Row | What it shows |
| --- | --- |
| **Attached machine** | The machine the router is attached to, with the block and the side of the router. |
| **Group** | The group's name and how many machines it has (**×N**), and how many exceptions, if any. The group's **▸**/**▾** hides and shows its machines. |
| **Machine** | The name (or **#N**), the coordinates and the state in words: **moving**, **asleep**, **asleep: input full**, **unloaded**, **out of range**, **over the limit** or **replaced**. On the right, the rate (**↓** receives, **↑** gives), the **exception** mark and the Eye **◉**. |

Each group shows its first 8 machines; **… N more · Show all** shows the rest, and **Show fewer** goes back to 8.

Click a machine to edit it in the panel: it becomes the target of the **Editing** row, as in the list of the [router screen](router.md). Click the attached machine or a group to edit the attached machine or the group's profile. The Eye **◉** lights the machine up in the world, as on the router screen.

When you reopen it, the Tablet remembers the open routers, the hidden groups and the ones showing all.

## Machines on the Map

On the Map, the chosen node is the same as in the List. If it has linked machines, they show up as small squares joined to the router by a line; the ones lit by the Eye are cyan. Click a machine to choose it as the target of the node's panel, the same one as in the List.

## Clicking a machine

With the Tablet in hand, right-click a machine with a router (or one linked through the Range Upgrade): the Tablet opens the hub with its node already chosen, before the machine's own interface. In the panel, **In the world** lights the machine up through the blocks, like the Eye on the router screen, and **Clear all (N)**, at the foot of the List, turns off the ones that are lit.

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

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

## How to use it

1. Right-click with the Tablet. Or use the **Open Network Tablet** key with it in your inventory
   (unbound by default: set it in Options › Controls).
2. On the **List** tab, find the node and click it.
3. Change its faces in the panel on the right, as on the [router screen](router.md). It takes effect
   at once.

Shortcut: with the Tablet in hand, right-click a machine with a router (or one linked through the
Range Upgrade). The Tablet opens with its node already chosen, before the machine's own interface.

## Tabs

| Tab | What it does |
| --- | --- |
| **List** | Every node, with search, filters and the chosen node's panel. |
| **Flows** | How much of each type comes in and goes out, per network, and what needs attention. |
| **Map** | Top-down view, colored by status, with every router you can see. |
| **Networks** | Create networks, color, members, privacy and network groups. |

The header counts your base: nodes, machines and networks.

## The node panel

The panel is the same as the [router screen](router.md): faces, mode, network, filter, priority and
redstone. You can only edit a node **within the Tablet tier's range**. Unloaded or out of range, the
panel shows its last state read-only and says why.

**Edit** on the filter opens the filter screen even from a distance (across dimensions too, if the
tier allows it). **Back** returns you to the Tablet, on the same node and search. The Tablet remembers
its tab and node when you reopen it.

**In the world** lights the machine up through the blocks, like the Eye on the router screen. **Clear
all (N)**, at the foot of the List, turns off the lit ones.

## The List

The switch below the search changes the view:

| View | What it shows |
| --- | --- |
| **Routers** | One row per router, with the attached machine under the name. Opens as a tree with its machines. |
| **Machines** | Every machine of your base, attached and linked, grouped by block. |
| **Grid** | One row per router and one column per type, with a stripe in the mode's color for each active face. Clicking a cell opens the node on that type. |

Each row states its condition in words (**unloaded**, **no network**, **destination full**,
**paused**, or the network and rate) and shows six small squares, one per machine side, in the mode's
color.

In the search, suggestions are split into **Machines**, **Networks** and **Names**. Picking a machine or
network turns it into a **label** ("Machine: Furnace ✕"); the ✕ removes it. You can combine several
labels and some text. Next to it sit the quick filters **With problems**, **No network** and
**Unloaded**, with their counts; **! N** shows only the nodes with problems. **Clear** brings back the
whole list.

To name a router or its attached machine, click the ✎ on its row or double-click the name.

A node with faces on several networks shows "faces in N networks"; hover to see which. If a network is
deleted, its faces get the warning **faces without network: network removed**: pick another network.

| Gesture | What it does |
| --- | --- |
| **↑ ↓** | Moves through the suggestions or the rows. |
| **Enter** | Accepts the suggestion, chooses the row, or opens the node's full screen. |
| **← →** | With an empty search: opens and closes the router, group or block. |
| **Esc** | Closes the suggestions; again, clears the search. |
| **Backspace** | With an empty search, removes the last label. |
| **Double-click a node** | Opens the router's full screen. |

When you reopen it, the Tablet remembers the view and what was open.

## A router's machines

A router with linked machines has a **▸** in front of its name. Click it to open the tree:

| Row | What it shows |
| --- | --- |
| **Attached machine** | Where the router is attached, with the block and the side. |
| **Group** | The name, how many machines (**×N**) and how many exceptions. |
| **Machine** | The name (or **#N**), the coordinates, the state, the rate (**↓** receives, **↑** gives), the **exception** mark and the Eye **◉**. |

A machine's states: **moving**, **asleep**, **asleep: input full**, **unloaded**, **no Range
Upgrade** (without it the router moves from none of them), **out of range**, **over the limit**,
**replaced** or **missing** (the block was broken or has nothing left to move).

Each group or block shows its first 8 machines; **Show all** shows the rest. In a large base, the
blocks in the **Machines** view start collapsed.

Click a machine to edit it in the panel: it becomes the target of the **Editing** row. Click the
attached machine or a group to edit the attached machine or the group's profile.

In the **Machines** view, the type and network filters apply per machine: under **Only Fluids**, a
furnace does not show up, even if its router also moves fluids.

## The Map

Drag to move (**Center** brings it back to you), scroll to zoom and click a point to choose the node.
The Map shows every router, independent of the List's search and filters.

The chosen node is the same as in the List. Its linked machines show up as small squares joined by a
line; the ones lit by the Eye are cyan. Click one to make it the panel's target.

## Networks and groups

The **Networks** tab is a single list. Each network group is a section with **Pause** on its header;
networks outside any group come last, under **No group**. A network can be in two groups.

- Click a network to see its color and whether it is public or private, use it or remove it.
- On a private network you own, add **guests** and **FTB Teams teams** (only with that mod). Type a
  name and pick with the arrows and Enter; click the ✕ twice to remove. Only players who already joined
  the server show up. A team counts as a whole: whoever joins or leaves it gains or loses access on
  their own.
- Click a group's header to rename it, pause it, check its networks and remove it.
- **New network** and **New group**, below the list, create a network or a group.

A paused group stops all its networks at once; resuming lets them go.

## Flows

Each row shows **in → out**, the average of the last 5 seconds (items/s, mB/s or B/s, FE/t,
Source/s):

- **in**: what the **Extract** faces deliver (production);
- **out**: what the **Insert** faces receive (consumption);
- on the right, the Storage faces' **balance**: **+N filling** or **-N draining**.

**By type** adds up the networks; the arrow opens that type's networks. **By network** shows each
network with one row per type. A paused network is greyed out and left out of the totals.

Next to it, only what needs attention: **waiting** (Extract faces with something to give and no
destination), **full** (destinations with no room), **by redstone** (stopped by the signal) and
**without network** (faces with a mode and no network).

Click a row to list only the routers of that type or network; the "Only …" chip removes the filter.

## Copying and pasting a profile

1. Choose a node and click **Copy profile**. The **Profile of …** strip shows the source; **forget ✕**
   drops it.
2. For one node: choose it and click **Paste profile**.
3. For several: click **Select**, mark routers, groups, blocks or machines and click **Paste on
   marked**.

| Gesture | What it does |
| --- | --- |
| **Ctrl+C** | Copies the chosen node's profile. |
| **Ctrl+V** | Pastes. |
| **Ctrl+Z** | Undoes (the notice after pasting also has **Undo**). |

Nodes show "same as the profile" or "N different from the profile". Only networks you can use are
applied.

What pasting does to each marked thing:

- **Router:** pastes on its attached machine.
- **Machine:** becomes an exception of its own, with the sides by the machine's front. If it ends up
  equal to the group's profile, the exception goes away.
- **Whole group or block:** pastes on the group's profile; the exceptions already there stay.

Left out: the router the profile came from (the machines linked to it do receive) and unloaded or
out-of-range routers. A profile copied in an earlier version only pastes on routers: copy it again to
paste on machines and groups.

**Move** puts the marked ones in the chosen network: the whole router; on a machine, only its faces
with a mode (it becomes an exception); on a group, the group's profile.

## Recipe

<RecipeFor id="wirelessautomate:network_tablet" />

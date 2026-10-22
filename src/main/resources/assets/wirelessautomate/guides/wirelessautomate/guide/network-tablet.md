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
| **List** | Every node, with search, quick filters and the panel of the chosen node (see below). The **Routers · Machines · Grid** switch, below the search, shows the list by router (one row each, with the attached machine under the name, opening as a tree with its machines), by every machine, grouped by block, or as the **Grid**: one row per router and one column per type, with a stripe in the mode's color for every face that does something, plus the state and the network; clicking a cell opens the node on that type's tab. The **! N** button at the end filters the nodes with problems. **Select** marks routers, groups and machines to paste a profile or change networks at once. A node with faces on several networks shows "faces in N networks"; hover to see which. To name a router or its attached machine, click the ✎ on its row (with the mouse over it) or double-click the name; Enter saves and Esc cancels. |
| **Flows** | How much of each type comes in and goes out, per type and per network, the balance of Storage faces and what needs attention (see below). |
| **Map** | Top-down view colored by status, with every router you can see (independent of the List's search and filters). Drag to move the map (**Center** brings it back to you), scroll to zoom and click a point to choose the node. The chosen router shows the machines linked to it, each joined to it by a line (see below). |
| **Networks** | Create networks, color, members and privacy, and the network groups, which pause and resume a whole system at once (see below). |

The Tablet's header counts your base: nodes, machines and networks.

If a network is deleted, the faces that were on it are left without a network and the node shows the warning **faces without network: network removed**: pick another network for them.

## Networks and groups

The **Networks** tab is a single list. Each network group is a section, with **▾** to collapse it and **Pause** on its header (**paused** when it is); networks outside any group come last, under **No group**. A network in two groups shows up in both sections, and the tooltip says **Also in …**.

- Click a network to see its color and whether it is public or private on the right, use it or remove it.
- On a private network you own, the column shows the **guests** and the **FTB Teams teams** (only with that mod). Type a name to see suggestions, pick with the arrows and Enter (Tab completes) and click the ✕ twice to remove. Only players who already joined the server show up, and a team counts as a whole: whoever joins or leaves it gains or loses access on their own.
- Click a group's header to see the group on the right: its name, pause, its networks (check or uncheck each one; **in …** flags a network that is already in another group) and remove.
- **New network** and **New group**, below the list, create a network or a group: type the name and confirm (or Enter).

A paused group stops all its networks at once; resuming lets them go.

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

A router with linked machines has a **▸** in front of its name, and its row says how many machines it serves. Click the **▸** to open the router as a tree; the **▾** closes it. Below it you see:

| Row | What it shows |
| --- | --- |
| **Attached machine** | The machine the router is attached to, with the block and the side of the router. |
| **Group** | The group's name and how many machines it has (**×N**), and how many exceptions, if any. The group's **▸**/**▾** hides and shows its machines. |
| **Machine** | The name (or **#N**), the coordinates and the state in words: **moving**, **asleep**, **asleep: input full**, **unloaded**, **no Range Upgrade** (without the upgrade the router moves from none of them), **out of range**, **over the limit**, **replaced** or **missing** (the block was broken or has nothing left to move). On the right, the rate (**↓** receives, **↑** gives), the **exception** mark and the Eye **◉**. |

Each group shows its first 8 machines; **… N more · Show all** shows the rest, and **Show fewer** goes back to 8.

Click a machine to edit it in the panel: it becomes the target of the **Editing** row, as in the list of the [router screen](router.md). Click the attached machine or a group to edit the attached machine or the group's profile. The Eye **◉** lights the machine up in the world, as on the router screen.

When you reopen it, the Tablet remembers the open routers, the hidden groups and the ones showing all.

## The List by machine

In the switch below the search, **Machines** shows every machine of your base, attached and linked, grouped by block type; **Routers** goes back to the usual list. The quick filters sit to the right of the switch.

| Row | What it shows |
| --- | --- |
| **Block** | The **▸**/**▾**, the icon, the name and how many machines (for example "Furnace · 23") and, dimmed on the right, from how many routers. Click it to open or collapse the block. |
| **Machine** | The name (or **#N**, the same number as in the tree and on the router screen) and, below, **via** the router that serves it, or **attached** on the machine the router is attached to, with the coordinates or the state in words. On the right, the rate when it moves, the **exception** mark and the Eye **◉**. |

Each block shows its first 8 machines; **… N more · Show all** shows the rest and **Show fewer** goes back to 8. With a large base, the blocks start collapsed: open the one you want to see. The foot of the list counts the blocks and machines.

The search finds a machine by its name, its block and its router's name. The type and network filters apply per machine: under **Only Fluids**, a furnace does not show up, even if its router also moves fluids.

Click a machine: the Tablet chooses its router and makes the machine the panel's target, to set it up there; on the attached machine, the target is the attached machine. **↑ ↓** walk the rows, **← →** collapse and open the block, and **Enter** chooses. When you reopen it, the Tablet remembers the view and the open blocks.

## Machines on the Map

On the Map, the chosen node is the same as in the List. If it has linked machines, they show up as small squares joined to the router by a line; the ones lit by the Eye are cyan. Click a machine to choose it as the target of the node's panel, the same one as in the List. With the List by machine, the Map still shows every router; the chosen one is the chosen machine's router, with its machines.

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
| **Several routers and machines** | In **Select**, mark what you want and click **Paste on marked**; the button says what it will reach ("2 routers, 3 machines") or, without room, just how many, and the tooltip says it all. |

Nodes already equal to the profile say "same as the profile"; the others show "N different from the profile" and the first setting that is missing. The notice after pasting has an **Undo** button. Shortcuts: **Ctrl+C** copies the chosen node's profile, **Ctrl+V** pastes and **Ctrl+Z** undoes. Networks follow the usual rule: only the ones you can use are applied.

## Marking routers and machines

In **Select**, every row has a box, in both views: the router, the attached machine, the group, the machine and the block header. Marking a group or a block marks its machines; with only part of it marked, the box is half filled. **All** marks what the search shows: the routers under **Routers**, the blocks under **Machines**.

**Paste on marked** pastes the copied profile:

- on a **router**, on its attached machine, like the usual paste;
- on a **machine**, as an exception of its own, with the sides turned by the machine's front: each face goes to the same side of the machine, even if it faces another direction. If it ends up equal to the group's profile, the exception goes away;
- on a **whole group** (the marked group, the marked block or every machine of the group one by one), on the group's profile; the exceptions already there stay, and the notice says how many.

The router the profile came from is left out (the machines linked to it do receive); unloaded or out-of-range routers are left out with their machines, and the notice counts them. A profile copied in an earlier version only pastes on routers: copy it again to paste on machines and groups. The notice ("Pasted on 3 groups and 1 machine · 2 exceptions kept") has **Undo**, which brings back what is still as it was left.

**Move** puts the marked ones in the chosen network: whole routers, as always; on a marked machine, only its faces with a mode (it becomes an exception); on a whole group, the group's profile. A machine of a router that is also marked already goes along with it.

## Recipe

<RecipeFor id="wirelessautomate:network_tablet" />

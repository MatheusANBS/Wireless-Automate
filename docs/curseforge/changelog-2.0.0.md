Wireless Automate 2.0: a dark theme for every screen, and a node panel that lets you set up your whole base from the Network Tablet: search, groups, editing at a distance and copy and paste of profiles with undo.

**Light and dark theme** (new)

- Every screen of the mod (router, filter, Network Tablet, Linker, storages, recipe editor) now has a dark theme. Switch it with the moon/sun button in the top right corner of any screen.
- The choice is saved in the client config (`config/wirelessautomate-client.toml`, `gui.theme`, `LIGHT` by default) and applies to every screen at once. The light theme looks the same as before.

**Node panel** (new)

- One panel to configure a router, used both on the router screen and in the Network Tablet. The six faces of the machine are shown **unfolded like a cube** (Top above Front; Left, Front, Right and Back in the middle row; Bottom below), each with its mode color, its name and a dot in its network's color. The face the router sits on is marked "router".
- For the chosen face: the four modes with a sentence saying what each does on this machine, **Network**, **Filter** with Edit, **Priority** and **Redstone**, all in view.
- The 3D view and the cube work together: clicking a face in one picks it in the other.
- With the Range Upgrade, an **Editing** row picks the attached machine, a whole group or a single machine, with "Make it like the others" and Unlink.

**Network Tablet**

- **List tab redone:** one search box that looks at machine type, network and name at the same time, with suggestions that become tags ("Machine: Furnace ✕"), quick filters (With problems, No network, Unloaded) and the list grouped by machine type, with collapsible headers.
- Each row shows the node's state in words ("destination full", "no network", "unloaded") and six small squares with the mode of each face.
- Click a row to **edit that node right there** in the panel beside the list, from anywhere in the Tablet's reach. Double click (or Enter) opens the full router screen, as before. A node that is unloaded or out of reach shows its settings read-only, with the reason.
- **Copy and paste profiles:** copy a node's profile (Copy profile or Ctrl+C) and paste it on one node (Ctrl+V), on a whole group ("Paste on N") or on the nodes you marked. Rows say whether each node is equal to the profile or the first thing that differs. Every paste can be **undone** (Undo or Ctrl+Z). Networks you can't use are not pasted, as with the Configurator.
- Keyboard: type to search, ↑ ↓ to move, Enter, Esc, Backspace to remove the last tag, ← → to collapse and expand a group.

**Easier networks**

- Picking a mode on a face that has no network puts it in your **active network** right away, with an Undo. Without an active network, the Network row asks you to choose one.
- The filter can be edited from the Tablet or from a router screen opened at a distance (it used to close beyond 8 blocks), and **Back** returns to where you were, with the same node and search.

**Fixes**

- **Crash with Trash Cans:** a router sending items to a trash can (Trash Cans 1.1.0) could crash the server when saving the world ("Value must be within range [1;99]"). The router now sends at most one stack per call to a slot that doesn't show what it holds; drawers and stack-upgraded barrels still get everything in one go.
- When a machine accepted items in the simulation and refused them for real, the leftover could go back into the machine's input (a Mekanism machine's output turned into its input, ignoring the filter) or drop on the ground. It now goes back to the same slot it came from, then to other destinations whose filter accepts it, and only the rest drops.

**Other changes**

- The Network Tablet list no longer has pages or the role filter: it scrolls, and shows up to 256 nodes ("refine the search" beyond that).
- The row of six face buttons and the "More" section of the router screen were replaced by the cube.
- The guide book covers the theme button and the node panel, in English and Portuguese.

**Compatibility note:** the network protocol changed (16), so the server and every client need 2.0.0. Worlds from 1.9 and older load as they are. Still requires NeoForge 21.1.248 or newer.

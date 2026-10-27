Wireless Automate 2.11.0 adds the **Signal Screen**: a wall display that stretches from 1 × 1 up to 32 × 32 blocks and shows live numbers from your networks and storages, built with a full in-game editor.

**Signal Screen**

- A block you stick on the side of any solid block. Make it bigger in the **Size** tab (from 1 × 1 to 32 × 32, growing up, up-and-left or both ways); no extra blocks are needed and the picture is drawn across the whole wall.
- Only players near the Screen receive its data, and a network's numbers are shown only to players who have access to that network.
- **Editor** (Edit tab): pieces you drag, resize, group, reorder in layers, copy and paste between Screens, with undo (100 steps) and shortcuts.
- **Pieces:** Value, Text, Shape, **Icon**, **Bar**, **Gauge**, **Graph** and **Arrow**. Each piece can show a number from a **network**, from a **linked storage** (Wireless Chest, Battery or Tank) or from the **clock** (the in-game time).
- **Format and style:** units, prefixes and suffixes, alignment, five fonts, corner radius, opacity, and **rules** that change color, background, blink or hide a piece when a number is below, above or equal to a value.
- **Themes and backgrounds:** four ready themes (Graphite, Phosphor, Amber, Porcelain) plus your own with six editable colors; plain, solid or gradient background and scanlines.
- **Components and layouts:** ready-made components (Network panel, Number card, Title, Chest item, Battery charge, In and out) and your own, saved per player. Four ready **layouts** for the whole Screen (Energy, Storage, Network, Clock) that fit any size, and layouts you save yourself. Pick one from the list at the top of the Screen's base and apply it with a click (double-click applies right away).
- **Linking storages:** the Linker has a Screen mode: click the Screen, then a Wireless Chest, Battery or Tank to link or unlink it, at any distance and in any dimension. A linked storage in an unloaded chunk shows its last number, marked as stale; chunks are never force-loaded.
- **Chest items and flow:** pick an item of a linked Chest from a searchable grid (supports `@mod`) or drag it from JEI; the Screen can show how much of it goes in and out.
- **Icon piece:** shows **any item, fluid or Mekanism chemical** dragged from JEI, including items with data such as Apotheosis gems and enchanted gear. Blocks are drawn in 3D with the inventory lighting, and animated icons (clock, compass, enchantment glint, animated fluids) move at about 10 frames per second, only while the Screen is in view.
- **Performance:** a Screen costs nothing on the server while nobody is near it, and on the client it is redrawn only when a number or the layout changes. The resolution follows the size the Screen has on your window, within a video memory budget (`tv.vramBudgetMb`, 400 MB by default).
- The in-game guide has a new page for the Signal Screen, and the Linker page explains the Screen mode.

**Other changes**

- The Linker switches mode with Shift + mouse wheel.
- The default filter size and a "No network" hook for the end-to-end tests (fixes from 2.10).

**Compatibility note:** the network protocol changed to **37**, so a server and its clients need the same version. No world or configuration migration is needed; new options (`tv.*`) are added with their defaults. NeoForge 21.1.248 or newer is still required, and all optional integrations remain optional.

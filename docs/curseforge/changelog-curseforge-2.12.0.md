## 2.12.0

Wireless Automate 2.12.0 turns the **Signal Screen** into a canvas: draw and animate **pixel art** in the game, put **real images and GIFs** on the wall, share Screens by code (even with an AI), and choose who can edit each Screen. Filters also get separate **accept up to** and **keep in source** limits.

**Pixel art on the Signal Screen**

- A full **pixel art editor** in the Screen's new **Art** tab: pencil, eraser, fill, picker, line, rectangle and ellipse (outline or filled), dither, selection with move, copy and paste, mirror drawing, pixel perfect lines, brush size 1 to 8 (square or round), flip and rotate, and lighten/darken that walk along a **color ramp** instead of jumping hues.
- Arts from 8 × 8 up to **128 × 128**, each with a palette of **16 colors** you pick freely (Make ramp builds a smooth ramp from one color). Navigate without scrolling: hold Space or Alt and drag, middle mouse, and wheel zoom on the cursor. Live preview at 1×, 2× and tiled.
- Up to **32 arts** per player in a personal library (`tv.maxArts`).
- The new **Image** piece shows an art at a whole-number scale, and the background can **tile** an art. A Screen can use up to **131,072 pixels** of art (`tv.maxImagePixels`, eight 128 × 128 arts).
- Arts travel outside the chunk packet, only to players near the Screen and only once per change, so big drawings don't weigh on chunk loading.

**Animated pixel art**

- An art can have up to **64 frames**, playing at **1 to 20 frames per second**, in **Loop** or **Ping-pong**.
- Frame strip under the canvas (add, duplicate, remove, drag to reorder), **onion skin** (the previous frame at 35%), play/pause, and shortcuts (`,` `.` to step, Alt + N / D / O / P). Everything is undoable.
- Every Screen showing the same art plays in sync with the clock, and only Screens in view redraw. Frames count toward the Screen's pixel limit.

**Real images and GIFs**

- Upload a **PNG, JPG or GIF** from your computer with **Upload file**, or paste it as a `WA-IMAGEM1` code. Images keep their full colors: use them for photos, logos and backgrounds. Animated GIFs play on the Screen at their own pace.
- Default limits: **1024 px** on the longer side, **8 MB** per file, **240 frames** per GIF, **8 images** per Screen (all in `tv.images.*`).
- The server **never decodes** an image; it only checks the file header and size. Each image is stored once per world, however many Screens use it, and unused images are cleaned up.
- **Server controls:** turn images off (`tv.images.enabled`, applies right away), allow uploads for everyone or only operators (`tv.images.uploaders`), an upload rate per player (32 MB per minute), a world cap (4 GB on dedicated servers), an optional per-player quota, and an automatic upload lock for players who keep hitting the rate limit (60 minutes). Operators can list, remove and ban with `/wa tela imagens` and `/wa tela imagem remover | banir | desbanir`.
- **Client option** `tv.showOthersImages`: turn it off to hide images uploaded by other players. Your own images stay.

**Share by code, and AI-friendly**

- **Copy** and **Import** arts, images and whole Screen layouts as text codes (`WA-ARTE1`, `WA-IMAGEM1`, `WA-TELA1`), compressed or in a readable form.
- The readable form is meant to be written by an AI: paste the guide into ChatGPT or Claude and ask for "a 32 × 32 sword" or "a 4 × 2 Screen with a title and a clock". Guide on GitHub: [Pixel art and Screens with an AI](https://github.com/MatheusANBS/Wireless-Automate/blob/main/docs/pixel-art-ai-guide.md).

**Who can edit a Screen**

- A new **Access** tab, like private networks: the owner adds players and, with FTB Teams, teams that can edit the Screen. A team counts as a whole and is live.

**Filters: accept up to and keep in source**

- Each whitelist entry on ports and cards now has two separate limits: **Accept up to** (at the destination) and **Keep in source** (at the source). Old filters keep working: an entry without "keep" uses its stock value.
- **Wireless Chest, Tank and Chemical Tank:** no more admission list. A global **limit per type** sits in a fixed "All other types" row at the top, and each entry is the exception for its type (0 = no limit). It never affects extraction and never blocks other types. The Quotas mode is gone.
- Number fields show their unit, keep a typed value when you switch tabs or close the screen, never hide digits, and the mouse wheel over a field adds or subtracts.

**Other changes**

- Lists on the Screen editor (theme, background, font, layout, mode, unit, direction and others) are now **dropdowns**, and the mouse wheel no longer changes values while you scroll a panel.
- The Screen's default video memory budget is now **1024 MB** (`tv.vramBudgetMb`, client config). It is a limit, not what the Screen uses.
- No tooltip on the small step buttons (filters, router priority, Screen size).
- The in-game guide covers pixel art, animation, images and GIFs, and the Access tab.

**Compatibility note:** the network protocol changed to **38**, so a server and its clients need the same version. Worlds and Screens from 2.11 load unchanged; new options (`tv.images.*`, `tv.maxArts`, `tv.maxImagePixels`, `tv.showOthersImages`) are added with their defaults. NeoForge 21.1.248 or newer is still required, and all optional integrations remain optional.

## 2.11.1

Wireless Automate 2.11.1 makes energy, fluid, chemical and Source routers notice machines that change without warning within a second, remembers the selected face when you edit a ranged machine, and fixes animated Icons on the Signal Screen with Sodium.

**Faster transfers from machines that change silently**

- Many machines gain or lose energy and fluid every tick without telling their neighbors. A router that found such a machine empty or full used to wait up to 5 seconds before looking again. For energy, fluid, chemicals and Source the wait is now at most **1 second** (20 ticks).
- In our test with a machine that changes silently, the delay dropped from about 77 ticks to 1 to 4 ticks.
- Items are unchanged: inventories report their changes, so they keep the longer wait and cost nothing extra. The server cost measured in the benchmark did not change.

**Ranged machines**

- The router screen now remembers the face you were editing on each ranged machine or group, so coming back from the Filter screen or reopening the router no longer jumps to the default face. The attached machine already did this.

**Signal Screen**

- Animated Icons (animated blocks and fluids) animate again when Sodium is installed.

**Compatibility note:** the network protocol is unchanged (**37**) and no world or configuration migration is needed. NeoForge 21.1.248 or newer is still required, and all optional integrations remain optional.

## 2.11.0

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

## 2.10.1

Wireless Automate 2.10.1 fixes item duplication and vanishing items when the Wireless Chest is used as storage through an AE2 storage bus.

**AE2 storage bus fix**

- When a request emptied one item type in the Wireless Chest, the next type slid into its slot and the storage bus kept pulling from that same slot: the requested item was **duplicated** and the other item was **deleted**. It showed up with the pick block of AE2WTLib, and could also happen with export buses, interfaces and autocrafting whenever a request was larger than what the chest held of that item.
- An emptied type now keeps its slot until the end of the tick, so slots never shift in the middle of a request.
- The same fix covers the Chemical Tank (Mekanism) and the Fluid Tank.
- Items already lost to this bug are not restored.

**Compatibility note:** the network protocol is unchanged (**34**) and no world or configuration migration is needed. NeoForge 21.1.248 or newer is still required, and all optional integrations remain optional.

## 2.10.0

Wireless Automate 2.10.0 makes the filter's **component rule work for any mod** (genes with 100% purity, bee types, NBT fields), roomier filter screen, a **searchable network list** on routers, and a one-click way to put a group's exceptions back on the profile.

**Filter by data component, for any mod**

- The Rule tab's "Component" field now reads **fields inside a component**, not just the whole value: `productivebees:gene_group->purity>=100`, `productivebees:gene_group_list->*.purity>=100`, `minecraft:custom_data->genes.speed>2`. Old rules such as `apotheosis:purity=perfect` keep working.
- Operators: `=`, `!=`, `>=`, `<=`, `>`, `<` and `~` (contains). Lists can be indexed (`->0.field`) or searched with `*`. A field that does not exist never matches, except with `!=`.
- **Several conditions in one rule:** press Enter and each one becomes a line with a ✕ (up to 4); all of them must hold, e.g. a gene of one attribute **and** purity 100.
- **Guided typing:** the suggestion list walks you through component, field, operator and value. Components show their shape (object, list, value), fields show their type and the range seen in your items (`number · 40 to 100`), operators come in words (at least, at most, different from…) and values are the ones found in your inventory and the creative tabs. Nothing in it is specific to a mod.
- The rule summary reads like text (`gene_group → purity ≥ 100`). The field accepts up to 256 characters.

**Filter screen**

- The right panel is wider (208 px, minimum window 392 × 264): "Only in" and "Component" fields are no longer squeezed, the Yes/No buttons no longer touch, and the rule summary and the Filter Card text have room to wrap.

**Router network list**

- The list now has a **search box** (accent and case insensitive, Enter picks the first match, Esc clears and then closes), **"No network" pinned at the top**, a square colour swatch, a ✓ on the current network, the matching part highlighted and an "N of M" footer.

**Machine groups (Range Upgrade)**

- A group with exceptions shows a **↺ "Back to profile (all)"** button on its row. Two clicks put every machine of that group back on the group's profile in one go; other groups are untouched.

**Other changes**

- A new test confirms the Wireless Chest never hands out more items than it stores when an AE2 storage bus asks for a full stack (the pick block of AE2WTLib asks for a stack by design).

**Compatibility note:** the network protocol changed to **34**, so a server and its clients need the same version. No world or configuration migration is needed. NeoForge 21.1.248 or newer is still required, and all optional integrations remain optional.

## 2.9.0

Wireless Automate 2.9.0 adds the **Crystal Repairer**, a block that mends cracked Resonator crystals, and makes the Resonator recharge every paid Dull crystal at once.

**Crystal Repairer** (new block)

- A cracked crystal is no longer a trip to the crafting table: put it on one of the **8 pedestals** and the Repairer mends it. Up to eight crystals are repaired **in parallel**, one per line.
- Each repair takes **7 seconds** and uses **4 full Signal Crystals** from the shared stock slot plus energy: **2 times the FE per crystal** of the crystal's tier (Signal Crystal at the Basic price, Cut Crystal at the Advanced one, a Tuned crystal at its own tier). That is 432 FE for a Signal Crystal and 13,824 FE for a Cut one.
- The mended crystal comes out as the same crystal, **fully charged and with full integrity**, in the output slots. A line with no free output waits instead of losing the crystal.
- Energy goes in from any side (a router can feed it), items go in and out from any side (only the outputs can be taken). The buffer holds eight repairs of the dearest tier.
- Breaking the block while a line is working gives back the crystals it had already taken.
- The block is **animated**: the cracks close from the inside out, the crystals converge on the emitter with a flash and a shockwave on the floor, and the mended crystal rises out. With a shaderpack of Iris, the crystals stay still on their pedestals.
- Recipe: Resonator Casing, Phase Circuit, Hopper, Harmonic Lens and a Redstone Block. JEI shows one example per crystal type with the cost and time from the config, and the guide has a new page.
- Two new config entries, `repairer.repairTicks` (140) and `repairer.costFactor` (2).

**Resonator**

- The Core now **recharges all the Dull crystals the catalyst pays for at the same time**, one catalyst per crystal, instead of one after the other. The chamber and the screen show every crystal that is recharging, and the ones still waiting are marked as queued.
- The recharge still costs one point of integrity per crystal; the order of the chambers decides who goes first when the catalyst runs short.

**Other changes**

- Fixed a possible blank pixel at the tip of the Resonator's ribbons on some graphics cards.
- The in-game guide, the recipe pages and the Resonator page describe the Repairer and the parallel recharge.

**Compatibility note:** the network protocol changed to **33**, so a server and its clients need the same version. No world or configuration migration is needed. NeoForge 21.1.248 or newer is still required, and all optional integrations remain optional.

## 2.8.3

Wireless Automate 2.8.3 redraws the chamber of the Resonator of Signal: a plasma sphere, 3D crystals and plasma tendrils replace the old beams and curves.

**Resonator chamber** (visual only; the generation, tuning and balance are unchanged)

- The core is now a **plasma sphere** drawn by a shader, with convection, a soft halo and a filmic color, and it covers whatever passes behind it.
- The **crystals** are 3D pieces in the pixel-art style of the item sprites (a cluster for the Signal Crystal, a six-sided gem for the Cut and Tuned ones, with the tier color on the Tuned band). They stand on a ring that slowly orbits the sphere (one turn every 42 seconds) and spin on their own axis, faster when tuned.
- Each live crystal sends a **plasma tendril** to the sphere: thick and steady when tuned, thin and wavering when not, so the tuning can be read from the chamber.
- The **recharge** has its own look: a reversed tendril in the catalyst color while the crystal fills, and a flash when it finishes.
- The conduit to each Energy Port follows the real flow, and the plasma shows pressure when the buffer is full.
- Gone: the Lissajous curves, orbits, beams and auras of the old look (they stay only with a shaderpack of Iris, or without the shader), and the Chladni plate on the chamber floor.

**Compatibility note:** the network protocol is unchanged (**32**) and no world or configuration migration is needed. NeoForge 21.1.248 or newer is still required, and all optional integrations remain optional.

## 2.8.2

Wireless Automate 2.8.2 makes the Occultism miners bring up Signal Crystals directly instead of Signal Quartz Ore.

**Occultism miners** (optional, needs Occultism; nothing changes without it)

- The **Djinni** miner (weight 300) and the **Ancient Eldritch** miner (weight 90) now give **2 Signal Crystals** per result, the average of what the ore drops, instead of the ore block that 2.8.1 gave.
- The Fortune of the item in the miner works as usual: Occultism adds extra rolls for it, and each roll can now be a Signal Crystal.
- Both are plain data recipes (`occultism:miner`) that read the new item tag `c:gems/signal_quartz`, which holds the Signal Crystal (the full one). A pack can change the weights or remove them with a datapack.

**Compatibility note:** the network protocol is unchanged (**32**) and no world or configuration migration is needed. NeoForge 21.1.248 or newer is still required, and all optional integrations remain optional.

## 2.8.1

Wireless Automate 2.8.1 lets the Occultism miners dig up Signal Quartz Ore.

**Occultism miners** (optional, needs Occultism; nothing changes without it)

- The **Djinni** miner can now bring up **Signal Quartz Ore**, at a weight of 300: more common than diamond and emerald ore, rarer than copper and iron ore in the same miner.
- The **Ancient Eldritch** miner can bring it up too, at the usual weight of its recipes (90).
- Both are plain data recipes (`occultism:miner`) that read the `c:ores/signal_quartz` tag, so a pack can change the weights or remove them with a datapack.

**Compatibility note:** the network protocol is unchanged (**32**) and no world or configuration migration is needed. NeoForge 21.1.248 or newer is still required, and all optional integrations remain optional.

## 2.8.0

Wireless Automate 2.8.0 ties the energy of routers and Batteries to the Signal Resonator of the same tier, so a router carries what its tier's Resonator generates and a Battery lasts an hour of it.

**Energy rebalance**

- **Router energy** per tier is now the same tier's Resonator full output plus 5%, rounded up to three significant digits. A router moves a Resonator's whole output without much to spare.
- **Battery capacity** per tier is now **one hour** (72,000 ticks) of the same tier's Resonator at full output. They fill and drain on a human timescale instead of in seconds.
- **Ultimate** is still unlimited, for both the router and the Battery.
- The full output of a Resonator is its chambers times the FE per crystal of its tier times 2 (the best crystal, at 100% efficiency).

| Tier | Resonator (FE/t) | Router (FE/t), was → now | Battery (FE), was → now |
| --- | --- | --- | --- |
| Basic | 432 | 1,000 → 454 | 1M → 31.1M |
| Advanced | 27,648 | 32,768 → 29,100 | 8M → 1.99G |
| Elite | 60,000 | 64,000 → 63,000 | 64M → 4.32G |
| Emerald | 1,280,000 | 2,097,152 → 1,350,000 | 512M → 92.16G |
| Allthemodium | 64,000,000 | 67,108,864 → 67,200,000 | 4.1G → 4.61T |
| Vibranium | 80,400,000 | 134,217,728 → 84,500,000 | 32.8G → 5.79T |
| Unobtainium | 149,800,000 | 4,294,967,296 → 158,000,000 | 262G → 10.79T |
| Ultimate | 4,294,967,296 | Unlimited | Unlimited |

Several routers now carry **less** energy than before, on purpose: a Basic router moves 454 FE/t, and an Unobtainium one 158 million instead of 4.29 billion. If you moved energy from other generators through routers of these tiers, you will see lower throughput per face. Both values are still plain config entries (`tiers.<tier>.energyPerTick` and `storage.batteryCapacity.<tier>`), so a pack can set them back.

**Other changes**

- The in-game guide, the tier tables and the Battery page show the new numbers.

**Compatibility note:** the network protocol is unchanged (**32**), so nothing about joining a server changes. A new config migration (balance version 5) replaces the router energy and the Battery capacity only if they are still at the 2.7.0 defaults; values you changed are kept. NeoForge 21.1.248 or newer is still required, and all optional integrations remain optional.

## 2.7.0

Wireless Automate 2.7.0 lets you share a private network with friends and with FTB Teams teams, makes the Tablet Map draggable and complete, and reworks the Linker and the Configurator into two simple tools each.

**Private networks: guests and FTB Teams** (the teams part needs FTB Teams, optional)

- **Invite players** to a private network from the Tablet's **Networks** tab. A guest can use the network: pick it on a face, move nodes onto it, see it and its nodes in the Tablet, and take a face off it. Only the owner and operators manage it (rename, color, public or private, invite, remove).
- **Add an FTB Teams party team** and everyone in it gets the same access, live: whoever joins or leaves the team gains or loses access on their own. A team that was disbanded shows as "removed" and grants nothing.
- **Suggestions while you type**, for players (online first, then everyone who already joined the server) and for teams, up to 8 per query. Arrow keys and Enter pick, Tab completes, and the ✕ asks for two clicks. Typing a full name and pressing Enter works too.
- **Commands:** `/wa network invite <player> <network>`, `/wa network uninvite <player> <network>` and `/wa network team add|remove "<team>" <network>`, for the owner and operators.
- **Config:** `networks.maxMembersPerNetwork` (32) and `networks.maxTeamsPerNetwork` (8), 0 for no limit, operators exempt. `networks.maxNetworksPerPlayer` and `maxGroupsPerPlayer` (64 each) cap how many networks and groups a player can own.
- Taking a face off a network from the router screen now needs the same rights as Move and paste: you must own, be a guest of, or be an operator of that network. A public network you don't own can no longer be emptied of other people's faces.
- `/wa network use` now respects who can use the network.

**Tablet Map**

- **Drag to move the map**, like a canvas. A click without dragging still picks a node, and the **Center** button brings the map back to you.
- **The Map shows every router you can see** in your dimension, no matter what the List has: a search, quick filters, the Machines view and the 256-row cap of the List no longer hide routers from the Map. Up to 1,024 routers, the nearest to you, are sent, and only while the Map tab is open. A note on the Map says when some farther ones are left out.

**Linker (reworked)**

- **Link mode:** click a router to choose it, click a machine to link it to that router (or unlink it if it is already linked), and Shift + click two blocks to mark an area; the button on the screen links the area's machines, nearest first, up to the Range Upgrade's radius and limit. The **Unlink** switch on the screen turns clicks and the area button into unlinking.
- **Open mode (new):** click a machine to open the screen of the router that reaches it, already editing that machine. If several routers reach it, a short list (up to 16) lets you pick one. Range, claims and protection mods are checked on the router.
- The Linker no longer touches networks, tabs or faces: choose the network of each face on the router screen or in the Tablet. Old Linkers keep loading.

**Configurator (reworked)**

- The Configurator is now just the **Brush**: Shift + click a router copies it, click pastes onto another, Shift + click on a block without a router clears it. The wheel (Left Alt) has a single ring: the type that gets pasted (All, Items, Fluids, Energy and, with the optional mods, Chemicals and Source).
- Area paste and the paste modes are gone. To configure many routers, paste a profile on the marked ones in the Tablet. Old Configurators keep loading.
- The network of each face is only pasted if you can use that network, and a face only leaves a network you can edit.

**Fixes**

- **Resonator Core:** a catalyst the tier does not accept could duplicate the stored one when inserted; it no longer does. The Ports are told when the catalyst count drops, tuning crystals take the worst integrity of the ingredients, and recharging without a Dull crystal clears the progress.
- **Energy and Source transfer** now extracts per destination, so a destination that accepts less no longer loses what was taken.
- A corrupted entry in the saved networks no longer breaks loading the rest, a corrupted list mode in a filter no longer breaks the filter, and the balance migration of the config only touches files older than its step.
- The Tablet's bulk Move is rate limited in every path, and the Battery and Source Tank screens no longer leak a text-frame counter.
- The Tablet and the Linker's Open mode are checked against claims, reach and ownership on the server.

**Compatibility note:** the network protocol is now **32** (was 29), so server and clients need the same version. Worlds and items from 2.6.0 load normally; the Linker's and Configurator's old saved data is kept only so those items still load. Old private networks keep working as before (no guests, no teams). **FTB Teams** for 1.21.1 (tested with 2101.1.11) is a new optional integration (it needs FTB Library and Architectury, which it asks for itself); nothing changes without it. NeoForge 21.1.248 or newer is still required, and all other optional integrations remain optional.

## 2.6.0

Wireless Automate 2.6.0 adds data-component conditions to filter rules (so you can filter by things like an Apotheosis gem tier), suggestions while you type in the rule fields, and a few Tablet and chest quality-of-life fixes.

**Filter rules**

- **New "Component" condition** in the rule builder: match any mod's data component, written as `mod:component=value` (for example `apotheosis:purity=perfect` for a perfect gem). Without `=value` it just requires the component to be present, and the value may omit the namespace. It works for components of any mod without depending on it; if the mod is not installed, the rule simply never matches.
- **Suggestions while typing** in the "Only in" (tags and mods) and "Component" (ids and values) fields, including values taken from the items in your inventory.
- **The old "Components" toggle button is gone** from the filter screen: component rules replace it.

**Router and Tablet**

- The face cube only changes a face's mode when that face is already selected, so the first click just selects it.
- The router screen and the Tablet remember the last face you chose for each router (the 64 most recent) and reopen on it. The Tablet no longer overrides your choice when it opens a node by itself.

**Storage**

- In the chest and tank list, the mouse wheel only moves items between the list and your inventory while Shift is held; otherwise it scrolls the list.

**Compatibility note:** the network protocol is now **29** (was 28), so server and clients need the same version. Filters saved with 2.5.0 load normally. NeoForge 21.1.248 or newer is still required, and all optional integrations remain optional.

## 2.5.0

Wireless Automate 2.5.0 redraws the router, Tablet, Linker and filter screens around a cleaner face editor, and rebalances the top of the Resonator ladder so each tier costs what it generates.

**Cleaner screens**

- **Face editor:** the router screen and the Tablet's node panel show the six faces of the machine as an unfolded cube, one button per face that is also the mode (click cycles Extract, Insert, Storage, None; right-click goes back; Shift sets None). Each face shows its network as a dot, and a face with a mode but no network is outlined and asks you to pick one.
- **Clear, Copy and Paste** buttons beside the cube, with Clear needing two clicks; the line below it shows the face, its network, filter, redstone and priority in labelled rows.
- **Range Upgrade:** a bigger 3D view with the machine list under it, and the Linker uses the same face cube for choosing which faces to link.
- **Tablet:** a new **Grid** view in the List (one row per router, one column per type, one stripe per face with a mode), the problem count ("! N") next to the view selector, and the filter screen tabs reorganised.
- **Small windows:** the face cube stays whole above Clear/Copy/Paste, the Tablet notice no longer covers the theme button, the Routers/Machines/Grid selector shrinks to fit in Portuguese, and network names stay readable on the light theme in the Linker.

**Resonator rebalance (measured against the generators of the Infinity Legacy II pack)**

We counted the whole crafting cascade of the rival of each tier, from the first raw item to the finished generator, and put the Resonator on the same line.

- **Unobtainium** now generates up to **149.8 million FE/t** (10.7 million FE per crystal, was 184 million and 2.58 billion FE/t). Ultimate stays at 4.29 billion FE/t, now as an end-game project.
- **Resonator Hearts from Emerald to Ultimate have a second recipe with items from other mods**, used only when those mods are installed (the previous recipe still works without them):
  - Emerald: 6 emerald blocks + Field Stabilizer + Nether Star + Modern Industrialization Processing Unit.
  - Allthemodium: Field Stabilizer + 4 Allthemodium blocks + 2 Nether Stars + 2 MEGA Cells 16M Cell Components.
  - Vibranium: Condensed Core + 4 Vibranium blocks + Nether Star + Processing Unit + 2 Draconic Evolution Chaotic Cores.
  - Unobtainium: Condensed Core + 4 Unobtainium blocks + 2 Nether Stars + 16M Cell Component + Chaotic Core.
  - Ultimate: 3 Cell Components 256M + 64M Cell Component + Quantum Circuit + Chaotic Core + Awakened Core + Alloy Frame + Nether Star.
- Building a whole Core from Basic now takes about 4,500 base items at Emerald, 42,500 at Allthemodium, 49,400 at Vibranium, 70,900 at Unobtainium and 650,000 at Ultimate (was 1,300, 2,100, 3,600, 6,700 and 11,300). Basic, Advanced and Elite are unchanged.
- The guide book lists both recipes, and JEI shows the one that is active.

**Compatibility note:** the network protocol is now **28** (was 27), so server and clients need the same version. A new config migration (balance version 4) changes the Unobtainium FE per crystal from 184,000,000 to 10,700,000 only if it is still at the old default; values you changed are kept. NeoForge 21.1.248 or newer is still required, and all optional integrations (Mekanism, Ars Nouveau, Allthemodium, MEGA Cells, Draconic Evolution, Modern Industrialization) remain optional.

## 2.4.2

Wireless Automate 2.4.2 stops new Range Upgrade machines from silently inheriting their group's profile, and makes the router screen remember your last tab and face.

**Range Upgrade: new machines no longer inherit the group profile**

- Linking a machine whose block already has a group (Linker, Machines or Area mode) now adds it with an empty exception: no mode and no network, so it stays idle until you configure it. The same block can run a different process, so nothing is applied automatically.
- The new machine shows as an "exception" in the list. Use "Back to profile" to make it follow the group again. The first machine of a block still creates its group as before.

**Screens remember your choice**

- The router screen (and the node panel in the Tablet) now remembers the last resource tab and face you picked, so coming back from the filter screen no longer resets to Items and Front. The tab applies to any router; the face only to the same router. It is kept until you close the game.

**Compatibility note:** the network protocol remains 27, unchanged from 2.4.1. No world or configuration migration is required; existing groups and exceptions are preserved. NeoForge 21.1.248 or newer is still required, and all optional integrations remain optional.

## 2.4.1

Wireless Automate 2.4.1 fixes unwanted automatic network assignment when configuring a machine face.

**Explicit face networks**

- Choosing Extract, Insert or Storage on a face without a network no longer assigns the active network selected in the Network Tablet or Linker.
- Opening the router or its panel in the Tablet does not assign a network. Choose the face's network explicitly in the Network row or use the Linker.
- Changing an action keeps any network already assigned to the face. Explicit Linker operations and copied configurations continue to work as before.

**Other changes**

- Updated the English and Portuguese hints, guide and documentation to explain explicit network selection.
- The interface layout is unchanged in this patch.

**Compatibility note:** the network protocol remains 27, unchanged from 2.4.0. No world or configuration migration is required; existing face networks are preserved. Install the update on the server or single-player instance to apply the fix. NeoForge 21.1.248 or newer is still required, and all optional integrations remain optional.

## 2.4.0

Wireless Automate 2.4: the Signal Resonator rebalanced to be the best generator of its tier, with a crafting cascade, a Resonator Heart per tier, Tuned crystals, crystal fatigue, interference and the Resonant Echo; a new animated chamber drawn by core shaders; and the Network Tablet List by router, with renaming.

**Signal Resonator: the ladder**

- **New FE per crystal:** Basic 216 · Advanced 6,912 · Elite 10,000 · Emerald 160,000 · Allthemodium 6,400,000 · Vibranium 6,700,000 · Unobtainium 184,000,000 · Ultimate 268,435,456 (all configurable). Each tier now beats the best generator of that tier in a typical pack, by about 20%: at full tune with the tier's Tuned crystals, from 432 FE/t on Basic to 4,294,967,296 FE/t on Ultimate, plus the Echo.
- **Router energy per face** follows the generator: Basic 1,000 · Advanced 32,768 · Elite 64,000 · Emerald 2,097,152 · Allthemodium 67,108,864 · Vibranium 134,217,728 · Unobtainium 4,294,967,296 · Ultimate unlimited.
- **Yield in quarters:** from an Elite Core up, a plain Signal Crystal yields 1/4, a Cut crystal 1/2 and the **Tuned crystal** of the Core's tier (or higher) 2. Basic and Advanced Cores keep 1 and 2. Nothing leaves a chamber in old worlds; the crystals just yield less until you tune them.
- **Tuned Signal Crystals** (Elite, Emerald, Allthemodium, Vibranium, Unobtainium, Ultimate): a Cut crystal plus the tier's material at the crafting table (netherite for Elite, emerald blocks and a Nether Star for Emerald, the Allthemodium metals above). A Tuned crystal can be retuned to a higher tier.
- **Crystal life per tier:** a charge lasts 20, 20, 15, 10, 5, 5, 4 and 3 minutes at 100% generation from Basic to Ultimate (`resonator.crystalLifeMinutes`).

**Signal Resonator: maintenance**

- **Crystal fatigue:** every crystal has 8 charges of integrity (configurable). Each recharge spends one; at 0 the crystal **cracks**, stops generating and must be **recast** at the crafting table with 4 full Signal Crystals, keeping its tier.
- **Catalyst by tier:** Basic and Advanced Cores recharge with redstone or **Resonance Dust** (1 redstone + 1 glowstone dust + 1 full Signal Crystal, makes 4). Elite and Emerald Cores take only Resonance Dust. Allthemodium, Vibranium, Unobtainium and Ultimate Cores take only the **Charged Dust** of their own tier (8 Resonance Dust + 1 ingot of the tier, makes 8; without Allthemodium the Ultimate one takes a netherite ingot).
- **Interference:** every other formed Core within 32 blocks in the same dimension takes 15% of the efficiency, up to 60% (configurable, 0 disables). The Core screen shows the loss and the nearest neighbour.
- **Resonant Echo** (Ultimate only): burn a cracked Ultimate Tuned crystal in the Echo slot (or through the Crystal Port) for a permanent +5% of base generation each, with no cap by default.

**Signal Resonator: the crafting cascade and the Heart**

- **11 new items** made from Signal Crystals and vanilla materials: Raw Signal Alloy (blasted into the **Signal Alloy Ingot**), Resonance Plate, Wire and Coil, Harmonic Lens, Phase Circuit and Matrix, Field Stabilizer, Condensed Core and the **Resonator Heart**, one per tier (Basic to Elite from Phase Matrices, Emerald and Allthemodium from Field Stabilizers, Vibranium to Ultimate from Condensed Cores, each with the tier's own materials; the Ultimate Heart takes an **Alloy Frame**).
- The **Resonance Core** recipe takes the Basic Heart instead of a diamond, and **raising the Core a tier** with an Upgrade Card now also spends the Heart of the target tier from your inventory (not in creative). Routers and storage blocks do not change.
- The Ultimate Upgrade Card ring with Allthemodium (and without All The Tweaks) now takes 4 Nether Stars, a dragon egg and 3 Unobtainium-Vibranium alloy blocks.
- All of the cascade's crafting recipes are editable in `/wa recipes` (the ingot is a blast furnace recipe).

**Signal Resonator: the chamber and the screen**

- **New chamber** when the Core is formed, drawn by three core shaders: a plasma Core with the tier's ring and the Lissajous figure inside, an energy conduit to every Energy Port that is actually delivering (width and speed follow the real flow), the plasma under pressure when the buffer is full, a catalyst spiral and a ring for the recharge, an aura on every live crystal (shaking out of tune) and faceted 3D crystals that fill up as they recharge. With an Iris shader pack, or without the shaders, the previous drawing stays.
- The Core screen shows the **integrity** bar under each chamber (red when cracked), the catalyst the Core takes, the **interference** line and, on Ultimate, the **Echo** box.
- **JEI:** the recharge category shows the catalyst per tier and the integrity cost, a "Recast the crystal" category, and the per-tier page with the Tuned crystal, integrity, interference and the Echo. The items of the Allthemodium tiers stay hidden without the mod.
- **New textures** for the whole cascade: raw alloy, ingot and dusts as recolours of the vanilla shapes, plate, wire and coil in the vanilla style, and each component with its own silhouette.
- **Guide book:** the Signal Quartz and Signal Resonator pages cover the Tuned crystals, integrity, recasting, the dusts, the cascade and the Heart, interference and the Echo, in English and Portuguese.

**Network Tablet**

- **The List by router:** every router is a top row with its tier icon, its name (or "Router <tier>"), the tier pill and the attached machine on the second line; the old grouping by attached block is gone. Routers are sorted by name (named ones first), then by tier and machine.
- **Rename from the List:** ✎ or a double click on the router row renames the router, and on the attached machine's row in the tree names that machine; the same ✎ is on the Editing pill of the router screen and the panel. The attached machine's name shows in the List, the search, the Machines view and the panel.

**Other changes**

- Config migration (balance 3): on the first load, the FE per crystal and the router energy that are still at the 2.3 defaults move to the new values; anything you changed stays.
- The e2e test measures the chamber's frame time, old drawing and plasma.

**Compatibility note:** the network protocol changed (27), so the server and every client need 2.4.0. Worlds from 2.3 load as they are: crystals already in a Core keep working (with the new yield from Elite up) and redstone already in an Elite or higher Core is used up before the Core asks for Resonance Dust; existing Cores keep their tier, and no item or block is lost. The server config migrates the Resonator and router energy defaults on its own (`migration.balanceVersion` 3), keeping anything you changed. Still requires NeoForge 21.1.248 or newer. Mekanism, Ars Nouveau, Allthemodium, JEI and GuideME stay optional.

## 2.3.0

Wireless Automate 2.3: find any linked machine and see it in the world, the Network Tablet List with every machine under its router or by block type, and pasting profiles onto single machines and whole groups.

**Find your machines** (Range Upgrade)

- **Names for machines and groups:** rename a linked machine or a machine group with ✎ or a double click on its name (Enter saves, Esc cancels). Unnamed machines keep their number (#3).
- **Coordinates and state** on every row of the router's machine list: where the machine is, how far from the router, and whether it is moving, sleeping, unloaded, out of range or replaced.
- **Show in the world:** the eye ◉ on a machine row, or **In world** in the Tablet panel, outlines the machine through blocks with a line to its router. Up to 64 machines lit at once, from any router; they stay lit with the screen closed until you turn them off, use **Turn all off** or leave the world. Works with Fabulous graphics.
- **Right-click a machine with the Tablet** to open it with that machine already picked (before the machine's own screen opens). The Linker now says the machine and the group when it links or unlinks.

**Network Tablet**

- **New tabs:** List · Flows · Map · Networks. Network groups are now sections of the Networks tab, with Pause in each header, "No group" at the bottom and the group details on the right.
- **The List as a tree:** ▸ opens a router to show its attached machine, its machine groups (×N, with the exceptions) and each machine with its state in words, its rate while it moves and the eye ◉. 8 machines per group before **Show all**. The Tablet remembers the open routers and collapsed groups when you reopen it.
- **The List by machines:** the **Routers | Machines** switch shows every machine of the base, attached and linked, grouped by block type ("Furnace · 23 · 3 routers"), with "via <router>" or "attached" on each row. The type and network filters now apply per machine, not per router. Clicking a machine picks its router and edits only that machine in the panel.
- **The Map** shows the machines of the picked router as small squares joined to it by dashed lines, lit ones in cyan; clicking one picks it as the target.
- **Select, paste and move on machines:** Select now marks routers, machine groups, single machines and whole blocks. **Paste on marked** pastes a profile on a machine as its exception (sides follow that machine's front), on a whole group as the group profile (exceptions are kept), and on a router as before. **Move** changes the network of a whole router, or only of the faces with a mode on a machine. The notice says what changed and has **Undo**.
- The header counts the machines you can see ("14 routers · 63 machines").

**Other changes**

- The Tablet List builds its text only when the page changes, not on every frame (from 33–62 µs to 2–12 µs per frame).
- Guide book: the Network Tablet and Router pages explain names, the world highlight, the tree, the Machines view, pasting on machines and the new tabs, in English and Portuguese.

**Compatibility note:** the network protocol changed (25), so the server and every client need 2.3.0. Worlds from 2.2 load as they are. Profiles copied with the Configurator before 2.3 can still be pasted on routers, but not on single machines: copy them again. Still requires NeoForge 21.1.248 or newer. Mekanism, Ars Nouveau, Allthemodium, JEI and GuideME stay optional.

## 2.2.0

Wireless Automate 2.2: a Flows tab in the Network Tablet that shows what comes in and goes out of every network, fixes for the Range Upgrade, and a full efficiency pass that makes the whole mod lighter on the server, the network and the client.

**Flows tab** (Network Tablet, replaces Statistics)

- **What comes in and goes out**, per type or per network (**By type** | **By network**): Extract faces are what comes in, Insert faces what goes out, in each type's unit (items/s, mB/s or B/s, FE/t, Source/s). Storage faces show their balance, filling or emptying.
- **What needs attention**, in short words on each line: sources with nothing to give, sources waiting for a destination, full destinations, faces stopped by redstone and faces without a network. The tooltip has the full counts.
- **Click a line** to open the List filtered by that type (and network).
- The "Mod time" bar stays on top. Paused networks show as paused.
- **Guide book:** the Network Tablet page explains the Flows tab, in English and Portuguese.

**Range Upgrade**

- **The Linker links machines without a network.** A new machine group used to start with every face, on every tab, in your active network. Now it starts with no network, and a face joins your active network when you pick a mode on it.
- **Clear networks:** while editing a machine group or a machine with an exception, a new button removes the network from every face set to None, on every tab. Faces that move something stay as they are. The notice has **Undo**.
- **Fix:** what a linked destination refused after accepting it went back into the router's attached machine instead of the linked one. It now goes back to the right machine.
- **Fix:** full linked destinations now count in the Tablet's "With problems" filter and in the full-destination notice.

**Efficiency** (measured with the mod's own benchmark, before and after)

- **Idle networks cost a third of what they did:** sleeping sources are skipped without being touched (−68% with 500 idle routers).
- **Redstone-controlled faces** no longer rebuild the routes on every pulse; the signal just locks the face (a clock next to 100 routers: −42%).
- **The Network Tablet sends about 93% less data:** when only rates and states change, only they are sent (from 9–30 KB/s down to 0.4–1.7 KB/s per player with 1,000 routers). With several players looking, the measurement is done once for all of them.
- **The mod's storages:** one neighbour update per tick instead of one per transfer (250 sources on one Chest: up to −22%), stock limits in constant time (a Chest with 2,000 item types and a stock limit: −70%), and saving no longer re-encodes every item type.
- **Memory:** linked machines only keep what their modes use (about 300 bytes less per linked machine), filters with the same tags share them, and the Tablet's node index drops routers that vanished without notice.
- **Signal Resonator:** about a third cheaper, both generating and idle.
- **Screens:** the Chest's `#` search no longer rebuilds every tooltip on each key (with 2,500 item types: from 14–26 ms to under 3 ms per key), and the node panel and the Tablet List lay out once per frame. Opening a Filter goes through the item registry only once per game.

**Other changes**

- The contents of a broken storage are only kept on the server when its item actually dropped. A storage removed with `/setblock` or `/fill` loses its contents, like a vanilla chest. New server option `storage.stashExpiryDays` (default 0: kept forever) to let old stashed contents expire.

**Compatibility note:** the network protocol changed (22), so the server and every client need 2.2.0. Worlds from 2.1 load as they are. Still requires NeoForge 21.1.248 or newer. Mekanism, Ars Nouveau, Allthemodium, JEI and GuideME stay optional.

## 2.1.0

Wireless Automate 2.1: the Signal Resonator, a multiblock crystal generator you tune like a radio, and quicker buttons in the Range Upgrade's machine list.

**Signal Resonator** (new)

- **Signal Quartz ore** in the Overworld, in stone and deepslate (from y = −64 to 32, most common around −16). Mine it with an iron pickaxe or better: 1 to 3 **Signal Crystals**, more with Fortune; Silk Touch gives the ore.
- **Signal Crystal** generates for 10 minutes. The **Cut Signal Crystal** (4 crystals around an Amethyst Shard, only full crystals) yields twice as much and lasts twice as long. The charge shows as the item's bar; a spent crystal turns **Dull**.
- **The structure:** a hollow 5×5×5 box. Porcelain Casing on the edges and corners; Resonance Glass, the ports and the Tuner in the middle of the faces. It needs one **Resonance Core**, at least one **Energy Port** and one **Crystal Port**; the **Tuner** is optional. Click the loose Core with an empty hand to project the missing blocks in the world (Shift + scroll switches between the Showcase and Band layouts). Two Resonators can't share a wall.
- **Tuning:** every Resonator has its own **station** between 88 and 108 MHz, and it drifts slowly. Open the Core screen (click the assembled Core with an empty hand) and turn the dial until the **IN TUNE** lamp lights: 100%. Off the station, efficiency drops, and at 2 MHz away it generates nothing. An oscilloscope and a pointer show how close you are.
- **Tuner:** in automatic mode it follows the station on its own, up to a ceiling per tier (85% on Basic to 98% on Ultimate).
- **The chamber:** through the glass you can read the tuning. In tune, a light figure closes, the crystals rise together and a Chladni pattern settles on the floor; out of tune, it scribbles. It lights up in sequence when the structure is assembled.
- **Recharge:** a Dull crystal left in a chamber recharges with redstone in the Core: one at a time, 30 seconds and 1 dust each. The Crystal Port takes crystals and redstone from pipes or routers and hands back the Dull ones while the Core has no redstone.
- **Energy** leaves through the Energy Port, straight into a router. The Core keeps a buffer of 10 seconds of its full output.
- **Tiers by the Core**, raised with the same Upgrade Cards as the router, without breaking anything: from 1 chamber and 128 FE/t per crystal (Basic) to 8 chambers and 1,073,741,824 FE/t per crystal (Ultimate); Allthemodium, Vibranium and Unobtainium in between with ATM10.
- **JEI:** the structure in 3D (drag to turn, scroll to zoom, view by layer), the recharge, and a per-tier table on the Core.
- **Guide book:** two new pages, Signal Quartz and crystals, and Signal Resonator, in English and Portuguese.
- Operators: `/wa resonator <pos> info` and `dial <MHz>`; `/wa profile` shows the Resonators' time per tick.

**Range Upgrade**

- The chosen row in the machine list now has its own buttons: **Update**, **Back to profile** and **Unlink** (two clicks). The "Editing" line only appears when the list isn't in view.
- A linked machine that was replaced by another block with an inventory or tank shows as **replaced**, in brass, with the new block's name. **Update** moves it to that block's group (also for a whole group at once).

**Other changes**

- The Unobtainium router's energy rate went from 262,144,000 to **2,097,152,000 FE/t**, so it keeps up with an Unobtainium Resonator. Configs still at the old default are updated on the first start; changed values are kept.
- Upgrade Cards say they also raise a Resonance Core.
- Panel notices wrap to up to three lines instead of being cut.

**Compatibility note:** the network protocol changed (18), so the server and every client need 2.1.0. Worlds from 2.0 load as they are. Still requires NeoForge 21.1.248 or newer. Mekanism, Ars Nouveau, Allthemodium, JEI and GuideME stay optional.

## 2.0.1

Wireless Automate 2.0.1: a fix for the Network Tablet on servers with friends.

**Fixes**

- **Public networks in the Network Tablet:** another player's public network showed up in the Tablet with 0 nodes, and its routers never appeared in the List. Now a router with a face in a public network shows up for everyone: in the List, on the map, in the network's node count and throughput, and in the node panel (editable within the Tablet's reach, the same as walking up to it). Other players' private networks stay hidden.

**Compatibility note:** no protocol change (still 16): 2.0.0 and 2.0.1 clients and servers work together, but the fix is on the server, so update the server. Worlds from 2.0.0 load as they are. Still requires NeoForge 21.1.248 or newer.

## 2.0.0

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

## 1.9.1

Wireless Automate 1.9.1: the recipe editor handles the tier rings again, and the Linker is easier to clean up with.

**Recipe editor (`/wa recipes`)**

- The tier rings (Upgrade Cards) show up again and can be edited: the 8 ring slots take an item or a tag, the center and the result stay locked. Save, Restore and Disable work as for the other recipes, and an edited ring counts for the direct recipe and for the cards alike, so both paths still cost the same.
- Ring recipes edited before 1.9.0 show up as "edited" and can be restored from the screen.

**Linker**

- The network list matches the Network Tablet: your networks first, then the public ones (every network for an operator).
- "None (unlink)" with **All faces** takes the checked tabs out of the network completely, including the range router's groups and exceptions. To clear a router entirely: None (unlink) + All tabs + All faces.

**Compatibility note:** the network protocol changed (15), so the server and every client need 1.9.1. Worlds from 1.9.0 and older load as they are.

## 1.9.0

Wireless Automate 1.9: one router can now serve many machines around it with the new Range Upgrade, networks belong to each face of a machine instead of each tab, and raising a tier costs the same at the crafting table as with a card.

**Range Upgrade** (new)

- A new upgrade for the router's second upgrade slot. With it, one router moves to and from **many machines in its radius**, not only the one it sits on. No pipes, no extra routers.
- Pick machines with the **Linker in Machines mode** (Shift + click in the air cycles Single → Area → Machines): click the router, then click machines to link or unlink them, or mark an area and link every machine inside at once. With **None (unlink)** selected, the area button unlinks the router's machines inside it instead.
- Radius and machine limit per tier (server config `machineRadius` and `maxMachines`): Advanced 6 blocks / 8 machines, Elite 12 / 16, Emerald 16 / 32, Allthemodium 24 / 48, Vibranium 32 / 64, Unobtainium 48 / 96, Ultimate 64 / 128. The Basic router does not take the upgrade.
- **Configure by machine type:** linked machines of the same block form a group with one profile, so setting up the group sets up all of them. Change a single machine and it becomes an exception; **Back to profile** undoes it, **Unlink** removes it.
- Sides of linked machines are **relative to each machine's front** (Front, Back, Left, Right, Top, Bottom), so a farm with machines facing different ways shares one profile.
- Every linked machine gets the full throughput of the router's tier, as if it had its own router.
- Linked machines wake the router the moment they change, like the attached machine. A machine in an unloaded chunk just waits (the router never loads chunks for it) and shows a warning; a broken one shows as gone, with Unlink.
- On a wide screen the machine list and the 3D view sit side by side.
- Respects claims: the Linker only links machines you are allowed to use.
- Recipe: ender pearls, eyes of ender, redstone blocks and a Linker.

**Networks per face**

- Each face of a machine now has its own network, per resource type. A furnace can take items from your ore line on top and send results to storage from the bottom, with a single router.
- New **Network** row on the router screen for the selected face, a colored dot on every face button, and an "N networks" summary on the tab row.
- Face names on the router screen are relative to the machine (Front, Back, Left, Right, Top, Bottom). A machine with no front uses North as its front, and the screen says so.
- Linker: a new **Faces** row (All, or one button per side of the machine) picks which faces join the active network, or leave it with **None (unlink)**.
- Configurator: copies and pastes the network of each face.
- Network Tablet: a router shows up in every network one of its faces is in ("faces in 2 networks"), and warns about faces left in a network that was removed. Moving nodes with the Tablet only takes faces out of networks you own (or as an operator).
- Command: `/wa face ... network <name|none>`.
- **Old worlds migrate on their own:** the network of each tab moves to all six faces of that type, so everything keeps working as before.

**Raising a tier**

- **Direct recipe:** put a router (or any storage block) in the middle of the next tier's ring at the crafting table to get it one tier up, keeping what the item carries (the name, or the contents of a storage block).
- **Upgrade Cards now have an origin**, like "Upgrade Card: Basic → Elite": the ring around a Redstone makes the one-step card, and a card in the middle of the next ring extends it one more tier ("Basic → Elite" + the Emerald ring = "Basic → Emerald"). Both paths cost exactly the same.
- A card only works on a block of its origin tier, by Shift + right click or at the crafting table; otherwise the action bar says which tier it upgrades.
- New rings for Advanced (gold ingots and diamonds), Elite (netherite ingots and obsidian) and Emerald (emerald blocks, crying obsidian, eye of ender and the Nether Star, which moved here from Elite). The Allthemodium and Ultimate rings are unchanged.
- **Old cards keep working** as before, from any lower tier.
- JEI shows the direct recipes, the cards and the block + card pairs by origin.

**Fixes**

- Machines that refuse an item by its type (like Apotheosis's Salvaging Table with items it can't salvage) no longer put the router to sleep as if they were full. Before, a chest full of non-salvageable items made deliveries stop and start, with "destination full" on an empty table.

**Other changes**

- Guide book pages updated (router, networks, Linker with the Machines mode, Configurator, Tablet, Upgrade Cards and recipes), in English and Portuguese.

**Compatibility note:** the network protocol changed, so server and clients need the same version (1.9). Worlds from 1.8 and earlier load and migrate on their own; Upgrade Cards made before 1.9 still work. Requires NeoForge 21.1.248 or newer; Allthemodium, All The Tweaks, Mekanism, Ars Nouveau and JEI are optional.

## 1.8.0

Wireless Automate 1.8: fixes a crash when opening the Wireless Chest and makes its list easier to work with: tag and tooltip search, a list that holds still while you hold Shift, and a right click that always takes half.

**Fixes**

- Opening a Wireless Chest (or Tank) no longer crashes the game with "Comparison method violates its general contract!". It happened in big packs when an item from another mod changed itself while its name was read, in the middle of sorting the list.
- The search box now starts empty every time you open the screen. Before, the last search came back and could show up scrolled or cut off.
- Right click on an item in the list takes half of what is there when the chest holds less than a stack (10 → 5, 1 → 1). With a stack or more, it still takes half a stack.

**Chest and tank list**

- **`#` search:** `#text` looks in the item's tags and tooltip, like JEI. `#c:gems`, `#ingots`, or a tooltip line such as `#not yet eaten` (everything after `#` up to the next `@` or `#` is one phrase). Fluids search their tags. It mixes with the name and `@mod`: `iron #ingots @mekanism`.
- **Hold Shift to lock the order**, like the ME terminal of AE2: while Shift is down, items keep their places as you Shift + click them into your inventory. An item that runs out stays as a faded cell until you let go, then the list sorts again.

**Other changes**

- Guide book page of the Wireless Chest updated, in English and Portuguese.

**Compatibility note:** same network protocol as 1.7, but server and clients should both update (the half-stack rule runs on the server, the search and the Shift lock on the client). Worlds load as they are. Requires NeoForge 21.1.248 or newer; Allthemodium, All The Tweaks, Mekanism, Ars Nouveau and JEI are optional.

## 1.7.1

Wireless Automate 1.7.1: fixes the recipe editor starting several reloads at once in big packs.

**Fixes**

- The recipe editor now runs one reload at a time. Before, opening or closing the screen while a reload was still running (it takes several seconds in a big pack) started another one on top of it. Overlapping reloads broke mods like KubeJS ("reload failed") and could fill the memory.
- If a reload fails, saved changes stay pending and closing the screen no longer retries it on its own; press **Reload now** to try again. Pressing it while a reload is running just says one is already in progress.

**Compatibility note:** same network protocol as 1.7, but server and clients should both update. Worlds and saved recipe edits load as they are. Requires NeoForge 21.1.248 or newer; Allthemodium, All The Tweaks, Mekanism, Ars Nouveau and JEI are optional.

## 1.7.0

Wireless Automate 1.7: a recipe editor for server operators. Change, disable or restore any of the mod's crafting recipes in game, with no datapack or script to write.

**Recipe editor** (operators only)

- Run **`/wa recipes`** to open the editor. The list shows the mod's crafting recipes that are loaded in your pack, with a search box and a dot for the state: green is the mod default, orange is edited, grey is disabled.
- Pick a grid slot and click an item in your inventory to put it there. The item stays in your inventory; the grid only keeps a ghost copy. You can also click with an item on the cursor, or drag one from JEI. Right-click a slot to clear it.
- **Tag** turns a slot into "any item of this tag", choosing among the tags of the item in it; the arrows go through them.
- **−** and **+** change how many items the recipe makes, up to the item's stack size (64 at most).
- **Save** writes the change, **Disable** removes the crafting recipe of that item, **Restore default** brings back the mod's recipe.
- Changes apply after a reload: press **Reload now**, or just close the screen. Everyone online gets the new recipes and JEI updates. Saving doesn't reload every time, because a reload freezes a big pack for a few seconds.
- Edits are saved as a datapack in `config/wirelessautomate/recipes/`, so they work in every world of the instance (on a server, for the whole server), survive mod updates and can be copied to another instance. Delete the `data` folder in there and reload to undo everything.
- Recipes that only exist with another mod (Allthemodium tiers, the ATM Star Ultimate, the Chemical and Source tanks) keep that condition, so an edit never breaks a pack that loses the mod later.
- If another datapack or a KubeJS script changes the same recipe, it wins, and the editor says the loaded recipe is different from what you saved.

**Other changes**

- New guide book page, "Recipe editor", in English and Portuguese.

**Compatibility note:** 1.7 changes the network protocol, so server and clients must all run 1.7. Worlds load as they are, and nothing changes until an operator edits a recipe. In single player, the editor needs cheats on (open to LAN). Requires NeoForge 21.1.248 or newer; Allthemodium, All The Tweaks, Mekanism, Ars Nouveau and JEI are optional.

## 1.6.0

Wireless Automate 1.6: a radial wheel for the Configurator, and Area pasting can now reach routers on any machine.

**Configurator wheel**

- Hold **Left Alt** with the Configurator in your main hand to open a wheel in the middle of the screen (the key can be changed in Controls, under Wireless Automate). The game doesn't pause.
- The inner ring picks the **mode** (Brush, Area on the same machine, Area on any machine); the outer ring picks the **pasted type** (All, Items, Fluids, Energy, plus Chemicals with Mekanism and Source with Ars Nouveau).
- Release the key to pick the slice under the mouse and close. Click to pick without closing, so you can change the mode and the type in one go. Release in the center or press Esc to close without changing anything.
- The current choice of each ring has an orange border, the slice under the mouse grows, and the center tells you what it does. Names follow the curve of the ring, and the wheel looks the same at any GUI scale.
- Shift + click in the air and Shift + mouse wheel still work as before.

**Area (any machine)**

- Until now, Area pasting only reached routers attached to the same kind of machine as the copied one (a router copied from a Barrel would skip the one on a Wireless Chest). That rule is now a choice: **Area (same machine)** keeps it, **Area (any machine)** pastes on every router in the area.
- Shift + click in the air now cycles through the three modes: Brush → Area (same machine) → Area (any machine) → Brush.
- When the same-machine mode skips routers, the action bar tells you how many and that the any-machine mode is on the wheel. The tooltip shows the current mode and the wheel's key.

**Other changes**

- The guide book's Configurator page explains the three modes and the wheel, in English and Portuguese.

**Compatibility note:** 1.6 changes the network protocol, so server and clients must all run 1.6. Worlds load as they are; existing Configurators in Area mode keep pasting on the same machine only. Requires NeoForge 21.1.248 or newer; Allthemodium, All The Tweaks, Mekanism, Ars Nouveau and JEI are optional.

## 1.5.2

Wireless Automate 1.5.2: a brand-new look, "Porcelain and Signal": every item and block redrawn, real 3D block models, light screens, and collision boxes that hug the models. No gameplay changes.

**New visual identity**

- The mod now looks like signal instrumentation gear: a porcelain shell, a graphite chassis, a single coral stripe and a glass lens in the tier's color (the "Eye") on top of every block and on every item.
- All 13 items are animated: a slow, subtle pulse shared by the whole mod plus one motion of its own for each item (the Tablet's radar sweep, the Linker's probe, the valves of the Tier Cores, the Chunk Loading Upgrade's lantern flame...). Cycles of 1.6 to 3.2 s, so nothing flickers.
- Real 3D block models instead of textured cubes: the router with its tilted dish antenna, the Wireless Chest as a drawer cabinet, the Tank as a glass column, the Battery as a stack of cells, the Chemical Tank as a spherical vessel and the Source Tank as a jar that shows its 11 fill levels.
- In your inventory, the creative tab and JEI, the router item now shows its front, with the dish and the Eye in the tier's color.
- Light theme on all six screens (router, filter, Tablet, Linker, Chest list, Battery and Source Tank), with the 3D viewer on graph paper. Only the look changed: every control works as before.

**Collision boxes**

- The router's collision and selection boxes follow the new geometry (base, body, rods, mast and the dish), for every facing and spin.
- Storage blocks now have shaped collision boxes that hug their models instead of a full cube, and they no longer darken or hide the faces of neighbouring blocks.

**Other changes**

- All textures, block models and blockstates are generated by script (`scripts/textures/`), with a geometry check that rejects overlapping or self-intersecting elements. The design guide is in `docs/identidade-visual.md`.

**Compatibility note:** 1.5.2 doesn't change the network protocol, so it works with 1.5.0 and 1.5.1 clients and servers. Worlds load as they are; blocks already placed get the new look. Resource packs made for the old textures will no longer match. Requires NeoForge 21.1.248 or newer; Allthemodium, All The Tweaks, Mekanism, Ars Nouveau and JEI are optional.

## 1.5.1

Wireless Automate 1.5.1: the Wireless Chest now feeds big-stack inventories (Sophisticated Storage with stack upgrades, drawers) a whole slot at a time.

**Faster deliveries from the Wireless Chest**

- From a Wireless Chest to another mod's inventory, the router used to hand over at most one stack (64 items) per insert, even when the slot could take far more. Now each slot gets everything it accepts in one go when the slot holds more than 99, as with Sophisticated Storage stack upgrades or drawers.
- Tested: 2.1 billion cobblestone from a Wireless Chest into a Sophisticated Storage barrel with the Omega Stack Upgrade, on an Ultimate router, now arrive **in one tick (about 1 ms of server time)**. Before, that took about an hour and 45 minutes of game time.
- Regular inventories (vanilla chests, most machines) still get at most one stack per insert, so a machine that doesn't check what it receives never gets an oversized stack. They fill faster too: one delivery now fills every free slot instead of one stack at a time.
- Other mods' storages as the source don't change: they still hand out one stack per extraction.

**Compatibility note:** 1.5.1 doesn't change the network protocol, so it works with 1.5.0 clients and servers, though the server is the one that moves items. Worlds load as they are. Requires NeoForge 21.1.248 or newer; Allthemodium, All The Tweaks, Mekanism, Ars Nouveau and JEI are optional.

## 1.5.0

Wireless Automate 1.5: Mouse Tweaks-style controls in the Wireless Chest, and it now runs on NeoForge 21.1.248.

**Wireless Chest controls**

- **Shift + drag** across the item list: one stack of each item you pass over goes to your inventory. Storing already worked with Mouse Tweaks dragging over your inventory; taking now works too.
- **Mouse wheel**, in the same direction as Mouse Tweaks: over an item in the list, scroll down to take one and up to store one from your inventory. Over an item in your inventory, scroll down to store one and up to pull one from the chest. Over an empty cell or the scroll bar, the wheel still scrolls the list.
- **Shift + double-click** an item in the list while holding any item on the cursor, as in vanilla: fills your inventory with as much of that item as fits. The cursor stays as it is. A plain double-click does nothing extra, so you won't fill your inventory by accident.
- All of these work with or without Mouse Tweaks installed.

**Fixes**

- In creative mode, sending items from the Chest to a full inventory deleted from the Chest whatever didn't fit. Now only what actually enters your inventory leaves the Chest.
- In creative mode, an emptied bucket or container from the Tank screen could vanish when your inventory was full. It now drops on the ground.
- With absurd amounts (high tiers or unlimited storages), the throughput in the Network Tablet and the router screen could show as negative, like "-426P items/s". It now stays correct, capped at the highest value it can show.

**Other changes**

- The guide book's Wireless Chest page lists the new controls, in English and Portuguese.

**Compatibility note:** 1.5 changes the network protocol, so server and clients must all run 1.5. Worlds and blocks from 1.4 and earlier load as they are. Requires NeoForge 21.1.248 or newer (it was 21.1.251); Allthemodium, All The Tweaks, Mekanism, Ars Nouveau and JEI are optional.

## 1.4.0

Wireless Automate 1.4: Upgrade Cards jump straight to their tier, and JEI shows every tier.

**Direct tier upgrades** (thanks to taccio3 for the suggestion)

- An Upgrade Card now takes a router or storage from **any lower tier** straight to the card's tier. A Basic router + the Emerald Card is an Emerald router; an Elite router + the Ultimate Card is an Ultimate router.
- Works both ways as before: use the card on the placed block, or combine both in a crafting table. Configuration, name and contents are kept.
- Each card still takes the previous card in its recipe, so going straight to the tier you want now costs exactly one card of each step. Before, climbing from Basic to Ultimate meant crafting 4 Advanced Cards (7 with Allthemodium).
- Cards never lower a tier and don't apply to a block already at their tier.
- With Allthemodium, an Emerald router can take the Ultimate Card directly (the Ultimate Card already requires the Unobtainium Card).

**JEI**

- Routers and all storages now show up in JEI once per tier, like in the creative tab. Before, only the Basic version was listed.
- Every upgrade combination appears as a crafting-table recipe, from each lower tier to the card's tier.
- JEI shortcuts now work in the Wireless Chest, Tank and Chemical Tank lists: hover an item, fluid or chemical and press R (recipes), U (uses) or A (bookmark), or click with JEI's mouse buttons.

**Other changes**

- The Upgrade Card tooltip now shows the values of its own tier (throughput, range and storage capacity), since the starting tier can vary.
- The guide book's Upgrade Cards page explains direct upgrades, in English and Portuguese.

**Compatibility note:** no network protocol or world changes; worlds and blocks from 1.3 load as they are. Update server and clients together so tooltips and JEI match the server's rules. Requires NeoForge 21.1.251 or newer; Allthemodium, All The Tweaks, Mekanism, Ars Nouveau and JEI are optional.

## 1.3.0

Wireless Automate 1.3: turn your routers to face any way, and pick enchantments by name.

**Router rotation**

- Shift + right-click a router with **both hands empty** to turn it 90° around the face it sits on. Four clicks bring it back. Works on floors, ceilings and walls.
- Turning is only visual: every face of the machine keeps its mode, priority, redstone setting, filter and cards.
- A normal right-click still opens the screen. You need permission to build there to turn a router.
- Placed routers start as before, so routers in existing worlds look exactly the same.

**Enchantments by name** (Rule tab of the filter screen)

- The `<` `>` arrows are replaced by a text field: type part of the name or the id (`fort`, `minecraft:sharpness`, `apotheosis:`) and pick from a list of up to 5 suggestions. Accents and case don't matter, and enchantments from every mod are listed.
- Up/Down and Enter/Tab pick a suggestion, Esc closes the list, the mouse wheel scrolls it.
- The minimum level is now a number field from **1 to 255** for any enchantment, not just up to its normal maximum. Mouse wheel: ±1, with Shift ±10.
- Levels up to X still show as Roman numerals; higher ones as numbers (Efficiency ≥ 37).
- An unknown name or an invalid level turns the field red and disables Add.

**Other changes**

- Long texts on the Rule tab (the rule summary, "Matches N stacks…", condition labels) now shorten or wrap and show the full text in a tooltip.
- The guide book explains the new gesture and the enchantment field, in English and Portuguese.

**Compatibility note:** the router's block states changed, so server and clients must all run 1.3. Worlds from 1.2 and earlier load normally; existing routers keep their setup and orientation. Requires NeoForge 21.1.251 or newer; Allthemodium, All The Tweaks, Mekanism and Ars Nouveau are optional.

## 1.2.0

Wireless Automate 1.2: the Emerald tier, Allthemodium tiers for ATM10 and a rebalanced tier ladder.

**Emerald tier** (new, for everyone)

- A new tier between Elite and Ultimate, with its own Upgrade Card: 16,384 items/s · 1,024,000 mB/s · 512,000 FE/t · 51,200 Source/s, and it **works across dimensions**.
- Recipe: emerald blocks, eyes of ender and crying obsidian around an Elite Card.
- Routers and every storage block (Chest, Tank, Battery, Chemical Tank, Source Tank) upgrade to it.

**Allthemodium tiers** (optional, needs the Allthemodium mod, as in ATM10)

- Three tiers between Emerald and Ultimate: **Allthemodium** (131,072 items/s), **Vibranium** (1,048,576 items/s) and **Unobtainium** (8,388,608 items/s), each with its own card, colors and textures for the router and the storage blocks.
- Cards are crafted from the metal's own ingots, blocks and alloys (by `c:` tags), around the previous card.
- With Allthemodium, the Ultimate Card takes the Unobtainium Card; in ATM10 (with All The Tweaks) it also takes **ATM Star shards**, a dragon egg and Unobtainium-Allthemodium alloy blocks.
- Without Allthemodium nothing changes: these tiers stay out of the creative tab, JEI and recipes, and Emerald upgrades straight to Ultimate.

**Rebalanced tiers** (×8 per step)

| Tier | Items/s | Fluid and chemical (mB/s) | Energy (FE/t) | Source/s | Range |
| --- | --- | --- | --- | --- | --- |
| Basic | 32 | 2,000 | 1,000 | 100 | 64 blocks |
| Advanced | 256 | 16,000 | 8,000 | 800 | 512 blocks |
| Elite | 2,048 | 128,000 | 64,000 | 6,400 | Whole dimension |
| Emerald | 16,384 | 1,024,000 | 512,000 | 51,200 | Every dimension |
| Allthemodium¹ | 131,072 | 8,192,000 | 4,096,000 | 409,600 | Every dimension |
| Vibranium¹ | 1,048,576 | 65,536,000 | 32,768,000 | 3,276,800 | Every dimension |
| Unobtainium¹ | 8,388,608 | 524,288,000 | 262,144,000 | 26,214,400 | Every dimension |
| Ultimate | Unlimited | Unlimited | Unlimited | Unlimited | Every dimension |

¹ Only with Allthemodium.

- The vanilla-material tiers are now deliberately modest, so the Allthemodium tiers are a clear jump. Elite no longer reaches other dimensions; Emerald does.
- Storage capacity follows the same ladder: the Chest holds 32,768 items on Basic, 16,777,216 on Emerald and 8,589,934,592 on Unobtainium (unlimited on Ultimate).

**Other changes**

- Upgrade Cards show up to four marks; Allthemodium cards have contacts in their own metal.
- The Battery and Source Tank screens are a bit wider, so long names fit next to the tier.
- The guide book has the new tiers, cards and recipes, in English and Portuguese.

**Compatibility note:** 1.2 changes the network protocol. Server and clients must all run 1.2. Worlds from 1.1 and earlier load normally: routers and storage keep their tier. On the first start, throughput, range and capacity values still at the old defaults in the server config switch to the new ones; values you changed are kept. Requires NeoForge 21.1.251 or newer; Allthemodium, All The Tweaks, Mekanism and Ars Nouveau are optional.

## 1.1.0

Wireless Automate 1.1: Ars Nouveau Source, the Wireless Source Tank and resizable screens.

**Ars Nouveau Source** (optional, needs Ars Nouveau 5.2 or newer)

- New **Source** tab on the router. Throughput per face and per tier: 1,000 · 16,000 · 256,000 Source per second · unlimited on Ultimate. Like energy, it has no filter.
- The Linker, Configurator and Network Tablet handle Source like every other type.
- Source Jars, Relays and the Imbuement Chamber connect straight to a router.

**Wireless Source Tank** (new block, only with Ars Nouveau)

- A slim jar whose glass column shows how full it is. Capacity: 160,000 · 2,560,000 · 40,960,000 Source · unlimited on Ultimate.
- Sourcelinks within 5 blocks deposit into it, and Ars machines nearby draw from it as from a Source Jar.
- A router between two Source Tanks moves billions of Source in one operation.
- Same Upgrade Cards as the other storage blocks (in the world or in a crafting table). Breaking it keeps everything in the dropped item. Comparator output. Its own screen with the level and the rate per second.
- Recipe: a Wireless Tank with Source Gems and iron.

**Screens**

- The router, Network Tablet and Linker can be resized from their edges and corner.
- Router tabs adapt to the width (full name, name only on the active tab, or just the icon) and the network selector is larger.
- The Tablet's Statistics tab shows one card per resource type; click a card to filter the list by that type.
- The Linker has a chip per type, with an "All" button.
- No more text spilling out of the screens: long names are shortened, with the full text in the tooltip.

**Other changes**

- Colors: Chemicals are now lime green and Source is purple.
- The guide book has new pages for Source and the Wireless Source Tank, in English and Portuguese.

**Compatibility note:** 1.1 changes the network protocol. Server and clients must all run 1.1. Worlds from 1.0.x load normally. Requires NeoForge 21.1.251 or newer; Ars Nouveau 5.2 or newer is optional.

## 1.0.2

Bug fix: fluids pulled from Mekanism machines no longer vanish.

- A router extracting fluid from some Mekanism machines (for example the Rotary Condensentrator's output) could empty the machine's tank and deliver nothing: when the tank was drained completely, the fluid was lost on the way to other machines. It only worked into a Wireless Tank. Fluids now arrive intact in any destination. Items, energy and chemicals were not affected.

## 1.0.1

Compatibility fix: works with NeoForge 21.1.251 (All the Mods 10).

- 1.0.0 required NeoForge 21.1.256 or newer, so it would not load in packs like ATM10 that ship 21.1.251. 1.0.1 is built and tested on 21.1.251 and runs on it and any newer 21.1 version. Nothing else changed.

## 1.0.0

Wireless Automate 1.0: storage blocks of its own, a brand new filter screen and property rules.

**Wireless storage** (new blocks)

- **Wireless Chest:** stores items by type and amount, with no slots and unlimited types, up to the tier's capacity (262,144 · 16,777,216 · 1,073,741,824 items · unlimited on Ultimate). Searchable list (`@mod` works), sorting, click to take, Shift + click to send to your inventory. The window is resizable from its edges and corner and stays centered.
- **Wireless Tank** (fluids by type, 1,000 B to unlimited), **Wireless Battery** (16M FE to unlimited) and **Wireless Chemical Tank** (Mekanism chemicals, only with Mekanism installed).
- Same Upgrade Cards as the router: use the card on the block or combine them in a crafting table. The contents are kept.
- Breaking a storage block keeps everything in the dropped item, tier included, in survival, in creative and without the right tool. If a block ever goes missing, operators can give its contents back with `/wa storage list` (click the row).
- Input filter on every block (except the Battery): only what it accepts goes in, by any path. Comparator output.
- **Throughput:** a router between two storage blocks moves a whole item type in one operation (12 million items in one tick), and fluids and energy are no longer capped at ~2.1 billion per call (tested with 50 billion FE and 30 billion mB). Other mods' machines still see a normal inventory, tank or battery.
- Storage blocks are passive: all six faces are the same. Generators and pipes can push into them and pull from them; routers move things in and out.

**Filters, redesigned**

- New filter screen: entries in a list (icon, name, type, stock) with search, resizable like the Chest, with four tabs: **Entry**, **Tags**, **Rule** and **More**.
- **Tag inspector:** put an item in the inspector slot (click it with the item on the cursor, drag it from JEI, Ctrl + click it in your inventory or use **See tags** on an entry) and every tag of that item shows up, plus its mod, to check and add at once.
- **Tag search:** type part of a name to search every tag in the game, with how many items each one matches; hover a row to preview its items. `@` searches mods.
- **Property rules** (items): match items by what they are, not which item they are. Each condition is any / yes / no: Enchanted (books included), Damaged, Renamed, Has potion, Stackable, Has contents; plus an enchantment with a minimum level (Fortune ≥ III), remaining durability (≥ or < a percentage) and "Only in" a tag or mod (Enchanted + `#c:armors` = enchanted armor only). Your inventory lights up on what the rule matches before you add it. Rules work with stock limits and blacklists, and can be edited later.
- Filter Cards show rules in their tooltip.

**Other changes**

- Network Tablet: the Statistics tab now counts chemicals too (with Mekanism).
- Guide book: new pages for the Wireless Chest, Tank, Battery and Chemical Tank, and an updated Filters page, in English and Portuguese.

**Compatibility note:** 1.0 changes the network protocol. Server and clients must all run 1.0. Worlds from 0.1.x load normally.

## 0.1.1

New routers now start with no network, and the Configurator can paste a single tab.

- Placed routers no longer join your active network on every tab. Set up the first router (Linker or router screen), then copy it to the rest with the Configurator.
- Configurator: Shift + mouse wheel picks what to paste: All, Items, Fluids, Energy or Chemicals (with Mekanism). Pasting one type only changes that tab; the other tabs of the target keep their faces and network.
- Linker: the screen has a checkbox per tab (Items, Fluids, Energy and, with Mekanism, Chemicals); linking only changes the checked tabs. Shift + mouse wheel still cycles All, Items, Fluids, Energy and Chemicals.
- Linker can unlink: pick **None (unlink)** in its network list and the same clicks (one router or a whole area) take the checked tabs out of their network.
- Guide book updated (Getting started, Networks, Configurator, Linker and Chemicals pages).

**Performance** (same rules and throughput, lower cost):

- Networks with many idle sources: deliveries no longer wake every empty source, so a network with one busy source and hundreds of empty ones costs about a quarter of before.
- Destinations already at their stock limit are now almost free (they used to rescan the whole inventory every visit).
- Big single-slot stacks (drawers, bins, storage with stack upgrades) now move at the full tier rate instead of 64 items per visit.
- Fluids and chemicals now read tanks beyond the 16th.
- Redstone clocks next to routers that don't use redstone no longer rebuild the network; changing one tab only rebuilds that tab.
- Lighter Network Tablet, router screen and Linker screen on big servers.

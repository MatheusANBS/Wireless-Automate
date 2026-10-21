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

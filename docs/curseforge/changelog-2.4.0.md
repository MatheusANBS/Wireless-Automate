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

Wireless Automate 2.17.0 adds the **Pattern Dock**, a block that takes processing pattern orders from AE2 and Refined Storage 2 and hands each one to a machine you linked wirelessly, with the result going straight back to the network.

**Pattern Dock** (needs AE2 or Refined Storage 2, both optional)

- **One side, one kind of machine.** Place a Pattern Provider (AE2) or an Autocrafter (Refined Storage 2) against a side of the Dock, link the machines of one block type to that same side with the Linker, and put their processing patterns in the provider. Nothing to set up on the screen.
- **Works with the AE2 addons of ATM10:** the Extended Pattern Provider (ExtendedAE), the Advanced Pattern Provider and the Quantum Computer (Advanced AE).
- **Each order goes whole to one free machine.** Orders rotate between the machines of the side; a recipe is never split between machines. The result goes **straight back to the provider's network** (ME or RS2), not through the provider's return slots.
- **Items, fluids and chemicals in the same order.** Chemicals need Mekanism plus Applied Mekanistics (AE2) or Refined Storage Mekanism Integration (RS2). An order with a resource the Dock does not know is refused.
- **Reach and machine limit per tier** (default config; the limit counts all six sides): Basic 6 blocks and 8 machines, Advanced 12 / 16, Elite 16 / 32, Emerald 24 / 48, Allthemodium¹ 32 / 64, Vibranium¹ 48 / 96, Unobtainium¹ 64 / 128, Ultimate 96 / 256. Each tier uses the Range Upgrade values of the tier above (`tiers.<tier>.machineRadius` and `maxMachines`); Ultimate has its own keys (`patternDock.ultimateRadius`, `patternDock.ultimateMaxMachines`). Same dimension only.
- **Linking per side:** with the Linker, click a face of the Dock, then click a machine, or Ctrl + click to link every machine of the same block in reach (nearest first, up to the limit). The first machine sets the side's profile through the Configurator's Auto-Setup (which faces take and which give, per type), and the Configurator can redo it later. A machine belongs to one Dock side only; it can still be linked to a router, which brings the energy (the Dock uses none).
- **The Dock's reach cube** shows for 5 seconds when you place it, and whenever you hold the Linker with the Dock chosen, with each side's machines lit in the side's color.
- **Its own screen:** six rows, one per side, with the machine, how many are linked and working, and the state in one line ("No provider on this side", "Remove the provider lock", "Crafting pattern refused", machines out of reach or unloaded...).
- **Animated art:** a circuit in the tier's color, a gyroscope with the crystal at its center, and a work loop while the Dock delivers.
- **Recipe:** Porcelain Casing, Signal Alloy Ingot and a router, plus an Engineering Processor and a Pattern Provider with AE2; with Refined Storage 2 only, an Advanced Processor and an Autocrafter instead. Without either mod there is no recipe. It makes the Basic Dock.
- **The Upgrade Card raises the tier** of a placed Dock, and in the crafting table (Dock + card), keeping whatever the Dock carries. JEI shows the pairs.
- **A leftover only holds its own machine.** When an order does not fit whole in a machine, the rest waits for that machine to consume it, and the other machines of the side keep taking orders. Leftovers are stored as one line per item, fluid and chemical, so even a 524,288-item pattern saves in a few hundred bytes. Breaking the Dock keeps every pending leftover in the item, and placing it again puts each one back on its side.
- **A broken machine leaves its side on its own** and frees its slot; an unloaded chunk is not treated as broken, and the Dock never loads chunks.
- **Heavy use, measured with the real mods:** one Infinity Smelting Factory (Mekanism Extras, Creative Upgrade, 256× with a Time Wand) fed by an AE2 crafting CPU runs at 86,139 items/s, 99% of what the factory can smelt; five of them with the Quantum Computer reach **409,600 items/s** (94%), 1,000,000 ingots in 2.5 s. The Dock's work runs inside the engine's 1 ms tick budget; only the burst at the start of a very large order goes over, and that is paid back on the next tick.
- Results come back at the tier's throughput per side, the router table (32 items/s on Basic, ×8 per tier, unlimited on Ultimate).
- Covered in the guide book, with a JEI information page.

¹ Only with Allthemodium.

**Range Upgrade**

- **A broken or replaced remote machine unlinks itself** and frees its slot in the tier's limit; the cards of a group that empties go back into the router. An unloaded chunk is not treated as broken.

**Other changes**

- **Chemicals work with Mekanism 10.7.0 to 10.7.10 too:** the mod no longer uses a constant that only arrived in 10.7.11.
- Spec, guide, README and config comments updated for the Dock.

**Compatibility note:** the network protocol changed (**49 → 51**), so the server and every client must run 2.17.0. Worlds from 2.16.x load as they are. NeoForge 21.1.248 or newer is still required. Every other mod is optional: Mekanism 10.7+, Ars Nouveau 5.2+, FTB Teams 2101.x, Allthemodium 2+ (with All The Tweaks), JEI 19+, and for the Pattern Dock AE2 19 (1.21.1 builds) or Refined Storage 2.0.0-milestone.4.14+, with Applied Mekanistics 1.6.0+ or Refined Storage Mekanism Integration 0.1.0+ for chemicals.

# Wireless Automate

**Move items, fluids, energy, Mekanism chemicals and Ars Nouveau Source between machines, without pipes.**
Attach a router to a machine, pick what each face does, and every router on the same network trades with the others, near or far, in any dimension. Built for big modpacks: one central manager with a small time budget per tick, so hundreds of machines don't turn into lag.

---

## Routers

![A router, configured in seconds](https://raw.githubusercontent.com/MatheusANBS/Wireless-Automate/main/docs/curseforge/roteador.webp)

- **All six faces of the machine** from one screen, per resource type: Extract, Insert, Storage or None, with priority, round-robin and redstone.
- **A network per face:** one furnace takes ore from your mining line on top and sends ingots to storage from the bottom.
- **Range Upgrade:** one router feeds every machine you link around it, up to 128 machines on Ultimate.

## Filters

![Items, tags and rules](https://raw.githubusercontent.com/MatheusANBS/Wireless-Automate/main/docs/curseforge/filtro.webp)

Exact items, tags and whole mods, whitelist or blacklist, with stock limits. A tag inspector, and **rules** by what an item is: any enchanted item, tools under 25% durability, Fortune ≥ III.

## Wireless Chest

![Crafting and compacting inside the Chest](https://raw.githubusercontent.com/MatheusANBS/Wireless-Automate/main/docs/curseforge/bau.webp)

Unlimited item types and billions per operation (12 million items in one tick between two of them). Two upgrade slots: a **3×3 crafting grid** that refills from the Chest and a **Compacting Upgrade** that packs nuggets → ingots → blocks as they come in. Also a Tank, a Battery and, with the optional mods, a Chemical Tank and a Source Tank.

## Pattern Dock (AE2 and Refined Storage 2)

![The Dock handing orders to its machines](https://raw.githubusercontent.com/MatheusANBS/Wireless-Automate/main/docs/curseforge/doca.webp)

Put an AE2 Pattern Provider or an RS2 Autocrafter against a side, link the machines with the Linker, and the Dock hands **each order whole to a free machine**, returning the result straight to the network. Items, fluids and chemicals; up to six production lines per Dock.

## Signal Screen

![A wall of Signal Screens](https://raw.githubusercontent.com/MatheusANBS/Wireless-Automate/main/docs/curseforge/tela.webp)

A wall display from 1×1 to 32×32 blocks with **live numbers** from your networks and storages, a full editor in the game, a **pixel art editor with animation**, and your own PNG, JPG and GIF files.

## Signal Resonator

![The Armillary assembling](https://raw.githubusercontent.com/MatheusANBS/Wireless-Automate/main/docs/curseforge/armilar-montagem.webp)
![Out of tune, the orbits wide open](https://raw.githubusercontent.com/MatheusANBS/Wireless-Automate/main/docs/curseforge/armilar-fora.webp)
![Tuning in](https://raw.githubusercontent.com/MatheusANBS/Wireless-Automate/main/docs/curseforge/armilar-sintonia.webp)
![A conjunction sending energy to the port](https://raw.githubusercontent.com/MatheusANBS/Wireless-Automate/main/docs/curseforge/armilar-conjuncao.webp)

A generator you tune like a radio. Build the **Armillary**, put Signal Crystals in the Core and turn the dial to the drifting station: in tune, the orrery lines up and the energy flows every tick, from 432 FE/t on Basic to **4.29 billion FE/t** on Ultimate. The Energy Port pushes into whatever touches it. Crystals wear out, crack and get mended in the **Crystal Repairer**.

---

## Tiers

| Tier | Items/s | Fluids (mB/s) | Energy (FE/t) | Range |
| --- | --- | --- | --- | --- |
| Basic | 32 | 8,000 | 454 | 64 blocks |
| Advanced | 256 | 64,000 | 29,100 | 512 blocks |
| Elite | 2,048 | 512,000 | 63,000 | Whole dimension |
| Emerald | 16,384 | 4,096,000 | 1,350,000 | Every dimension |
| Ultimate | Unlimited | Unlimited | Unlimited | Every dimension |

With **Allthemodium** (ATM10), three more tiers go between Emerald and Ultimate. Upgrade Cards raise a placed router and keep its setup. Every value is in the config.

## Also inside

**Network Tablet** (every router from anywhere, with a map and live flows) · **Linker** and **Configurator** for big builds · **Chunk Loading Upgrade** · a **guide book** with 3D scenes, in English and Portuguese · light and dark theme on every screen.

## Compatibility

Works with any block that exposes NeoForge's item, fluid and energy handlers. Optional: **Mekanism** (chemicals), **Ars Nouveau** (Source), **Allthemodium** (tiers), **AE2** and **Refined Storage 2** (Pattern Dock), **FTB Teams** (shared networks), **JEI**.

**Requires** Minecraft 1.21.1 and NeoForge 21.1.248 or newer (works with All the Mods 10).

[Source code on GitHub](https://github.com/MatheusANBS/Wireless-Automate) (All Rights Reserved; issues welcome).

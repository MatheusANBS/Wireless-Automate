# Wireless Automate

*[Leia em português](README.md)*

Wireless transport of items, fluids, energy, chemicals and Source for **NeoForge 1.21.1** (Java 21), built for ATM10. The goal is to be the fastest transport in the pack and the lightest on TPS: one central manager, no per-block ticking, and a time cap per tick.

**Status:** version 1.7 (1.7.1: the editor runs one reload at a time; recipe editor for operators: `/wa recipes` changes, disables and restores the mod's recipes in game). Before that, 1.6 (Configurator wheel on Left Alt and Area mode on any machine) and 1.5 (1.5.0: Mouse Tweaks-style controls in the Chest, such as Shift + drag, mouse wheel and Shift + double-click, and NeoForge 21.1.248; 1.5.1: the Chest fills other mods' big-stack slots at once; 1.5.2: new "Porcelain and Signal" visual identity, with animated items, 3D blocks, light screens and tight hitboxes). Download it on [CurseForge](https://www.curseforge.com/minecraft/mc-mods/wireless-automate) or from the [latest GitHub release](https://github.com/MatheusANBS/Wireless-Automate/releases/latest), both with the changelog. Requires NeoForge 21.1.248 or newer (runs on ATM10).

## Features

- **Wireless Router:** attached to any face of a machine, it reaches every face of that machine; Shift + right-click with empty hands rotates the router 90° (1.3) without changing the face configuration.
- **Networks per tab:** each router tab (Items, Fluids, Energy, Chemicals and Source) joins its own network; everything of the same type on the same network trades with each other.
- **Modes per face and per type:** Extract, Insert, Storage or None, with priority, round-robin on ties and redstone control.
- **Unlimited filters:** exact item or fluid, tag and mod, whitelist or blacklist, stock limit, and Filter Cards (two slots per face and per type, copyable in the crafting table). The screen is resizable: put an item in the inspector to see and check all its tags, or search every tag in the game with an item preview.
- **Property rules** (1.0): match any item that is enchanted, damaged, renamed, a potion, unstackable or has contents, an enchantment with a minimum level (1.3: typed by name, with suggestions, levels 1 to 255) or durability below X%, limited to a tag or mod (only enchanted armor, tools for repair).
- **Five tiers** (1.2; Basic, Advanced, Elite, Emerald and Ultimate), and **eight with Allthemodium** (Allthemodium, Vibranium and Unobtainium between Emerald and Ultimate), raised with Upgrade Cards (there is no Basic card, the router starts as Basic; since 1.4, each card raises from any lower tier straight to its own), by clicking the placed router or combining both in the crafting table. On ATM10, the Ultimate Card needs ATM Star fragments.
- **Linker:** picks the active network and puts routers on it, one at a time or by area, on the checked tabs (Items, Fluids, Energy and, with Mekanism and Ars Nouveau, Chemicals and Source); set to "None (unlink)", it removes those tabs from their network.
- **Configurator:** copies one router's configuration and pastes it onto another, or onto every router in an area (only those attached to the same kind of machine, or on any machine); all tabs or a single type, Source included. Holding Left Alt opens a wheel to pick the mode and the pasted type (1.6); Shift + click in the air and Shift + mouse wheel work too.
- **Chunk Loading Upgrade:** keeps the router's chunk and the machine's chunk loaded, with a per-player limit in the config.
- **Recipe editor** (1.7): `/wa recipes`, for operators only, opens a screen to change, disable and restore the mod's crafting recipes (items from the inventory or JEI, a tag instead of an item, the result count). It writes a datapack in `config/wirelessautomate/recipes/` that works in every world of the instance and survives updates.
- **Network Tablet:** list, map, statistics, networks and groups from a distance, and opens a router's screen remotely.
- **Wireless Chest** (1.0): stores items by type and amount, with no slots and unlimited types, up to the tier's capacity (from 32,768 items on Basic to 16,777,216 on Emerald and 8,589,934,592 on Unobtainium, unlimited on Ultimate), raised with the same Upgrade Cards. Between two Chests, the router moves a whole type in a single operation. It has a list screen with search (`@mod`) and sorting, Mouse Tweaks and vanilla gestures (Shift + drag, mouse wheel and Shift + double-click), an input filter (the stock limit becomes "keep up to N") and a comparator signal. When broken, the item keeps its contents and filter. To other mods, it is a regular inventory; as a source, it fills big-stack slots (Sophisticated Storage stack upgrades, drawers) at once (1.5.1: 2.1 billion into an Omega-upgraded barrel in one tick).
- **Wireless Tank, Battery and Chemical Tank** (1.0): the same design as the Chest for fluids (several per tank, up to 131 million mB on Emerald), energy (up to 512 million FE on Emerald) and Mekanism chemicals (only with Mekanism). Unlimited on Ultimate. The Tank swaps buckets directly on the block and through the screen (containers on the cursor); the Battery shows its charge and the change per tick. To other mods and to the router, they are a regular tank, battery and chemical tank that already move billions per call.
- **Wireless Source Tank** (1.1, only with Ars Nouveau): a slim jar whose glass column shows the level, from 10,000 to 5,120,000 Source on the vanilla ladder (unlimited on Ultimate). Sourcelinks within 5 blocks deposit into it and nearby Ars machines draw from it like a Source Jar; between two tanks the router moves everything in one operation. Same Upgrade Cards, keeps its contents when broken, comparator and its own screen.
- **Resizable screens** (1.1): router, Tablet and Linker resize by the edge and the corner; the router tabs adapt to the width, and text that doesn't fit is abbreviated, with the full text in a tooltip.
- **Guide book (GuideME):** in the creative tab and given to each player on first login.

| Tier | Items/s | Fluid and chemical (mB/s) | Energy (FE/t) | Source/s | Range |
| --- | --- | --- | --- | --- | --- |
| Basic | 32 | 2,000 | 1,000 | 100 | 64 blocks |
| Advanced | 256 | 16,000 | 8,000 | 800 | 512 blocks |
| Elite | 2,048 | 128,000 | 64,000 | 6,400 | Whole dimension |
| Emerald | 16,384 | 1,024,000 | 512,000 | 51,200 | Across dimensions |
| Allthemodium¹ | 131,072 | 8,192,000 | 4,096,000 | 409,600 | Across dimensions |
| Vibranium¹ | 1,048,576 | 65,536,000 | 32,768,000 | 3,276,800 | Across dimensions |
| Unobtainium¹ | 8,388,608 | 524,288,000 | 262,144,000 | 26,214,400 | Across dimensions |
| Ultimate | Unlimited | Unlimited | Unlimited | Unlimited | Across dimensions |

¹ Only with the Allthemodium mod; without it, Emerald goes straight to Ultimate.

Throughput applies per face and per type (`sourcePerSecond` is the Source one). The values live in the server config (`<world>/serverconfig/wirelessautomate-server.toml`; in a modpack, the default goes in `defaultconfigs/`), as do the range, the storage capacity per tier (`storage.chestCapacity`, `tankCapacity`, `batteryCapacity`, `chemicalTankCapacity` and `sourceTankCapacity`), chunk loading and the Linker's area size. Capacity counts the total stored, with no limit on the number of different types.

## Optional integrations

| Mod | What it adds |
| --- | --- |
| Mekanism | Chemicals: a Chemicals tab on the router, with filters by chemical or mod, and the Wireless Chemical Tank |
| Ars Nouveau (5.2 or newer) | Source: a Source tab on the router (Source Jars, Relays and the Imbuement Chamber connect directly) and the Wireless Source Tank |
| Allthemodium (All The Tweaks optional) | The Allthemodium, Vibranium and Unobtainium tiers, and on ATM10 the Ultimate Card recipe with ATM Star fragments |
| JEI | Drag and Shift + click into filters (and the tag inspector), and the upgrade recipe in the crafting table (router and storages) |
| GuideME | The guide book, in English and Portuguese |

Without one of them, the matching part doesn't load and everything else works normally.

## Development

```bash
./scripts/setup.sh              # Linux, macOS or WSL
.\scripts\setup.ps1             # Windows (PowerShell)
```

The script installs whatever is missing (JDK 21, git, curl, unzip) and runs the first build, which downloads and decompiles Minecraft (about 4 minutes). Options: `--gametest` (`-GameTest`) also runs the GameTests; `--no-build` (`-NoBuild`) only installs the tools.

| Command | What it does |
| --- | --- |
| `./gradlew build` | Compiles, runs the JUnit tests and builds the jar in `build/libs/` |
| `./gradlew test` | JUnit tests only (logic without Minecraft) |
| `./gradlew runGameTestServer` | GameTests on a headless server (without Mekanism); fails if any test fails |
| `./gradlew runGameTestServerChemicals` | Chemical GameTests, on a server with Mekanism |
| `./gradlew runGameTestServerSource` | Source GameTests, on a server with Ars Nouveau (plus GeckoLib and Curios) |
| `./gradlew runGameTestServerAllthemodium` | ATM tier GameTests, with Allthemodium, All The Tweaks and GeckoLib |
| `./gradlew runClient` | Dev client with JEI, Sophisticated Storage, Observable, Mekanism, Ars Nouveau (with GeckoLib and Curios), Allthemodium, All The Tweaks and GuideME, for manual testing |
| `./gradlew runData` | Data generators, output in `src/generated/resources/` |
| `./scripts/e2e.sh` | End-to-end test in a real world (needs Xvfb) |
| `./scripts/bench.sh <scenarios>` | Benchmark on a dedicated server with Sophisticated Storage (see `docs/benchmark.md`) |

CI (`.github/workflows/build.yml`) runs `build`, `runGameTestServer`, `runGameTestServerChemicals`, `runGameTestServerSource` and `runGameTestServerAllthemodium` on every push and pull request.

## Layout

The source code map is in [`CLAUDE.md`](CLAUDE.md) ("Mapa do código"). The project docs are in Portuguese.

- `docs/especificacao.md`: the mod's specification (the source of truth for the design).
- `docs/progresso.md`: what's done, what's left and the next step.
- `docs/benchmark.md`: how to run the benchmark, and the results.
- `docs/pacote-de-design.md`: sprites and the router model.
- `docs/curseforge/`: description, logo, banner and images for the CurseForge page.
- `scripts/`: `setup.sh`/`setup.ps1`, `e2e.sh`, `bench.sh`, `textures/` (generates the textures), `guide/` (generates the guide pages) and `curseforge/` (generates the CurseForge images).

## License

All Rights Reserved: the code and assets are here for reference only, with no permission to copy, modify or redistribute them (see [`LICENSE`](LICENSE)). For suggestions and bugs, open an issue.

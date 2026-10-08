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

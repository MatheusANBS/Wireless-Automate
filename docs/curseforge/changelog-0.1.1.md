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

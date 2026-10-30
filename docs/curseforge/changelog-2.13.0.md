Wireless Automate 2.13.0 gives the **Wireless Chest** two upgrade slots: a **Crafting Upgrade** with a 3×3 grid that refills itself from the Chest, and a **Compacting Upgrade** that packs and unpacks item chains (nugget, ingot, block, or AllTheCompressed's 1x to 9x) on its own as they come in.

**Chest upgrades**

- The Wireless Chest has **two upgrade slots**, to the left of its list. Each upgrade fits once and opens its own panel to the right of the list. Click the open panel's tab to hide it; it stays hidden, even in other Chests, until you click a tab.
- The upgrades and the crafting grid items **travel with the Chest** when you break it, even with an empty Chest, and come back when you place it. If the block disappears without becoming an item (a command, another mod), they drop on the ground.

**Crafting Upgrade** (crafting table + Signal Crystal, shapeless)

- A **3×3 crafting grid** inside the Chest. Take the result and the grid **refills itself** from the Chest's stock.
- **Shift + click** the result repeats the recipe up to a stack and sends it to your inventory.
- **Return** puts the whole grid back into the Chest.
- In **JEI**, the **+** button fills the grid from the Chest first, then from your inventory.
- With the Compacting Upgrade in the same Chest, a missing ingredient is taken a form down on demand (an ingot from a block).

**Compacting Upgrade** (piston + Signal Crystal + iron block, shapeless)

- Chains come from crafting recipes that **go both ways**: nugget, ingot, block; cobblestone up to AllTheCompressed's 9x; any mod's chain works, with no list to maintain. A chain only counts if the crafting table can also undo it.
- **Automatic on insertion**, by any path (router, hopper, another mod, the screen).
- A **global mode**: **Off** (the default, so placing the upgrade changes nothing), **Compress** (the highest form) or **Decompress** (the base).
- A **target per chain**: arrows or the mouse wheel on its row, or click the icon to pick the form from a list. **Global** takes the chain back to the global mode. The target works both ways: what is below it is compressed up to it, what is above it is decompressed down to it.
- Decompressing converts **only what fits** in the Chest; the rest stays in the high form, and nothing is lost. A leftover that doesn't make a full factor stays in the lower form.
- Enchanted or named items are left alone.
- The per-type **limit counts the whole chain**: 11 blocks and 1 ingot are worth 100 ingots.
- The router only sees the stored form: to pull ingots, leave the chain's target on ingot.

**Same material from different mods**

- Raw silver from Modern Industrialization and from Occultism (or any other pair) is **one row per material**, with the mod name under it. Click it to pick which mod's variant the Chest keeps; the others are swapped 1:1.
- A chain with your own rule (a target or a chosen mod) stays in the list, dimmed, even when the Chest has none of it: the rule still applies.

**Other changes**

- Short amounts in the Chest cells now **truncate instead of rounding**: 2,664 shows 2.6k, never more than there is.
- A refused network change on a face now tells you why: only those who edit a network (the owner, a guest, an added team or an operator) can take faces out of it.
- The in-game guide covers both upgrades.

**Compatibility note:** the network protocol changed to **40**, so a server and its clients need the same version. Worlds from 2.12 load as they are. NeoForge 21.1.248 or newer is still required. AllTheCompressed, Occultism and Modern Industrialization are not required: the chains come from whatever crafting recipes are loaded. All optional integrations remain optional.

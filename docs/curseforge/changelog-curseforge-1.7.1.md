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

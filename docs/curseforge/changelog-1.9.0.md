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

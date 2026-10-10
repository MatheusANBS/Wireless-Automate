Wireless Automate 1.5.1: the Wireless Chest now feeds big-stack inventories (Sophisticated Storage with stack upgrades, drawers) a whole slot at a time.

**Faster deliveries from the Wireless Chest**

- From a Wireless Chest to another mod's inventory, the router used to hand over at most one stack (64 items) per insert, even when the slot could take far more. Now each slot gets everything it accepts in one go when the slot holds more than 99, as with Sophisticated Storage stack upgrades or drawers.
- Tested: 2.1 billion cobblestone from a Wireless Chest into a Sophisticated Storage barrel with the Omega Stack Upgrade, on an Ultimate router, now arrive **in one tick (about 1 ms of server time)**. Before, that took about an hour and 45 minutes of game time.
- Regular inventories (vanilla chests, most machines) still get at most one stack per insert, so a machine that doesn't check what it receives never gets an oversized stack. They fill faster too: one delivery now fills every free slot instead of one stack at a time.
- Other mods' storages as the source don't change: they still hand out one stack per extraction.

**Compatibility note:** 1.5.1 doesn't change the network protocol, so it works with 1.5.0 clients and servers, though the server is the one that moves items. Worlds load as they are. Requires NeoForge 21.1.248 or newer; Allthemodium, All The Tweaks, Mekanism, Ars Nouveau and JEI are optional.

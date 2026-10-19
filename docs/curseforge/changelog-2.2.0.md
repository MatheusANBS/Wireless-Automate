Wireless Automate 2.2: a Flows tab in the Network Tablet that shows what comes in and goes out of every network, fixes for the Range Upgrade, and a full efficiency pass that makes the whole mod lighter on the server, the network and the client.

**Flows tab** (Network Tablet, replaces Statistics)

- **What comes in and goes out**, per type or per network (**By type** | **By network**): Extract faces are what comes in, Insert faces what goes out, in each type's unit (items/s, mB/s or B/s, FE/t, Source/s). Storage faces show their balance, filling or emptying.
- **What needs attention**, in short words on each line: sources with nothing to give, sources waiting for a destination, full destinations, faces stopped by redstone and faces without a network. The tooltip has the full counts.
- **Click a line** to open the List filtered by that type (and network).
- The "Mod time" bar stays on top. Paused networks show as paused.
- **Guide book:** the Network Tablet page explains the Flows tab, in English and Portuguese.

**Range Upgrade**

- **The Linker links machines without a network.** A new machine group used to start with every face, on every tab, in your active network. Now it starts with no network, and a face joins your active network when you pick a mode on it.
- **Clear networks:** while editing a machine group or a machine with an exception, a new button removes the network from every face set to None, on every tab. Faces that move something stay as they are. The notice has **Undo**.
- **Fix:** what a linked destination refused after accepting it went back into the router's attached machine instead of the linked one. It now goes back to the right machine.
- **Fix:** full linked destinations now count in the Tablet's "With problems" filter and in the full-destination notice.

**Efficiency** (measured with the mod's own benchmark, before and after)

- **Idle networks cost a third of what they did:** sleeping sources are skipped without being touched (−68% with 500 idle routers).
- **Redstone-controlled faces** no longer rebuild the routes on every pulse; the signal just locks the face (a clock next to 100 routers: −42%).
- **The Network Tablet sends about 93% less data:** when only rates and states change, only they are sent (from 9–30 KB/s down to 0.4–1.7 KB/s per player with 1,000 routers). With several players looking, the measurement is done once for all of them.
- **The mod's storages:** one neighbour update per tick instead of one per transfer (250 sources on one Chest: up to −22%), stock limits in constant time (a Chest with 2,000 item types and a stock limit: −70%), and saving no longer re-encodes every item type.
- **Memory:** linked machines only keep what their modes use (about 300 bytes less per linked machine), filters with the same tags share them, and the Tablet's node index drops routers that vanished without notice.
- **Signal Resonator:** about a third cheaper, both generating and idle.
- **Screens:** the Chest's `#` search no longer rebuilds every tooltip on each key (with 2,500 item types: from 14–26 ms to under 3 ms per key), and the node panel and the Tablet List lay out once per frame. Opening a Filter goes through the item registry only once per game.

**Other changes**

- The contents of a broken storage are only kept on the server when its item actually dropped. A storage removed with `/setblock` or `/fill` loses its contents, like a vanilla chest. New server option `storage.stashExpiryDays` (default 0: kept forever) to let old stashed contents expire.

**Compatibility note:** the network protocol changed (22), so the server and every client need 2.2.0. Worlds from 2.1 load as they are. Still requires NeoForge 21.1.248 or newer. Mekanism, Ars Nouveau, Allthemodium, JEI and GuideME stay optional.

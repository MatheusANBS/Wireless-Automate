Wireless Automate 2.7.0 lets you share a private network with friends and with FTB Teams teams, makes the Tablet Map draggable and complete, and reworks the Linker and the Configurator into two simple tools each.

**Private networks: guests and FTB Teams** (the teams part needs FTB Teams, optional)

- **Invite players** to a private network from the Tablet's **Networks** tab. A guest can use the network: pick it on a face, move nodes onto it, see it and its nodes in the Tablet, and take a face off it. Only the owner and operators manage it (rename, color, public or private, invite, remove).
- **Add an FTB Teams party team** and everyone in it gets the same access, live: whoever joins or leaves the team gains or loses access on their own. A team that was disbanded shows as "removed" and grants nothing.
- **Suggestions while you type**, for players (online first, then everyone who already joined the server) and for teams, up to 8 per query. Arrow keys and Enter pick, Tab completes, and the ✕ asks for two clicks. Typing a full name and pressing Enter works too.
- **Commands:** `/wa network invite <player> <network>`, `/wa network uninvite <player> <network>` and `/wa network team add|remove "<team>" <network>`, for the owner and operators.
- **Config:** `networks.maxMembersPerNetwork` (32) and `networks.maxTeamsPerNetwork` (8), 0 for no limit, operators exempt. `networks.maxNetworksPerPlayer` and `maxGroupsPerPlayer` (64 each) cap how many networks and groups a player can own.
- Taking a face off a network from the router screen now needs the same rights as Move and paste: you must own, be a guest of, or be an operator of that network. A public network you don't own can no longer be emptied of other people's faces.
- `/wa network use` now respects who can use the network.

**Tablet Map**

- **Drag to move the map**, like a canvas. A click without dragging still picks a node, and the **Center** button brings the map back to you.
- **The Map shows every router you can see** in your dimension, no matter what the List has: a search, quick filters, the Machines view and the 256-row cap of the List no longer hide routers from the Map. Up to 1,024 routers, the nearest to you, are sent, and only while the Map tab is open. A note on the Map says when some farther ones are left out.

**Linker (reworked)**

- **Link mode:** click a router to choose it, click a machine to link it to that router (or unlink it if it is already linked), and Shift + click two blocks to mark an area; the button on the screen links the area's machines, nearest first, up to the Range Upgrade's radius and limit. The **Unlink** switch on the screen turns clicks and the area button into unlinking.
- **Open mode (new):** click a machine to open the screen of the router that reaches it, already editing that machine. If several routers reach it, a short list (up to 16) lets you pick one. Range, claims and protection mods are checked on the router.
- The Linker no longer touches networks, tabs or faces: choose the network of each face on the router screen or in the Tablet. Old Linkers keep loading.

**Configurator (reworked)**

- The Configurator is now just the **Brush**: Shift + click a router copies it, click pastes onto another, Shift + click on a block without a router clears it. The wheel (Left Alt) has a single ring: the type that gets pasted (All, Items, Fluids, Energy and, with the optional mods, Chemicals and Source).
- Area paste and the paste modes are gone. To configure many routers, paste a profile on the marked ones in the Tablet. Old Configurators keep loading.
- The network of each face is only pasted if you can use that network, and a face only leaves a network you can edit.

**Fixes**

- **Resonator Core:** a catalyst the tier does not accept could duplicate the stored one when inserted; it no longer does. The Ports are told when the catalyst count drops, tuning crystals take the worst integrity of the ingredients, and recharging without a Dull crystal clears the progress.
- **Energy and Source transfer** now extracts per destination, so a destination that accepts less no longer loses what was taken.
- A corrupted entry in the saved networks no longer breaks loading the rest, a corrupted list mode in a filter no longer breaks the filter, and the balance migration of the config only touches files older than its step.
- The Tablet's bulk Move is rate limited in every path, and the Battery and Source Tank screens no longer leak a text-frame counter.
- The Tablet and the Linker's Open mode are checked against claims, reach and ownership on the server.

**Compatibility note:** the network protocol is now **32** (was 29), so server and clients need the same version. Worlds and items from 2.6.0 load normally; the Linker's and Configurator's old saved data is kept only so those items still load. Old private networks keep working as before (no guests, no teams). **FTB Teams** for 1.21.1 (tested with 2101.1.11) is a new optional integration (it needs FTB Library and Architectury, which it asks for itself); nothing changes without it. NeoForge 21.1.248 or newer is still required, and all other optional integrations remain optional.

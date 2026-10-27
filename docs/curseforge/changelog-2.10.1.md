Wireless Automate 2.10.1 fixes item duplication and vanishing items when the Wireless Chest is used as storage through an AE2 storage bus.

**AE2 storage bus fix**

- When a request emptied one item type in the Wireless Chest, the next type slid into its slot and the storage bus kept pulling from that same slot: the requested item was **duplicated** and the other item was **deleted**. It showed up with the pick block of AE2WTLib, and could also happen with export buses, interfaces and autocrafting whenever a request was larger than what the chest held of that item.
- An emptied type now keeps its slot until the end of the tick, so slots never shift in the middle of a request.
- The same fix covers the Chemical Tank (Mekanism) and the Fluid Tank.
- Items already lost to this bug are not restored.

**Compatibility note:** the network protocol is unchanged (**34**) and no world or configuration migration is needed. NeoForge 21.1.248 or newer is still required, and all optional integrations remain optional.

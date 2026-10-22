Wireless Automate 2.8.2 makes the Occultism miners bring up Signal Crystals directly instead of Signal Quartz Ore.

**Occultism miners** (optional, needs Occultism; nothing changes without it)

- The **Djinni** miner (weight 300) and the **Ancient Eldritch** miner (weight 90) now give **2 Signal Crystals** per result, the average of what the ore drops, instead of the ore block that 2.8.1 gave.
- The Fortune of the item in the miner works as usual: Occultism adds extra rolls for it, and each roll can now be a Signal Crystal.
- Both are plain data recipes (`occultism:miner`) that read the new item tag `c:gems/signal_quartz`, which holds the Signal Crystal (the full one). A pack can change the weights or remove them with a datapack.

**Compatibility note:** the network protocol is unchanged (**32**) and no world or configuration migration is needed. NeoForge 21.1.248 or newer is still required, and all optional integrations remain optional.

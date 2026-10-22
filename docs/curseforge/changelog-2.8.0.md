Wireless Automate 2.8.0 ties the energy of routers and Batteries to the Signal Resonator of the same tier, so a router carries what its tier's Resonator generates and a Battery lasts an hour of it.

**Energy rebalance**

- **Router energy** per tier is now the same tier's Resonator full output plus 5%, rounded up to three significant digits. A router moves a Resonator's whole output without much to spare.
- **Battery capacity** per tier is now **one hour** (72,000 ticks) of the same tier's Resonator at full output. They fill and drain on a human timescale instead of in seconds.
- **Ultimate** is still unlimited, for both the router and the Battery.
- The full output of a Resonator is its chambers times the FE per crystal of its tier times 2 (the best crystal, at 100% efficiency).

| Tier | Resonator (FE/t) | Router (FE/t), was → now | Battery (FE), was → now |
| --- | --- | --- | --- |
| Basic | 432 | 1,000 → 454 | 1M → 31.1M |
| Advanced | 27,648 | 32,768 → 29,100 | 8M → 1.99G |
| Elite | 60,000 | 64,000 → 63,000 | 64M → 4.32G |
| Emerald | 1,280,000 | 2,097,152 → 1,350,000 | 512M → 92.16G |
| Allthemodium | 64,000,000 | 67,108,864 → 67,200,000 | 4.1G → 4.61T |
| Vibranium | 80,400,000 | 134,217,728 → 84,500,000 | 32.8G → 5.79T |
| Unobtainium | 149,800,000 | 4,294,967,296 → 158,000,000 | 262G → 10.79T |
| Ultimate | 4,294,967,296 | Unlimited | Unlimited |

Several routers now carry **less** energy than before, on purpose: a Basic router moves 454 FE/t, and an Unobtainium one 158 million instead of 4.29 billion. If you moved energy from other generators through routers of these tiers, you will see lower throughput per face. Both values are still plain config entries (`tiers.<tier>.energyPerTick` and `storage.batteryCapacity.<tier>`), so a pack can set them back.

**Other changes**

- The in-game guide, the tier tables and the Battery page show the new numbers.

**Compatibility note:** the network protocol is unchanged (**32**), so nothing about joining a server changes. A new config migration (balance version 5) replaces the router energy and the Battery capacity only if they are still at the 2.7.0 defaults; values you changed are kept. NeoForge 21.1.248 or newer is still required, and all optional integrations remain optional.

Wireless Automate 2.16.0 rebalances fluids, chemicals, Source and every storage capacity, and fixes two transfer bugs that could stall a machine with two outputs.

**Rebalance 6: fluids, chemicals, Source and storage** (measured, not guessed)

- **Fluid and chemical throughput ×4:** the Basic tier now moves **8,000 mB/s** (400 mB/t, on par with a basic Mekanism pipe), then ×8 per tier: 64,000 · 512,000 · 4,096,000 · 32,768,000 · 262,144,000 · 2,097,152,000 mB/s · unlimited on Ultimate.
- **Source throughput ×10:** the Basic tier now moves **1,000 Source/s**, the rate of one Ars Nouveau Source Relay (read from its code: 1,000 every 20 ticks), then ×8 per tier: 8,000 · 64,000 · 512,000 · 4,096,000 · 32,768,000 · 262,144,000 · unlimited.
- **Every storage block now holds one hour of a face of its own tier**, like the Battery already did: Wireless Chest 115,200 items on Basic (was 32,768), Wireless Tank and Chemical Tank 28,800,000 mB (was 256,000), Wireless Source Tank 3,600,000 Source (was 10,000); ×8 per tier, unlimited on Ultimate.
- Item throughput (32 items/s on Basic, ×8 per tier) and energy (tied to the Signal Resonator) do not change.
- **Existing servers migrate on the first start:** values still at the old defaults in the config switch to the new ones; values you changed are kept (`migration.balanceVersion` 6).
- Verified in game tests: every tier with a limit reaches 100% of its configured throughput with an unlimited source and sink, for items and fluids; Ultimate is bounded only by the tick budget.

**Transfer fixes**

- **A machine with two outputs no longer stalls.** When the first output filled faster than the tier's throughput, the router kept its cursor on it and the second output was never emptied (an Electrolytic Separator, machines with a secondary output). The next visit now continues from the next slot or tank.
- **A tank that refuses a fluid by type no longer sleeps as if it were full.** A lava-only tank offered water used to go to sleep and miss the lava coming from the second tank of the same machine; the refusal is now remembered per fluid (and per chemical) until the machine changes, and only a truly full tank sleeps.

**Performance**

- Remote machines (Range Upgrade): the "chunk loaded" check is done once per tick per machine instead of once per capability query (two per destination per slot during a visit).
- Audit of the whole transfer path; no regression in the benchmark scenarios (`raw`, `many`, `mixed`, `range`).

**Other changes**

- Spec, guide, README and config comments updated with the new tables.

**Compatibility note:** the network protocol is unchanged (**49**), so 2.16.0 clients and servers work with 2.15.x. Worlds load as they are; the server config migrates the throughput and capacity defaults on the first start (values you changed are kept). NeoForge 21.1.248 or newer is still required; Allthemodium, All The Tweaks, Mekanism, Ars Nouveau and FTB Teams remain optional.

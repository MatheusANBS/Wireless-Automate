Wireless Automate 2.9.0 adds the **Crystal Repairer**, a block that mends cracked Resonator crystals, and makes the Resonator recharge every paid Dull crystal at once.

**Crystal Repairer** (new block)

- A cracked crystal is no longer a trip to the crafting table: put it on one of the **8 pedestals** and the Repairer mends it. Up to eight crystals are repaired **in parallel**, one per line.
- Each repair takes **7 seconds** and uses **4 full Signal Crystals** from the shared stock slot plus energy: **2 times the FE per crystal** of the crystal's tier (Signal Crystal at the Basic price, Cut Crystal at the Advanced one, a Tuned crystal at its own tier). That is 432 FE for a Signal Crystal and 13,824 FE for a Cut one.
- The mended crystal comes out as the same crystal, **fully charged and with full integrity**, in the output slots. A line with no free output waits instead of losing the crystal.
- Energy goes in from any side (a router can feed it), items go in and out from any side (only the outputs can be taken). The buffer holds eight repairs of the dearest tier.
- Breaking the block while a line is working gives back the crystals it had already taken.
- The block is **animated**: the cracks close from the inside out, the crystals converge on the emitter with a flash and a shockwave on the floor, and the mended crystal rises out. With a shaderpack of Iris, the crystals stay still on their pedestals.
- Recipe: Resonator Casing, Phase Circuit, Hopper, Harmonic Lens and a Redstone Block. JEI shows one example per crystal type with the cost and time from the config, and the guide has a new page.
- Two new config entries, `repairer.repairTicks` (140) and `repairer.costFactor` (2).

**Resonator**

- The Core now **recharges all the Dull crystals the catalyst pays for at the same time**, one catalyst per crystal, instead of one after the other. The chamber and the screen show every crystal that is recharging, and the ones still waiting are marked as queued.
- The recharge still costs one point of integrity per crystal; the order of the chambers decides who goes first when the catalyst runs short.

**Other changes**

- Fixed a possible blank pixel at the tip of the Resonator's ribbons on some graphics cards.
- The in-game guide, the recipe pages and the Resonator page describe the Repairer and the parallel recharge.

**Compatibility note:** the network protocol changed to **33**, so a server and its clients need the same version. No world or configuration migration is needed. NeoForge 21.1.248 or newer is still required, and all optional integrations remain optional.

Wireless Automate 2.1: the Signal Resonator, a multiblock crystal generator you tune like a radio, and quicker buttons in the Range Upgrade's machine list.

**Signal Resonator** (new)

- **Signal Quartz ore** in the Overworld, in stone and deepslate (from y = −64 to 32, most common around −16). Mine it with an iron pickaxe or better: 1 to 3 **Signal Crystals**, more with Fortune; Silk Touch gives the ore.
- **Signal Crystal** generates for 10 minutes. The **Cut Signal Crystal** (4 crystals around an Amethyst Shard, only full crystals) yields twice as much and lasts twice as long. The charge shows as the item's bar; a spent crystal turns **Dull**.
- **The structure:** a hollow 5×5×5 box. Porcelain Casing on the edges and corners; Resonance Glass, the ports and the Tuner in the middle of the faces. It needs one **Resonance Core**, at least one **Energy Port** and one **Crystal Port**; the **Tuner** is optional. Click the loose Core with an empty hand to project the missing blocks in the world (Shift + scroll switches between the Showcase and Band layouts). Two Resonators can't share a wall.
- **Tuning:** every Resonator has its own **station** between 88 and 108 MHz, and it drifts slowly. Open the Core screen (click the assembled Core with an empty hand) and turn the dial until the **IN TUNE** lamp lights: 100%. Off the station, efficiency drops, and at 2 MHz away it generates nothing. An oscilloscope and a pointer show how close you are.
- **Tuner:** in automatic mode it follows the station on its own, up to a ceiling per tier (85% on Basic to 98% on Ultimate).
- **The chamber:** through the glass you can read the tuning. In tune, a light figure closes, the crystals rise together and a Chladni pattern settles on the floor; out of tune, it scribbles. It lights up in sequence when the structure is assembled.
- **Recharge:** a Dull crystal left in a chamber recharges with redstone in the Core: one at a time, 30 seconds and 1 dust each. The Crystal Port takes crystals and redstone from pipes or routers and hands back the Dull ones while the Core has no redstone.
- **Energy** leaves through the Energy Port, straight into a router. The Core keeps a buffer of 10 seconds of its full output.
- **Tiers by the Core**, raised with the same Upgrade Cards as the router, without breaking anything: from 1 chamber and 128 FE/t per crystal (Basic) to 8 chambers and 1,073,741,824 FE/t per crystal (Ultimate); Allthemodium, Vibranium and Unobtainium in between with ATM10.
- **JEI:** the structure in 3D (drag to turn, scroll to zoom, view by layer), the recharge, and a per-tier table on the Core.
- **Guide book:** two new pages, Signal Quartz and crystals, and Signal Resonator, in English and Portuguese.
- Operators: `/wa resonator <pos> info` and `dial <MHz>`; `/wa profile` shows the Resonators' time per tick.

**Range Upgrade**

- The chosen row in the machine list now has its own buttons: **Update**, **Back to profile** and **Unlink** (two clicks). The "Editing" line only appears when the list isn't in view.
- A linked machine that was replaced by another block with an inventory or tank shows as **replaced**, in brass, with the new block's name. **Update** moves it to that block's group (also for a whole group at once).

**Other changes**

- The Unobtainium router's energy rate went from 262,144,000 to **2,097,152,000 FE/t**, so it keeps up with an Unobtainium Resonator. Configs still at the old default are updated on the first start; changed values are kept.
- Upgrade Cards say they also raise a Resonance Core.
- Panel notices wrap to up to three lines instead of being cut.

**Compatibility note:** the network protocol changed (18), so the server and every client need 2.1.0. Worlds from 2.0 load as they are. Still requires NeoForge 21.1.248 or newer. Mekanism, Ars Nouveau, Allthemodium, JEI and GuideME stay optional.

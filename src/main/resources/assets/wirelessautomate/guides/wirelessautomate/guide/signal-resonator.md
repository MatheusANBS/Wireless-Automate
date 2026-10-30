---
navigation:
  title: Signal Resonator
  icon: wirelessautomate:resonance_core
  parent: index.md
  position: 19
item_ids:
  - wirelessautomate:resonator_casing
  - wirelessautomate:resonator_glass
  - wirelessautomate:resonance_core
  - wirelessautomate:resonator_energy_port
  - wirelessautomate:resonator_crystal_port
  - wirelessautomate:resonator_tuner
---


# Signal Resonator

<ItemImage id="wirelessautomate:resonance_core" scale="2" float="left" />

A multiblock generator: crystals vibrate in a box and you **tune** the Core to pull the most FE out of them.
Crystals, catalysts and Hearts come from [Signal Quartz](signal-quartz.md).

<br clear="all" />

## How to use it

1. Craft the **Resonance Core** (its recipe takes a **Basic Resonator Heart**) and place it.
2. Click it with an empty hand: the structure shows up **projected** in the world. **Shift + mouse wheel** switches
   the build.
3. Build the box around it following the projection.
4. Add a crystal: click the Core with it in hand.
5. Click the formed Core to open its screen and turn the dial until **IN TUNE** lights up.
6. Attach a [Router](router.md) to the **Energy Port**, face on **Extract** in the **Energy** tab.

## Building it

<GameScene zoom="3" interactive={true}>
  <Block id="wirelessautomate:resonator_casing" x="0" y="0" z="0" p:position="nnn" />
  <Block id="wirelessautomate:resonator_casing" x="0" y="0" z="1" p:position="nnc" />
  <Block id="wirelessautomate:resonator_casing" x="0" y="0" z="2" p:position="nnc" />
  <Block id="wirelessautomate:resonator_casing" x="0" y="0" z="3" p:position="nnc" />
  <Block id="wirelessautomate:resonator_casing" x="0" y="0" z="4" p:position="nnp" />
  <Block id="wirelessautomate:resonator_casing" x="0" y="1" z="0" p:position="ncn" />
  <Block id="wirelessautomate:resonator_glass" x="0" y="1" z="1" p:face="west" p:connections="3" />
  <Block id="wirelessautomate:resonator_glass" x="0" y="1" z="2" p:face="west" p:connections="11" />
  <Block id="wirelessautomate:resonator_glass" x="0" y="1" z="3" p:face="west" p:connections="9" />
  <Block id="wirelessautomate:resonator_casing" x="0" y="1" z="4" p:position="ncp" />
  <Block id="wirelessautomate:resonator_casing" x="0" y="2" z="0" p:position="ncn" />
  <Block id="wirelessautomate:resonator_glass" x="0" y="2" z="1" p:face="west" p:connections="7" />
  <Block id="wirelessautomate:resonator_glass" x="0" y="2" z="2" p:face="west" p:connections="15" />
  <Block id="wirelessautomate:resonator_glass" x="0" y="2" z="3" p:face="west" p:connections="13" />
  <Block id="wirelessautomate:resonator_casing" x="0" y="2" z="4" p:position="ncp" />
  <Block id="wirelessautomate:resonator_casing" x="0" y="3" z="0" p:position="ncn" />
  <Block id="wirelessautomate:resonator_glass" x="0" y="3" z="1" p:face="west" p:connections="6" />
  <Block id="wirelessautomate:resonator_glass" x="0" y="3" z="2" p:face="west" p:connections="14" />
  <Block id="wirelessautomate:resonator_glass" x="0" y="3" z="3" p:face="west" p:connections="12" />
  <Block id="wirelessautomate:resonator_casing" x="0" y="3" z="4" p:position="ncp" />
  <Block id="wirelessautomate:resonator_casing" x="0" y="4" z="0" p:position="npn" />
  <Block id="wirelessautomate:resonator_casing" x="0" y="4" z="1" p:position="npc" />
  <Block id="wirelessautomate:resonator_casing" x="0" y="4" z="2" p:position="npc" />
  <Block id="wirelessautomate:resonator_casing" x="0" y="4" z="3" p:position="npc" />
  <Block id="wirelessautomate:resonator_casing" x="0" y="4" z="4" p:position="npp" />
  <Block id="wirelessautomate:resonator_casing" x="1" y="0" z="0" p:position="cnn" />
  <Block id="wirelessautomate:resonator_casing" x="1" y="0" z="1" p:position="cnc" />
  <Block id="wirelessautomate:resonator_casing" x="1" y="0" z="2" p:position="cnc" />
  <Block id="wirelessautomate:resonator_casing" x="1" y="0" z="3" p:position="cnc" />
  <Block id="wirelessautomate:resonator_casing" x="1" y="0" z="4" p:position="cnp" />
  <Block id="wirelessautomate:resonator_casing" x="1" y="1" z="0" p:position="ccn" />
  <Block id="wirelessautomate:resonator_casing" x="1" y="1" z="4" p:position="ccp" />
  <Block id="wirelessautomate:resonator_casing" x="1" y="2" z="0" p:position="ccn" />
  <Block id="wirelessautomate:resonator_casing" x="1" y="2" z="4" p:position="ccp" />
  <Block id="wirelessautomate:resonator_casing" x="1" y="3" z="0" p:position="ccn" />
  <Block id="wirelessautomate:resonator_casing" x="1" y="3" z="4" p:position="ccp" />
  <Block id="wirelessautomate:resonator_casing" x="1" y="4" z="0" p:position="cpn" />
  <Block id="wirelessautomate:resonator_casing" x="1" y="4" z="1" p:position="cpc" />
  <Block id="wirelessautomate:resonator_casing" x="1" y="4" z="2" p:position="cpc" />
  <Block id="wirelessautomate:resonator_casing" x="1" y="4" z="3" p:position="cpc" />
  <Block id="wirelessautomate:resonator_casing" x="1" y="4" z="4" p:position="cpp" />
  <Block id="wirelessautomate:resonator_casing" x="2" y="0" z="0" p:position="cnn" />
  <Block id="wirelessautomate:resonator_casing" x="2" y="0" z="1" p:position="cnc" />
  <Block id="wirelessautomate:resonator_casing" x="2" y="0" z="2" p:position="cnc" />
  <Block id="wirelessautomate:resonator_casing" x="2" y="0" z="3" p:position="cnc" />
  <Block id="wirelessautomate:resonator_casing" x="2" y="0" z="4" p:position="cnp" />
  <Block id="wirelessautomate:resonator_casing" x="2" y="1" z="0" p:position="ccn" />
  <Block id="wirelessautomate:resonator_casing" x="2" y="1" z="4" p:position="ccp" />
  <Block id="wirelessautomate:resonator_energy_port" x="2" y="2" z="0" p:facing="north" p:formed="true" />
  <Block id="wirelessautomate:resonance_core" x="2" y="2" z="4" p:facing="south" p:formed="true" p:tier="emerald" />
  <Block id="wirelessautomate:resonator_casing" x="2" y="3" z="0" p:position="ccn" />
  <Block id="wirelessautomate:resonator_casing" x="2" y="3" z="4" p:position="ccp" />
  <Block id="wirelessautomate:resonator_casing" x="2" y="4" z="0" p:position="cpn" />
  <Block id="wirelessautomate:resonator_casing" x="2" y="4" z="1" p:position="cpc" />
  <Block id="wirelessautomate:resonator_tuner" x="2" y="4" z="2" p:facing="up" p:formed="true" />
  <Block id="wirelessautomate:resonator_casing" x="2" y="4" z="3" p:position="cpc" />
  <Block id="wirelessautomate:resonator_casing" x="2" y="4" z="4" p:position="cpp" />
  <Block id="wirelessautomate:resonator_casing" x="3" y="0" z="0" p:position="cnn" />
  <Block id="wirelessautomate:resonator_casing" x="3" y="0" z="1" p:position="cnc" />
  <Block id="wirelessautomate:resonator_casing" x="3" y="0" z="2" p:position="cnc" />
  <Block id="wirelessautomate:resonator_casing" x="3" y="0" z="3" p:position="cnc" />
  <Block id="wirelessautomate:resonator_casing" x="3" y="0" z="4" p:position="cnp" />
  <Block id="wirelessautomate:resonator_casing" x="3" y="1" z="0" p:position="ccn" />
  <Block id="wirelessautomate:resonator_casing" x="3" y="1" z="4" p:position="ccp" />
  <Block id="wirelessautomate:resonator_casing" x="3" y="2" z="0" p:position="ccn" />
  <Block id="wirelessautomate:resonator_casing" x="3" y="2" z="4" p:position="ccp" />
  <Block id="wirelessautomate:resonator_casing" x="3" y="3" z="0" p:position="ccn" />
  <Block id="wirelessautomate:resonator_casing" x="3" y="3" z="4" p:position="ccp" />
  <Block id="wirelessautomate:resonator_casing" x="3" y="4" z="0" p:position="cpn" />
  <Block id="wirelessautomate:resonator_casing" x="3" y="4" z="1" p:position="cpc" />
  <Block id="wirelessautomate:resonator_casing" x="3" y="4" z="2" p:position="cpc" />
  <Block id="wirelessautomate:resonator_casing" x="3" y="4" z="3" p:position="cpc" />
  <Block id="wirelessautomate:resonator_casing" x="3" y="4" z="4" p:position="cpp" />
  <Block id="wirelessautomate:resonator_casing" x="4" y="0" z="0" p:position="pnn" />
  <Block id="wirelessautomate:resonator_casing" x="4" y="0" z="1" p:position="pnc" />
  <Block id="wirelessautomate:resonator_casing" x="4" y="0" z="2" p:position="pnc" />
  <Block id="wirelessautomate:resonator_casing" x="4" y="0" z="3" p:position="pnc" />
  <Block id="wirelessautomate:resonator_casing" x="4" y="0" z="4" p:position="pnp" />
  <Block id="wirelessautomate:resonator_casing" x="4" y="1" z="0" p:position="pcn" />
  <Block id="wirelessautomate:resonator_casing" x="4" y="1" z="1" p:position="pcc" />
  <Block id="wirelessautomate:resonator_casing" x="4" y="1" z="2" p:position="pcc" />
  <Block id="wirelessautomate:resonator_casing" x="4" y="1" z="3" p:position="pcc" />
  <Block id="wirelessautomate:resonator_casing" x="4" y="1" z="4" p:position="pcp" />
  <Block id="wirelessautomate:resonator_casing" x="4" y="2" z="0" p:position="pcn" />
  <Block id="wirelessautomate:resonator_casing" x="4" y="2" z="1" p:position="pcc" />
  <Block id="wirelessautomate:resonator_crystal_port" x="4" y="2" z="2" p:facing="east" p:formed="true" />
  <Block id="wirelessautomate:resonator_casing" x="4" y="2" z="3" p:position="pcc" />
  <Block id="wirelessautomate:resonator_casing" x="4" y="2" z="4" p:position="pcp" />
  <Block id="wirelessautomate:resonator_casing" x="4" y="3" z="0" p:position="pcn" />
  <Block id="wirelessautomate:resonator_casing" x="4" y="3" z="1" p:position="pcc" />
  <Block id="wirelessautomate:resonator_casing" x="4" y="3" z="2" p:position="pcc" />
  <Block id="wirelessautomate:resonator_casing" x="4" y="3" z="3" p:position="pcc" />
  <Block id="wirelessautomate:resonator_casing" x="4" y="3" z="4" p:position="pcp" />
  <Block id="wirelessautomate:resonator_casing" x="4" y="4" z="0" p:position="ppn" />
  <Block id="wirelessautomate:resonator_casing" x="4" y="4" z="1" p:position="ppc" />
  <Block id="wirelessautomate:resonator_casing" x="4" y="4" z="2" p:position="ppc" />
  <Block id="wirelessautomate:resonator_casing" x="4" y="4" z="3" p:position="ppc" />
  <Block id="wirelessautomate:resonator_casing" x="4" y="4" z="4" p:position="ppp" />
  <BlockAnnotation x="2" y="2" z="4" color="#f0603e">
    Resonance Core: the screen and the crystals
  </BlockAnnotation>
  <BlockAnnotation x="2" y="2" z="0" color="#45d6cc">
    Resonator Energy Port: a router pulls the energy here
  </BlockAnnotation>
  <BlockAnnotation x="4" y="2" z="2" color="#e2b347">
    Resonator Crystal Port: crystals and catalyst in, Dull and cracked ones out
  </BlockAnnotation>
  <BlockAnnotation x="2" y="4" z="2" color="#a06bff">
    Resonator Tuner (optional)
  </BlockAnnotation>
  <IsometricCamera yaw="35" pitch="30" />
</GameScene>

The scene shows the **Showcase** build. Rotate it with the mouse.

- A **hollow 5×5×5 box**: **Porcelain Casing** on the edges and corners; the 3×3×3 inside stays empty.
- On face positions (never on edges): **one Core**, **at least one Energy Port**, **at least one Crystal Port**, at most
  one **Tuner** (optional) and Glass for the rest.
- Examples: **Showcase** (a 3×3 Glass window on one side) and **Band** (three glass blocks along the top of each
  side).
- Two Resonators **can't share a wall**.

If it doesn't form, click the Core: the action bar says what's in the way and the wrong block is outlined.

| Block | Role |
| --- | --- |
| <ItemImage id="wirelessautomate:resonator_casing" /> **Porcelain Casing** | The shell of the box. |
| <ItemImage id="wirelessautomate:resonator_glass" /> **Resonance Glass** | The window onto the chamber. |
| <ItemImage id="wirelessautomate:resonance_core" /> **Resonance Core** | Screen, crystals and generated power. |
| <ItemImage id="wirelessautomate:resonator_energy_port" /> **Resonator Energy Port** | Only pulls energy out. |
| <ItemImage id="wirelessautomate:resonator_crystal_port" /> **Resonator Crystal Port** | Takes crystals and catalyst; hands back Dull and cracked ones. |
| <ItemImage id="wirelessautomate:resonator_tuner" /> **Resonator Tuner** | Optional: tunes by itself. |

## Crystals and catalyst

The Core has chambers for crystals: 1 on Basic, up to 8 on Ultimate.

| How | What to do |
| --- | --- |
| **Click** | Crystal or catalyst in hand, click the Core. |
| **Screen** | Crystal slots and catalyst slot. |
| **Crystal Port** | A router puts crystals and catalyst in and takes Dull and cracked ones out. |

- To take the last crystal out: click with **Shift and both hands empty**.
- From Elite up, use the tier's **Tuned Signal Crystal**: others yield much less.
- The **catalyst** only recharges Dull crystals, and each tier takes its own (the screen says which).
- Each recharge uses up **integrity**: the crystal cracks after 8 charges. More on [Signal Quartz](signal-quartz.md).

## Tuning

The Resonator has a hidden **station**, and the **dial** has to sit right on it.

- Turn the dial with the buttons (**±1 MHz** and **±0.05 MHz**) or the **mouse wheel** over it (±0.05 MHz).
- **IN TUNE** lit: generation at 100%. The farther off, the less power, down to **zero**.
- **no signal: sweep the dial**: you're far off; sweep until the pointer shows up.
- The station **drifts over time**: come back now and then and set it again.

With a **Tuner** in the structure, the **Tuner** button turns on **automatic mode**: the dial follows the station by
itself, but efficiency is capped by a tier **ceiling** (table below). To turn it by hand, switch back to **Manual**.

## Interference

Every other formed Core within **32 blocks** (by the largest of the x, y and z distances), in the same dimension and loaded, takes **15%** of the
efficiency, up to **60%**. To generate at full, keep them more than 32 blocks apart.

## Resonant Echo

Only on the **Ultimate** Core. A **cracked Tuned Ultimate crystal** in the **Echo** slot burns and adds **1 Echo**.
Each Echo gives **+5%** generation, with no ceiling.

Watch out: a cracked Tuned Ultimate crystal that arrives through the Crystal Port goes to the Echo too. The Core
**keeps its Echo** when broken.

## The chamber

Through the glass: in tune, the figure of light **closes** and the crystals rise and fall **together**. Out of tune,
it turns into a **scribble**; at zero generation, it disappears. A cracked crystal goes dark.

## Power

A router of the Core's tier keeps up with full generation. The Core stores about 10 seconds of generation; when the
reserve is full, it **stops generating and doesn't wear the crystals**. It only works while its chunk is loaded: use
the [Chunk Loading Upgrade](chunk-loading.md) on the Port's router.

## Tiers

Upgrade the Core with [Upgrade Cards](upgrade-cards.md) (Shift + click), without taking it apart. Each step also needs
the **Resonator Heart of the target tier** in your inventory; the click uses up both. Hearts are on
[Signal Quartz](signal-quartz.md).

Max generation: a full-yield crystal in every chamber, **IN TUNE**, no interference.

| Core tier | Chambers | Max generation (FE/t) | Tuner ceiling |
| --- | --- | --- | --- |
| **Basic** | 1 | 432 | 85% |
| **Advanced** | 2 | 27,648 | 88% |
| **Elite** | 3 | 60,000 | 90% |
| **Emerald** | 4 | 1,280,000 | 92% |
| **Allthemodium¹** | 5 | 64,000,000 | 94% |
| **Vibranium¹** | 6 | 80,400,000 | 95% |
| **Unobtainium¹** | 7 | 149,800,000 | 96% |
| **Ultimate** | 8 | 4,294,967,296 | 98% |

¹ Only with the Allthemodium mod.

Upkeep per tier: the right crystal, how long a charge lasts (at 100%) and the catalyst. A Signal Crystal lasts half
as long.

| Core tier | Crystal at full yield | A charge lasts | Recharge catalyst |
| --- | --- | --- | --- |
| **Basic** | Cut or Tuned | 20 min | Redstone or Resonance Dust |
| **Advanced** | Cut or Tuned | 20 min | Redstone or Resonance Dust |
| **Elite** | Tuned Elite or higher | 15 min | Resonance Dust |
| **Emerald** | Tuned Emerald or higher | 10 min | Resonance Dust |
| **Allthemodium¹** | Tuned Allthemodium or higher | 5 min | Charged Allthemodium Dust |
| **Vibranium¹** | Tuned Vibranium or higher | 5 min | Charged Vibranium Dust |
| **Unobtainium¹** | Tuned Unobtainium or higher | 4 min | Charged Unobtainium Dust |
| **Ultimate** | Tuned Ultimate | 3 min | Charged Ultimate Dust |

¹ Only with the Allthemodium mod.

The numbers are the defaults; JEI shows your pack's.

## Recipes

<RecipeFor id="wirelessautomate:resonator_casing" />
<RecipeFor id="wirelessautomate:resonator_glass" />
<RecipeFor id="wirelessautomate:resonance_core" />
<RecipeFor id="wirelessautomate:resonator_energy_port" />
<RecipeFor id="wirelessautomate:resonator_crystal_port" />
<RecipeFor id="wirelessautomate:resonator_tuner" />

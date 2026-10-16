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

A multiblock power generator: quartz crystals vibrate inside a box, and you **tune** the Core to the right
frequency to pull the most FE out of them. The crystals come from
[Signal Quartz](signal-quartz.md).

<br clear="all" />

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
    Resonator Crystal Port: crystals and redstone in, Dull ones out
  </BlockAnnotation>
  <BlockAnnotation x="2" y="4" z="2" color="#a06bff">
    Resonator Tuner (optional)
  </BlockAnnotation>
  <IsometricCamera yaw="35" pitch="30" />
</GameScene>

The scene shows the **Showcase** build, with the glass window on one face and the Core on another. Rotate the scene with the mouse.

## Building it

1. Place the **Resonance Core** and click it with an empty hand: the structure shows up **projected** in the world
   (the missing parts and what is wrong). Click again to turn it off. **Shift + mouse wheel** switches between the
   **Showcase** and **Band** builds.
2. The structure is a **hollow 5×5×5 box**: **Porcelain Casing** on the edges and corners; **Resonance Glass**, the
   ports and the Tuner on the face positions in between. The 3×3×3 inside stays empty.
3. It needs **one Core**, **at least one Energy Port** and **at least one Crystal Port**; the **Tuner** is optional
   (at most one). The Core and the ports go on face positions, never on the edges.
4. Two example builds work: the **Showcase** (a 3×3 Glass window in front) and the **Band** (three glass blocks on
   the top row of each side). Other layouts also build: the Glass can go on any face position.
5. A **wall can't be shared** with another Resonator: each one has its own box.

When it completes, the structure lights up with a pulse and the action bar says **Resonator formed**. If it
doesn't build, click the loose Core: the action bar says what's in the way and the wrong block is outlined in coral.

| Block | Role |
| --- | --- |
| <ItemImage id="wirelessautomate:resonator_casing" /> **Porcelain Casing** | The shell of the box. |
| <ItemImage id="wirelessautomate:resonator_glass" /> **Resonance Glass** | The window onto the chamber. |
| <ItemImage id="wirelessautomate:resonance_core" /> **Resonance Core** | The brain: screen, crystals and the generated power. |
| <ItemImage id="wirelessautomate:resonator_energy_port" /> **Resonator Energy Port** | Only pulls energy out: attach a router to it. |
| <ItemImage id="wirelessautomate:resonator_crystal_port" /> **Resonator Crystal Port** | Accepts crystals and redstone and hands Dull ones back. |
| <ItemImage id="wirelessautomate:resonator_tuner" /> **Resonator Tuner** | Optional: tunes by itself. |

## Crystals and redstone

Each Core has chambers for crystals (1 on Basic, up to 8 on Ultimate). Put a **Signal Crystal** or a
**Cut Crystal** in one of these ways:

| How | What to do |
| --- | --- |
| **By clicking** | Click the Core with the crystal in hand: it goes to the first empty chamber. With redstone in hand, the dust goes into the Core. |
| **Through the screen** | Open the Core's screen and use the crystal slots and the redstone slot. |
| **Through the Crystal Port** | A router (or another item transport) puts crystals and redstone into the port and takes the Dull ones out. |

To take the last crystal out, click the Core with **Shift and both hands empty**. The **redstone** is only for
recharging Dull crystals, as on the [Signal Quartz](signal-quartz.md) page.

## Tuning

Click the formed Core with an empty hand to open its screen. The Resonator has a **station**, a hidden
frequency, and the **dial** has to sit right on it:

- Turn the dial with the buttons (**±1 MHz** and **±0.05 MHz**) or with the **mouse wheel** (±0.05 MHz).
- The pointer shows how close you are; when the **IN TUNE** lamp lights up, generation is at 100%.
- If the pointer says **no signal: sweep the dial**, the dial is far off: sweep it until the pointer shows up.
- The farther from the station, the less power. Far enough, generation drops to **zero**.

The station **drifts slowly over time**, and the tuning is lost little by little: come back now and then and set
the dial again. The station never shows up as a number: you find it by the lamp.

## Tuner

With a **Tuner** in the structure, the screen's **Tuner** button turns on **automatic mode**: the dial follows the
station by itself, with no need to come back. In exchange, efficiency is capped by a **ceiling** that depends on the
Core's tier (in the table below). In automatic mode the dial ignores the buttons: switch back to **Manual** to turn it.

## The chamber

Through the glass you can read the tuning without opening the screen:

- **In tune**: the figure of light in the air **closes**, the crystals rise and fall **together**, each with a beam to
  the light, and the plate on the floor gets sharp.
- **Out of tune**: the figure turns into a **scribble**, each crystal moves on its own and the floor hisses. With
  generation at zero, the figure disappears.
- The Core's lens lights up on its wall, sparks run to the Energy Port and a thread of redstone reaches the Dull
  crystal that is recharging.

## Power

Power leaves through the **Energy Port**: attach a [Router](router.md) to it and set the face to **Extract** on the
**Energy** tab. The Core keeps a small reserve (about 10 seconds of full generation); when it's full, the Core
**stops generating and doesn't wear the crystals**. The Core only works while its chunk is loaded: to run it while
you're away, use the [Chunk Loading Upgrade](chunk-loading.md) on the router attached to the Port.

## Tiers

The Core goes up in tier with the same [Upgrade Cards](upgrade-cards.md) as the router (Shift + click the Core with
the card), without taking it apart: the contents stay and the new chambers show up. Each tier has more chambers and
more FE per crystal. The Cut Crystal yields twice what the table shows for the Signal Crystal.

| Core tier | Chambers | FE/t per Signal Crystal | Tuner ceiling |
| --- | --- | --- | --- |
| **Basic** | 1 | 128 | 85% |
| **Advanced** | 2 | 1,024 | 88% |
| **Elite** | 3 | 8,192 | 90% |
| **Emerald** | 4 | 32,768 | 92% |
| **Allthemodium¹** | 5 | 262,144 | 94% |
| **Vibranium¹** | 6 | 2,097,152 | 95% |
| **Unobtainium¹** | 7 | 134,217,728 | 96% |
| **Ultimate** | 8 | 1,073,741,824 | 98% |

¹ Only with the Allthemodium mod.

Default values: the server can change the FE per crystal. JEI shows your pack's values on the Core.

## Recipes

<RecipeFor id="wirelessautomate:resonator_casing" />
<RecipeFor id="wirelessautomate:resonator_glass" />
<RecipeFor id="wirelessautomate:resonance_core" />
<RecipeFor id="wirelessautomate:resonator_energy_port" />
<RecipeFor id="wirelessautomate:resonator_crystal_port" />
<RecipeFor id="wirelessautomate:resonator_tuner" />

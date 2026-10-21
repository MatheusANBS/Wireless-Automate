---
navigation:
  title: Signal Quartz and crystals
  icon: wirelessautomate:signal_crystal
  parent: index.md
  position: 18
item_ids:
  - wirelessautomate:signal_quartz_ore
  - wirelessautomate:deepslate_signal_quartz_ore
  - wirelessautomate:signal_crystal
  - wirelessautomate:cut_signal_crystal
  - wirelessautomate:tuned_signal_crystal_elite
  - wirelessautomate:tuned_signal_crystal_emerald
  - wirelessautomate:tuned_signal_crystal_allthemodium
  - wirelessautomate:tuned_signal_crystal_vibranium
  - wirelessautomate:tuned_signal_crystal_unobtainium
  - wirelessautomate:tuned_signal_crystal_ultimate
  - wirelessautomate:resonance_dust
  - wirelessautomate:charged_dust_allthemodium
  - wirelessautomate:charged_dust_vibranium
  - wirelessautomate:charged_dust_unobtainium
  - wirelessautomate:charged_dust_ultimate
  - wirelessautomate:signal_alloy_raw
  - wirelessautomate:signal_alloy_ingot
  - wirelessautomate:resonance_plate
  - wirelessautomate:resonance_wire
  - wirelessautomate:resonance_coil
  - wirelessautomate:harmonic_lens
  - wirelessautomate:phase_circuit
  - wirelessautomate:phase_matrix
  - wirelessautomate:field_stabilizer
  - wirelessautomate:condensed_core
  - wirelessautomate:alloy_frame
  - wirelessautomate:resonator_heart_basic
  - wirelessautomate:resonator_heart_advanced
  - wirelessautomate:resonator_heart_elite
  - wirelessautomate:resonator_heart_emerald
  - wirelessautomate:resonator_heart_allthemodium
  - wirelessautomate:resonator_heart_vibranium
  - wirelessautomate:resonator_heart_unobtainium
  - wirelessautomate:resonator_heart_ultimate
---


# Signal Quartz and crystals

<Row gap="8">
  <ItemImage id="wirelessautomate:signal_quartz_ore" />
  <ItemImage id="wirelessautomate:deepslate_signal_quartz_ore" />
  <ItemImage id="wirelessautomate:signal_crystal" />
  <ItemImage id="wirelessautomate:cut_signal_crystal" />
  <ItemImage id="wirelessautomate:tuned_signal_crystal_elite" />
  <ItemImage id="wirelessautomate:resonance_dust" />
</Row>

Quartz crystals are the fuel of the [Signal Resonator](signal-resonator.md), the mod's power generator. This page
shows where they come from, how to look after them and the parts the Resonator needs to go up in tier.

## The ore

**Signal Quartz Ore** generates in the Overworld, in stone and in deepslate (the deepslate version is
harder to mine). It sits in small veins, most common near **y = −16**. Mine it with an **iron pickaxe** or better:

| Tool | Drop |
| --- | --- |
| Iron pickaxe or better | 1 to 3 **Signal Crystals** and a bit of experience. **Fortune** raises the amount. |
| Silk Touch | The ore itself. |

## The crystals

| Crystal | Where it comes from |
| --- | --- |
| <ItemImage id="wirelessautomate:signal_crystal" /> **Signal Crystal** | The ore. |
| <ItemImage id="wirelessautomate:cut_signal_crystal" /> **Cut Signal Crystal** | Crafting table: four Signal Crystals in a cross around an Amethyst Shard. |
| <ItemImage id="wirelessautomate:tuned_signal_crystal_elite" /> **Tuned Signal Crystal** | Crafting table: a Cut crystal with the tier's material (below). One per tier, from Elite to Ultimate. |

How much each one yields in a chamber depends on the Core's tier:

| Crystal | Basic or Advanced Core | Elite Core or higher |
| --- | --- | --- |
| **Signal Crystal** | 1× | 1/4× |
| **Cut** | 2× | 1/2× |
| **Tuned** for the Core's tier or higher | 2× | 2× |
| **Tuned** for a lower tier | 2× | 1/2× |

Cut and Tuned crystals last twice as long as a Signal Crystal. The higher the Core's tier, the shorter a charge
lasts: the table on the [Signal Resonator](signal-resonator.md) page shows the time for each tier.

The crystal recipes only accept **full**, uncracked crystals: spent ones don't count.

<RecipeFor id="wirelessautomate:cut_signal_crystal" />

## Tuning a crystal

<Row gap="8">
  <ItemImage id="wirelessautomate:tuned_signal_crystal_elite" />
  <ItemImage id="wirelessautomate:tuned_signal_crystal_emerald" />
  <ItemImage id="wirelessautomate:tuned_signal_crystal_allthemodium" />
  <ItemImage id="wirelessautomate:tuned_signal_crystal_vibranium" />
  <ItemImage id="wirelessautomate:tuned_signal_crystal_unobtainium" />
  <ItemImage id="wirelessautomate:tuned_signal_crystal_ultimate" />
</Row>

From the **Elite Core** up, only the **Tuned Signal Crystal** for the Core's tier (or a tier above) yields in full.
To tune one, put a **Cut** crystal (or a Tuned one of a lower tier, to retune it) and the tier's material anywhere on
a crafting table:

| Tuned | Material, besides the crystal |
| --- | --- |
| <ItemImage id="wirelessautomate:tuned_signal_crystal_elite" /> **Elite** | 2 netherite ingots |
| <ItemImage id="wirelessautomate:tuned_signal_crystal_emerald" /> **Emerald** | 2 emerald blocks + 1 Nether Star |
| <ItemImage id="wirelessautomate:tuned_signal_crystal_allthemodium" /> **Allthemodium¹** | 2 Allthemodium ingots + 1 Nether Star |
| <ItemImage id="wirelessautomate:tuned_signal_crystal_vibranium" /> **Vibranium¹** | 2 Vibranium ingots + 1 Vibranium-Allthemodium alloy ingot |
| <ItemImage id="wirelessautomate:tuned_signal_crystal_unobtainium" /> **Unobtainium¹** | 2 Unobtainium ingots + 1 Unobtainium-Vibranium alloy ingot |
| <ItemImage id="wirelessautomate:tuned_signal_crystal_ultimate" /> **Ultimate** | With Allthemodium: 2 Unobtainium-Vibranium alloy ingots + 1 Nether Star. Without it: 1 netherite block + 1 Nether Star + 1 Echo Shard |

¹ Only with the Allthemodium mod.

The tuned crystal comes out full, but with the **integrity** the input crystal had: tuning doesn't renew the crystal.

<RecipeFor id="wirelessautomate:tuned_signal_crystal_elite" />

## Charge and Dull crystals

The item's **durability bar** shows how much is left before the crystal runs out: it only appears once the
crystal starts to wear. Inside the Core, the charge drops while it generates power.

With no charge left, the crystal turns **Dull**: it's the same item, just dimmed. A Dull crystal generates
nothing, but you can **recharge** it:

1. Leave the Dull crystal in a Core chamber.
2. Put the tier's **catalyst** in the Core (click with it in hand, through the screen or through the Crystal Port):
   **redstone** or **Resonance Dust** on Basic and Advanced, only **Resonance Dust** on Elite and Emerald, and the
   tier's **Charged Dust** from Allthemodium up.
3. The Core recharges **one Dull crystal at a time**: it takes 30 seconds and uses 1 catalyst, and the crystal comes back full.

The catalyst slot holds one kind at a time: to switch catalysts, wait for the current one to run out or take it out
through the screen. The Crystal Port only hands the Dull crystals back when the Core has no catalyst; with catalyst
in it, the Dull crystal stays inside, waiting its turn to recharge.

## Integrity and cracked crystals

Each crystal takes **8 charges**: the one it is made with and 7 recharges. Each recharge uses 1 **integrity** (the
item's tooltip shows "Integrity 5 of 8", and the Core's screen a small bar under each chamber). When integrity runs
out, the recharge doesn't fill the crystal: it **cracks**.

A **cracked crystal** doesn't generate, doesn't recharge and doesn't go into a chamber. The Crystal Port always hands
it back. To **recast** it, put the cracked crystal and **4 full Signal Crystals** anywhere on a crafting table: out
comes the same crystal, full and with its integrity restored. It works for all three kinds; a Tuned one keeps its tier.

In an Ultimate Core, a cracked Tuned Ultimate crystal can also be burned in the **Echo**, as on the
[Signal Resonator](signal-resonator.md) page.

## Resonance Dust and Charged Dust

<Row gap="8">
  <ItemImage id="wirelessautomate:resonance_dust" />
  <ItemImage id="wirelessautomate:charged_dust_allthemodium" />
  <ItemImage id="wirelessautomate:charged_dust_vibranium" />
  <ItemImage id="wirelessautomate:charged_dust_unobtainium" />
  <ItemImage id="wirelessautomate:charged_dust_ultimate" />
</Row>

**Resonance Dust** is the catalyst of the Elite and Emerald Cores (and it works on Basic and Advanced too). On a
crafting table, shapeless: **1 redstone + 1 glowstone dust + 1 full Signal Crystal** make 4.

<RecipeFor id="wirelessautomate:resonance_dust" />

From Allthemodium up, each Core takes the **Charged Dust** of its own tier. On a crafting table, shapeless:
**8 Resonance Dust + 1 ingot of the tier** make 8.

| Charged Dust | Ingot |
| --- | --- |
| <ItemImage id="wirelessautomate:charged_dust_allthemodium" /> **Allthemodium¹** | 1 Allthemodium ingot |
| <ItemImage id="wirelessautomate:charged_dust_vibranium" /> **Vibranium¹** | 1 Vibranium ingot |
| <ItemImage id="wirelessautomate:charged_dust_unobtainium" /> **Unobtainium¹** | 1 Unobtainium ingot |
| <ItemImage id="wirelessautomate:charged_dust_ultimate" /> **Ultimate** | With Allthemodium: 1 Unobtainium-Vibranium alloy ingot. Without it: 1 netherite ingot |

¹ Only with the Allthemodium mod.

## The Heart's parts

To build the Core and to raise its tier, the Resonator takes a **Resonator Heart** (below). The Hearts come from
this chain of parts, made in order:

| Part | Recipe | Makes |
| --- | --- | --- |
| <ItemImage id="wirelessautomate:signal_alloy_raw" /> **1. Raw Signal Alloy** | Crafting table, shapeless: 2 full Signal Crystals + 2 iron ingots + 1 redstone | 2 |
| <ItemImage id="wirelessautomate:signal_alloy_ingot" /> **2. Signal Alloy Ingot** | **Blast furnace**: 1 Raw Signal Alloy | 1 |
| <ItemImage id="wirelessautomate:resonance_plate" /> **3. Resonance Plate** | 3 Signal Alloy Ingots in a row | 1 |
| <ItemImage id="wirelessautomate:resonance_wire" /> **4. Resonance Wire** | 1 Signal Alloy Ingot between 2 copper ingots, with 1 gold ingot below | 4 |
| <ItemImage id="wirelessautomate:resonance_coil" /> **5. Resonance Coil** | 8 Wires around 1 iron block | 1 |
| <ItemImage id="wirelessautomate:harmonic_lens" /> **6. Harmonic Lens** | Crafting table, shapeless: 1 full Cut Signal Crystal + 2 diamonds + 4 Resonance Glass | 1 |
| <ItemImage id="wirelessautomate:phase_circuit" /> **7. Phase Circuit** | 1 Plate in the center, 2 redstone blocks, 2 gold ingots and 4 Nether quartz | 2 |
| <ItemImage id="wirelessautomate:phase_matrix" /> **8. Phase Matrix** | 1 Lens in the center, 4 Coils in the corners, 2 Circuits and 2 Plates | 1 |
| <ItemImage id="wirelessautomate:field_stabilizer" /> **9. Field Stabilizer** | 4 Matrices + 1 diamond block + 2 crying obsidian + 1 eye of ender | 1 |
| <ItemImage id="wirelessautomate:condensed_core" /> **10. Condensed Core** | 4 Stabilizers + 4 netherite blocks + 1 Nether Star in the center | 1 |

JEI shows where each ingredient goes.

<RecipeFor id="wirelessautomate:signal_alloy_ingot" />
<RecipeFor id="wirelessautomate:phase_matrix" />

## The Resonator Heart

<Row gap="8">
  <ItemImage id="wirelessautomate:resonator_heart_basic" />
  <ItemImage id="wirelessautomate:resonator_heart_advanced" />
  <ItemImage id="wirelessautomate:resonator_heart_elite" />
  <ItemImage id="wirelessautomate:resonator_heart_emerald" />
  <ItemImage id="wirelessautomate:resonator_heart_allthemodium" />
  <ItemImage id="wirelessautomate:resonator_heart_vibranium" />
  <ItemImage id="wirelessautomate:resonator_heart_unobtainium" />
  <ItemImage id="wirelessautomate:resonator_heart_ultimate" />
</Row>

There is one **Resonator Heart** per tier. The Basic one goes into the Resonance Core's recipe; the others are used
up when you raise the Core with an Upgrade Card (see the [Signal Resonator](signal-resonator.md)). All of them are
made on a crafting table, shapeless:

| Heart | Recipe |
| --- | --- |
| <ItemImage id="wirelessautomate:resonator_heart_basic" /> **Basic** | 1 Phase Matrix + 4 redstone blocks |
| <ItemImage id="wirelessautomate:resonator_heart_advanced" /> **Advanced** | 2 Phase Matrices + 4 gold blocks |
| <ItemImage id="wirelessautomate:resonator_heart_elite" /> **Elite** | 3 Phase Matrices + 4 netherite ingots |
| <ItemImage id="wirelessautomate:resonator_heart_emerald" /> **Emerald** | 1 Field Stabilizer + 7 emerald blocks + 1 Nether Star |
| <ItemImage id="wirelessautomate:resonator_heart_allthemodium" /> **Allthemodium¹** | 2 Field Stabilizers + 4 Allthemodium blocks + 2 Nether Stars |
| <ItemImage id="wirelessautomate:resonator_heart_vibranium" /> **Vibranium¹** | 1 Condensed Core + 4 Vibranium blocks + 2 Nether Stars |
| <ItemImage id="wirelessautomate:resonator_heart_unobtainium" /> **Unobtainium¹** | 2 Condensed Cores + 4 Unobtainium blocks + 3 Nether Stars |
| <ItemImage id="wirelessautomate:resonator_heart_ultimate" /> **Ultimate** | 3 Condensed Cores + 1 Alloy Frame + 5 Nether Stars |

¹ Only with the Allthemodium mod.

The <ItemImage id="wirelessautomate:alloy_frame" /> **Alloy Frame** for the Ultimate Heart is a ring of 8 blocks around
1 Nether Star: with Allthemodium, Unobtainium-Vibranium alloy blocks; without it, netherite blocks.

<RecipeFor id="wirelessautomate:resonator_heart_basic" />
<RecipeFor id="wirelessautomate:alloy_frame" />

The numbers are the defaults; JEI shows your pack's.

See how to build the generator in [Signal Resonator](signal-resonator.md).

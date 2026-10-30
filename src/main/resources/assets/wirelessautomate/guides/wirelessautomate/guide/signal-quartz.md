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

The crystals are the fuel of the [Signal Resonator](signal-resonator.md), the mod's power generator.

## How to use them

1. Mine the ore with an iron pickaxe or better.
2. Put the crystals in the Core's chambers. From Elite up, use the tier's **Tuned** crystal.
3. **Dull** crystal (no charge): leave it in the chamber and put catalyst in the Core.
4. **Cracked** crystal: recast it on a crafting table with 4 full Signal Crystals.

## The ore

In the Overworld, in stone and deepslate, in small veins. Most common near **y = −16**.

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

How much each one yields in a chamber:

| Crystal | Basic or Advanced Core | Elite Core or higher |
| --- | --- | --- |
| **Signal Crystal** | 1× | 1/4× |
| **Cut** | 2× | 1/2× |
| **Tuned** for the Core's tier or higher | 2× | 2× |
| **Tuned** for a lower tier | 2× | 1/2× |

Cut and Tuned last twice as long. The higher the Core, the shorter a charge
([Signal Resonator](signal-resonator.md)). Recipes only accept **full**, uncracked crystals.

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

On a crafting table, shapeless: a **Cut** crystal (or a lower-tier Tuned one) and the tier's material.

| Tuned | Material, besides the crystal |
| --- | --- |
| <ItemImage id="wirelessautomate:tuned_signal_crystal_elite" /> **Elite** | 2 netherite ingots |
| <ItemImage id="wirelessautomate:tuned_signal_crystal_emerald" /> **Emerald** | 2 emerald blocks + 1 Nether Star |
| <ItemImage id="wirelessautomate:tuned_signal_crystal_allthemodium" /> **Allthemodium¹** | 2 Allthemodium ingots + 1 Nether Star |
| <ItemImage id="wirelessautomate:tuned_signal_crystal_vibranium" /> **Vibranium¹** | 2 Vibranium ingots + 1 Vibranium-Allthemodium alloy ingot |
| <ItemImage id="wirelessautomate:tuned_signal_crystal_unobtainium" /> **Unobtainium¹** | 2 Unobtainium ingots + 1 Unobtainium-Vibranium alloy ingot |
| <ItemImage id="wirelessautomate:tuned_signal_crystal_ultimate" /> **Ultimate** | With Allthemodium: 2 Unobtainium-Vibranium alloy ingots + 1 Nether Star. Without it: 1 netherite block + 1 Nether Star + 1 Echo Shard |

¹ Only with the Allthemodium mod.

It comes out full, but with the same **integrity**: tuning doesn't renew it.

<RecipeFor id="wirelessautomate:tuned_signal_crystal_elite" />

## Charge and Dull crystals

The item's bar shows the charge. With none left, the crystal turns **Dull** and generates nothing. To recharge
it, leave it in the chamber and put the catalyst in the Core (by hand, through the screen or the Crystal Port):

| Core | Catalyst |
| --- | --- |
| Basic and Advanced | Redstone or Resonance Dust |
| Elite and Emerald | Resonance Dust |
| Allthemodium and up | The tier's Charged Dust |

All Dull crystals recharge together in 30 seconds, 1 catalyst each. The slot holds one kind of catalyst at a
time. The Crystal Port only hands Dull crystals back when the Core has no catalyst.

## Integrity and cracked crystals

Each crystal takes **8 charges** (the first and 7 recharges); the tooltip shows how many are left. Then it
**cracks**: it doesn't generate, doesn't recharge and doesn't go into a chamber.

To **recast** it: cracked crystal + **4 full Signal Crystals** on a crafting table. The same crystal comes back,
full and whole (a Tuned one keeps its tier). In an Ultimate Core, a cracked Tuned Ultimate can also be burned in
the **Echo** ([Signal Resonator](signal-resonator.md)).

## Resonance Dust and Charged Dust

<Row gap="8">
  <ItemImage id="wirelessautomate:resonance_dust" />
  <ItemImage id="wirelessautomate:charged_dust_allthemodium" />
  <ItemImage id="wirelessautomate:charged_dust_vibranium" />
  <ItemImage id="wirelessautomate:charged_dust_unobtainium" />
  <ItemImage id="wirelessautomate:charged_dust_ultimate" />
</Row>

**Resonance Dust**, shapeless: **1 redstone + 1 glowstone dust + 1 full Signal Crystal** make 4.

<RecipeFor id="wirelessautomate:resonance_dust" />

**Charged Dust**, shapeless: **8 Resonance Dust + 1 ingot of the tier** make 8.

| Charged Dust | Ingot |
| --- | --- |
| <ItemImage id="wirelessautomate:charged_dust_allthemodium" /> **Allthemodium¹** | 1 Allthemodium ingot |
| <ItemImage id="wirelessautomate:charged_dust_vibranium" /> **Vibranium¹** | 1 Vibranium ingot |
| <ItemImage id="wirelessautomate:charged_dust_unobtainium" /> **Unobtainium¹** | 1 Unobtainium ingot |
| <ItemImage id="wirelessautomate:charged_dust_ultimate" /> **Ultimate** | With Allthemodium: 1 Unobtainium-Vibranium alloy ingot. Without it: 1 netherite ingot |

¹ Only with the Allthemodium mod.

## The Heart's parts

**Resonator Hearts** come from these parts, in order. JEI shows where the ingredients go.

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

One per tier, shapeless. The Basic one goes into the Resonance Core's recipe; the others are used up raising the
Core with an Upgrade Card ([Signal Resonator](signal-resonator.md)).

| Heart | Recipe |
| --- | --- |
| <ItemImage id="wirelessautomate:resonator_heart_basic" /> **Basic** | 1 Phase Matrix + 4 redstone blocks |
| <ItemImage id="wirelessautomate:resonator_heart_advanced" /> **Advanced** | 2 Phase Matrices + 4 gold blocks |
| <ItemImage id="wirelessautomate:resonator_heart_elite" /> **Elite** | 3 Phase Matrices + 4 netherite ingots |
| <ItemImage id="wirelessautomate:resonator_heart_emerald" /> **Emerald** | 1 Field Stabilizer + 7 emerald blocks + 1 Nether Star; with Modern Industrialization: 1 Field Stabilizer + 6 emerald blocks + 1 Nether Star + 1 Processing Unit |
| <ItemImage id="wirelessautomate:resonator_heart_allthemodium" /> **Allthemodium¹** | 2 Field Stabilizers + 4 Allthemodium blocks + 2 Nether Stars; with MEGA Cells: 1 Field Stabilizer + 4 Allthemodium blocks + 2 Nether Stars + 2 Cell Components 16M |
| <ItemImage id="wirelessautomate:resonator_heart_vibranium" /> **Vibranium¹** | 1 Condensed Core + 4 Vibranium blocks + 2 Nether Stars; with Modern Industrialization and Draconic Evolution: 1 Condensed Core + 4 Vibranium blocks + 1 Nether Star + 1 Processing Unit + 2 Chaotic Cores |
| <ItemImage id="wirelessautomate:resonator_heart_unobtainium" /> **Unobtainium¹** | 2 Condensed Cores + 4 Unobtainium blocks + 3 Nether Stars; with MEGA Cells and Draconic Evolution: 1 Condensed Core + 4 Unobtainium blocks + 2 Nether Stars + 1 Cell Component 16M + 1 Chaotic Core |
| <ItemImage id="wirelessautomate:resonator_heart_ultimate" /> **Ultimate** | 3 Condensed Cores + 1 Alloy Frame + 5 Nether Stars; with MEGA Cells, Draconic Evolution and Modern Industrialization: 3 Cell Components 256M + 1 Cell Component 64M + 1 Quantum Circuit + 1 Chaotic Core + 1 Awakened Core + 1 Alloy Frame + 1 Nether Star |

¹ Only with the Allthemodium mod.

The <ItemImage id="wirelessautomate:alloy_frame" /> **Alloy Frame** is a ring of 8 blocks around 1 Nether Star: Unobtainium-Vibranium
alloy blocks with Allthemodium, netherite blocks without it.

<RecipeFor id="wirelessautomate:resonator_heart_basic" />
<RecipeFor id="wirelessautomate:alloy_frame" />

The numbers are the defaults; JEI shows your pack's. To build the generator, see the
[Signal Resonator](signal-resonator.md).

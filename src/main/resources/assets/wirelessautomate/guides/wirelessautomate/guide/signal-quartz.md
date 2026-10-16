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
---


# Signal Quartz and crystals

<Row gap="8">
  <ItemImage id="wirelessautomate:signal_quartz_ore" />
  <ItemImage id="wirelessautomate:deepslate_signal_quartz_ore" />
  <ItemImage id="wirelessautomate:signal_crystal" />
  <ItemImage id="wirelessautomate:cut_signal_crystal" />
</Row>

Quartz crystals are the fuel of the [Signal Resonator](signal-resonator.md), the mod's power generator.

## The ore

**Signal Quartz Ore** generates in the Overworld, in stone and in deepslate (the deepslate version is
harder to mine). It sits in small veins, most common near **y = −16**. Mine it with an **iron pickaxe** or better:

| Tool | Drop |
| --- | --- |
| Iron pickaxe or better | 1 to 3 **Signal Crystals** and a bit of experience. **Fortune** raises the amount. |
| Silk Touch | The ore itself. |

## The crystals

| Crystal | Yields | Lasts while generating |
| --- | --- | --- |
| <ItemImage id="wirelessautomate:signal_crystal" /> **Signal Crystal** | 1× | 10 minutes |
| <ItemImage id="wirelessautomate:cut_signal_crystal" /> **Cut Crystal** | 2× | 20 minutes |

The Signal Crystal only comes from the ore. The **Cut Crystal** is crafted from four Signal Crystals in a
cross around an Amethyst Shard, and only accepts **full** crystals: spent ones don't count.

<RecipeFor id="wirelessautomate:cut_signal_crystal" />

The Signal Crystal is also in the recipes for the Porcelain Casing and the Resonance Glass, and the Cut Crystal
in the one for the Resonance Core.

## Charge and Dull crystals

The item's **durability bar** shows how much is left before the crystal runs out: it only appears once the
crystal starts to wear. Inside the Core, the charge drops while it generates power.

With no charge left, the crystal turns **Dull**: it's the same item, just dimmed. A Dull crystal generates
nothing, but you can **recharge** it:

1. Leave the Dull crystal in a Core chamber.
2. Put **Redstone Dust** in the Core (click with redstone in hand, through the screen or through the Crystal Port).
3. The Core recharges **one Dull crystal at a time**: it takes 30 seconds and uses 1 redstone dust, and the crystal comes back full.

The Crystal Port only hands the Dull crystals back when the Core has no redstone; with redstone in it, the Dull
crystal stays inside, waiting its turn to recharge.

See how to build the generator in [Signal Resonator](signal-resonator.md).

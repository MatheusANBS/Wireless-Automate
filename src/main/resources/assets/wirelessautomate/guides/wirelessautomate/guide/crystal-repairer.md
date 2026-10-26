---
navigation:
  title: Crystal Repairer
  icon: wirelessautomate:crystal_repairer
  parent: index.md
  position: 20
item_ids:
  - wirelessautomate:crystal_repairer
---


# Crystal Repairer

<ItemImage id="wirelessautomate:crystal_repairer" scale="2" float="left" />

A block that **fixes the cracked crystals** of the [Signal Resonator](signal-resonator.md): the crystal comes back full,
with full charge and integrity. It does the same as recasting the crystal at a crafting table, without standing at
the table and with **up to eight repairs at the same time**. It has no tier and doesn't need to be near a Resonator.

<br clear="all" />

## How to use

1. Place the Repairer and give it **energy**. Energy enters through **any face**: a cable, a router in **Insert** mode
   with a Battery or a generator on the other side, or a Wireless Battery. No router pulls energy out of it.
2. Put the **cracked crystals** in the eight input slots (one per line) and the **full Signal Crystals** in the middle
   slot. Use the screen (right click), a router attached to the block or a hopper: each slot only accepts what fits.
3. Each line starts by itself when it has a cracked crystal, **4 full Signal Crystals** in the stock and the repair's
   energy. A repair takes **7 seconds** and the eight lines run together.
4. The fixed crystal goes to the output slots. Take them out from the screen, with a router or a hopper. If the output
   is full, the line waits.

The screen shows the block's energy, each line's progress and what is missing when nothing moves (**no energy**,
**missing crystals** or **output full**).

## The cost

Each repair spends **4 full Signal Crystals** (the same as the crafting table) and an amount of **FE** that depends on
the crystal type:

| Crystal | FE per repair |
| --- | --- |
| Signal Crystal | 432 |
| Cut Crystal | 13,824 |
| Tuned Elite | 20,000 |
| Tuned Emerald | 320,000 |
| Tuned Allthemodium | 12,800,000 |
| Tuned Vibranium | 13,400,000 |
| Tuned Unobtainium | 21,400,000 |
| Tuned Ultimate | 536,870,912 |

The numbers are the defaults; JEI shows your pack's. With little energy, a repair only starts when the buffer
holds its whole cost. Breaking the block in the middle of a repair **loses the 4 crystals and the energy already spent** on it.

## Recipe

<RecipeFor id="wirelessautomate:crystal_repairer" />

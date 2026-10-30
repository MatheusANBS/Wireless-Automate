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

Fixes the cracked crystals of the [Signal Resonator](signal-resonator.md): the crystal comes back with full charge and
integrity. It does the same as the crafting table, but with **up to eight repairs at the same time**. It has no tier and
doesn't need to be near the Resonator.

<br clear="all" />

## How to use

1. Place the Repairer and give it **energy** through **any face** (a cable, a router on **Insert** or a Wireless
   Battery). No router pulls energy out of it.
2. Put the **cracked crystals** in the eight input slots and the **full Signal Crystals** in the middle slot. Use the
   screen, a router or a hopper.
3. Each line starts by itself when it has a cracked crystal, **4 full Signal Crystals** and the energy. A repair takes
   **7 seconds**.
4. Take the fixed crystals from the output. Output full: the line waits.

The screen shows each line's progress and what is missing (**no energy**, **missing crystals** or **output full**).

## The cost

Each repair spends **4 full Signal Crystals** and an amount of **FE** that depends on the crystal:

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

The numbers are the defaults; JEI shows your pack's. A repair only starts once its whole cost is stored. Breaking the
block in the middle of a repair **loses the 4 crystals and the energy** already spent on it.

## Recipe

<RecipeFor id="wirelessautomate:crystal_repairer" />

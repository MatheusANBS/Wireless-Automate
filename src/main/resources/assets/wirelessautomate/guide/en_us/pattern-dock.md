---
group: integracoes
navigation:
  title: Pattern Dock
  icon: wirelessautomate:pattern_dock
  parent: index.md
  position: 17
item_ids:
  - wirelessautomate:pattern_dock
---


# Pattern Dock

<ItemImage id="wirelessautomate:pattern_dock" scale="2" float="left" />

Takes the orders of an AE2 **Pattern Provider** to the machines that make the recipe. Each **side** of the Dock handles
**one kind of machine**. It needs **AE2** to receive orders.

<br clear="all" />

## How to use

1. Place the Dock and put an AE2 **Pattern Provider** against one of its sides.
2. With the [Linker](linker.md), click **that side** of the Dock. Then **Ctrl + click** a machine: the matching ones
   nearby join that side. A plain click links just one machine; on an already linked one, it unlinks it.
3. Put **the patterns for those machines** in the provider. Done: the AE2 crafting CPU can now ask for them.

When you link the first machine of a side, the Dock works out on its own which face takes the ingredients and which one
gives the result. The action bar shows what it found.

## What the Dock does by itself

- It hands **each whole order** to a free machine of the side.
- The machines **take turns**: the next order goes to the next one.
- The result **goes back to the provider**, and the provider sends it to the network.
- When **all the machines are full**, the order waits. **Nothing is lost**: if you break the Dock while the rest of an
  order is waiting, it goes along in the item ("Order pending") and returns to the same side when you place it again.

## Who does what

The Dock **spends no energy and no fuel**. It only carries the orders.

- **Energy:** also link the machines to a [router](router.md). The router brings the energy to them.
- **Fuel:** what the recipe burns (coal in a furnace, for example) goes **inside the pattern**, as an ingredient.

## Tips

- **Don't multiply the patterns.** Each order goes whole to one machine, so a big pattern keeps just one busy. For more
  machines working together, put **co-processors** on the crafting CPU.
- **Crafting patterns** (crafting table, stonecutter, smithing) belong to the Molecular Assembler. The Dock refuses
  them.
- **Another kind of machine?** Use **another side**. A side handles one kind, and the Dock has six sides.
- **If the Dock gets the faces wrong** for a machine, use the [Configurator](configurator.md) on a machine of that side,
  with the usual gesture. The result holds for the whole side.

## Reach

The radius and the maximum number of machines depend on the Dock's tier. Machines must be inside the radius, measured on
each axis, and the maximum counts the **whole Dock**, adding up the **six sides**. The Upgrade Card raises the tier.

| Tier | Radius | Machines |
| --- | --- | --- |
| Basic | 6 | 8 |
| Advanced | 12 | 16 |
| Elite | 16 | 32 |
| Emerald | 24 | 48 |
| Allthemodium | 32 | 64 |
| Vibranium | 48 | 96 |
| Unobtainium | 64 | 128 |
| Ultimate | 96 | 256 |

The numbers are the defaults; JEI shows your pack's. If the config goes down, the machines left outside stay linked but
get no orders. A machine in an unloaded area waits too.

## What the screen shows

Right-click the Dock. The screen has one row per side, with the machine's name, how many there are and how many are
working, and the state:

| State | What it means |
| --- | --- |
| No provider on this side | There are machines, but nothing attached. Place a provider |
| No machine linked | There is a provider, but no machine. Link them with the Linker |
| Crafting pattern refused | The last order was a crafting, stonecutting or smithing one |
| Item and fluid order refused | The last order mixed items and fluids |
| Provider full | The result waits for the provider to send what it already got |
| All full | No machine took the last order; it waits |
| Order pending | The machine has not taken the rest of the last order yet; the side waits for it |
| Working | A machine got an order or gave back a result a moment ago |
| Ready | All good, waiting for orders |
| free | Nothing attached and no machine |

Next to the state, the screen warns how many machines are **out of reach** or **in an unloaded area**.

## Limits

- **One kind of machine per side.** A Furnace side doesn't take a Blast Furnace.
- **Orders of items only or fluids only**, in this version. An order with both is refused.
- **Same dimension** and inside the tier's radius.
- A machine belongs to **one side of one Dock**.
- The Dock needs **AE2** to receive orders.

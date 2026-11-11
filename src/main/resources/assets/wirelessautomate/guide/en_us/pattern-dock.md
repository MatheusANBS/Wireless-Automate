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

Takes the orders of an AE2 **Pattern Provider** or a Refined Storage 2 **Autocrafter** to the machines that make the
recipe. Each **side** of the Dock handles **one kind of machine**. It needs **AE2** or **RS2** to receive orders.

<br clear="all" />

## How to use

1. Place the Dock and put an AE2 **Pattern Provider** (or an RS2 **Autocrafter**, facing it) against one of its
   sides.
2. With the [Linker](linker.md), click **that side** of the Dock. Then **Ctrl + click** a machine: the matching ones
   nearby join that side. A plain click links just one machine; on an already linked one, it unlinks it.
3. Put **the patterns for those machines** in the provider. Done: the AE2 crafting CPU (or the RS2 network) can now
   ask for them.

When you link the first machine of a side, the Dock works out on its own which face takes the ingredients and which one
gives the result. The action bar shows what it found.

## What the Dock does by itself

- It hands **each whole order** to a free machine of the side.
- The machines **take turns**: the next order goes to the next one.
- The result goes **straight to the provider's network** (AE2's ME or RS2's), without passing through its inventory,
  and the order counts it there.
- An order can carry **items, fluids and chemicals together**: each one goes in through the machine face that takes
  it, and the result comes back to the network the same way, be it an item, a fluid or a chemical.
- When **all the machines are full**, the order waits. **Nothing is lost**: if you break the Dock while the rest of an
  order is waiting, it goes along in the item ("Order pending") and returns to the same side when you place it again.

## Who does what

The Dock **spends no energy and no fuel**. It only carries the orders.

- **Energy:** also link the machines to a [router](router.md). The router brings the energy to them.
- **Fuel:** what the recipe burns (coal in a furnace, for example) goes **inside the pattern**, as an ingredient.

## Tips

- **Multiply the patterns up to a batch a machine finishes in good time** (×8, ×64). Each push of the CPU goes whole
  to one machine and fills it: with a 1-ore pattern, each machine gets one at a time; with a 64 one, it gets a batch
  that keeps it busy. **More co-processors** on the CPU still help: more pushes at once, more machines working
  together. A small order keeps few machines busy.
- **Don't use the provider's "lock until result"**: the result doesn't pass through it, so the lock never releases.
  The side's row warns you ("Remove the provider lock").
- **More patterns on RS2:** chain **Autocrafters** (one facing the other, the last one facing the Dock). The patterns
  of all of them reach the same side.
- **The Autocrafter locks:** "lock until all outputs are received" works with the Dock. "Lock until the connected
  machine is empty" treats the whole side as one machine, which only counts as empty 15 s after its last move: one
  batch at a time on the side, with that gap between them. To use several machines at once, leave it unlocked.
- **The final product on RS2 needs room on a disk.** RS2 only counts the order's final result after storing it: with
  no room on the disks, it stays in the machine, the side shows "Provider full" and waits until there is space.
- **Crafting patterns** (crafting table, stonecutter, smithing) belong to the Molecular Assembler. The Dock refuses
  them.
- **Another kind of machine?** Use **another side**. A side handles one kind, and the Dock has six sides.
- **If the Dock gets the faces wrong** for a machine, use the [Configurator](configurator.md) on a machine of that side,
  with the usual gesture. The result holds for the whole side.

## Reach

The radius and the maximum number of machines depend on the Dock's tier. Machines must be inside the radius, measured on
each axis, and the maximum counts the **whole Dock**, adding up the **six sides**. It goes up a tier with the Upgrade
Card, in the world or at the crafting table.

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
| Remove the provider lock | The provider is set to "lock until result", which never releases with the Dock |
| Crafting pattern refused | The last order was a crafting, stonecutting or smithing one |
| Order with a resource the Dock doesn't know | The last order brought something that is not an item, a fluid or a chemical |
| Provider full | The result waits for room: the network is full, unpowered or without a channel |
| All full | No machine took the last order; it waits |
| Order pending | The machine has not taken the rest of the last order yet; the side waits for it |
| Working | A machine got an order or gave back a result a moment ago |
| Ready | All good, waiting for orders |
| free | Nothing attached and no machine |

Next to the state, the screen warns how many machines are **out of reach** or **in an unloaded area**.

## Limits

- **One kind of machine per side.** A Furnace side doesn't take a Blast Furnace.
- **Items, fluids and chemicals**, together or not. **Chemicals** need **Mekanism** and, for the network, **Applied
  Mekanistics** (on AE2) or **Refined Storage Mekanism Integration** (on RS2). An order with a resource the Dock
  doesn't know is refused.
- **Same dimension** and inside the tier's radius.
- A machine belongs to **one side of one Dock**.
- The Dock needs **AE2** or **RS2** to receive orders.

## Recipe

A Wireless Router among Porcelain Casings, with Signal Alloy Ingots, an AE2 **Pattern Provider** and an AE2
**Engineering Processor** (only with AE2 or RS2). Without AE2, in a pack with only RS2, the recipe swaps the provider for
an **Autocrafter** (any color) and the processor for the RS2 **Advanced Processor**. It makes the Basic Dock; the
Upgrade Card raises the tier, as with the router.

<RecipeFor id="wirelessautomate:pattern_dock" />

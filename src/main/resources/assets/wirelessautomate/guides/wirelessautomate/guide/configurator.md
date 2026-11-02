---
navigation:
  title: Configurator
  icon: wirelessautomate:configurator
  parent: index.md
  position: 8
item_ids:
  - wirelessautomate:configurator
---


# Configurator

<ItemImage id="wirelessautomate:configurator" scale="2" float="left" />

The **Auto-Setup**: it finds out on its own what each face of a machine takes and what it gives, and
proposes the modes (Insert, Extract, Storage) for you to check before applying them.

<br clear="all" />

## How to operate it

1. Link the machine to a router (attached to it, or a range router through the [Linker](linker.md)).
2. Click the machine with the Configurator. The faces with a mode get a frame in the mode's color, and
   the action bar sums up the proposal.
3. Check the proposal. If it looks right, click the same machine again within 10 seconds: the modes
   are applied to it.
4. With **Shift**, the second click applies them to the whole group's profile.
5. Pick the faces' network on the router screen, if they don't have one yet.

Clicking another machine analyzes that one instead. Clicking the air, aiming at another block for 2
seconds or waiting 10 seconds clears the proposal. Holding the button doesn't apply: let go and click
again.

## What changes

- Only the **modes** of the types the analysis found. Each face's priority, redstone, filter and
  network stay as they are.
- On a machine in a group with others, the change becomes its exception; with Shift, it goes to the
  profile.
- Chests, barrels, tanks and batteries get **Storage** on a single face; the other faces go to None.
- There is no Undo: the frame shows the proposal first, and the second click is the confirmation.

## Limits

- The analysis looks at up to **27 slots per face** and up to 64 probe items (a few common ones, the
  machine's own and your inventory's). A machine with more slots, or one that only takes a rare item,
  may get an incomplete proposal.
- It is a proposal: check it before applying, especially on other mods' machines.
- You must be able to touch the machine and edit the router.
- On a machine with no router, "Link the machine to a router first" shows up and nothing is analyzed.

On a router, clicking with the Configurator opens its screen.

## Recipe

<RecipeFor id="wirelessautomate:configurator" />

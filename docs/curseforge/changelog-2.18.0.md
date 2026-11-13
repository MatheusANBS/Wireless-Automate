Wireless Automate 2.18.0 makes the **Linker** stricter and easier to read: a machine belongs to one router, Ctrl + click takes only the machines touching the one you clicked, and the chosen router's range now shows in the world as a cube.

**Linker: one router per machine**

- **A machine of another router is refused.** Clicking a machine that is already linked to another router (in range or not) or that is another router's attached machine does nothing, and the action bar says which router and where: "Furnace already belongs to North Forge at 812, 64, -230".
- A router and a Pattern Dock can still share a machine (the Dock brings the orders, the router the energy).
- Machines that were already linked to two routers keep both links; clicking one with a router chosen unlinks it from that router, as before.

**Linker: Ctrl + click takes only the touching machines**

- **Ctrl + click links the machines of the same block that touch the one you clicked, or each other** (by a face, an edge or a corner), like a row or a wall, inside the router's radius, **nearest to the clicked one first**, up to the tier limit. A machine of the same block that does not touch the group stays out.
- The group goes on past the machines that don't join (already linked, the attached machine, protected, or another router's): those are skipped and counted, for example "1 of another router left out".
- **Ctrl + click on a linked machine unlinks only its touching group**, not every machine of the same block.
- **The same on the Pattern Dock:** Ctrl + click on a machine links the touching group to the chosen side, and on a linked one unlinks only that group.
- With the limit, the message is now "Linked 32 Furnaces · tier limit".
- It is also cheaper: Ctrl + click reads only the touching group, instead of the whole cube of the radius.

**Router range cube**

- **With the Linker in hand and a router chosen**, the router's machine range shows as a cube in the tier's color, and each linked machine is outlined in its group's color; machines out of range or over the limit show in gray. Without the Range Upgrade, only the machines show.
- It works like the Pattern Dock's cube: it hides when you put the Linker away, comes back when you take it, and switches when you choose another router or a Dock.

**Other changes**

- Guide book pages for the Linker and the Pattern Dock updated.

**Compatibility note:** the network protocol changed (**51 → 52**), so the server and every client must run 2.18.0. Worlds from 2.17.x load as they are, and existing links are kept. NeoForge 21.1.248 or newer is still required. Every other mod is optional: Mekanism 10.7+, Ars Nouveau 5.2+, FTB Teams 2101.x, Allthemodium 2+ (with All The Tweaks), JEI 19+, and for the Pattern Dock AE2 19 (1.21.1 builds) or Refined Storage 2 2.0.0-beta.1+ (except milestones 4.12 and 4.13), with Applied Mekanistics 1.6.0+ or Refined Storage Mekanism Integration 0.1.0+ for chemicals.

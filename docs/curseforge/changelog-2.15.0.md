Wireless Automate 2.15.0 makes the **Range Upgrade** easier to set up: the attached machine joins its block's group, the Linker links a whole block type with Ctrl + click, the Configurator can propose the faces of a machine, and groups of networks get an icon in the Tablet.

**Range router**

- The **attached machine** (the block in front of the router) is now a member of its block's group, so a furnace you attached and the furnaces you linked are configured in one place. Old worlds are migrated with their settings.
- A range router placed on the floor or on a wall no longer shows that block as a machine: the list shows only the linked machines and the screen opens on the first one.
- **Unlink a whole group** from the router screen; the Filter Cards of a group that empties go back to your inventory.
- **Copy and paste per machine** (Ctrl + C / Ctrl + V on the list), with Undo.
- **Update** moves a replaced machine to the group of its new block without losing cards.

**Linker**

- **Ctrl (Cmd on Mac) + click** a machine: links every machine of the same block within the router's range. Ctrl + click a machine that is already linked: unlinks all of that block.
- Click the chosen router (or Screen) again, or **Shift + right-click in the air**, to release it.
- Right-click in the air shows the chosen router and how many machines it holds.
- The area linking (corners and the Linker screen) was removed: Ctrl + click replaces it.

**Configurator: the Tuner**

- Right-click a machine with the Configurator: it analyzes what the machine accepts and gives, paints the proposed mode on each face and describes it in the action bar. Click again to apply (Shift: apply to the whole group).
- Holding the button never confirms on its own, and the Configurator no longer has a visible cooldown.

**Tablet**

- **Group icons:** pick an item as the icon of a group of networks by dragging it from JEI onto the slot next to the group's name (the slot blinks while you drag), or by clicking with the item in your off hand. The icon also shows in the network list.

**Transfer**

- A destination that only has room for other items no longer stalls the network: after repeated refusals it waits, then tries again on its own when space frees up.
- A full destination is checked again after a while instead of staying marked as full.

**Other changes**

- A "Group removed" notice no longer disappears right away when the edited target changes.
- Several fixes found while testing the range router and the Linker.

**Compatibility note:** the network protocol changed (**49**), so servers and clients must both run 2.15.0. Worlds from 2.14 load as they are: attached machines join their groups, and Linkers that had an area marked keep working without it. The config keys `maxAreaVolume` and `maxDistance` were removed (old config files are fine). NeoForge 21.1.248 or newer is still required. All optional integrations remain optional.

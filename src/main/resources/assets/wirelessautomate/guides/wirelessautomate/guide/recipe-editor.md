---
navigation:
  title: Recipe editor
  icon: minecraft:writable_book
  parent: index.md
  position: 22
---


# Recipe editor

Fit the cost of the mod's crafting recipes to your pack, without leaving the game.

## How to use it

1. Type `/wa recipes`. Operators only (permission level 2); in single player, with cheats.
2. Pick a recipe in the list. **Green** dot: default; **coral**: edited; **gray**: disabled.
3. Edit the 3 × 3 grid and the result.
4. **Save**, then **Reload now**: it applies to everyone right away.

## Editing

| To... | Do this |
| --- | --- |
| Put an item | Click the slot, then an item in your inventory (it stays there), or drag from JEI. |
| Empty | Right-click the slot, or **Clear**. |
| Accept a tag | **Tag**; the arrows cycle the tag. **Item** goes back to the exact item. |
| Amount | **−** and **+**, up to the stack size (64 at most). |
| An Upgrade Card's ring | Swap the eight slots around it. The center is locked; the new ring applies to everything that goes there. |

**Restore default** brings back the mod's recipe. **Disable** / **Enable** takes the recipe out or
brings it back. Closing the screen also applies what was saved.

## Good to know

| | |
| --- | --- |
| **Where it lives** | `config/wirelessautomate/recipes/`. Applies to every world in the instance (or the server) and survives updates. |
| **Datapacks and KubeJS** | If they change the same recipe, they win; the screen warns you. |
| **Not in the editor** | Other mods' recipes, the Filter Card copy and the router upgrade in the crafting table. It can't create new recipes. |
| **Undo everything** | Delete `data` inside `config/wirelessautomate/recipes/` and run `/reload`. |

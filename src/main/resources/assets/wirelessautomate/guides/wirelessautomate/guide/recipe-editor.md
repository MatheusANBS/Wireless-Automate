---
navigation:
  title: Recipe editor
  icon: minecraft:writable_book
  parent: index.md
  position: 19
---


# Recipe editor

Change the ingredients and the result of the mod's crafting-table recipes without leaving the game
and without touching files. Use it to fit the cost of Wireless Automate items to your pack.

## Who can use it

Operators only (permission level 2). In a single-player world, cheats must be enabled.

## Opening it

Type `/wa recipes`. The screen lists, on the left, the mod's crafting recipes that are currently
loaded, with a search box on top. The dot next to each recipe shows its state:

| Dot | State |
| --- | --- |
| **Green** | The mod's default recipe. |
| **Coral** | A recipe you edited. |
| **Gray** | A disabled recipe. |

## Editing a recipe

Pick a recipe in the list: the 3 × 3 grid and the result show up on the right.

| To... | Do this |
| --- | --- |
| Put an item in a slot | Click the slot, then an item in your inventory (the item stays there), or click the slot with the item on your cursor. With JEI, drag the item onto the slot. |
| Empty a slot | Right-click it. |
| Accept any item of a tag | Pick the slot and click **Tag**. The arrows cycle through the item's tags. **Item** goes back to the exact item. |
| Empty the chosen slot | **Clear** (or right click the slot). |
| Change the result's amount | **−** and **+**, up to the item's stack size (64 at most). |

## Saving and reloading

| Button | What it does |
| --- | --- |
| **Save** | Only writes the recipe. Nothing changes in the game yet. |
| **Reload now** | Applies the saved changes, for every player, right away. |
| **Restore default** | Puts the recipe back to what the mod ships. |
| **Disable** / **Enable** | Takes the recipe out of the game, or brings it back. |

If you close the screen with saved changes that haven't been applied, they are reloaded on close.

## Good to know

| | |
| --- | --- |
| **Where it lives** | Edits are kept in `config/wirelessautomate/recipes/`, a folder for the instance. They apply to every world in it and, on a server, to the server. They survive mod updates. |
| **Other datapacks and KubeJS** | If another datapack or KubeJS changes the same recipe, theirs wins, and the screen warns that the recipe differs from what is loaded. |
| **What is not listed** | Other mods' recipes, the Filter Card copy and the router upgrade in the crafting table. You can't create new recipes either. |
| **Undo everything** | Delete the `data` folder inside `config/wirelessautomate/recipes/` and reload (`/reload`). |

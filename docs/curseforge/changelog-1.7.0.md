Wireless Automate 1.7: a recipe editor for server operators. Change, disable or restore any of the mod's crafting recipes in game, with no datapack or script to write.

**Recipe editor** (operators only)

- Run **`/wa recipes`** to open the editor. The list shows the mod's crafting recipes that are loaded in your pack, with a search box and a dot for the state: green is the mod default, orange is edited, grey is disabled.
- Pick a grid slot and click an item in your inventory to put it there. The item stays in your inventory; the grid only keeps a ghost copy. You can also click with an item on the cursor, or drag one from JEI. Right-click a slot to clear it.
- **Tag** turns a slot into "any item of this tag", choosing among the tags of the item in it; the arrows go through them.
- **−** and **+** change how many items the recipe makes, up to the item's stack size (64 at most).
- **Save** writes the change, **Disable** removes the crafting recipe of that item, **Restore default** brings back the mod's recipe.
- Changes apply after a reload: press **Reload now**, or just close the screen. Everyone online gets the new recipes and JEI updates. Saving doesn't reload every time, because a reload freezes a big pack for a few seconds.
- Edits are saved as a datapack in `config/wirelessautomate/recipes/`, so they work in every world of the instance (on a server, for the whole server), survive mod updates and can be copied to another instance. Delete the `data` folder in there and reload to undo everything.
- Recipes that only exist with another mod (Allthemodium tiers, the ATM Star Ultimate, the Chemical and Source tanks) keep that condition, so an edit never breaks a pack that loses the mod later.
- If another datapack or a KubeJS script changes the same recipe, it wins, and the editor says the loaded recipe is different from what you saved.

**Other changes**

- New guide book page, "Recipe editor", in English and Portuguese.

**Compatibility note:** 1.7 changes the network protocol, so server and clients must all run 1.7. Worlds load as they are, and nothing changes until an operator edits a recipe. In single player, the editor needs cheats on (open to LAN). Requires NeoForge 21.1.248 or newer; Allthemodium, All The Tweaks, Mekanism, Ars Nouveau and JEI are optional.

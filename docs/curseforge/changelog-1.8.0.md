Wireless Automate 1.8: fixes a crash when opening the Wireless Chest and makes its list easier to work with: tag and tooltip search, a list that holds still while you hold Shift, and a right click that always takes half.

**Fixes**

- Opening a Wireless Chest (or Tank) no longer crashes the game with "Comparison method violates its general contract!". It happened in big packs when an item from another mod changed itself while its name was read, in the middle of sorting the list.
- The search box now starts empty every time you open the screen. Before, the last search came back and could show up scrolled or cut off.
- Right click on an item in the list takes half of what is there when the chest holds less than a stack (10 → 5, 1 → 1). With a stack or more, it still takes half a stack.

**Chest and tank list**

- **`#` search:** `#text` looks in the item's tags and tooltip, like JEI. `#c:gems`, `#ingots`, or a tooltip line such as `#not yet eaten` (everything after `#` up to the next `@` or `#` is one phrase). Fluids search their tags. It mixes with the name and `@mod`: `iron #ingots @mekanism`.
- **Hold Shift to lock the order**, like the ME terminal of AE2: while Shift is down, items keep their places as you Shift + click them into your inventory. An item that runs out stays as a faded cell until you let go, then the list sorts again.

**Other changes**

- Guide book page of the Wireless Chest updated, in English and Portuguese.

**Compatibility note:** same network protocol as 1.7, but server and clients should both update (the half-stack rule runs on the server, the search and the Shift lock on the client). Worlds load as they are. Requires NeoForge 21.1.248 or newer; Allthemodium, All The Tweaks, Mekanism, Ars Nouveau and JEI are optional.

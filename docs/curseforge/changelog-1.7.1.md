Wireless Automate 1.7.1: fixes the recipe editor starting several reloads at once in big packs.

**Fixes**

- The recipe editor now runs one reload at a time. Before, opening or closing the screen while a reload was still running (it takes several seconds in a big pack) started another one on top of it. Overlapping reloads broke mods like KubeJS ("reload failed") and could fill the memory.
- If a reload fails, saved changes stay pending and closing the screen no longer retries it on its own; press **Reload now** to try again. Pressing it while a reload is running just says one is already in progress.

**Compatibility note:** same network protocol as 1.7, but server and clients should both update. Worlds and saved recipe edits load as they are. Requires NeoForge 21.1.248 or newer; Allthemodium, All The Tweaks, Mekanism, Ars Nouveau and JEI are optional.

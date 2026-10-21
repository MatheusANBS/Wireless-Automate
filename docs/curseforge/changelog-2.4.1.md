Wireless Automate 2.4.1 fixes unwanted automatic network assignment when configuring a machine face, and stops new Range Upgrade machines from silently inheriting their group's profile.

**Explicit face networks**

- Choosing Extract, Insert or Storage on a face without a network no longer assigns the active network selected in the Network Tablet or Linker.
- Opening the router or its panel in the Tablet does not assign a network. Choose the face's network explicitly in the Network row or use the Linker.
- Changing an action keeps any network already assigned to the face. Explicit Linker operations and copied configurations continue to work as before.

**Range Upgrade: new machines no longer inherit the group profile**

- Linking a machine whose block already has a group (Linker, Machines or Area mode) now adds it with an empty exception: no mode and no network, so it stays idle until you configure it. The same block can run a different process, so nothing is applied automatically.
- The new machine shows as an "exception" in the list. Use "Back to profile" to make it follow the group again. The first machine of a block still creates its group as before.

**Screens remember your choice**

- The router screen (and the node panel in the Tablet) now remembers the last resource tab and face you picked, so coming back from the filter screen no longer resets to Items and Front. The tab applies to any router; the face only to the same router. It is kept until you close the game.

**Other changes**

- Updated the English and Portuguese hints, guide and documentation to explain explicit network selection.
- The interface layout is unchanged in this patch.

**Compatibility note:** the network protocol remains 27, unchanged from 2.4.0. No world or configuration migration is required; existing face networks are preserved. Install the update on the server or single-player instance to apply the fix. NeoForge 21.1.248 or newer is still required, and all optional integrations remain optional.

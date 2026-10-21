Wireless Automate 2.4.2 stops new Range Upgrade machines from silently inheriting their group's profile, and makes the router screen remember your last tab and face.

**Range Upgrade: new machines no longer inherit the group profile**

- Linking a machine whose block already has a group (Linker, Machines or Area mode) now adds it with an empty exception: no mode and no network, so it stays idle until you configure it. The same block can run a different process, so nothing is applied automatically.
- The new machine shows as an "exception" in the list. Use "Back to profile" to make it follow the group again. The first machine of a block still creates its group as before.

**Screens remember your choice**

- The router screen (and the node panel in the Tablet) now remembers the last resource tab and face you picked, so coming back from the filter screen no longer resets to Items and Front. The tab applies to any router; the face only to the same router. It is kept until you close the game.

**Compatibility note:** the network protocol remains 27, unchanged from 2.4.1. No world or configuration migration is required; existing groups and exceptions are preserved. NeoForge 21.1.248 or newer is still required, and all optional integrations remain optional.

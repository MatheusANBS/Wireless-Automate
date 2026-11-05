Wireless Automate 2.15.1 is an urgent fix for 2.15.0: breaking a machine linked to a range router could crash the server.

**Fixes**

- Breaking (or replacing) a machine linked to a router with the **Range Upgrade** no longer crashes the server. It happened when the machine had more than one face in use, and depended on the order in which the game reported the change, so it did not happen every time.

**Compatibility note:** no protocol or world changes: the protocol is still **49**, so 2.15.1 works with 2.15.0 clients and servers, but update the server to get the fix. NeoForge 21.1.248 or newer is still required. All optional integrations remain optional.

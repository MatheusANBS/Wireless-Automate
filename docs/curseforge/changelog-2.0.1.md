Wireless Automate 2.0.1: a fix for the Network Tablet on servers with friends.

**Fixes**

- **Public networks in the Network Tablet:** another player's public network showed up in the Tablet with 0 nodes, and its routers never appeared in the List. Now a router with a face in a public network shows up for everyone: in the List, on the map, in the network's node count and throughput, and in the node panel (editable within the Tablet's reach, the same as walking up to it). Other players' private networks stay hidden.

**Compatibility note:** no protocol change (still 16): 2.0.0 and 2.0.1 clients and servers work together, but the fix is on the server, so update the server. Worlds from 2.0.0 load as they are. Still requires NeoForge 21.1.248 or newer.

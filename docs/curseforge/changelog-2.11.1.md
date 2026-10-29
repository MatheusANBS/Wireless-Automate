Wireless Automate 2.11.1 makes energy, fluid, chemical and Source routers notice machines that change without warning within a second, remembers the selected face when you edit a ranged machine, and fixes animated Icons on the Signal Screen with Sodium.

**Faster transfers from machines that change silently**

- Many machines gain or lose energy and fluid every tick without telling their neighbors. A router that found such a machine empty or full used to wait up to 5 seconds before looking again. For energy, fluid, chemicals and Source the wait is now at most **1 second** (20 ticks).
- In our test with a machine that changes silently, the delay dropped from about 77 ticks to 1 to 4 ticks.
- Items are unchanged: inventories report their changes, so they keep the longer wait and cost nothing extra. The server cost measured in the benchmark did not change.

**Ranged machines**

- The router screen now remembers the face you were editing on each ranged machine or group, so coming back from the Filter screen or reopening the router no longer jumps to the default face. The attached machine already did this.

**Signal Screen**

- Animated Icons (animated blocks and fluids) animate again when Sodium is installed.

**Compatibility note:** the network protocol is unchanged (**37**) and no world or configuration migration is needed. NeoForge 21.1.248 or newer is still required, and all optional integrations remain optional.

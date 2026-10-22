Wireless Automate 2.6.0 adds data-component conditions to filter rules (so you can filter by things like an Apotheosis gem tier), suggestions while you type in the rule fields, and a few Tablet and chest quality-of-life fixes.

**Filter rules**

- **New "Component" condition** in the rule builder: match any mod's data component, written as `mod:component=value` (for example `apotheosis:purity=perfect` for a perfect gem). Without `=value` it just requires the component to be present, and the value may omit the namespace. It works for components of any mod without depending on it; if the mod is not installed, the rule simply never matches.
- **Suggestions while typing** in the "Only in" (tags and mods) and "Component" (ids and values) fields, including values taken from the items in your inventory.
- **The old "Components" toggle button is gone** from the filter screen: component rules replace it.

**Router and Tablet**

- The face cube only changes a face's mode when that face is already selected, so the first click just selects it.
- The router screen and the Tablet remember the last face you chose for each router (the 64 most recent) and reopen on it. The Tablet no longer overrides your choice when it opens a node by itself.

**Storage**

- In the chest and tank list, the mouse wheel only moves items between the list and your inventory while Shift is held; otherwise it scrolls the list.

**Compatibility note:** the network protocol is now **29** (was 28), so server and clients need the same version. Filters saved with 2.5.0 load normally. NeoForge 21.1.248 or newer is still required, and all optional integrations remain optional.

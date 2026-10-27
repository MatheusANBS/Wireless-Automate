Wireless Automate 2.10.0 makes the filter's **component rule work for any mod** (genes with 100% purity, bee types, NBT fields), roomier filter screen, a **searchable network list** on routers, and a one-click way to put a group's exceptions back on the profile.

**Filter by data component, for any mod**

- The Rule tab's "Component" field now reads **fields inside a component**, not just the whole value: `productivebees:gene_group->purity>=100`, `productivebees:gene_group_list->*.purity>=100`, `minecraft:custom_data->genes.speed>2`. Old rules such as `apotheosis:purity=perfect` keep working.
- Operators: `=`, `!=`, `>=`, `<=`, `>`, `<` and `~` (contains). Lists can be indexed (`->0.field`) or searched with `*`. A field that does not exist never matches, except with `!=`.
- **Several conditions in one rule:** press Enter and each one becomes a line with a ✕ (up to 4); all of them must hold, e.g. a gene of one attribute **and** purity 100.
- **Guided typing:** the suggestion list walks you through component, field, operator and value. Components show their shape (object, list, value), fields show their type and the range seen in your items (`number · 40 to 100`), operators come in words (at least, at most, different from…) and values are the ones found in your inventory and the creative tabs. Nothing in it is specific to a mod.
- The rule summary reads like text (`gene_group → purity ≥ 100`). The field accepts up to 256 characters.

**Filter screen**

- The right panel is wider (208 px, minimum window 392 × 264): "Only in" and "Component" fields are no longer squeezed, the Yes/No buttons no longer touch, and the rule summary and the Filter Card text have room to wrap.

**Router network list**

- The list now has a **search box** (accent and case insensitive, Enter picks the first match, Esc clears and then closes), **"No network" pinned at the top**, a square colour swatch, a ✓ on the current network, the matching part highlighted and an "N of M" footer.

**Machine groups (Range Upgrade)**

- A group with exceptions shows a **↺ "Back to profile (all)"** button on its row. Two clicks put every machine of that group back on the group's profile in one go; other groups are untouched.

**Other changes**

- **Fix: item duplication and vanishing items with an AE2 storage bus on the Wireless Chest.** When a request emptied one item type, the next type slid into its slot and the storage bus kept pulling from that slot: the requested item was duplicated and the other one was deleted (seen with AE2WTLib's pick block, export buses and autocrafting). An emptied type now keeps its slot until the end of the tick. The same fix covers the Chemical Tank (Mekanism) and the Fluid Tank. Items already lost are not restored.

**Compatibility note:** the network protocol changed to **34**, so a server and its clients need the same version. No world or configuration migration is needed. NeoForge 21.1.248 or newer is still required, and all optional integrations remain optional.

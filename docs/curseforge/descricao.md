![Wireless Automate](https://media.forgecdn.net/attachments/2023/182/banner-png.png)

**Wireless Automate** moves items, fluids, energy, Mekanism chemicals and Ars Nouveau Source between machines **without pipes**. Attach a router to a machine, choose what each face of the machine does, and every router on the same network trades with the others, near or far. It also brings **storage blocks of its own** that move billions per operation, and filters that pick tags in one click or match **any enchanted item**.

It was built for large modpacks: instead of every block ticking on its own, one central manager moves everything within a small time budget per tick, so hundreds of machines don't turn into lag.

![Everything wireless](https://media.forgecdn.net/attachments/2027/582/feature-0-overview-png.png)

---

## Features

![Connect machines without pipes](https://media.forgecdn.net/attachments/2027/583/feature-1-network-png.png)

- **One router per machine.** Attach it to any face of any block. From its screen you configure **all six faces of the machine**, not just the one it sits on.
- **Networks, not cables.** Routers on the same network trade resources of the same type (items, fluids, energy and, with the optional mods, chemicals and Source). Each resource tab of a router can join a different network, so a furnace's items can go to your ore line while its energy comes from your base grid.
- **Four modes per face:** Extract, Insert, Storage (a buffer that takes from extractors and gives to inserters) or None.
- **Priority, round-robin and redstone control** for every face and resource type.
- **Resizable screens:** the router, Network Tablet and Linker can be resized from their edges and corner, and the router tabs adapt to the width.

![Configure every face](https://media.forgecdn.net/attachments/2027/584/feature-2-router-png.png)

### Filters with no entry limit

Exact items, tags (`#c:ores`) and whole mods (`@mekanism`), as a whitelist or blacklist, with **stock limits** ("keep 64 in the source", "accept up to 16 in the destination"). Add entries by Shift + clicking your inventory or by dragging from **JEI**. Filter Cards carry a whole filter to reuse on other faces.

- **Tag inspector:** put any item in the inspector and every tag it has shows up, ready to check and add. Or search every tag in the game, with how many items each one matches and a preview.
- A resizable window with the entries in a searchable list.

![Tags in one click](https://media.forgecdn.net/attachments/2027/585/feature-3-filters-png.png)

### Property rules

Match items by what they **are**, not which item they are: **any enchanted item**, damaged or undamaged, renamed, potions, unstackable, shulker boxes with contents, an enchantment with a minimum level (Fortune ≥ III) or the remaining durability (send tools under 25% to repair). Limit a rule to a tag or mod: Enchanted + `#c:armors` = enchanted armor only. Your inventory lights up on what the rule matches before you add it.

![Property rules](https://media.forgecdn.net/attachments/2027/590/feature-8-rules-png.png)

### Four tiers

Upgrade Cards raise a router's throughput and range, one tier at a time. Upgrade in place by using the card on the router, or combine both in a crafting table. The router keeps its configuration.

![Four tiers](https://media.forgecdn.net/attachments/2027/586/feature-4-tiers-png.png)

| Tier | Items/s | Fluid and chemical (mB/s) | Energy (FE/t) | Source/s | Range |
| --- | --- | --- | --- | --- | --- |
| Basic | 512 | 32,000 | 16,000 | 1,000 | 128 blocks |
| Advanced | 8,192 | 512,000 | 256,000 | 16,000 | 1,024 blocks |
| Elite | 131,072 | 8,000,000 | 4,000,000 | 256,000 | Whole dimension |
| Ultimate | Unlimited | Unlimited | Unlimited | Unlimited | Every dimension |

*Per face and per resource type. Server owners can change every value in the config.*

### Wireless storage

![Storage of its own](https://media.forgecdn.net/attachments/2027/591/feature-9-storage-png.png)

Five storage blocks made for the router: the **Wireless Chest** (unlimited item types, no slots), the **Wireless Tank**, the **Wireless Battery** and, with the optional mods, the **Wireless Chemical Tank** (Mekanism) and the **Wireless Source Tank** (Ars Nouveau). Between two of them, a router moves a whole item type in a single operation (**12 million items in one tick**) and fluids and energy by the billions. For every other mod they are a normal inventory, tank or battery.

| Tier | Chest (items) | Tank and Chemical Tank | Battery | Source Tank |
| --- | --- | --- | --- | --- |
| Basic | 262,144 | 1,000 B | 16M FE | 160,000 |
| Advanced | 16,777,216 | 64,000 B | 1G FE | 2,560,000 |
| Elite | 1,073,741,824 | 4,000,000 B | 64G FE | 40,960,000 |
| Ultimate | Unlimited | Unlimited | Unlimited | Unlimited |

- Upgrade with the same Upgrade Cards as the router, without losing the contents.
- **Break it and the contents go with the item**, like a shulker box, tier included.
- Searchable, sortable and resizable screen; an input filter decides what may enter, by any path; comparator output.

![Wireless Chest](https://media.forgecdn.net/attachments/2027/592/feature-10-chest-png.png)

### Ars Nouveau Source

With Ars Nouveau installed, every router gets a **Source** tab. Source Jars, Relays and the Imbuement Chamber connect straight to a router, so Source travels wirelessly between them like any other resource, at the Source/s of the router's tier.

![Ars Nouveau Source](https://media.forgecdn.net/attachments/2027/593/feature-11-source-png.png)

The **Wireless Source Tank** holds from 160,000 Source (Basic) to unlimited (Ultimate) and shows its level in the glass. Sourcelinks within 5 blocks deposit into it and Ars machines nearby draw from it, as from a Source Jar.

![Wireless Source Tank](https://media.forgecdn.net/attachments/2027/594/feature-12-source-tank-png.png)

### Tools for big builds

![The toolkit](https://media.forgecdn.net/attachments/2027/596/feature-14-toolkit-png.png)

![Link whole areas at once](https://media.forgecdn.net/attachments/2027/587/feature-5-area-png.png)

- **Linker:** picks your active network and puts routers in it, one by one or a **whole marked area** at once, only on the tabs you check (Items, Fluids, Energy and, with the optional mods, Chemicals and Source). Pick **None (unlink)** to take those tabs out of their network instead.
- **Configurator:** copies a router's setup (faces, filters, priorities, redstone and networks) and pastes it on other routers, one by one or over an area. Area paste only touches routers on the **same kind of machine**, so you can configure a whole production line in a few clicks. Paste every tab or just one type (Shift + mouse wheel), leaving the other tabs alone.
- **Chunk Loading Upgrade:** keeps a router and its machine working while you're away.

![Linker by type](https://media.forgecdn.net/attachments/2027/595/feature-13-linker-png.png)

### Network Tablet

![Network Tablet](https://media.forgecdn.net/attachments/2027/588/feature-6-tablet-png.png)

Every router of every network, from anywhere: search and filter by role, a top-down map, per-network statistics (one card per resource type; click one to filter the list), and groups to pause and resume a whole system at once. Click a node to open its screen remotely.

### Built-in guide book

![Built-in guide book](https://media.forgecdn.net/attachments/2025/364/feature-7-guide-png.png)

Every player gets the guide book on their first join (it's also in the creative tab, or craft it with a book and redstone). It explains every item with interactive 3D scenes and recipes, in **English and Portuguese**. Hover any item of the mod and hold **G** to jump to its page.

---

## Getting started

1. Craft two routers (iron, redstone and an eye of ender) and a Linker.
2. Use one on a chest and one on another chest. New routers start with no network.
3. Holding the Linker, click both routers: they join your active network (the first click creates one).
4. Open the first router, pick the **Up** face on the **Items** tab and set it to **Extract**.
5. On the second router, set the same face to **Insert**. Items now flow from the first chest to the second.

Once the first router of a line is set up, copy it with the Configurator and paste on the rest.

All recipes use vanilla items.

---

## Compatibility

| Mod | What you get |
| --- | --- |
| **Any mod with standard NeoForge storage** | Items, fluids and energy work with any machine, chest or tank that exposes NeoForge's standard item, fluid and energy handlers, which covers most tech and storage mods (tested with Sophisticated Storage and Mekanism). |
| **Mekanism** (optional) | A **Chemicals** tab for gases, infuse types, pigments and slurries, and the Wireless Chemical Tank. Remember to enable the face in the Mekanism machine's side configuration. |
| **Ars Nouveau** (optional) | A **Source** tab (Source Jars, Relays and the Imbuement Chamber connect straight to a router) and the **Wireless Source Tank**, which Ars machines use like a Source Jar. Needs Ars Nouveau 5.2 or newer. |
| **JEI** (optional) | Drag and drop (or Shift + click) ingredients into filters without having the item, or onto the tag inspector; router and storage upgrades in the crafting table show in JEI. |
| **GuideME** (optional) | The in-game guide book. |

## Performance

- No per-block ticking: one central manager per server.
- A time budget per tick (1 ms by default, configurable) that shrinks automatically when the server is struggling. Work that doesn't fit continues next tick; nothing is lost.
- Empty sources and full destinations sleep and cost almost nothing.

## Requirements

- Minecraft **1.21.1**
- **NeoForge** 21.1.251 or newer (works with All the Mods 10)

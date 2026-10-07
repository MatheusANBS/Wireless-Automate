![Wireless Automate](https://media.forgecdn.net/attachments/2023/182/banner-png.png)

**Wireless Automate** moves items, fluids, energy and Mekanism chemicals between machines **without pipes**. Attach a router to a machine, choose what each face of the machine does, and every router on the same network trades with the others, near or far.

It was built for large modpacks: instead of every block ticking on its own, one central manager moves everything within a small time budget per tick, so hundreds of machines don't turn into lag.

---

## Features

![Connect machines without pipes](https://media.forgecdn.net/attachments/2023/183/feature-1-network-png.png)

- **One router per machine.** Attach it to any face of any block. From its screen you configure **all six faces of the machine**, not just the one it sits on.
- **Networks, not cables.** Routers on the same network trade resources of the same type. Each resource tab of a router can join a different network, so a furnace's items can go to your ore line while its energy comes from your base grid.
- **Four modes per face:** Extract, Insert, Storage (a buffer that takes from extractors and gives to inserters) or None.
- **Priority, round-robin and redstone control** for every face and resource type.

![Configure every face](https://media.forgecdn.net/attachments/2023/184/feature-2-router-png.png)

### Filters with no entry limit

Exact items, tags (`#c:ores`) and whole mods (`@mekanism`), as a whitelist or blacklist, with **stock limits** ("keep 64 in the source", "accept up to 16 in the destination"). Add entries by Shift + clicking your inventory or by dragging from **JEI**. Filter Cards carry a whole filter to reuse on other faces.

![Unlimited filters](https://media.forgecdn.net/attachments/2023/185/feature-3-filters-png.png)

### Four tiers

Upgrade Cards raise a router's throughput and range, one tier at a time. Upgrade in place by using the card on the router, or combine both in a crafting table. The router keeps its configuration.

![Four tiers](https://media.forgecdn.net/attachments/2023/186/feature-4-tiers-png.png)

| Tier | Items/s | Fluid and chemical (mB/s) | Energy (FE/t) | Range |
| --- | --- | --- | --- | --- |
| Basic | 512 | 32,000 | 16,000 | 128 blocks |
| Advanced | 8,192 | 512,000 | 256,000 | 1,024 blocks |
| Elite | 131,072 | 8,000,000 | 4,000,000 | Whole dimension |
| Ultimate | Unlimited | Unlimited | Unlimited | Every dimension |

*Per face and per resource type. Server owners can change every value in the config.*

### Tools for big builds

![Link whole areas at once](https://media.forgecdn.net/attachments/2023/187/feature-5-area-png.png)

- **Linker:** picks your active network and puts routers in it, one by one or a **whole marked area** at once, only on the tabs you check (Items, Fluids, Energy and, with Mekanism, Chemicals). Pick **None (unlink)** to take those tabs out of their network instead.
- **Configurator:** copies a router's setup (faces, filters, priorities, redstone and networks) and pastes it on other routers, one by one or over an area. Area paste only touches routers on the **same kind of machine**, so you can configure a whole production line in a few clicks. Paste every tab or just one type (Shift + mouse wheel), leaving the other tabs alone.
- **Chunk Loading Upgrade:** keeps a router and its machine working while you're away.

### Network Tablet

![Network Tablet](https://media.forgecdn.net/attachments/2023/188/feature-6-tablet-png.png)

Every router of every network, from anywhere: search and filter by role, a top-down map, per-network statistics, and groups to pause and resume a whole system at once. Click a node to open its screen remotely.

### Built-in guide book

![Built-in guide book](https://media.forgecdn.net/attachments/2023/189/feature-7-guide-png.png)

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
| **Mekanism** (optional) | A **Chemicals** tab for gases, infuse types, pigments and slurries. Remember to enable the face in the Mekanism machine's side configuration. |
| **JEI** (optional) | Drag and drop (or Shift + click) ingredients into filters, without having the item; the router upgrade in the crafting table shows in JEI. |
| **GuideME** (optional) | The in-game guide book. |

## Performance

- No per-block ticking: one central manager per server.
- A time budget per tick (1 ms by default, configurable) that shrinks automatically when the server is struggling. Work that doesn't fit continues next tick; nothing is lost.
- Empty sources and full destinations sleep and cost almost nothing.

## Requirements

- Minecraft **1.21.1**
- **NeoForge** 21.1

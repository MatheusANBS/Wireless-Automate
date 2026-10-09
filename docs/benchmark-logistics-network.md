# Benchmark: Wireless Automate vs Logistics Network

Status: **done on 2026-10-09.** This document covers why the comparison was made, a study of the other mod (read from its source code), a side-by-side feature comparison, the method and the results. For the short version, see "Results".

## Why

Logistics Network ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/logistics-network), [source](https://github.com/Almana-mc/LogisticsNetworks)) is a popular wireless transport mod for items, fluids, energy and chemicals, and players asked how the two mods compare on server cost. Until now the only numbers were our own (`docs/benchmark.md`), measured from inside the mod, with no other mod alongside.

## Goal

Compare both mods fairly and publish the numbers together with the method, **whatever the result**. If Logistics Network wins a scenario, that goes in this document too (and becomes a performance task for us).

## Study of Logistics Network

Version studied: **1.17.2 for 1.21.1** (branch `1.21.1` of their repository, commit `c4555c9` from 2026-10-07; on CurseForge, project 1448257, file 9086994). We read the code, guides, recipes and changelogs; nothing was built or run from source. Paths below are relative to `me/almana/logisticsnetworks/`.

### Model

- **A node is an entity** (`logisticsnetworks:logistics_node`, size 0.05) attached to a block with a capability. One node per block, **9 channels** and 4 upgrade slots. Each channel picks a side (one of the six, or "all", which merges the handlers of every face) and is a Sender (`EXPORT`, takes from the block) or a Receiver (`IMPORT`, puts into the block).
- **A network** is a logical group of node UUIDs, saved in the overworld (`NetworkRegistry`). A Sender on channel N delivers only to Receivers on channel N of the same network. There is no range limit inside a dimension; another dimension needs the Dimensional Upgrade **on both ends**. There is no chunk loading: a node in an unloaded chunk is skipped.
- **Channel:** type (item, fluid, energy, chemical with Mekanism, Source with Ars), `batchSize` (default **8**), `tickDelay` (default **20**, minimum 1; energy always 1), priority from −99 to 99, redstone (ignore, high, low), distribution mode (`PRIORITY` by default, nearest, farthest, equal, priority round-robin) and 6 filters.

### Upgrades and throughput (`upgrade/UpgradeLimitsConfig.java`)

The node's tier is the highest upgrade in its 4 slots. Each channel performs one operation every `max(tickDelay, tier minTicks)` ticks, of up to `min(batchSize, tier cap)`. The actual values (from the code, editable in `config/logistics-network/upgrades.json`):

| Tier | Minimum delay | Items/operation | Fluid (mB) | FE | Chemical / Source |
| --- | --- | --- | --- | --- | --- |
| None | 20 ticks | 16 | 1,000 | 2,000 | 500 |
| Iron | 10 | 32 | 10,000 | 10,000 | 1,000 |
| Gold | 5 | 64 | 100,000 | 50,000 | 5,000 |
| Diamond | 1 | 256 | 1,000,000 | 250,000 | 20,000 |
| Netherite | 1 | 10,000 | 2.1 billion | `Integer.MAX_VALUE` | 1,000,000 |

- The guide and the tooltips show different numbers (Iron 16 items / 1,000 mB, and so on); what runs is the table above.
- The README's "10k items, 1000b fluid, 2.1b fe/t" is the Netherite tier, **per operation and per channel**; it only becomes "per tick" with the delay at 1. With the channel defaults (8 items every 20 ticks), a channel without upgrades moves **8 items/s**.
- Recipes: Iron and Gold early (4 ingots, 2 hoppers, 2 chests, smooth stone), Diamond and Netherite mid and late game; Dimensional after the Ender Dragon and the Wither.

### Engine (what matters for performance)

- **One central scheduler** (`logic/NetworkScheduler`) on `ServerTickEvent.Post` (and on `Pre` only in async mode). No per-entity transfer tick, like ours (`NetworkManager` on `Post`).
- **The unit of work is the network, woken by events.** Each network is either dirty (process now) or has a tick to wake up at. When processed, it runs **every node and channel of the network** (channels still on cooldown are cheap to skip) and sleeps again for the smallest channel delay. A network wakes when a node joins the world, on `NeighborNotifyEvent` at the attached block, when a container is closed, and on any GUI change.
- **No time budget in synchronous mode, which is the default:** every due network runs in the same tick, to completion. Ours has a budget (1 ms, adaptive) and continues next tick whatever did not fit. Direct consequence for the benchmark: with a lot of work, Logistics Network moves everything and MSPT goes up; ours holds MSPT and throughput drops. That is why the results must show throughput and cost together.
- **Exponential backoff** per channel without success: ×1.3 per failure, capped at 40 ticks (5 for energy), divided by 3 on each success. An empty source or full destination is cheap, but it can take up to 2 s to move again if no event wakes the network (ours: the source sleeps until a destination wakes, `SourceSleep`, plus `Backoff`).
- **On every pass, `prepareNetwork`** looks up each node by UUID (entity), reads redstone for the nodes that use it, and builds the destination lists (5 types × 9 channels). Destinations are sorted by priority; "nearest" and "farthest" re-sort by distance **on every operation**.
- **Cached capabilities** (`BlockCapabilityCache` per dimension, position and side), like ours. Chemicals are not cached.
- **Async mode, off by default** (`asyncPlanning = false` since 1.16.2): on the main thread it snapshots every occupied slot of the item sources and destinations (with no budget), plans on up to `min(4, cores − 2)` threads and commits on the main thread with a 2 ms budget (each move is simulated again). Fluid, energy, chemical and Source stay synchronous. Work on those threads **does not show up in MSPT**.
- **Per-node cost outside transfers:** each entity runs `tick()` (every 5 ticks it rechecks the 9 channels and the upgrades; every 20, whether the attached block became air). And **every** `NeighborNotifyEvent` in the world does an entity search in a 3×3×3 box, whether or not there is a node nearby: the cost scales with block updates in the world, not with transfers.
- **Per-inventory fast paths** (important for fairness):
  - **Sophisticated Storage/Core:** inserts with `IItemHandlerSimpleInserter.insertItem(stack)`, a bulk insert with no slot scan. Ours has no Sophisticated fast path (it goes through the plain `IItemHandler`).
  - **Functional Storage:** an always-on mixin changes the slot limit of empty drawers for **any** mod that inserts into them. Functional Storage drawers are left out of the benchmark.
  - **AE2 and Refined Storage:** with the Network Upgrade on an Interface, it talks directly to the network storage.
  - Inventories with more than 64 slots: it remembers the slots found during simulation and reuses them for the real insert (similar to our planned insertion).
- **Built-in measurement:** none (no `nanoTime` outside the async budget). Telemetry only counts units moved, and only while the Computer is open or a CC: Tweaked computer is watching the network.
- **Published performance data:** no benchmark, MSPT figure or large-server data in the repository (README, changelogs and guides). The changelogs mention "major lag improvements" (1.10.1) and multithreaded planning (1.14.0), without numbers.

### Creating nodes from code

There is no command that creates nodes (`/ln` only has `removeNodes`, `cullNetwork` and `admin`). A `/summon` with the node NBT creates the entity, but it does not join the network: membership lives in the network's UUID list, and only `NetworkRegistry.addNodeToNetwork` (called by the GUI, paste and mass placement) fills it. For the harness, the way in is reflection, with none of their types in our code: place the node through their public API, load the NBT (`"logisticsnetworks:node"` with `attached_pos`, `valid`, `channels` and `upgrades`), then call `createNetwork` and join the network.

## Features side by side

| | Wireless Automate 1.4.0 | Logistics Network 1.17.2 |
| --- | --- | --- |
| Piece in the world | Router (block) attached to a machine face, rotatable, 3D view | Node (optionally invisible entity) on the block, 9 channels |
| Types | Items, fluids, energy, chemicals (Mekanism), Source (Ars) | Items, fluids, energy, chemicals (Mekanism, with an upgrade), Source (Ars, with an upgrade) |
| Progression | 8 tiers (Basic to Ultimate, 3 from Allthemodium), ×8 per step, range and dimension by tier, direct upgrade by card | 5 levels by upgrade (none to Netherite), no range limit, dimension by an upgrade on both ends |
| Top throughput | Ultimate unlimited (bounded by the budget) | Netherite: 10,000 items per operation and channel, every tick |
| Configuration | Per face and type: mode, priority, redstone; one network per type | Per channel: mode, side, batch, delay, priority, redstone, 5 distribution modes |
| Filters | Item, tag, mod, rules (enchanted, damaged, enchantment and level, durability, "only in"), stock, filter cards | Item, tag, NBT/components, durability, stock, slot, mod, regex on name or tooltip; 6 filters per channel |
| Tools | Linker (single and area), Configurator (area paste), Tablet (statistics, list, remote GUI) | Wrench (copy and paste with fill, mass placement of up to 2,048 nodes), Computer (directory, flow monitor, table, graph, `.lnet` files), flow lines |
| Own storage | Chest, Tank, Battery, Chemical Tank, Source Tank, by tier | None |
| Chunk loading | Router upgrade | None |
| Integrations | JEI, GuideME, Mekanism, Ars, Allthemodium | JEI, EMI, Jade, GuideME, Mekanism, Ars, AE2, RS (direct access and autocrafting), Create (contraptions), CC: Tweaked, FTB Teams, Building Gadgets 2, Sophisticated and Functional Storage |
| Cost control | Per-tick budget (1 ms, adaptive), sleep and backoff per port, no per-block tick | No budget (synchronous); sleep per network and backoff per channel; optional async mode |
| Built-in measurement | `/wa profile` (ms per tick, per network) and `/wa bench` | Units moved only, with the Computer open |

## Method

### Principles

- **Same server, same scene, same mods.** Both mods are installed in every run; in each task only one of them has pieces in the scene. The scene (machines, contents, refill every 20 ticks) is the one from `docs/benchmark.md`, built by the same code; only what sits on top of the machines changes: a router or a Logistics Network node.
- **Measured from outside, the same way for both:** MSPT from the start of `ServerTickEvent.Pre` (highest priority) to the end of `Post` (lowest). It covers both loops (both run on `Post`), entity ticking and neighbor events. The **cost** is MSPT with the network minus MSPT of the same scene with no network (baseline). The difference between baselines shows the cost of existing (entities vs blocks).
- **Neutral counting:** throughput is what reached the destinations (items in the destinations plus those removed by the refill; for the test sinks, what they received), and "sources that moved" comes from what left each source. Nothing comes from either mod's counters. For ours, the neutral count matched the internal one in every scenario.
- **Cost per item** (ns/item, the same number as µs per thousand items): cost per tick × 20 ÷ items per second. This is the number that compares a mod that moves little with one that moves a lot. Item-only scenarios only.
- **Async threads:** CPU time of the `LogisticsNetworks-Worker-*` threads (via `ThreadMXBean`) is reported in the "threads" column and counted in the cost per item, so the async mode does not look free.
- **Items per game second** (20 ticks). When MSPT goes over 50 ms the server runs below 20 TPS and real-time throughput drops by the same ratio: at 256 ms per tick, a table "second" takes about 5 real seconds.
- **3 repetitions** per task (mean ± standard deviation), 200 ticks measured after warm-up, and one discarded JIT warm-up task for each mod.

### Configuring both

The throughput models differ (ours per face and second; theirs per channel operation), so equivalence is by target throughput, computed by `bench/LnChannelPlan` (with JUnit):

- **Same throughput (main comparison):** our Elite (2,048 items/s, 128,000 mB/s, 64,000 FE/t per face) against their channel with a Diamond upgrade, delay 1 and a batch of 102 items, 6,400 mB or 64,000 FE per operation (2,040 items/s). Scenarios `many`, `pairs`, `idle`, `full`, `sparse`, `big`, `bigfull`, `mixed`, `redstone`.
- **Each at its maximum:** our Ultimate (unlimited, bounded by the budget) against their Netherite, delay 1 and a batch of 10,000 (the cap). Scenarios `raw` and `inf`.

Five transports per scenario:

| Id | What it is |
| --- | --- |
| `wa` | Ours, with the default budget (1 ms per tick, adaptive) |
| `wa-full` | Ours with a 50 ms budget and no adaptive mode: does all the work in the tick, like their synchronous mode |
| `ln` | Logistics Network at its defaults: synchronous, `PRIORITY` distribution |
| `ln-rr` | Synchronous with "Equal Distribution" (`round_robin`) |
| `ln-async` | With their async planning on (off by default since 1.16.2) |

Their channel sits on the top side of the machine (the same side as the router), with the upgrade in slot 0. The adapter reads the stored channel back through their codec and aborts the task if anything does not match.

### Inventories

- **Neutral:** vanilla barrels (27 slots) and double chests (54); both mods go through the plain `IItemHandler`. For the maximum, the test machines from `bench/BenchCapabilities` (a stack of 1,000,000 cobblestone that gives up to 64 per extraction, like a drawer, and a sink), with no fast path in either mod.
- **Sophisticated Storage:** one task (`many` with 500), **favorable to them**: Logistics Network has a bulk insert fast path for Sophisticated Core; ours does not.
- **Left out:** our own storage blocks (favorable to us), Functional Storage drawers (their mixin changes insertion for any mod) and AE2/RS.

### Scenarios

| Scenario | Setup | What it shows |
| --- | --- | --- |
| `many` | 100, 500 and 1,000 machines, half full sources, half empty destinations, **all in one network** | Scaling with one large network |
| `pairs` | Like `many`, but **each source in its own network with the neighboring destination** | Scaling with many small networks |
| `idle` | 500 empty machines in one network | Cost of existing with nothing to move |
| `full` | 500 machines, full destinations | Cost of trying and failing |
| `sparse` | 500 machines, only the first source has items | Cost of empty sources |
| `raw` | 1 pair of double chests, refilled every tick, at maximum | Raw throughput (bounded by the chest) |
| `big` / `bigfull` | 10 pairs of double chests, a different item per slot; destination empty, or full except 4 slots | Large inventory scans |
| `mixed` | 498 machines: one third items, one third fluid, one third energy (test source and sink) | Mixed use |
| `redstone` | Like `many` with 100, plus a redstone block toggling every tick next to each piece | Cost of neighbor events nobody uses |
| `inf` | 1 and 50 pairs of infinite source and sink, at maximum | The engine at the top, with no inventory bottleneck |

Our own scenarios (`types`, `stock`, `rebuild`, `tablet`, `bigstack`) were left out because they have no direct equivalent on their side.

### How to run

```bash
./scripts/bench.sh comparativo                         # every scenario above in all five transports (about 2 h)
./scripts/bench.sh "many:500:3:vanilla:ln;pairs:500:3:vanilla:wa"   # chosen tasks: scenario:n:reps:storage:transport
```

The script downloads the Logistics Network jar once into `run/bench-ln/` (outside git) and puts it in `run/bench/mods` only when a task uses `ln`. In game: `/wa bench run <scenario> <n> <reps> <storage> <transport>`. The adapter (`bench/LogisticsNetworkBench`) uses only reflection over their public API (`NodePlacementHelper.placeNode`, `loadNodeState`, `NetworkRegistry.createNetwork`, `NodeClipboardConfig.joinNetwork`); none of their types are in our code and there is no build dependency. Logistics Network is All Rights Reserved: the jar is never committed or redistributed.

## Results

Runs from 2026-10-09 (raw reports `run/bench/reports/20261009-183310.md` and `20261009-195827.md`, outside git). Local idle machine: Windows 11, 12 CPUs, Java 21.0.12, 2 GB heap, NeoForge 21.1.251, Wireless Automate 1.4.0, Logistics Network 1.17.2, Sophisticated Storage 1.6.2 and Spark 1.10.124 on the server. Flat world, no player.

"Cost" is in ms per tick (mean of 3 repetitions; the deviation stayed under 5% of the mean in most cases; the raw reports have all of them). "Moved" is the share of what the refill offered that reached the destination.

### Scaling: one large network vs many small ones

| Machines | Topology | Ours (`wa`) | Ours (`wa-full`) | LN (`ln`) | LN (`ln-rr`) | LN (`ln-async`, + threads) |
| --- | --- | --- | --- | --- | --- | --- |
| 100 | single network | 0.131 | 0.146 | 2.65 | 4.66 | 1.26 + 0.60, moved 40% |
| 500 | single network | 0.639 | 0.625 | **66.1** | 74.7 | 23.8 + 10.0, moved 32% |
| 1,000 | single network | 1.04, **moved 80%** | 1.24 | **255.6** (~4 TPS) | 245.2 | 39.4 + 7.4, moved 12% |
| 100 | one network per pair | 0.131 | 0.142 | 0.537 | 0.530 | 0.60 + 0.08, moved 39% |
| 500 | one network per pair | 0.695 | 0.678 | 2.65 | 2.54 | 2.43 + 1.09, moved 39% |
| 1,000 | one network per pair | 1.03, **moved 86%** | 1.32 | 5.22 | 5.23 | 4.27 + 1.74, moved 39% |

Apart from the marked ones, all moved 100% (86,400, 432,000 and 864,000 items/s). Cost per item moved, for the same work:

| Topology | Ours | LN default |
| --- | --- | --- |
| Single network | 29 to 31 ns/item, the same at 100, 500 and 1,000 | 614, 3,059 and 5,918 ns/item at 100, 500 and 1,000 |
| One network per pair | 28 to 33 ns/item | 121 to 124 ns/item |

**Reading:**

- **With small networks, Logistics Network scales well** (linearly, about 0.005 ms per machine) and costs about **4 times** ours per item. This is the comparison closest to common use, and the most favorable to them.
- **In one large network, their cost grows with sources × destinations.** In the default mode (`PRIORITY`), each source tries the destinations in order, every tick, and scans each full destination looking for room: 66 ms per tick with 500 machines; with 1,000 the server drops to about 4 TPS. A Java Flight Recorder profile (`many` with 200, `ln`) put 81% of server thread time in `TransferEngine.processNetwork`, almost all of it in simulated inserts (`BulkInsertRejectionCache.simulate` → `insertItemStacked`). Equal distribution (`ln-rr`) does not help. Ours stays linear in both topologies, because each source has a cursor and full destinations sleep.
- **Our budget trades throughput for TPS:** with 1,000 machines and 1 ms, it moved 80% to 86%; with the high budget (`wa-full`) it moved everything at 1.24 to 1.32 ms. Anyone who wants everything can raise `tickBudgetMs`.
- **Their async mode** takes work off the main thread with a large network (from 66 to 24 ms at 500), but in every scenario with refills it moved only 12% to 40% of what the synchronous mode moves, and with small networks it cost more than synchronous. We measured the effect without investigating the cause in their code; it is off by default.

### Cost of existing and of waiting

| Scenario (500 machines) | Ours | LN | LN (`ln-async`, + threads) |
| --- | --- | --- | --- |
| Baseline (machines and pieces, no network) | 0.13 to 0.14 | 0.33 to 0.37 | 0.32 to 0.36 |
| `idle`: network built, nothing to move | 0.005 | 0.417 | 2.12 + 0.16 |
| `full`: full destinations | 0.025 | 2.14 | 2.12 + 1.90 |
| `sparse`: 1 source with items, 249 empty | 0.017 | 0.98 | 3.03 + 0.18 (moved 40%) |

- **Entities cost something:** the baseline with their nodes is about 0.2 ms higher at 500 machines and 0.35 to 0.5 ms higher at 1,000 (each node's `tick()`), before any transfer. Routers do not tick.
- **Waiting costs something:** with nothing to move, their network still runs at most every 40 ticks (backoff), and each time it processes every node in the network. Ours sleeps per port and wakes on events.

### Large inventories, mixed and redstone

| Scenario | Ours | LN | LN (`ln-rr`) | LN (`ln-async`, + threads) |
| --- | --- | --- | --- | --- |
| `raw` (1 pair of double chests, refilled every tick; both bounded by the chest at 69,120 items/s) | 0.132 | 0.222 | 0.199 | 0.112 + 0.05, moved 33% |
| `big` (10 pairs of double chests, 20,480 items/s) | 0.099 | 0.572 | 0.932, moved 78% | 0.137 + 0.05, moved 33% |
| `bigfull` (destination full except 4 slots, 2,560 items/s) | 0.016 | 1.14 | 0.90, moved 54% | 0.158 + 1.04 |
| `mixed` (498 machines, items, fluid and energy) | 0.705 | 9.56 | 14.5 | 5.88 + 3.91 |
| `redstone` (100, a clock on each piece) | 0.145 (+0.014 over `many`) | 2.80 (+0.15) | 4.98 | 1.01 + 0.70, moved 40% |

In all five, ours moved everything offered. Neighbor events are cheap in both.

### Each at its maximum (`inf`, infinite source and sink)

| Pairs | Ours (`wa`) | LN (`ln`) |
| --- | --- | --- |
| 1 | 1.01 ms, **33 million items/s** (0.6 ns/item) | 0.081 ms, 200 thousand items/s (8.1 ns/item) |
| 50 | 1.02 ms, 30 million items/s (0.7 ns/item) | 2.29 ms, 10 million items/s (4.6 ns/item) |

- Each at its own ceiling: our Ultimate has no limit and is bounded by the budget (it uses the whole 1 ms); their channel stops at 10,000 items per operation, 200 thousand per second. **With one pair, theirs uses 12 times less server time**, because it moves 165 times less. Per item, ours is 7 to 13 times cheaper. For more throughput, Logistics Network would use more channels (9 per node), which we did not measure.
- `wa-full` (50 ms budget) in this scenario moves 1.8 billion items/s and eats the full 50 ms: the default budget is what keeps the Ultimate in check.
- `ln-async` moved almost nothing here (425 and 1,280 items/s). The test source reports 1,000,000 in a slot and hands out 64 per extraction, like a drawer; our guess is that the plan made on the snapshot does not hold when it is committed. We did not investigate; this is an observation, not a result.

### Sophisticated Storage (favorable to them)

`many` with 500 machines, Sophisticated Storage wooden barrels:

| Transport | Cost | Moved | ns/item |
| --- | --- | --- | --- |
| Ours (`wa`) | 1.14 | **45%** (196 thousand of 432 thousand items/s), bounded by the budget | 117 |
| Ours (`wa-full`) | 2.14 | 100% | 99 |
| LN (`ln`) | 75.9 | 100% | 3,512 |
| LN (`ln-rr`) | 173.3 | 100% | 8,024 |
| LN (`ln-async`, + threads) | 20.6 + 7.2 | 33% | 3,906 |

Even with their bulk insert fast path, the single network weighs more. Ours, with the default budget, leaves more than half behind on Sophisticated (each insert into it is expensive); raising the budget fixes that.

### Where Logistics Network wins or ties

- **Absolute cost at the maximum of one channel** (`inf` with 1 pair): 0.08 ms against our 1 ms, at the price of moving 165 times less.
- **Throughput with our default budget** in large scenarios: with 1,000 machines or on Sophisticated, their synchronous mode delivers everything (paying 5 to 256 ms per tick); ours delivers 45% to 86% until someone raises the budget.
- **Small networks:** 4 times our cost per item, but with headroom: 5 ms per tick with 1,000 active machines.
- **Features** (see "Features side by side"): no range limit from the start, 9 channels per node, direct AE2 and RS, Create, CC: Tweaked, richer mass placement and copy and paste.

### Limitations

- A single machine, a development server with no player, a flat world. Absolute numbers vary between machines; the ratios between the mods should hold.
- Synthetic scenarios (chests and test machines), not a real base with real machines.
- One channel per node on their side; nobody measured nodes with several active channels.
- Logistics Network with its default config (40-tick backoff, default upgrades), version 1.17.2. It changes quickly (three versions in one week); these numbers are for that version.

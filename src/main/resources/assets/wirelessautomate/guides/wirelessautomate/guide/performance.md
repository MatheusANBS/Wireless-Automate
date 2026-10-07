---
navigation:
  title: Performance and config
  icon: minecraft:clock
  parent: index.md
  position: 12
---


# Performance and config

The mod doesn't tick per block: a central manager moves everything within a **time budget per
tick**. Whatever doesn't fit continues next tick, nothing is lost. With many machines throughput is
shared, but the lag never goes over the cap.

## How the mod protects your TPS

| Mechanism | What it does |
| --- | --- |
| **Budget per tick** | The mod's time cap: 1 ms by default, of the 50 ms tick. |
| **Adaptive budget** | If the server goes over 40 ms per tick, the cap drops gradually (down to 25% from 50 ms) and comes back on its own. |
| **Sleeping costs nothing** | Empty sources and full destinations sleep and barely cost anything. |
| **Taking turns** | With the budget used up, every source keeps moving, each a little less. |

## Measuring

| Command | Shows |
| --- | --- |
| `/wa profile` (operator) | ms per tick of the mod and of each network, operations per second, sources and destinations awake and sleeping. |
| Tablet › Statistics | Throughput per type and mod time per tick, no operator needed. |

## Server config

File `config/wirelessautomate-server.toml`:

| Key | Default | What it does |
| --- | --- | --- |
| `performance.tickBudgetMs` | `1.0` | The mod's time cap per tick, in ms. More = more throughput with many machines. |
| `performance.adaptiveBudget` | `true` | Lowers the cap when the server is struggling. |
| `tiers.<tier>.itemsPerSecond` | per tier | Items per second, per face and type (0 = unlimited). |
| `tiers.<tier>.fluidPerSecond` | per tier | Fluid and chemical in mB/s (0 = unlimited). |
| `tiers.<tier>.energyPerTick` | per tier | Energy in FE/t (0 = unlimited). |
| `tiers.<tier>.range` | per tier | Range in blocks (0 = the whole dimension). |
| `tiers.<tier>.crossDimension` | Ultimate only | Allows routes between dimensions. |
| `chunkLoading.*` | | See [Chunk Loading Upgrade](chunk-loading.md). |
| `linker.maxAreaVolume` / `maxDistance` | `262144` / `64` | Linker and Configurator area. |

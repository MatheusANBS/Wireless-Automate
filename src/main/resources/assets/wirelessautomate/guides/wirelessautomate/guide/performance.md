---
navigation:
  title: Performance and config
  icon: minecraft:clock
  parent: index.md
  position: 12
---


# Performance and config

The mod doesn't tick per block: a central manager moves everything within a **time budget per
tick** (default: 1 ms of the 50 ms tick). Whatever doesn't fit continues next tick, nothing is
lost; with many machines, throughput is shared, but the lag never goes over the cap.

- **Adaptive budget:** if the server goes over 40 ms per tick, the mod lowers its own cap (down to
  25% from 50 ms) and restores it on its own when the server recovers.
- **Sleeping costs nothing:** empty sources and full destinations sleep and barely cost anything.
- **`/wa profile`** (operator): ms per tick of the mod and of each network, operations per second and
  sources and destinations awake or sleeping.

## Server config

File `config/wirelessautomate-server.toml`:

- `tickBudgetMs` and `adaptiveBudget`: the budget.
- `tiers.<tier>`: throughput and range of each tier (0 = unlimited).
- `chunkLoading`: turns the upgrade on and sets the per-player chunk limit.
- `linker`: maximum size and distance of the Linker and Configurator area.

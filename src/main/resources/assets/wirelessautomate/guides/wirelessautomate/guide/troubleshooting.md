---
navigation:
  title: Troubleshooting
  icon: minecraft:barrier
  parent: index.md
  position: 21
---


# Troubleshooting

Something not moving? Check in order.

| Symptom | Cause and fix |
| --- | --- |
| Nothing leaves the source | The face isn't on **Extract**, or the routers aren't on the same **Network** (the dot above the button has the network's color). |
| Nothing reaches the destination | Too far: raise the source with an [Upgrade Card](upgrade-cards.md). Or full: it resumes on its own when there's room. |
| Only some items pass | A [filter](filters.md) or stock limit blocks. An empty filter lets everything through. |
| Works, then stops | Redstone on **With signal** or **No signal**. Use **Ignore**. |
| Stops when you walk away | The chunk unloaded: [Chunk Loading Upgrade](chunk-loading.md). |
| Mode unavailable | The machine doesn't open that type on that face. Try another. |
| Chemicals don't move | Enable the face in Mekanism's side config. |
| Can't put it in a network | The network belongs to another player. |
| Less throughput than the tier | Many machines share the throughput. It's normal. |

Still stuck? The [Network Tablet](network-tablet.md)'s **Flows** tab shows what each network is
moving, which faces are waiting for a destination and which destinations are full.

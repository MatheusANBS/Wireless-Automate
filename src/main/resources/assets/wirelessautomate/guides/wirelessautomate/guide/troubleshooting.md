---
navigation:
  title: Troubleshooting
  icon: minecraft:barrier
  parent: index.md
  position: 12
---


# Troubleshooting

Something not moving? Check in order: it's almost always one of these.

| Symptom | Likely cause | What to do |
| --- | --- | --- |
| Nothing leaves the source | The face is on **None** or **Insert**. | On the right tab, set the face to **Extract**. |
| Nothing leaves the source | The tabs are on different networks. | Check the tab's network selector on both routers. |
| Nothing reaches the destination | It's too far for the sender's tier. | Raise the source's tier with an [Upgrade Card](upgrade-cards.md). |
| Nothing reaches the destination | The destination is full. | It sleeps until there's room and comes back on its own. |
| Only some items pass | A [filter](filters.md) or stock limit is blocking. | Check the face filter on both sides; an empty filter lets everything through. |
| The face works, then stops | Redstone set to **With signal** or **No signal**. | Set it to **Ignore** or fix the signal. |
| It stops when you walk away | The chunk unloaded. | Use the [Chunk Loading Upgrade](chunk-loading.md). |
| The mode shows as unavailable | The machine gives no access to that type through that face. | Pick another face in the 3D viewer, or configure the machine. |
| Chemicals don't move | The Mekanism machine's face is disabled in its side config. | Enable the face with Mekanism's configurator tool. |
| Can't put it in a network | The network belongs to another player. | Ask the owner, or use one of yours. |
| Less throughput than the tier | Many machines moving at once. | Normal: throughput is shared between them, without freezing the game. |

Still stuck? Open the [Network Tablet](network-tablet.md): the **Statistics** tab shows what each
network is moving and which destinations are full.

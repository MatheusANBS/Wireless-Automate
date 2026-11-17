Wireless Automate 2.19.0 rebuilds the **Signal Resonator** as the **Armillary**: a new open structure with an animated orrery on top, generation every tick as the screen says, an Energy Port that pushes on its own, a continuous energy beam and sounds of its own for every stage.

**The Armillary: a new structure**

- **35 blocks instead of the 5×5×5 box.** Layer 1 is a floor of **Resonator Floor** blocks with the **Core** at the front, the **Energy Port** on the right, the **Crystal Port** on the left and the optional **Tuner** at the back. Layers 2 and 3 hold a **Resonator Column** on each of the four diagonals and a **Sun Cradle** in the center. The top two layers stay empty.
- **Two new blocks:** the **Resonator Column** (2 per craft: copper ingots, a Signal Crystal and a Resonator Floor) and the **Sun Cradle** (gold ingots, a Cut Signal Crystal and a Resonator Floor). Both have detailed models and collide and select by the shape you see, not as full cubes.
- **Porcelain Casing is now the Resonator Floor** (same block), and **Resonance Glass is gone**: glass blocks and items in old worlds turn into Resonator Floor. The Harmonic Lens now takes 4 plain Glass.
- Click the Core with an empty hand to see the structure projected in the world, as before.

**The orrery**

- Above the Sun Cradle, a brass orrery turns around a plasma sun, with the crystals in their mounts. In tune, the rings lie down and line up, and at each **conjunction** a burst leaves the horizon, an arc runs to the Energy Port and strikes it.
- **The energy beam is now continuous:** while energy leaves the Port, the beam from the sun to the Port stays lit, thicker and faster the more energy flows. The conjunction is a pulse on top of it.
- Crystal recharge, the full buffer, cracked and Dull crystals and the assembly all have their own animation.
- Drawn the vanilla way, so it works with **Iris shaderpacks** (tested with Complementary) and with Fabulous graphics.

**Generation every tick**

- **The Core now generates and delivers every tick**, the amount the screen shows (the Ultimate makes about 4.2 billion FE/t every tick), instead of a batch once a second.
- **The Energy Port pushes on its own:** every tick, up to the tier's maximum generation goes into any machine or battery touching it (never into the Armillary itself). A router on the Port still works; with both, the router pulls first.

**Sounds**

- **14 sounds of its own** (20 files), one for each stage: assembly, the sun, tuning locked and lost, the four phases of the conjunction, the beam, the full buffer, crystal recharge, a cracking crystal and a crystal going Dull. All made for the mod, none taken from Minecraft.
- They play at the point where things happen, with subtitles. The sun, the beam and the full buffer are seamless loops that fade in and out, and the beam's pitch and volume follow the energy flow.

**Other changes**

- Guide book page for the Signal Resonator rewritten for the Armillary.

**Compatibility note:** the network protocol changed (**52 → 53**) and there are new blocks, so the server and every client must run 2.19.0. In worlds from 2.18.x, an old Resonator box **comes apart when it loads**: the Core keeps its tier, crystals and items, the Casings stay as Resonator Floors and the Glass turns into Resonator Floors, so nothing is lost; rebuild it as the Armillary. NeoForge 21.1.248 or newer is still required. Every other mod is optional: Mekanism 10.7+, Ars Nouveau 5.2+, FTB Teams 2101.x, Allthemodium 2+ (with All The Tweaks), JEI 19+, and for the Pattern Dock AE2 19 (1.21.1 builds) or Refined Storage 2 2.0.0-beta.1+ (except milestones 4.12 and 4.13), with Applied Mekanistics 1.6.0+ or Refined Storage Mekanism Integration 0.1.0+ for chemicals.

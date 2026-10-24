Wireless Automate 2.8.3 redraws the chamber of the Resonator of Signal: a plasma sphere, 3D crystals and plasma tendrils replace the old beams and curves.

**Resonator chamber** (visual only; the generation, tuning and balance are unchanged)

- The core is now a **plasma sphere** drawn by a shader, with convection, a soft halo and a filmic color, and it covers whatever passes behind it.
- The **crystals** are 3D pieces in the pixel-art style of the item sprites (a cluster for the Signal Crystal, a six-sided gem for the Cut and Tuned ones, with the tier color on the Tuned band). They stand on a ring that slowly orbits the sphere (one turn every 42 seconds) and spin on their own axis, faster when tuned.
- Each live crystal sends a **plasma tendril** to the sphere: thick and steady when tuned, thin and wavering when not, so the tuning can be read from the chamber.
- The **recharge** has its own look: a reversed tendril in the catalyst color while the crystal fills, and a flash when it finishes.
- The conduit to each Energy Port follows the real flow, and the plasma shows pressure when the buffer is full.
- Gone: the Lissajous curves, orbits, beams and auras of the old look (they stay only with a shaderpack of Iris, or without the shader), and the Chladni plate on the chamber floor.

**Compatibility note:** the network protocol is unchanged (**32**) and no world or configuration migration is needed. NeoForge 21.1.248 or newer is still required, and all optional integrations remain optional.

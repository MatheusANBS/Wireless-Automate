Wireless Automate 2.12.0 turns the **Signal Screen** into a canvas: draw and animate **pixel art** in the game, put **real images and GIFs** on the wall, share Screens by code (even with an AI), and choose who can edit each Screen. Filters also get separate **accept up to** and **keep in source** limits.

**Pixel art on the Signal Screen**

- A full **pixel art editor** in the Screen's new **Art** tab: pencil, eraser, fill, picker, line, rectangle and ellipse (outline or filled), dither, selection with move, copy and paste, mirror drawing, pixel perfect lines, brush size 1 to 8 (square or round), flip and rotate, and lighten/darken that walk along a **color ramp** instead of jumping hues.
- Arts from 8 × 8 up to **128 × 128**, each with a palette of **16 colors** you pick freely (Make ramp builds a smooth ramp from one color). Navigate without scrolling: hold Space or Alt and drag, middle mouse, and wheel zoom on the cursor. Live preview at 1×, 2× and tiled.
- Up to **32 arts** per player in a personal library (`tv.maxArts`).
- The new **Image** piece shows an art at a whole-number scale, and the background can **tile** an art. A Screen can use up to **131,072 pixels** of art (`tv.maxImagePixels`, eight 128 × 128 arts).
- Arts travel outside the chunk packet, only to players near the Screen and only once per change, so big drawings don't weigh on chunk loading.

**Animated pixel art**

- An art can have up to **64 frames**, playing at **1 to 20 frames per second**, in **Loop** or **Ping-pong**.
- Frame strip under the canvas (add, duplicate, remove, drag to reorder), **onion skin** (the previous frame at 35%), play/pause, and shortcuts (`,` `.` to step, Alt + N / D / O / P). Everything is undoable.
- Every Screen showing the same art plays in sync with the clock, and only Screens in view redraw. Frames count toward the Screen's pixel limit.

**Real images and GIFs**

- Upload a **PNG, JPG or GIF** from your computer with **Upload file**, or paste it as a `WA-IMAGEM1` code. Images keep their full colors: use them for photos, logos and backgrounds. Animated GIFs play on the Screen at their own pace.
- Default limits: **1024 px** on the longer side, **8 MB** per file, **240 frames** per GIF, **8 images** per Screen (all in `tv.images.*`).
- The server **never decodes** an image; it only checks the file header and size. Each image is stored once per world, however many Screens use it, and unused images are cleaned up.
- **Server controls:** turn images off (`tv.images.enabled`, applies right away), allow uploads for everyone or only operators (`tv.images.uploaders`), an upload rate per player (32 MB per minute), a world cap (4 GB on dedicated servers), an optional per-player quota, and an automatic upload lock for players who keep hitting the rate limit (60 minutes). Operators can list, remove and ban with `/wa tela imagens` and `/wa tela imagem remover | banir | desbanir`.
- **Client option** `tv.showOthersImages`: turn it off to hide images uploaded by other players. Your own images stay.

**Share by code, and AI-friendly**

- **Copy** and **Import** arts, images and whole Screen layouts as text codes (`WA-ARTE1`, `WA-IMAGEM1`, `WA-TELA1`), compressed or in a readable form.
- The readable form is meant to be written by an AI: paste the guide into ChatGPT or Claude and ask for "a 32 × 32 sword" or "a 4 × 2 Screen with a title and a clock". Guide on GitHub: [Pixel art and Screens with an AI](https://github.com/MatheusANBS/Wireless-Automate/blob/main/docs/pixel-art-ai-guide.md).

**Who can edit a Screen**

- A new **Access** tab, like private networks: the owner adds players and, with FTB Teams, teams that can edit the Screen. A team counts as a whole and is live.

**Filters: accept up to and keep in source**

- Each whitelist entry on ports and cards now has two separate limits: **Accept up to** (at the destination) and **Keep in source** (at the source). Old filters keep working: an entry without "keep" uses its stock value.
- **Wireless Chest, Tank and Chemical Tank:** no more admission list. A global **limit per type** sits in a fixed "All other types" row at the top, and each entry is the exception for its type (0 = no limit). It never affects extraction and never blocks other types. The Quotas mode is gone.
- Number fields show their unit, keep a typed value when you switch tabs or close the screen, never hide digits, and the mouse wheel over a field adds or subtracts.

**Other changes**

- Lists on the Screen editor (theme, background, font, layout, mode, unit, direction and others) are now **dropdowns**, and the mouse wheel no longer changes values while you scroll a panel.
- The Screen's default video memory budget is now **1024 MB** (`tv.vramBudgetMb`, client config). It is a limit, not what the Screen uses.
- No tooltip on the small step buttons (filters, router priority, Screen size).
- The in-game guide covers pixel art, animation, images and GIFs, and the Access tab.

**Compatibility note:** the network protocol changed to **38**, so a server and its clients need the same version. Worlds and Screens from 2.11 load unchanged; new options (`tv.images.*`, `tv.maxArts`, `tv.maxImagePixels`, `tv.showOthersImages`) are added with their defaults. NeoForge 21.1.248 or newer is still required, and all optional integrations remain optional.

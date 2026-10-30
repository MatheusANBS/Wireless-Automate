---
navigation:
  title: Signal Screen
  icon: wirelessautomate:signal_screen
  parent: index.md
  position: 20
item_ids:
  - wirelessautomate:signal_screen
---


# Signal Screen

<ItemImage id="wirelessautomate:signal_screen" scale="2" float="left" />

A wall panel, from **1 × 1 up to 32 × 32 blocks**, that shows your [networks'](networks.md) numbers live: what comes
in, what goes out and how many faces are moving.

<br clear="all" />

## How to use it

1. Click with the Screen on the **side of a solid block**. It can't go on the floor or the ceiling, and it drops if
   the wall goes away.
2. Click it with an **empty hand** to open its settings.
3. In the **Size** tab, pick the width and height and click **Apply**.
4. In the **Edit** tab, apply a ready layout or build your own out of pieces.
5. Click **Save** (or **Ctrl+S**). Nothing reaches the wall before that.

Only whoever placed the Screen, operators and those in the **Access** tab can configure it; everyone else just
watches.

## Size

| Part | How to use |
| --- | --- |
| **Width** and **Height** | ‹ and › change it 1 block at a time; with **Shift**, 4 at a time. |
| **Grows from the block toward** | ↗, ↖ or ✥: where the block you placed sits inside the screen. |
| **Preview** | The red cell is the one in the way. |

The big screen **takes no blocks**: it is drawn over the wall. Every block of the area needs a **solid wall behind
it** and **free space in front** (air or grass work; a torch is in the way), and the area can't cross another Screen.

## The editor

In the **Edit** tab you build what the Screen shows out of **pieces**. If the window doesn't fit, lower the interface
scale.

| Piece | What for |
| --- | --- |
| **Value** | A live number: what comes in, goes out or is stored, how many faces move... |
| **Text** | A fixed phrase. |
| **Shape** | A rectangle: card, frame, divider. |
| **Item icon** | The picture of an item. |
| **Bar** | Fills with a number. |
| **Gauge** | An arc dial with a needle. |
| **Graph** | A number's history over the last minutes. |
| **Arrow** | Points up, down or stays flat with the number. |
| **Image** | A pixel art of yours or an uploaded image (PNG, JPG or GIF). |

| Gesture | What it does |
| --- | --- |
| **Click** | Picks the piece (in a group, the whole group; two clicks enter it). |
| **Shift + click** | Adds to or removes from the pick. |
| **Drag** | Moves. The corner handle resizes. |
| **Alt** while dragging | Adjusts 1 px at a time (normally a 4 px grid). |
| **Layers** | Drag a row to change the order; the eye hides. |

| Shortcut | What it does |
| --- | --- |
| **Ctrl+Z**, **Ctrl+Y** | Undo and redo. |
| **Ctrl+C**, **Ctrl+X**, **Ctrl+V** | Copy, cut and paste, even from one Screen to another. |
| **Ctrl+D**, **Delete** | Duplicate and remove. |
| **Ctrl+A**, **Ctrl+G**, **Ctrl+Shift+G** | Pick all, group and ungroup. |
| **[** and **]** | Raise and lower the layer. |
| **Arrow keys** | Move 4 px; with **Shift**, 32 px; with **Alt**, 1 px. |

The tabs on the right set up the picked piece: **Data** (where the number comes from), **Format** (decimals, k/M/G,
prefix, unit), **Style** (position, colors, border, font) and **Rules** (changes the color, hides or blinks when the
number crosses a value).

### Where the number comes from

In the **Data** tab, the **Source** can be:

- the **Network**: what comes in, goes out or is stored, and how many faces move. **Held** adds up the network's
  Storages;
- a **Storage** (Chest, Battery or Tank) linked to the Screen with the [Linker](linker.md): stored, capacity, fill,
  and what comes in, goes out or is left over per second. On a Chest you can read a single item, picked from its grid
  (or dragged from JEI);
- the game **Clock**: time of day and day.

The numbers arrive once a second while someone is nearby. A private network that whoever placed the Screen lost
access to shows **—**.

Closing with changes takes two clicks on the ✕ (**Discard?**).

### Base, themes and layouts

With **nothing picked**, the right column shows the Screen's **base**:

- **Theme:** a palette of six colors. There are four ready ones (Graphite, Phosphor, Amber and Porcelain) and you can
  save your own. "From the theme" colors (round dots) follow the swap; square ones are fixed. The Screen keeps the
  palette: deleting one of your themes later changes no Screen.
- **Background:** the theme's, a color, a gradient or a tiled art.
- **Ready layout:** **Energy**, **Storage**, **Network** or **Clock**. **Apply layout** replaces what is on the Screen
  and fits its size (**Ctrl+Z** undoes before saving). **Save current layout** keeps your own; **Remove layout**
  deletes it, in two clicks.

### Components

In the left column, **Components** are ready groups of pieces (**Network panel**, **Number card**, **Title** and your
own). Click one to insert it. To keep your own, pick the pieces and click **Save as component** (⊕). Components,
themes and layouts work on any Screen of yours.

## Pixel art

In the **Art** tab, **Pixel art** part:

1. Click **New** (or pick an art and click **Edit**).
2. Give it a name and a **Size**, 8 to 128 pixels per side.
3. Draw and click **Save** (or **Ctrl+S**). If the art doesn't fit in your library, the drawing stays open with the
   warning.

Each art has up to **16 colors** and **128 × 128** pixels. Left click uses the tool and **right click erases**.
**Ctrl** + click picks a pixel's color.

| Key | Tool |
| --- | --- |
| **B** | Pencil. |
| **E** | Eraser. |
| **G** | Bucket. With **Shift**, swaps the color in the whole art. |
| **I** | Eyedropper. |
| **L** | Line. **Shift** locks to 0°, 45° and 90°. |
| **R** | Rectangle. **R** again toggles outline and filled; **Shift** makes a square. |
| **O** | Ellipse. **O** again toggles outline and filled; **Shift** makes a circle. |
| **D** | Lighten (left) and darken (right). |
| **T** | Checkerboard dither. |
| **S** | Selection. |

| Key | Helper |
| --- | --- |
| **[** and **]** | Brush from 1 to 8 px. |
| **M** and **Shift+M** | Horizontal and vertical mirror. |
| **P** | Pixel perfect on the 1 px pencil. |
| **X** | Back to the previous color. |
| **Ctrl+G** | Turns the grid on and off. |
| **Ctrl+Z**, **Ctrl+Y** | Undo and redo, up to 100 steps. |
| **Shift+H**, **Shift+V** | Flip horizontally and vertically. |
| Mouse wheel | Zoom. |
| **Space** + drag | Moves the view. |
| **0** | Fits the art. |

- **Palette:** 16 colors in 4 rows; color 0 is always transparent. Each row is a **ramp** from dark to light, which
  lighten and darken follow. Changing a color changes all its pixels. **Make ramp** builds the row from the chosen
  color.
- **Selection (S):** with one, tools only paint inside it. Drag from inside to move; **Enter** settles, **Esc**
  undoes.

### Animation

An art can have **up to 64 frames**, all the same size and with the same palette. The strip under the canvas shows
the frames: click to choose, drag to reorder.

- **Speed** from 1 to 20 frames per second; **Loop** or **Ping-pong**.
- **Onion skin:** the previous frame shows faded underneath.
- Frames count toward the Screen's pixel limit: a 32 × 32 art with 10 frames counts 10,240.

| Key | What it does |
| --- | --- |
| **,** and **.** | Previous and next frame. |
| **Alt+N**, **Alt+D** | New frame and duplicate. |
| **Alt+O** | Onion skin. |
| **Alt+P** | Plays and pauses the preview. |

### Putting the art on the Screen

1. Click **Image** in the **Pieces** list, or drag the art from the **Art** tab onto the preview.
2. In the **Style** tab, set **Scale** (1× to 16×, or **Stretch**), **Align** and **Opacity**.

The Screen keeps a **copy** of the art: changing or deleting the art later doesn't change the Screen. **Update from
library** brings the current version. The **Pixels** line in the base shows how much the Screen's art adds up to, and
the cap.

### Export and import

| Button | What it does |
| --- | --- |
| **Export** | Copies the code of the layout (in the base) or of the picked art (in the **Art** tab). |
| **Import** | Reads the code: applies the layout (**Ctrl+Z** undoes) or makes a new art. |
| **Copy as text** | A readable form, to edit by hand or ask an AI. **Import** reads both. |

The layout takes its art along. Links to networks or storages you don't have are dropped: link them again in the
**Data** tab. There is a guide to making art and Screens with an AI on the mod's GitHub: `docs/pixel-art-ai-guide.md`.

## Images and GIFs

In the **Art** tab, **Images** part:

1. Click **Upload file** and pick a PNG, JPG or GIF (or import a **WA-IMAGEM1** code).
2. The image goes to the server and into your library.
3. Drag it onto the preview, or pick it in the **Style** tab's grid (a GIF has the ▶ mark).

| Limit | Default |
| --- | --- |
| File | Up to **8 MB** |
| Size | Up to **1024 × 1024** pixels |
| GIF | Up to **240** frames |
| Per Screen | Up to **8** different images |

The server can change these numbers. An image is kept in the world, never a link to the internet. A GIF only plays
while someone looks at the Screen.

- **Hide other players' images:** in the client config, `showOthersImages = false` swaps other players' images for a
  gray box.
- **Operators:** `/wa tela imagens` lists the world's images; `/wa tela imagem remover <hash>` takes one off every
  Screen; `/wa tela imagem banir <player>` stops someone from uploading (`desbanir` undoes it).

## Access

In the **Access** tab (only the owner and operators see it):

1. Type the name in **Invite player…** and press **Enter**. Only players who have joined the server count.
2. With FTB Teams, add a team in **Add team…**.
3. To remove someone, click the row's **✕** twice.

Whoever is on the list edits like the owner, but a new link only works to a network of whoever makes it. The links
the Screen already has stay when anyone saves.

## Recipe

<RecipeFor id="wirelessautomate:signal_screen" />

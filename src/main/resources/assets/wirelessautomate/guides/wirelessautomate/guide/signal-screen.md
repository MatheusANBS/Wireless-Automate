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

A panel you attach to a wall and stretch to any size, from **1 × 1 up to 32 × 32 blocks**. It shows your
[networks'](networks.md) numbers live: what comes in, what goes out and how many faces are moving.

<br clear="all" />

## Placing it

1. Click with the Screen on the **side of a solid block**: it sits flush against the wall, facing you. It can't go on
   the floor or the ceiling, and it drops if the wall behind it goes away.
2. Click it with an **empty hand** to open its settings. Only whoever placed it, operators and those in the **Access**
   tab can configure it; everyone else just watches.

## Choosing the size

In the **Size** tab:

| Part | How to use |
| --- | --- |
| **Width** and **Height** | ‹ and › change it 1 block at a time; with **Shift**, 4 at a time. |
| **Grows from the block toward** | ↗ up and right, ↖ up and left or ✥ center: where the block you placed sits inside the screen. |
| **Preview** | The wall around it, with the screen's area. The red cell is the one in the way. |
| **Apply** | Stretches the screen. It stays off while something is in the way. |

The big screen **takes no blocks**: it is drawn over the wall. To fit, every block of the area needs a **solid wall
behind it** and **free space in front** (air or grass, for example; a torch is in the way), and the area can't cross
another Screen.

## What to show: the editor

In the **Edit** tab you build what the Screen shows out of **pieces**. The window grows to fill the game screen; if it
doesn't fit, lower the interface scale.

| Piece | What for |
| --- | --- |
| **Value** | A live number from a [network](networks.md): what comes in, what goes out, what is stored, how many faces move... |
| **Text** | A fixed phrase. |
| **Shape** | A rectangle with a fill, border and corners: card, frame, divider. |
| **Item icon** | The picture of an item, picked by id or from the list of a linked chest. |
| **Bar** | A bar that fills with a number, lying down or standing up. |
| **Gauge** | An arc dial with a needle. |
| **Graph** | A number's history over the last minutes, as a line or columns. |
| **Arrow** | Points up, down or stays flat as the number rises, falls or holds. |
| **Image** | A pixel art of yours or an uploaded image (PNG, JPG or GIF), from the **Art** tab (see below). |

- **Picking and moving:** click a piece to pick it (on a grouped piece, the whole group; two clicks enter the group).
  **Shift** + click adds or removes from the pick; click the empty space to let go. Drag to move and pull the corner
  handle to resize. Everything snaps to a 4 px grid; hold **Alt** to adjust 1 px at a time.
- **Groups and layers:** **Group** joins the picked pieces to move and resize together; **Ungroup** lets them go.
  Below, **Layers** lists the pieces from top to bottom: drag a row to change the order, use the eye to hide, and
  **[** and **]** to raise and lower.
- **Tabs on the right:** **Data** links a Value to a network (and picks the type and what to read); **Format** sets
  decimals, abbreviating (k, M, G), sign, prefix, suffix and unit; **Style** changes position, size, colors, border,
  corners, opacity and font; **Rules** changes the color, hides or blinks when the number crosses a value.
- **Where the number comes from:** in the **Data** tab, the **Source** is the **Network** (what comes in, goes out or is stored, and how many faces move), a **Storage** linked with the [Linker](linker.md) (stored, capacity, fill, and what comes in, goes out or is left over per second; on a Chest you can read a single item) or the game **Clock** (time of day and day). On the Network, **Held** adds up what the network's Storages hold.
- **A Chest's item:** with a chest linked, the **Data** tab shows a grid of its items with a **Search items...** field, and a click picks the item. With JEI you can also **drag an item** from JEI onto the Icon's field or the Chest filter.
- **Saving:** nothing reaches the wall until you click **Save** (or **Ctrl+S**). Closing with changes takes two clicks
  on the ✕ (**Discard?**). The thumbnail shows the numbers of a new link before saving.

| Shortcut | What it does |
| --- | --- |
| **Ctrl+Z**, **Ctrl+Y** (or **Ctrl+Shift+Z**) | Undo and redo. |
| **Ctrl+C**, **Ctrl+X**, **Ctrl+V** | Copy, cut and paste, even from one Screen to another (the Images' arts come along). |
| **Ctrl+D**, **Delete** | Duplicate and remove. |
| **Ctrl+A**, **Ctrl+G**, **Ctrl+Shift+G** | Pick all, group and ungroup. |
| **Arrow keys** | Move 4 px; with **Shift**, 32 px; with **Alt**, 1 px. |

### Themes and background

With **nothing picked**, the right column shows the Screen's **base**. The **Theme** is a palette of six colors
(background, card, text, muted, high and low): there are four ready ones (Graphite, Phosphor, Amber and Porcelain) and
your own. The colors of pieces set "from the theme" (the round dots) follow the swap; the square ones are fixed. Edit
the six colors by their `#RRGGBB` code and use **Save theme** to keep the palette under a name. The **Background** can
be the theme's, a fixed color or a gradient, and you can turn on the scan **lines** and the preview **grid**. The
Screen keeps the whole palette: deleting one of your themes later changes no Screen.

### Ready layouts and your own

With nothing picked, **Ready layout** in the base offers **Energy**, **Storage**, **Network** and **Clock**: pick one and click **Apply layout**; it fits the Screen's size and replaces what is on it (undo with **Ctrl+Z** before saving). To keep your own, type a name in the field and click **Save current layout**; it then shows up in the same list, on any Screen of yours. **Remove layout** deletes your picked one, in two clicks.

### Components

In the left column, **Components** are groups of pieces ready to insert: **Network panel**, **Number card** and
**Title** come built in, and you keep your own. Click a component to insert it, in a new group, in the first free
spot. To keep your own, pick the pieces, click **Save as component** (⊕) and name it; the ✎ renames and the ✕ removes,
in two clicks. Components and themes live in your library and work on any Screen of yours. A component linked to a
network you can no longer use comes in with **—**.

To show the contents of a Chest, Battery or Tank, link it to the Screen with the [Linker](linker.md) before picking it in the **Data** tab.

The numbers arrive once a second while someone is nearby. A private network that whoever placed the Screen lost access
to shows **—**.

## Pixel art

Draw your art in the **Art** tab, **Pixel art** part, in the editor's left column. It lives in your library and works on any Screen
of yours.

1. Click **New** (or pick an art and click **Edit**). The pixel art editor opens over the Screen editor.
2. Type the name and the **Size**: 8 to 128 pixels wide and tall. Changing the size keeps the top left corner.
3. Draw and click **Save** (or **Ctrl+S**). The editor closes once the art reaches your library; if it doesn't fit
   (library full or too big), the drawing stays open, with the warning. **Cancel** goes back without saving; with
   changes, it takes two clicks.

Each art has up to **16 colors** and at most **128 × 128** pixels. **Duplicate** makes a copy and **Remove** deletes,
in two clicks.

### Tools

Left click uses the tool and **right click erases** (on lighten, it darkens). **Ctrl** + click picks a pixel's color
with any tool.

| Key | Tool |
| --- | --- |
| **B** | Pencil. |
| **E** | Eraser. |
| **G** | Bucket: fills the area of the same color. With **Shift**, swaps the color in the whole art. |
| **I** | Eyedropper. |
| **L** | Line. **Shift** locks to 0°, 45° and 90°. |
| **R** | Rectangle. **R** again toggles outline and filled; **Shift** makes a square. |
| **O** | Ellipse. **O** again toggles outline and filled; **Shift** makes a circle. |
| **D** | Lighten (left) and darken (right), along the color's ramp. |
| **T** | Dither: paints every other pixel, in a checkerboard. |
| **S** | Selection. |

| Key | Helper |
| --- | --- |
| **[** and **]** | Smaller and larger brush, 1 to 8 px. The footer button swaps square and round (round from 3 px). |
| **M** and **Shift+M** | Mirror left and right, and top and bottom. They work together. |
| **P** | Pixel perfect: removes the L corners of the 1 px pencil. |
| **X** | Back to the previous color. |
| **Ctrl+G** | Turns the grid on and off. |
| **Ctrl+Z**, **Ctrl+Y** | Undo and redo, up to 100 steps. |

### Palette and ramps

The palette has 16 colors in 4 rows, and color 0 is always transparent. Each row is a **ramp**, from the dark shade on
the left to the light one on the right: lighten and darken walk along it.

- Two clicks on a color (or **Edit color**) open the picker. Changing a color changes all its pixels.
- **Make ramp** redoes the chosen color's row as its own ramp: darker and bluer to the left, lighter and yellower to
  the right.

### Selection, flip and rotate

- With **S**, drag to mark a rectangle. Drag from inside it to move the piece.
- **Arrow keys** move 1 px (with **Shift**, 8 px). **Enter** settles and **Esc** undoes the move and drops the
  selection.
- **Ctrl+A** selects all and **Ctrl+D** drops it. **Ctrl+C**, **Ctrl+X** and **Ctrl+V** copy, cut and paste;
  **Delete** erases.
- With a selection, tools only paint inside it.
- **Shift+H** and **Shift+V** flip horizontally and vertically, and the rotate button turns 90° clockwise. With a
  selection, only the piece.
- **Clear** erases the whole art, in two clicks.

### Moving around the canvas

| Gesture | What it does |
| --- | --- |
| Mouse wheel | Zoom at the mouse. |
| **Alt**, **Space** or middle button + drag | Moves the view. |
| **Arrow keys**, with no selection | Move the view. |
| **0** | Fits the art. |
| **+** and **−** | Zoom at the center. |
| Click on the preview | Takes the view to that spot. |

The footer shows the position and color under the mouse. The right column shows the art at 1×, 2× and tiled, as in the
mosaic (with little room or a large art, only what fits whole). **Esc** with no selection does the same as **Cancel**.

### Putting the art on the Screen

- **Image:** click **Image** in the **Pieces** list (or drag it onto the preview). It starts with the art picked in
  the **Art** tab (a pixel art or an image); with none picked, it starts empty, with **Pick the art or image**.
- **Picking the art:** in the **Style** tab, **Piece** section, click a thumbnail in the grid. The ones marked **On
  this screen** came with the Screen, from another player or from a code. Dragging an art from the **Art** tab
  onto the preview works too.
- In the same section: **Scale** (1× to 16×, or **Stretch**), **Align** (9 spots) and **Opacity**.
- The Screen keeps a **copy** of the art: changing or deleting the art later doesn't change the Screen. **Update from
  library** brings the current version.
- **Tiled background:** with nothing picked, set the **Background** to **Tiled pixel art** and pick the art in the
  grid. It repeats over the whole Screen, under the pieces, with **Scale** (1× to 8×) and **Opacity**.
- The **Pixels** line in the base shows how much the Screen's art adds up to, and the cap.

### Export and import by code

Take a layout or an art to another world, or to a friend, as text.

| Button | What it does |
| --- | --- |
| **Export** | Copies the code: in the base (under **Apply layout**), of the layout; in the **Art** tab, of the picked art. |
| **Import** | Reads the copied code. In the base, applies the layout to the draft (**Ctrl+Z** undoes); in the **Art** tab, makes a new art. An image code (**WA-IMAGEM1**) becomes an image upload. |
| **Copy as text** | Copies in a readable form, to edit by hand or ask an AI. **Import** reads both forms. |

- The layout takes its art along. Links to networks or storages you don't have are dropped: link them again in the
  **Data** tab.
- An invalid code shows a warning in the bottom bar, and nothing changes.
- To make art and Screens with an AI, there is a guide on the mod's GitHub: `docs/pixel-art-ai-guide.md`.

## Images and GIFs

Besides pixel art, the Screen shows real photos and animated GIFs, in the **Images** part of the **Art** tab.

1. Click **Upload file** and pick a PNG, JPG or GIF on your computer. Or copy a **WA-IMAGEM1** code and click
   **Import**.
2. The image goes to the server and into your library with the file's name. **Rename** (or double-click the row)
   changes the name, and **Remove**, with two clicks, takes it out of the library.
3. Drag the image onto the preview to make an **Image**, or pick it in the grid of the **Style** tab: images come
   after the art, under the **Images** label, and a GIF has the ▶ mark. The **Tiled background** takes images too.

| Limit | Default |
| --- | --- |
| File | Up to **8 MB** |
| Size | Up to **1024 × 1024** pixels |
| GIF | Up to **240** frames |
| Per Screen | Up to **8** different images |

The server can change these numbers. An image is always a file uploaded and kept in the world, never a link to the
internet.

- **GIF:** plays at the file's pace, only while someone looks at the Screen. Two Screens with the same GIF play
  together.
- **Copy code:** puts the image on the clipboard as **WA-IMAGEM1**, to take it to another world. A layout's code takes
  its images along when they fit; otherwise only the references, which work on the same server.
- **Hide other players' images:** in the client config (`config/wirelessautomate-client.toml`, `tv` section),
  `showOthersImages = false` swaps the images other players uploaded for a gray box. Yours stay.
- **Operators:** `/wa tela imagens` lists the world's images, with the size, who uploaded and how many Screens use
  them. `/wa tela imagem remover <hash>` takes an image off every Screen, and `/wa tela imagem banir <player>` stops
  someone from uploading (`desbanir` undoes it). The `tv.images` section of the server config turns images off and
  limits who uploads and the space.

## Access

In the **Access** tab (only the owner and operators see it), give other players the right to edit the Screen.

1. Type the name in **Invite player…** and pick a suggestion, or press **Enter**. Only players who have joined the
   server count.
2. With FTB Teams installed, add a team in **Add team…**.
3. To remove someone, click the row's **✕** twice.

Whoever is on the list edits like the owner: size, layout and links, within the networks each one can use. The links
the Screen already has stay when anyone saves, even to a network they can't use; a new link only works to a network
of whoever makes it. Watching the Screen stays open to everyone.

## Recipe

<RecipeFor id="wirelessautomate:signal_screen" />

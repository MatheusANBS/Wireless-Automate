# Pixel art and Screens with an AI

*[Versão em português](guia-pixel-art-com-ia.md)*

This guide teaches a chat AI (Claude, ChatGPT and others) the text that Wireless Automate's **Signal Screen** reads.
With it, the AI draws a pixel art (still or animated), prepares a real image or GIF, or builds a whole Screen, and you
paste the result into the game.

## Which format to use

| You want | Use | Colors | Size |
| --- | --- | --- | --- |
| A logo, an icon, a crisp pixel drawing you'll still edit in the game | **Pixel art** (`WA-ARTE1`) | Up to 16 per art, any you like | Up to 128 × 128 |
| A pixel art that moves | **Animated pixel art** (`WA-ARTE1` with frames) | Up to 16 per art | Up to 128 × 128, up to 64 frames |
| A photo, a painting, any image with every color, or a GIF | **Image** (`WA-IMAGEM1`) | **All** (free color) | Up to 1024 × 1024, GIF up to 240 frames |
| A whole Screen, with texts, numbers and images | **Layout** (`WA-TELA1`) | Theme colors or any | The Screen's area |

The 16-color limit applies **only to pixel art**: it's what keeps it crisp and editable in the game, with the ramps.
For full fidelity (the Mona Lisa as it is, a photo), use an **image**.

## How to use it

1. Copy this whole file and paste it into the chat with the AI, before your request.
2. Ask for what you want: "a 32 × 32 sword" or "a 4 × 2 block Screen titled Base with a clock".
3. Copy **only the code text** from the answer, from the `WA-ARTE1`, `WA-IMAGEM1` or `WA-TELA1` line to the end. Without the
   backticks (```) and without the text around it.
4. In the game, open the Screen, **Edit** tab:
   - **an art:** **Art** tab, **Pixel art** section, **Import** button. The art goes into your library.
   - **an image or a GIF:** **Art** tab, **Images** section, **Import** button. The image is uploaded to the server and
     goes into your library. (An image you already have on your computer is easier through the **Upload file** button,
     same place.)
   - **a layout:** with nothing picked, the **Import** button in the base, under **Apply layout**. The layout goes into
     the draft (**Ctrl+Z** undoes, one import at a time) and only reaches the wall on **Save**.
5. If the code has a mistake, the editor's bottom bar says which one and where, and nothing changes: in a standalone
   art, the line of the text; in an art inside the JSON, the art's name and the pixel row. Send the warning back to
   the AI to fix.

**Tip:** the **Copy as text** button exports your art or your Screen in this same form. Paste it into the chat so the
AI starts from what you already have (with your networks already linked).

The first line that isn't blank must be exactly `WA-ARTE1`, `WA-IMAGEM1` or `WA-TELA1` (the headers are the same in
every language). The text of an art or a layout can be up to 524,288 characters (512 K); an image's, up to the allowed
file size in base64 (11,483,244 characters with the default 8 MB).

## Pixel art (`WA-ARTE1`)

```
WA-ARTE1
name: Logo
size: 16x16
palette: - 1B1920 7A7366 ECE5D6
# a comment
......3333......
(15 more lines of 16 characters)
```

- **First line:** `WA-ARTE1`.
- **Keys**, one per line, `key: value`, before the pixel lines. They work in English or Portuguese, in any case:

  | Key | In Portuguese | Value |
  | --- | --- | --- |
  | `name` | `nome` | The art's name, up to 32 characters (the rest is cut). Without a name, the imported art comes in as "New art". |
  | `size` | `tamanho` | `WIDTHxHEIGHT`, like `16x16` (also `16 x 16`, `16×16` or `16*16`). From **8 to 128** per side. Required. |
  | `palette` | `paleta` | Up to **16 colors**, separated by spaces. |

  Any other key is an error ("unknown key").
- **Palette:**
  - the position in the list is the color's index: the first is 0, the second is 1, up to 15 (`f`);
  - **color 0 is always transparent**: write `-` (`.`, `transparent`, `transparente` or a color, which is ignored,
    also work);
  - the others are `RRGGBB` (opaque) or `RRGGBBAA` (alpha at the end, `00` transparent and `FF` opaque), with an
    optional `#`: `C2442A`, `#C2442A`, `C2442A80`;
  - missing colors (with fewer than 16 in the list) are black.
- **Pixels:** after the keys, **one line per row**, top to bottom:
  - **one character per pixel**, left to right: the color's index in hexadecimal, `0`–`9` and `a`–`f` (uppercase works
    too);
  - `.` is the same as `0`, transparent; use it so the background is easy to read;
  - spaces inside the line don't count;
  - each line has exactly **width** pixels, and there are exactly **height** lines.
- **Comments:** a line starting with `#` (after the first one) is ignored, and so are blank lines. Don't put `#` or `:`
  in a pixel line.

On import, the art gets a new id and goes into your library (which has a limit of arts; 32 by default).

## Animated pixel art (frames)

An art can have several **frames**, all the same size and with the same palette. It plays in a loop on the Screen (in
the Image piece and the tiled background). An art without frames is a still image, as above.

```
WA-ARTE1
name: Blinking signal
size: 16x16
palette: - 1B1920 7A7366 ECE5D6 8E2A35 C2442A F0603E FFB08A
frames: 2
speed: 4
mode: loop
frame 1:
(16 lines of 16 characters)
frame 2:
(16 lines of 16 characters)
```

- **Extra keys** (before the first frame):

  | Key | In Portuguese | Value |
  | --- | --- | --- |
  | `frames` | `quadros` | How many frames, **1 to 64**. Must match the blocks below. |
  | `speed` | `velocidade` | Frames per second, **1 to 20** (default 6). |
  | `mode` | `modo` | `loop` (back to the first) or `pingpong` (`vaivem`: back and forth). Default `loop`. |

- **Each frame** starts with a `frame N:` line (`quadro N:`), N from 1, in order, followed by that frame's pixel lines,
  with the same rules as a still art.
- Without the `frames` key, the pixel lines come straight away, with no `frame 1:`: it's a still art.
- **Limit:** frames count toward the Screen's pixel limit. A 32 × 32 art with 10 frames counts 10,240 pixels.
- **Tips:** change little from one frame to the next; 2 to 8 frames already animate an icon well; 6 to 12 frames per
  second is the classic pixel art pace; for a blink, repeat the still frame and put the blink in 1 or 2 frames.
- **In the JSON (`images`)**, an animated art swaps `rows` for `frames` (`quadros`), a list of frames where each frame is
  a list of rows, and may have `speed` and `mode`.

## Images and GIFs (`WA-IMAGEM1`)

For a photo, a drawing with many colors or an animated GIF, use a **real image** instead of pixel art: PNG, JPG or GIF,
up to **1024 × 1024** pixels, up to **8 MB** and, for a GIF, up to **240 frames** (the server may change these limits
or turn images off).

```
WA-IMAGEM1
name: Mona Lisa
format: png
iVBORw0KGgoAAAANSUhEUgAAA...
(the whole file in base64, on one line or many)
```

- **Keys:** `name` (`nome`), up to 32 characters (without a name it comes in as "New image"), and `format` (`formato`):
  `png`, `jpg` (or `jpeg`) or `gif`, which must match the file.
- **After the keys**, the file in **base64** (standard or "URL-safe", with or without the trailing `=`). Line breaks and
  spaces don't count; lines starting with `#` are comments.
- **Only an AI that runs code** (like ChatGPT with data analysis or Claude with code execution) can do this: the base64
  comes from a script, not from memory. A text-only AI should make a pixel art instead.

**Python recipe (with Pillow), for an image:**

```python
from PIL import Image
import base64, io

img = Image.open("input.jpg").convert("RGB")     # use "RGBA" if the image has transparency
img.thumbnail((1024, 1024))                      # shrinks keeping the aspect ratio, up to 1024 on the longer side
buf = io.BytesIO()
img.save(buf, "JPEG", quality=90)                # photo: JPG is smaller; drawing or transparency: "PNG"
data = base64.b64encode(buf.getvalue()).decode()
with open("image.txt", "w") as f:
    f.write("WA-IMAGEM1\nname: My image\nformat: jpg\n")
    for i in range(0, len(data), 120):
        f.write(data[i:i + 120] + "\n")
```

**For an animated GIF:**

```python
from PIL import Image
import base64, io

frames = [Image.open(f"frame_{i}.png").convert("RGBA") for i in range(12)]
frames = [f.resize((256, 256)) for f in frames]
buf = io.BytesIO()
frames[0].save(buf, "GIF", save_all=True, append_images=frames[1:], duration=100, loop=0, disposal=2)
data = base64.b64encode(buf.getvalue()).decode()
# write it as above, with "format: gif"
```

- The text is too big to copy from the chat: ask the AI for the `image.txt` file, open it and copy everything.
- **Size on the Screen:** the Screen has 32 drawing pixels per block but draws the image at high resolution; a
  1024 × 1024 image looks sharp on a Screen of up to 8 × 8 blocks. Smaller than that, it's scaled down smoothly.
- **GIF:** fewer frames and fewer colors keep the file small; 10 to 15 frames per second is enough. The GIF plays on the
  Screen at its own pace.
- **Respect the server:** images are seen by everyone passing by; the server owner can remove them or turn them off.

## Screen (`WA-TELA1`)

The first line is `WA-TELA1` and the rest is **one JSON object** with the layout. It is the same JSON the base's
**Copy as text** exports.

### The area

The Screen is drawn in **drawing pixels: 32 per block**. A 3 × 2 block Screen is 96 × 64; the corner (0, 0) is the top
left, `x` grows to the right and `y` downward.

| Key | Value |
| --- | --- |
| `area_w`, `area_h` | The area the layout was made for (blocks × 32). If the Screen it is pasted on has another size, the pieces scale. Without them, nothing scales, and a piece that leaves the area is shrunk and brought back inside. |
| `keep_theme` | `true` keeps the Screen's current theme. Without it (or `false`), the JSON's `theme` applies, or the Graphite theme if the JSON has none. |
| `theme` | The theme (below). |
| `background` | The background (below). |
| `scanlines` | `true` draws scan lines over everything. |
| `pieces` | The list of pieces. The first is at the bottom and the last on top. |
| `images` | The pixel arts the Image pieces and the background use (below). |

### Colors

The color fields (`color`, `background`, `border`, `track`, `upColor`, `downColor` and `flatColor` in `style`; `color`
and `background` in rules; the theme's six colors; the background's `top` and `bottom`) accept:

- `"#RRGGBB"` or `"#RRGGBBAA"` (alpha at the end);
- **the theme role**, so the color follows the Screen's theme: `"theme:background"` (or `"theme:bg"`),
  `"theme:card"`, `"theme:text"`, `"theme:muted"`, `"theme:high"`, `"theme:low"`, or in Portuguese `"tema:fundo"`,
  `"tema:cartao"`, `"tema:texto"`, `"tema:apagado"`, `"tema:alta"`, `"tema:baixa"`;
- `"transparent"` (or `"transparente"`).

A color that doesn't read gives the "Invalid color" error. Prefer theme roles: the Screen stays good-looking when the
player swaps the theme.

### The piece

Every piece has:

| Field | Value |
| --- | --- |
| `type` | `text`, `value`, `shape`, `icon`, `bar`, `gauge`, `graph`, `arrow` or `image`. Required. |
| `x`, `y`, `w`, `h` | The box, in drawing pixels. |
| `name` | The name in the Layers list (up to 32 characters). |
| `group` | A number: pieces with the same number (above 0) form a group. |
| `hidden` | `true` hides the piece. |
| `bind` | Where the number comes from (only `value`, `bar`, `gauge`, `graph` and `arrow`). |
| `format` | How the Value writes the number. |
| `style` | The look. |
| `rules` | Up to 8 rules. |

A piece that doesn't read (wrong type, a field with the wrong kind of value) gives the "Piece N does not read" error,
with its number in the list, counting from 1.

**`style`, common to all:**

| Field | Value (default) |
| --- | --- |
| `color` | The color of the text, number or drawing (`#ECE5D6`). |
| `background` | The box's fill (transparent). |
| `border`, `border_width` | The border's color and thickness, 0 to 4 (no border). |
| `radius` | Rounded corners, 0 to 8 (0). |
| `opacity` | 0 to 100 (100). |
| `size` | The text height in drawing pixels, 1 to 128 (8). |
| `fit` | `true` shrinks the text to fit the box; `false` cuts it (`true`). |
| `align` | `left`, `center` or `right` (`left`). On the Image, the horizontal alignment. |
| `font` | `minecraft`, `pixel`, `terminal`, `mono` or `sans` (`minecraft`). |
| `text` | The fixed text of the Text piece (up to 128 characters). |

**The types:**

| Type | What for | Own fields in `style` (default) |
| --- | --- | --- |
| `text` | A fixed phrase. | `text`. |
| `value` | A live number. | Uses `format` and `bind`. |
| `shape` | A rectangle: card, frame, divider. | Only `background`, `border`, `border_width` and `radius`. |
| `icon` | An item, fluid or chemical. | `item`: the id, like `"minecraft:diamond"`; `icon`: `{"kind": "item"}`, `"fluid"` or `"chemical"`. |
| `bar` | A bar that fills. | `direction`: `horizontal` or `vertical`; `reverse`; `min` and `max` (with `max` 0, full comes from the data); `track`: the track color. |
| `gauge` | An arc gauge. | `sweep`: 90 to 360 degrees (270); `thickness`: 1 to 32 (5); `min`, `max` and `track`, as on the bar. |
| `graph` | The number's history. | `window`: 1 to 10 minutes (1); `graphMode`: `line` or `columns`; `area`: the area under the line (`true`). |
| `arrow` | Points up, down or stays flat. | `upColor` (`theme:high`), `downColor` (`theme:low`), `flatColor` (`theme:muted`); `deadZone`; `reverse`. |
| `image` | A pixel art or an image. | `image`: the art's `id` in `images`; `scale`: 1 to 16, or 0 to stretch to the box (1); `valign`: `top`, `middle` or `bottom` (`middle`). |

On the Image, the art shows at its size times `scale`, aligned by `align` and `valign` and cut by the box. A 16 × 16
art at `scale` 2 needs a 32 × 32 box.

**`format` (Value only):** `decimals` 0 to 3 (1); `abbreviate`, which writes k, M and G (`true`); `sign`, the + on
positives (`false`); `prefix` and `suffix`, up to 32 characters; `unit`: `none`, `second`, `tick` or `minute`
(`second`).

**`rules`:** each rule is `{"op": "lt", "value": 10, "color": "theme:low"}`, with `op` `lt` (less), `gt` (greater)
or `eq` (equal), `value` (the number), and the effect: `color` (the color of the text or drawing), `background`,
`blink: true` or `hide: true`. A rule can have its own `bind`; without it, it reads the piece's number.

### Links (`bind`)

A piece's number comes from a source:

- **The game clock**, which always works: `{"source": "clock", "metric": "time"}` (the time, HH:MM) or
  `"metric": "day"` (the day).
- **Network:** `{"source": "network", "network": "<network id>", "type": "energy", "metric": "in"}`, with `type`
  `item`, `fluid`, `energy`, `chemical` or `source` and `metric` `in`, `out`, `stored`, `released`, `balance`,
  `moving`, `empty`, `waiting`, `full`, `redstone`, `held`, `held_capacity` or `held_fill`.
- **Storage** linked to the Screen with the Linker: `{"source": "storage", "pos": ..., "metric": "stored"}`.

The AI doesn't know your networks' ids or your storages' positions. **On import, links to networks and storages you
don't have are dropped** (the piece stays, without data), and the editor says how many were dropped. Link them again
in the **Data** tab. So ask the AI for the data pieces without `bind` (or with the clock), or start from a **Copy as
text** of your Screen, which already has the right ids.

### Theme and background

- **`theme`:** `{"name": "My theme", "bg": "#0F1117", "card": "#1A1C24", "text": "#ECE5D6", "muted": "#8D8577",
  "high": "#3DB56E", "low": "#F26A48"}`. A missing color comes from the Graphite theme. The name is up to 24
  characters. The theme's colors are fixed colors: a theme role (`"theme:text"`) in a `theme` field is an error.
- **`background`:** `{"kind": "theme"}` (the theme's background), `{"kind": "solid", "top": "#1B1920"}`,
  `{"kind": "gradient", "top": "...", "bottom": "..."}` or the mosaic,
  `{"kind": "tiled", "image": "<art id>", "scale": 2, "opacity": 100}`, with `scale` 1 to 8 and `opacity` 0 to 100.
  The mosaic repeats the art over the whole Screen, under the pieces.

### Arts inside the JSON (`images`)

Each art is an object with the same parts as the `WA-ARTE1` form:

```
{"id": "heart", "name": "Heart", "size": "16x16",
 "palette": ["-", "1B1920", "C2442A"],
 "rows": ["................", "..."]}
```

- `id`: any text. The Image (`style.image`) and the mosaic (`background.image`) point to the art by it. Without `id`,
  the name is used.
- `name`/`nome`, `size`/`tamanho`, `palette`/`paleta` (a list, or one text with the colors separated by spaces) and
  `rows`/`linhas` (a list with one row per text), with the same rules as the art.
- The JSON rows have no comments: each text is a row.
- An art that no piece and no background uses is dropped on import. An Image pointing to an `id` that isn't in
  `images` stays without art (the dashed rectangle in the editor): pick its art in the **Style** tab.

### Caps

| What | Cap (server default) |
| --- | --- |
| Pixel art | From 8 × 8 to 128 × 128 pixels, 16 colors per art. |
| Pixels of a Screen's arts | 131,072 in total, counting each distinct art once and every frame (eight 128 × 128 arts). |
| Frames per art | 64, 1 to 20 per second. |
| Uploaded image | PNG, JPG or GIF, up to 1024 × 1024, 8 MB and 240 frames. |
| Distinct images per Screen | 8. |
| Pieces per Screen | 128. |
| Distinct links per Screen | 64. |
| Layout without the pixels | 16 KB of NBT. |
| Rules per piece | 8. |
| Distinct arts per Screen | 256. |
| Code text | 524,288 characters (512 K). |

Over a cap, the editor says "Too big for this screen" and nothing changes (the piece and link caps are checked on
**Save**).

## Tips for good-looking results

**In the art:**

- **Few colors.** 4 to 8 already make a nice pixel art; 16 is the pixel art maximum (for more colors, use an image).
- **4-shade ramps.** Arrange the palette in rows of 4 colors, dark to light: colors 0 to 3, 4 to 7, 8 to 11 and 12 to
  15 (the first row starts at transparent). That is how the game's editor arranges the palette, and lighten and darken
  walk along those rows.
- **Hue shifting.** Along the ramp, the dark shade leans toward blue or purple and gets more saturated; the light one
  leans toward yellow and gets less saturated. It looks livelier than just darkening the same color.
- **Outline.** A 1 px line around the shape, in a dark shade of the ramp (not pure black), sets the art apart from the
  background.
- **Dithering.** For a gradient with few colors, alternate two colors in a checkerboard in the band between them.
- **Light from one side.** Pick where the light comes from (top left, for example) and put the light shades on that
  side.
- **No stray pixels.** 1 px lines without double steps and curves in regular steps.

**On the Screen:**

- **Align to the 32 px grid** (one block) and use multiples of 4 for positions and sizes, like the editor.
- **Theme colors:** use roles (`theme:text`, `theme:card`...) instead of fixed colors.
- **Size hierarchy:** a big title, medium numbers and small labels; few font sizes.
- **Cards:** a `shape` with `background` `theme:card` and a `radius` behind a group of pieces organizes the Screen.
- **Margin:** leave about 4 px between the pieces and the Screen's edge.
- **Image at a whole scale:** make the box equal to the art's size times `scale`, so nothing blurs or gets cut.

## Tested examples

Both are pasted by the mod's automated tests and read without errors. They use the Portuguese keys (`nome`,
`tamanho`, `paleta`, `linhas`); the English ones work the same.

**A 16 × 16 art:**

```
WA-ARTE1
nome: Coração
tamanho: 16x16
paleta: - 1B1920 7A7366 ECE5D6 8E2A35 C2442A F0603E FFB08A
# 1 é o contorno, 4 e 5 o vermelho, 6 e 7 o brilho
................
................
...1111..1111...
..155551155551..
.15677555555551.
.15775555555551.
.15755555555551.
.15555555555541.
..155555555541..
...1555555541...
....15555541....
.....155541.....
......1541......
.......11.......
................
................
```

(The comment line says: 1 is the outline, 4 and 5 the red, 6 and 7 the shine.)

**A 3 × 2 block layout with Text, Value (the clock's time) and the heart's Image:**

```
WA-TELA1
{
  "area_w": 96,
  "area_h": 64,
  "background": {"kind": "solid", "top": "#1B1920"},
  "pieces": [
    {"type": "text", "x": 4, "y": 4, "w": 88, "h": 10,
     "style": {"text": "Base norte", "color": "tema:texto", "size": 8}},
    {"type": "value", "x": 4, "y": 20, "w": 56, "h": 16,
     "bind": {"source": "clock", "metric": "time"},
     "style": {"color": "#FFB08A", "size": 12}},
    {"type": "image", "x": 60, "y": 28, "w": 32, "h": 32,
     "style": {"image": "coracao", "scale": 2}}
  ],
  "images": [
    {"id": "coracao", "nome": "Coração", "tamanho": "16x16",
     "paleta": ["-", "1B1920", "7A7366", "ECE5D6", "8E2A35", "C2442A", "F0603E", "FFB08A"],
     "linhas": [
       "................",
       "................",
       "...1111..1111...",
       "..155551155551..",
       ".15677555555551.",
       ".15775555555551.",
       ".15755555555551.",
       ".15555555555541.",
       "..155555555541..",
       "...1555555541...",
       "....15555541....",
       ".....155541.....",
       "......1541......",
       ".......11.......",
       "................",
       "................"
     ]}
  ]
}
```

## Ready-made prompt

Paste this guide and then:

> Using the format above, make a `WA-ARTE1` pixel art of **[size, like 32x32]** of **[what you want]**, with at most
> **[8]** colors arranged in 4-shade ramps with hue shifting and a dark outline. Check that every line is exactly the
> width and that there are exactly height lines. Answer only with the code, in a code block.

For a Screen:

> Using the format above, build a `WA-TELA1` layout for a **[width × height]** block Screen showing **[what to
> show]**. Use theme-role colors, align to the 32 px grid and leave the data pieces without `bind` (I'll link them in
> the game). Answer only with the code, in a code block.

For an animation:

> Using the format above, make an animated `WA-ARTE1` pixel art of **[size]** with **[how many]** frames of
> **[what happens]**, at **[6]** frames per second in a loop, changing little from one frame to the next. Check the
> lines and columns of every frame. Answer with the code only, in a code block.

For an image (AI that runs code):

> Take the image **[attached / described]**, shrink it to fit 1024 × 1024 keeping the aspect ratio and generate the
> `WA-IMAGEM1` code with this guide's Python recipe, in an `image.txt` file for me to download.

"""Tanque de Source v3: silhueta fina de jarra (como a Source Jar do Ars), no visual do mod (metal
escuro do roteador, filetes na cor do tier), Source roxa do Ars e uma gema no topo. Texturas
desenhadas aqui; só a paleta da Source vem do Ars."""
import base64
import io
import json
import sys
from pathlib import Path

from PIL import Image, ImageDraw

sys.path.insert(0, sys.argv[1])
import gerar_texturas as gt  # noqa: E402

OUT = Path(sys.argv[2])
OUT.mkdir(parents=True, exist_ok=True)
rgb = gt.rgb
CASCO = gt.PALETAS["casco"]  # 4 claro .. 0 escuro
SRC = {"hi": "#d9a6f5", "top": "#b36de0", "a": "#9b4dc6", "b": "#9345be", "c": "#843aae", "deep": "#6b2f8f",
       "sp": "#ea8ef3", "w": "#fdd9f1"}
GEM = {"A": "#f3c2fa", "B": "#ea8ef3", "C": "#b36de0", "D": "#8a55d3", "E": "#6b197d", "W": "#fffbe8"}
VIDRO = {"V": "#e6f6f7", "v": "#b9dde3", "e": "#7fb3c0"}


def nova(cor=None):
    return Image.new("RGBA", (16, 16), rgb(cor) if cor else (0, 0, 0, 0))


def tier(t):
    return {k: rgb(v) for k, v in gt.PALETAS[f"roteador_{t}"].items()}


def metal_lado(t, tampa=False):
    """Metal escuro com costura no meio e o filete na cor do tier (em cima na base, embaixo na tampa)."""
    img = nova(CASCO["2"])
    tc = tier(t)
    for x in range(16):
        for y in range(16):
            img.putpixel((x, y), rgb(CASCO["2"] if (x + y) % 7 else CASCO["3"]))
    if tampa:  # tampa: y 13..15 -> v 0..2 (linha 2 = borda de baixo)
        for x in range(16):
            img.putpixel((x, 0), rgb(CASCO["4"]))
            img.putpixel((x, 1), rgb(CASCO["2"]))
            img.putpixel((x, 2), tc["3"])
    else:  # base: y 0..2 -> v 13..15 (linha 13 = borda de cima)
        for x in range(16):
            img.putpixel((x, 13), tc["3"])
            img.putpixel((x, 14), rgb(CASCO["2"]))
            img.putpixel((x, 15), rgb(CASCO["0"]))
        for x in (5, 10):  # parafusos
            img.putpixel((x, 14), rgb(CASCO["4"]))
    return img


def metal_topo(t, tampa=False):
    img = nova(CASCO["2"])
    tc = tier(t)
    for x in range(3, 13):
        img.putpixel((x, 3), tc["4"])
        img.putpixel((x, 12), tc["1"])
    for y in range(3, 13):
        img.putpixel((3, y), tc["3"])
        img.putpixel((12, y), tc["1"])
    if tampa:
        # arcos wireless pequenos e o anel da gema
        for (x, y, c) in ((5, 5, "4"), (6, 4, "4"), (9, 4, "3"), (10, 5, "2"),
                          (5, 10, "2"), (6, 11, "1"), (9, 11, "1"), (10, 10, "0")):
            img.putpixel((x, y), tc[c])
        for x in range(6, 10):
            for y in range(6, 10):
                img.putpixel((x, y), rgb(CASCO["0"] if 7 <= x <= 8 and 7 <= y <= 8 else CASCO["4"]))
    return img


def trilho_leste(t):
    img = nova(CASCO["1"])
    for y in range(16):
        img.putpixel((7, y), rgb(CASCO["2"]))
    return img


def trilho(t):
    tc = tier(t)
    img = nova()
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), tc["3"] if x in (4, 11) else rgb(CASCO["1"]))
    return img


def vidro():
    img = nova()
    for y in (4, 5, 6, 9):
        img.putpixel((6, y), rgb(VIDRO["V"] if y < 6 else VIDRO["v"]))
    img.putpixel((7, 4), rgb(VIDRO["v"]))
    img.putpixel((9, 10), rgb(VIDRO["v"]))
    for x in range(5, 11):
        img.putpixel((x, 3), rgb(VIDRO["e"]))
    return img


def liquido():
    img = nova()
    for y in range(16):
        for x in range(16):
            base = SRC["a"] if (x * 3 + y * 5) % 4 else SRC["b"]
            if y >= 13:
                base = SRC["c"] if y == 13 else SRC["deep"]
            img.putpixel((x, y), rgb(base))
    for x, y in ((6, 4), (9, 7), (7, 10), (8, 2), (10, 11), (6, 8)):
        img.putpixel((x, y), rgb(SRC["sp"]))
    img.putpixel((7, 6), rgb(SRC["w"]))
    return img


def superficie():
    img = nova(SRC["top"])
    for x, y in ((5, 6), (8, 9), (10, 5), (6, 10), (9, 7)):
        img.putpixel((x, y), rgb(SRC["hi"]))
    img.putpixel((7, 7), rgb(SRC["w"]))
    return img


def gema():
    img = nova()
    pad = ["ABBW", "BBCA", "CCDB", "DDEC", "EDDC"]
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), rgb(GEM[pad[(y + x // 2) % 5][(x + y) % 4]]))
    return img


def pescoco(t):
    tc = tier(t)
    img = nova(CASCO["3"])
    for x in range(16):
        img.putpixel((x, 1), tc["4"] if x % 2 else rgb(CASCO["4"]))
    return img


def modelo(t, nivel):
    T = {"base_lado": metal_lado(t), "base_topo": metal_topo(t), "tampa_lado": metal_lado(t, True),
         "tampa_topo": metal_topo(t, True), "trilho": trilho(t), "trilho_l": trilho_leste(t), "vidro": vidro(), "liq": liquido(),
         "sup": superficie(), "gema": gema(), "pescoco": pescoco(t), "pescoco_topo": metal_topo(t, True)}
    trl = {"up": "trilho_l", "south": "trilho", "east": "trilho_l"}
    cx = [
        ((3, 0, 3), (13, 2, 13), {"up": "base_topo", "south": "base_lado", "east": "base_lado"}),
        ((4, 2, 4), (5, 13, 5), trl), ((11, 2, 4), (12, 13, 5), trl), ((4, 2, 11), (5, 13, 12), trl),
    ]
    if nivel > 0:
        h = 2 + max(1, round(nivel * 11 / 10))
        cx.append(((5, 2, 5), (11, h, 11), {"up": "sup", "south": "liq", "east": "liq"}))
    cx += [
        ((4, 2, 4), (12, 13, 12), {"up": None, "south": "vidro", "east": "vidro"}),
        ((11, 2, 11), (12, 13, 12), trl),
        ((3, 13, 3), (13, 14, 13), {"up": "tampa_topo", "south": "tampa_lado", "east": "tampa_lado"}),
        ((5, 14, 5), (11, 15, 11), {"up": "pescoco_topo", "south": "pescoco", "east": "pescoco"}),
        ((7, 15, 7), (9, 18, 9), {"up": "gema", "south": "gema", "east": "gema"}),
    ]
    return cx, T


def render(caixas, T, s=9):
    W, H = 24 * s, 34 * s
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    cx0, cy0 = W // 2, 21 * s

    def Pp(x, y, z):
        return (cx0 + (x - z) * s, cy0 + (x + z - 16) * s // 2 - y * s)

    def quad(pts, cor):
        d.polygon([Pp(*p) for p in pts], fill=cor[:3])

    def sh(c, f):
        return (int(c[0] * f), int(c[1] * f), int(c[2] * f), 255)

    for (x0, y0, z0), (x1, y1, z1), f in caixas:
        if f.get("south"):
            t = T[f["south"]]
            for x in range(x0, x1):
                for y in range(y0, y1):
                    c = t.getpixel((x % 16, (15 - y) % 16))
                    if c[3]:
                        quad([(x, y + 1, z1), (x + 1, y + 1, z1), (x + 1, y, z1), (x, y, z1)], sh(c, 0.84))
        if f.get("east"):
            t = T[f["east"]]
            for z in range(z0, z1):
                for y in range(y0, y1):
                    c = t.getpixel(((15 - z) % 16, (15 - y) % 16))
                    if c[3]:
                        quad([(x1, y + 1, z + 1), (x1, y + 1, z), (x1, y, z), (x1, y, z + 1)], sh(c, 0.66))
        if f.get("up"):
            t = T[f["up"]]
            for x in range(x0, x1):
                for z in range(z0, z1):
                    c = t.getpixel((x % 16, z % 16))
                    if c[3]:
                        quad([(x, y1, z), (x + 1, y1, z), (x + 1, y1, z + 1), (x, y1, z + 1)], c)
    return img


def b64(img, scale=1):
    if scale != 1:
        img = img.resize((img.width * scale, img.height * scale), Image.NEAREST)
    buf = io.BytesIO()
    img.save(buf, "PNG")
    return "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode()


dados = {"tiers": {}, "niveis": {}, "texturas": {}}
for t in gt.TIERS_ROTEADOR:
    im = render(*modelo(t, 6))
    dados["tiers"][t] = b64(im)
    im.save(OUT / f"jar_{t}.png")
for n in (0, 2, 5, 8, 10):
    dados["niveis"][str(n)] = b64(render(*modelo("elite", n), s=6))
_, T = modelo("advanced", 5)
for nome in ("base_lado", "base_topo", "tampa_lado", "tampa_topo", "trilho", "vidro", "liq", "sup", "gema"):
    dados["texturas"][nome] = b64(T[nome], 6)
(OUT / "dados.json").write_text(json.dumps(dados))
g = Image.new("RGBA", (4 * 216, 306), (24, 28, 36, 255))
for i, t in enumerate(gt.TIERS_ROTEADOR):
    g.alpha_composite(Image.open(OUT / f"jar_{t}.png"), (i * 216, 0))
g.save(OUT / "grade.png")
print("ok")

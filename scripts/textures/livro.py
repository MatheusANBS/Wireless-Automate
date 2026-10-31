"""O livro-guia aberto (textures/gui/guide/book.png) e as fitas dos grupos (ribbon_<grupo>[_on].png).

Chamado por `gerar_texturas.py`. O livro tem capa de grafite com cantoneiras de latão, duas páginas de
pergaminho suave (ruído de baixa frequência em poucos tons) e a lombada com a costura coral. Cada fita
é a aba de um grupo, presa na borda direita da capa: a normal (17 × 18) e a ativa `_on` (22 × 18).
Estes PNGs não são 16 px de largura, então não passam pelo `Sprite`: `gerar()` grava direto e devolve
as chaves para a limpeza do `gerar_texturas.py` não apagá-los.
"""

from __future__ import annotations

import random

from PIL import Image, ImageDraw

from identidade import ASSETS, Cor, cor

PASTA = "gui/guide"

# Uma fita por grupo da barra lateral do livro (a cor de cada um vem da spec).
FITAS: dict[str, str] = {
    "comecar": "coral.2", "ferramentas": "latao.1", "armazenamentos": "fluido.2", "energia": "source.1",
    "painel": "chunk.1", "integracoes": "quimico.2", "referencia": "porcelana.0",
}


def c(mat: str, i: int, a: int = 255) -> Cor:
    return cor(f"{mat}.{i}", a)


# Tons do papel, do mais escuro (borda) ao mais claro, todos entre a porcelana 2 e a 4.
TONS_PAPEL = [(231, 222, 204), (239, 232, 218), (244, 238, 227), (248, 243, 234), (251, 248, 240)]


def papel(im: Image.Image, x0: int, y0: int, x1: int, y1: int, rnd: random.Random) -> None:
    """Pergaminho suave: ruído de baixa frequência em poucos tons próximos, mais escuro perto das bordas."""
    w, h = x1 - x0 + 1, y1 - y0 + 1
    camadas = []
    for gx, gy, peso in [(5, 4, 0.6), (11, 8, 0.3), (23, 16, 0.1)]:
        g = Image.new("L", (gx, gy))
        g.putdata([rnd.randint(0, 255) for _ in range(gx * gy)])
        camadas.append((g.resize((w, h), Image.BICUBIC), peso))
    px = im.load()
    for yy in range(h):
        for xx in range(w):
            v = sum(cam.getpixel((xx, yy)) * pe for cam, pe in camadas) / 255
            borda = min(xx, w - 1 - xx, yy, h - 1 - yy)
            v = 0.3 + v * 0.55 - max(0, 0.3 - borda * 0.05)
            # um pontilhado leve na fronteira entre os tons, como no pixel art feito à mão
            v += ((xx + yy) % 2) * 0.04
            k = max(0, min(len(TONS_PAPEL) - 1, int(v * len(TONS_PAPEL))))
            px[x0 + xx, y0 + yy] = TONS_PAPEL[k] + (255,)


def livro(capa_mat: str = "grafite") -> Image.Image:
    W, H = 300, 196
    im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    rnd = random.Random(7)
    # ---- capa: grafite com borda chanfrada e risco fino em diagonal (o brilho do couro)
    d.rectangle([2, 4, W - 3, H - 3], fill=c(capa_mat, 2))
    d.rectangle([2, 4, W - 3, H - 3], outline=c(capa_mat, 0))
    d.line([3, 5, W - 4, 5], fill=c(capa_mat, 4))
    d.line([3, 5, 3, H - 4], fill=c(capa_mat, 4))
    d.line([3, H - 4, W - 4, H - 4], fill=c(capa_mat, 1))
    d.line([W - 4, 6, W - 4, H - 4], fill=c(capa_mat, 1))
    riscos = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    dr = ImageDraw.Draw(riscos)
    for x in range(-H, W, 29):
        for k in range(2):
            dr.line([x + k, H - 4, x + k + H, 4], fill=c(capa_mat, 3, 110))
    mascara = Image.new("L", (W, H), 0)
    ImageDraw.Draw(mascara).rectangle([4, 6, W - 5, H - 5], fill=255)
    riscos.putalpha(Image.composite(riscos.getchannel("A"), Image.new("L", (W, H), 0), mascara))
    im.alpha_composite(riscos)
    d = ImageDraw.Draw(im)
    # ---- cantoneiras de latão
    for (x, y, sx, sy) in [(3, 5, 1, 1), (W - 4, 5, -1, 1), (3, H - 4, 1, -1), (W - 4, H - 4, -1, -1)]:
        for i in range(12):
            for j in range(12 - i):
                px, py = x + sx * i, y + sy * j
                tom = 3 if (i == 0 or j == 0) else (1 if i + j >= 10 else 2)
                d.point((px, py), fill=c("latao", tom))
        d.point((x + sx * 4, y + sy * 4), fill=c("latao", 0))
    # ---- presilhas de latão no meio, em cima e embaixo (como a lombada)
    for y0, y1 in [(0, 8), (H - 9, H - 1)]:
        d.rectangle([W // 2 - 14, y0, W // 2 + 13, y1], fill=c("latao", 2), outline=c("latao", 0))
        d.line([W // 2 - 13, y0 + 1, W // 2 + 12, y0 + 1], fill=c("latao", 3))
        d.rectangle([W // 2 - 2, y0 + 2, W // 2 + 1, y1 - 2], fill=c("latao", 1))
    # ---- as páginas: pilha de folhas nas bordas, porcelana manchada, sombra na dobra
    meio = W // 2
    for (x0, x1, lado) in [(11, meio - 2, -1), (meio + 1, W - 12, 1)]:
        # pilha de folhas (3 camadas) por baixo e por fora
        for k in range(3, 0, -1):
            ex = x0 - k if lado < 0 else x0
            dx = x1 if lado < 0 else x1 + k
            d.rectangle([ex, 10 + k, dx, H - 11 + k], fill=c("porcelana", 1 + (k % 2)))
        d.rectangle([x0, 10, x1, H - 12], fill=c("porcelana", 3))
        papel(im, x0, 10, x1, H - 12, rnd)
        d = ImageDraw.Draw(im)
        sombra = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        ds = ImageDraw.Draw(sombra)
        for k in range(12):
            xx = x1 - k if lado < 0 else x0 + k
            ds.line([xx, 10, xx, H - 12], fill=c("porcelana", 0, int(150 * (1 - k / 12) ** 1.8)))
        im.alpha_composite(sombra)
        d = ImageDraw.Draw(im)
        xo = x0 if lado < 0 else x1
        d.line([xo, 10, xo, H - 12], fill=c("porcelana", 4))
        # moldura fina com cantos em L e a antena nos cantos de cima
        fx0, fx1 = x0 + 6, x1 - 6
        d.line([fx0 + 10, 16, fx1 - 10, 16], fill=c("porcelana", 1))
        d.line([fx0 + 10, H - 18, fx1 - 10, H - 18], fill=c("porcelana", 1))
        for (cx, cy, sx, sy) in [(fx0, 16, 1, 1), (fx1, 16, -1, 1), (fx0, H - 18, 1, -1), (fx1, H - 18, -1, -1)]:
            d.line([cx, cy, cx + sx * 8, cy], fill=c("latao", 1))
            d.line([cx, cy, cx, cy + sy * 8], fill=c("latao", 1))
            d.point((cx + sx * 2, cy + sy * 2), fill=c("coral", 2))
        # a antena (prato com dois arcos de sinal) no canto de cima de fora
        ax = fx0 + 14 if lado < 0 else fx1 - 14
        for dx, dy in [(0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)]:
            d.point((ax + dx, 13 + dy), fill=c("coral", 1))
        d.arc([ax - 4, 9, ax + 4, 17], 200, 340, fill=c("coral", 2))
        d.arc([ax - 7, 6, ax + 7, 20], 210, 330, fill=c("coral", 3, 200))
    # ---- lombada: a costura coral entre as páginas
    d.line([meio - 1, 10, meio - 1, H - 12], fill=c("porcelana", 0))
    d.line([meio, 10, meio, H - 12], fill=c("porcelana", 1))
    for y in range(14, H - 14, 9):
        d.line([meio - 1, y, meio - 1, y + 4], fill=c("coral", 1))
        d.line([meio, y + 1, meio, y + 4], fill=c("coral", 2))
    # fita marcadora coral saindo por baixo
    d.rectangle([meio + 22, H - 12, meio + 26, H + 0], fill=c("coral", 2))
    d.line([meio + 26, H - 12, meio + 26, H], fill=c("coral", 1))
    d.polygon([(meio + 22, H - 1), (meio + 24, H - 3), (meio + 26, H - 1)], fill=(0, 0, 0, 0))
    return im


def fita(cor: Cor, ativa: bool = False) -> Image.Image:
    """A fita de um grupo, presa na borda direita da capa."""
    w, h = (22, 18) if ativa else (17, 18)
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    escura = tuple(int(v * 0.62) for v in cor[:3]) + (255,)
    clara = tuple(min(255, int(v * 1.25) + 20) for v in cor[:3]) + (255,)
    d.rectangle([0, 1, w - 4, h - 2], fill=cor, outline=escura)
    d.polygon([(w - 4, 1), (w - 1, 1), (w - 3, h // 2), (w - 1, h - 2), (w - 4, h - 2)], fill=cor, outline=escura)
    d.line([1, 2, w - 5, 2], fill=clara)
    return im


def gerar() -> set[str]:
    """Grava o livro e as fitas em textures/gui/guide e devolve as chaves (sem .png) gravadas."""
    destino = ASSETS / PASTA
    destino.mkdir(parents=True, exist_ok=True)
    livro().save(destino / "book.png")
    gerados = {f"{PASTA}/book"}
    for nome, ref in FITAS.items():
        fita(cor(ref)).save(destino / f"ribbon_{nome}.png")
        fita(cor(ref), True).save(destino / f"ribbon_{nome}_on.png")
        gerados |= {f"{PASTA}/ribbon_{nome}", f"{PASTA}/ribbon_{nome}_on"}
    return gerados

"""Protótipo da textura do livro aberto (Porcelana e Sinal), para o mockup. Gera o livro e as fitas."""
import random
import sys
from pathlib import Path

from PIL import Image, ImageDraw

SAIDA = Path(sys.argv[1])
SAIDA.mkdir(parents=True, exist_ok=True)

P = {
    "porcelana": ["#948870", "#BDB19A", "#DDD3BF", "#F2EBDD", "#FFFBF3"],
    "grafite": ["#0F0E12", "#1B1920", "#2A2730", "#3E3B44", "#5A5660"],
    "coral": ["#86301E", "#C2442A", "#F0603E", "#FF8A6B", "#FFB39E"],
    "latao": ["#80621C", "#B48E2E", "#DDBC5C", "#F7E3A1"],
}


def c(mat, i, a=255):
    h = P[mat][i].lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


# Tons do papel, do mais escuro (borda) ao mais claro, todos entre a porcelana 2 e a 4.
TONS_PAPEL = [(231, 222, 204), (239, 232, 218), (244, 238, 227), (248, 243, 234), (251, 248, 240)]


def papel(im, x0, y0, x1, y1, rnd):
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


def livro(capa_mat="grafite", capa_escura=False):
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


def fita(cor, ativa=False):
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


if __name__ == "__main__":
    livro().save(SAIDA / "livro.png")
    livro("coral").save(SAIDA / "livro-coral.png")
    cores = {"comecar": (240, 96, 62, 255), "ferramentas": (180, 142, 46, 255), "armazenamentos": (91, 157, 240, 255),
             "energia": (155, 77, 198, 255), "painel": (47, 157, 92, 255), "integracoes": (168, 219, 90, 255),
             "referencia": (122, 115, 102, 255)}
    for nome, cor in cores.items():
        fita(cor).save(SAIDA / f"fita-{nome}.png")
        fita(cor, True).save(SAIDA / f"fita-{nome}-ativa.png")
    prev = livro().resize((900, 588), Image.NEAREST)
    prev.save(SAIDA / "livro_x3.png")
    print("ok")

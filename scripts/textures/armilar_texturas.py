"""Os sprites 16 × 16 da escultura da Armilar formada: os materiais e as faces especiais.

Porte do `pintar_atlas` do conceito (`gerar_conceitos.py`). No conceito cada material é uma área de 128 × 128 com
8 × 8 casas de 16 e a UV escolhe uma casa por semente; aqui cada material é uma casa só, a de desenho típico
(a mesma ideia da casa 0 do conceito: o tampo de grafite com os rebites e as emendas, o corpo de porcelana, a
listra coral, o latão com os veios). A semente de cada casa é fixa, para o PNG sair igual a cada rodada.

As faces especiais ocupam o sprite inteiro e são esticadas na face, como no conceito: o mostrador do Núcleo (a cor
da lente do tier no vidro e as marcas do tier), a placa de sintonia, a escotilha e a face da energia.
"""

from __future__ import annotations

import math

import numpy as np
from PIL import Image

from identidade import LENTE, MARCAS, PALETA, TIERS, Sprite, cor, parado

MATERIAIS = ("porcelana", "grafite", "coral", "latao", "vidro", "grelha")
SEMENTES = {"porcelana": 3, "grafite": 5, "coral": 1, "latao": 11, "vidro": 0, "grelha": 0}


def _tons(mat: str) -> list:
    """Os tons do mais claro (0) ao mais escuro, como a lista `P[mat]` do conceito."""
    return [cor(f"{mat}.{t}") for t in sorted(PALETA[mat], reverse=True)]


class _Pincel:
    def __init__(self, semente: int = 0):
        self.img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        self.px = self.img.load()
        self.r = np.random.default_rng(semente).random

    def put(self, x: int, y: int, c) -> None:
        if 0 <= x < 16 and 0 <= y < 16:
            self.px[x, y] = c

    def ret(self, x0: int, y0: int, x1: int, y1: int, c) -> None:
        for y in range(y0, y1):
            for x in range(x0, x1):
                self.put(x, y, c)

    def borda(self, luz, sombra) -> None:
        """Luz em cima e à esquerda, sombra embaixo e à direita."""
        for i in range(16):
            self.put(i, 0, luz)
            self.put(0, i, luz)
            self.put(i, 15, sombra)
            self.put(15, i, sombra)

    def aleatorio(self, margem: int, lado: int) -> tuple[int, int]:
        return margem + int(self.r() * lado), margem + int(self.r() * lado)


def _porcelana() -> Image.Image:
    p, pl = _Pincel(SEMENTES["porcelana"]), _tons("porcelana")
    p.ret(0, 0, 16, 16, pl[1])
    p.borda(pl[0], pl[2])
    p.put(15, 15, pl[3])
    for _ in range(3):
        p.put(*p.aleatorio(2, 12), pl[2])
    sx, sy = 2 + int(p.r() * 6), 3 + int(p.r() * 6)
    for k in range(3):  # o brilho diagonal
        p.put(sx + k, sy - k, pl[0])
    return p.img


def _grafite() -> Image.Image:
    p, g = _Pincel(SEMENTES["grafite"]), _tons("grafite")
    p.ret(0, 0, 16, 16, g[2])
    p.borda(g[1], g[3])
    for _ in range(7):
        p.put(*p.aleatorio(1, 14), g[1] if p.r() < 0.5 else g[3])
    for (a, b) in ((2, 2), (12, 2), (2, 12), (12, 12)):  # os quatro rebites
        p.put(a, b, g[0])
        p.put(a + 1, b + 1, g[4])
    return p.img


def _latao() -> Image.Image:
    p, l = _Pincel(SEMENTES["latao"]), _tons("latao")
    p.ret(0, 0, 16, 16, l[2])
    for y in range(16):
        q = p.r()
        if q < 0.55:
            s, n = int(p.r() * 10), 3 + int(p.r() * 7)
            p.ret(s, y, min(16, s + n), y + 1, l[1] if q < 0.35 else l[3])
    for i in range(16):
        p.put(i, 0, l[0] if i < 10 else l[1])
        p.put(i, 15, l[3])
        p.put(0, i, l[1])
    return p.img


def _coral() -> Image.Image:
    p, c = _Pincel(SEMENTES["coral"]), _tons("coral")
    p.ret(0, 0, 16, 16, c[2])
    p.borda(c[1], c[3])
    for _ in range(2):
        p.put(*p.aleatorio(2, 12), c[0])
    return p.img


def _vidro() -> Image.Image:
    p, v = _Pincel(), _tons("vidro")
    p.ret(0, 0, 16, 7, v[2])
    p.ret(0, 7, 16, 16, v[3])
    for k in range(5):
        p.put(3 + k, 8 - k, v[1])
    p.put(4, 6, v[0])
    return p.img


def _grelha() -> Image.Image:
    p, g = _Pincel(), _tons("grafite")
    p.ret(0, 0, 16, 16, g[2])
    for linha in (2, 6, 10, 14):
        p.ret(1, linha, 15, linha + 1, g[4])
        if linha + 1 < 16:
            p.ret(1, linha + 1, 15, linha + 2, g[1])
    for i in range(16):
        p.put(i, 0, g[1])
        p.put(0, i, g[1])
    return p.img


def _marcas(p: _Pincel, tier: str, y: int, c) -> None:
    """As marcas do tier (1 a 4) numa linha de 2 px de altura, centradas, de 2 em 2 px."""
    n = MARCAS[tier]
    for i in range(n):
        x = 8 - n + 2 * i
        p.ret(x, y, x + 1, y + 2, c)


def _mostrador(tier: str) -> Image.Image:
    """A escala de FM sob o vidro, na cor da lente do tier, com a moldura de grafite e as marcas do tier. É
    esticado numa face de 16 × 5: as linhas finas viram faixas, como no conceito."""
    p, g, pl = _Pincel(), _tons("grafite"), _tons("porcelana")
    hi, mid, lo = (cor(x) for x in LENTE[tier])
    p.ret(0, 0, 16, 16, g[3])
    p.ret(1, 1, 15, 15, g[1])
    p.ret(2, 2, 14, 14, mid)
    p.ret(2, 2, 14, 3, hi)
    p.ret(2, 13, 14, 14, lo)
    for x in range(3, 13, 2):
        longo = (x - 3) % 4 == 0
        p.ret(x, 4 if longo else 6, x + 1, 9, g[2] if longo else g[1])
    p.ret(3, 9, 13, 10, g[2])
    _marcas(p, tier, 11, pl[0])
    p.put(3, 3, hi)
    return p.img


def _sintonia() -> Image.Image:
    """O mostrador redondo do Sintonizador: aro de grafite, as 12 marcas e o botão de latão no meio."""
    p, g, pl, l = _Pincel(), _tons("grafite"), _tons("porcelana"), _tons("latao")
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            c = g[3] if d > 7.5 else g[1] if d > 6.75 else pl[1] if d > 1 else l[1]
            if 6.75 >= d > 6.25:
                c = pl[2]
            p.put(x, y, c)
    for k in range(12):
        a = k * math.pi / 6
        for rr in (5.0, 5.75):
            p.put(int(7.5 + rr * math.cos(a)), int(7.5 + rr * math.sin(a)), g[2])
    return p.img


def _escotilha() -> Image.Image:
    """O painel de porcelana da Porta de Cristais: o losango coral do cristal e quatro parafusos de latão."""
    p, pl, c, l = _Pincel(), _tons("porcelana"), _tons("coral"), _tons("latao")
    p.ret(0, 0, 16, 16, pl[1])
    p.ret(0, 0, 16, 1, pl[0])
    p.ret(0, 0, 1, 16, pl[0])
    p.ret(0, 15, 16, 16, pl[3])
    p.ret(15, 0, 16, 16, pl[3])
    p.ret(1, 1, 15, 2, pl[2])
    p.ret(1, 14, 15, 15, pl[0])
    p.ret(1, 1, 2, 15, pl[2])
    p.ret(14, 1, 15, 15, pl[0])
    for y in range(16):
        for x in range(16):
            dx, dy = abs(x - 7.5), abs(y - 7.5)
            f = dx / 3 + dy / 4.5
            if f <= 1:
                p.put(x, y, c[4] if f > 0.82 else c[1] if x < 7 and y < 7 else c[3] if x > 8 and y > 8 else c[2])
    for (a, b) in ((2, 2), (13, 2), (2, 13), (13, 13)):
        p.put(a, b, l[1])
    return p.img


def _energia() -> Image.Image:
    """A placa de grafite da Porta de Energia com a faixa âmbar."""
    p, g, e = _Pincel(), _tons("grafite"), _tons("energia")
    p.ret(0, 0, 16, 16, g[2])
    p.borda(g[1], g[3])
    p.ret(1, 6, 15, 10, e[2])
    p.ret(1, 6, 15, 7, e[1])
    p.ret(1, 9, 15, 10, e[3])
    return p.img


def sprites() -> dict[str, Sprite]:
    """`block/armilar_<nome>` -> sprite 16 × 16 opaco, para os materiais, os especiais e o mostrador por tier."""
    pintores = {"porcelana": _porcelana, "grafite": _grafite, "coral": _coral, "latao": _latao, "vidro": _vidro,
                "grelha": _grelha, "sintonia": _sintonia, "escotilha": _escotilha, "energia": _energia}
    saida = {f"block/armilar_{nome}": parado(fn()) for nome, fn in pintores.items()}
    for t in TIERS:
        saida[f"block/armilar_mostrador_{t}"] = parado(_mostrador(t))
    return saida

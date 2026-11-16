"""O Ressonador de Sinal solto: as texturas e os modelos dos sete blocos do chão da Armilar (Laje, Coluna, Berço,
Núcleo, Porta de Energia, Porta de Cristais e Sintonizador), a prancha aprovada.

O `blocos.py` chama este módulo de dentro de `sprites()`, `modelos()` e `previas()`; os formados vêm do
`armilar_modelo`. Os pincéis e as texturas da Armilar solta levam o prefixo `arm_`, para não colidir com os
do `blocos.py`.
"""

from __future__ import annotations

import math
import random

from PIL import Image

from blocos import LATERAIS, NS, SINAL_MINERIO, Modelo, Vec, sobreposicoes, com_olho, nova
from identidade import LENTE, MARCAS, MODELOS, TIERS, Cor, Sprite, cor, grava_json, mistura, parado


def put(img: Image.Image, x: int, y: int, c: Cor) -> None:
    if 0 <= x < img.width and 0 <= y < img.height:
        img.putpixel((x, y), c)


def res_olho_par(img: Image.Image, tier: str, fase: float = 0.0, apagado: bool = False) -> None:
    """O Olho em 8×8 (colunas e linhas 4..11): anel de grafite com os cantos cortados, lente 6×6 com o brilho
    em cima à esquerda, centrado no centro óptico da textura (7,5; 7,5). `fase` 0..1 é o Pulso: um anel de
    luz sai do Olho e some, como no `olho` da identidade."""
    hi, mid, lo = (cor(x) for x in LENTE[tier])
    if apagado:
        hi, mid, lo = (mistura(c, cor("grafite.2"), .65) for c in (hi, mid, lo))
    g = cor("grafite.1")

    def anel_olho(raio: int, c: Cor) -> None:
        for ox in range(-raio, 8 + raio):
            for oy in range(-raio, 8 + raio):
                borda = ox in (-raio, 7 + raio) or oy in (-raio, 7 + raio)
                canto = ox in (-raio, 7 + raio) and oy in (-raio, 7 + raio)
                if borda and not canto:
                    put(img, 4 + ox, 4 + oy, c)
    anel_olho(0, g)
    for ox in range(1, 7):
        for oy in range(1, 7):
            put(img, 4 + ox, 4 + oy, lo if (ox - 3.5) + (oy - 3.5) > 0 else mid)
    put(img, 5, 5, hi)
    put(img, 6, 5, mistura(hi, mid, .5))
    put(img, 5, 6, mistura(hi, mid, .5))
    if 0 < fase < 1:
        passo = int(fase * 4)
        if passo == 0:
            anel_olho(1, hi)
        elif passo == 1:
            anel_olho(2, mistura(hi, cor("grafite.3"), .5))
        elif passo == 2:
            anel_olho(0, mistura(g, hi, .45))


def res_marcas_tier(img: Image.Image, tier: str, y: int, c: Cor) -> None:
    """As marcas do tier (1 a 4) numa linha, centradas: 2 px com vão de 2 (1 a 3 marcas) e 1 px com vão de 2
    (4 marcas), para a largura ser sempre par."""
    n = MARCAS[tier]
    if n == 0:
        return
    larg = 2 if n < 4 else 1
    total = n * larg + (n - 1) * 2
    x0 = 8 - total // 2
    for i in range(n):
        for k in range(larg):
            put(img, x0 + i * (larg + 2) + k, y, c)


# Os ids das três peças das faces (a Porta de Energia, a de Cristais e o Sintonizador).
PECAS = ("resonator_energy_port", "resonator_crystal_port", "resonator_tuner")


# ---------------------------------------------------------------------------
# Armilar: as texturas dos blocos soltos (a prancha aprovada). Cada face é 16 × 16 pelas rampas da PALETA.
# ---------------------------------------------------------------------------

def arm_campo(mat: str, tom: str = "3", grao: str = "4", p: float = .03, semente: int = 1) -> Image.Image:
    img = nova()
    rnd = random.Random(semente)
    for y in range(16):
        for x in range(16):
            put(img, x, y, cor(f"{mat}.{grao}" if rnd.random() < p else f"{mat}.{tom}"))
    return img


def arm_bisel(img, x0, y0, x1, y1, luz, sombra):
    """Luz na linha de cima e na coluna da esquerda, sombra na de baixo e na da direita (fechado em x1, y1)."""
    for x in range(x0, x1 + 1):
        put(img, x, y0, cor(luz))
        put(img, x, y1, cor(sombra))
    for y in range(y0, y1 + 1):
        put(img, x0, y, cor(luz))
        put(img, x1, y, cor(sombra))


def arm_caixa(img, x0, y0, x1, y1, ref):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            put(img, x, y, cor(ref))


def arm_rebite(img, x, y, mat="latao"):
    put(img, x, y, cor(f"{mat}.2" if mat == "latao" else f"{mat}.3"))
    put(img, x + 1, y + 1, cor(f"{mat}.0"))


# ---------------------------------------------------------------------------------------------------------
# A faixa da família (as laterais dos cinco blocos do chão): tampo, listra coral, corpo, rodapé.
# É a mesma faixa da escultura (tampo 14..16, listra 12..14, corpo 3..12, rodapé 0..3).
# ---------------------------------------------------------------------------------------------------------

def arm_faixa(img=None, corpo=True):
    img = img or nova()
    rnd = random.Random(7)
    for x in range(16):
        put(img, x, 0, cor("porcelana.4"))
        put(img, x, 1, cor("porcelana.3"))
        put(img, x, 2, cor("coral.2"))
        put(img, x, 3, cor("coral.1"))
        if corpo:
            put(img, x, 4, cor("porcelana.4"))
            for y in range(5, 12):
                put(img, x, y, cor("porcelana.4" if rnd.random() < .03 else "porcelana.3"))
            put(img, x, 12, cor("porcelana.2"))
        put(img, x, 13, cor("grafite.3"))
        put(img, x, 14, cor("grafite.2"))
        put(img, x, 15, cor("grafite.1"))
    for x in (3, 12):
        put(img, x, 14, cor("latao.2"))
    return img


def arm_familia_lado():
    img = arm_faixa()
    # a placa rebaixada do corpo: luz embaixo e à direita (é um rebaixo), sombra em cima e à esquerda
    for x in range(3, 13):
        put(img, x, 6, cor("porcelana.1"))
        put(img, x, 10, cor("porcelana.4"))
    for y in range(6, 11):
        put(img, 3, y, cor("porcelana.1"))
        put(img, 12, y, cor("porcelana.4"))
    for x in range(5, 11, 2):
        put(img, x, 8, cor("porcelana.2"))
    return img


def arm_fundo(mat="grafite"):
    img = arm_campo(mat, "2" if mat == "grafite" else "1", "3" if mat == "grafite" else "2", .04, 4)
    luz, sombra = (f"{mat}.3", f"{mat}.1") if mat == "grafite" else ("latao.2", "latao.0")
    arm_bisel(img, 0, 0, 15, 15, luz, sombra)
    return img


def arm_liso(mat, hi, mid, lo):
    img = nova()
    arm_caixa(img, 0, 0, 15, 15, f"{mat}.{mid}")
    arm_bisel(img, 0, 0, 15, 15, f"{mat}.{hi}", f"{mat}.{lo}")
    return img


# ---------------------------------------------------------------------------------------------------------
# 1. Laje: o chão da Armilar. Topo de porcelana com o aro de latão embutido e os quatro rumos.
# ---------------------------------------------------------------------------------------------------------

def arm_laje_topo():
    img = arm_campo("porcelana", semente=3)
    arm_bisel(img, 0, 0, 15, 15, "porcelana.4", "porcelana.1")
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if 4.2 < d <= 5.7:
                put(img, x, y, cor("latao.3" if x + y < 12 else "latao.2" if x + y < 19 else "latao.1"))
    for (x0, y0, x1, y1) in ((7, 1, 8, 2), (7, 13, 8, 14), (1, 7, 2, 8), (13, 7, 14, 8)):
        arm_caixa(img, x0, y0, x1, y1, "grafite.2")
    put(img, 7, 1, cor("grafite.3"))
    arm_caixa(img, 7, 7, 8, 8, "latao.2")
    put(img, 7, 7, cor("latao.3"))
    put(img, 8, 8, cor("latao.0"))
    for p in ((2, 2), (12, 2), (2, 12), (12, 12)):
        arm_rebite(img, *p)
    return img


# ---------------------------------------------------------------------------------------------------------
# 2. Coluna: grafite com a listra coral que sobe, base e capitel de latão (silhueta própria).
# ---------------------------------------------------------------------------------------------------------

def arm_coluna_lado():
    img = nova()
    # capitel (v 0..2), colunas 2..13
    for x in range(2, 14):
        put(img, x, 0, cor("latao.3"))
        put(img, x, 1, cor("latao.2"))
        put(img, x, 2, cor("latao.1"))
    put(img, 2, 1, cor("latao.3"))
    put(img, 13, 1, cor("latao.1"))
    # fuste (v 3..12), colunas 4..11
    for y in range(3, 13):
        for x in range(4, 12):
            put(img, x, y, cor("grafite.3" if x == 4 else "grafite.1" if x == 11 else "grafite.2"))
        put(img, 7, y, cor("coral.2"))
        put(img, 8, y, cor("coral.1"))
    for y in (4, 11):
        for x in range(4, 12):
            put(img, x, y, cor("latao.2" if x < 8 else "latao.1"))
        put(img, 4, y, cor("latao.3"))
    for y in (6, 9):
        put(img, 5, y, cor("grafite.4"))
    # base (v 13..15), colunas 1..14
    for x in range(1, 15):
        put(img, x, 13, cor("latao.3"))
        put(img, x, 14, cor("latao.2"))
        put(img, x, 15, cor("latao.0"))
    for x in (3, 12):
        put(img, x, 14, cor("grafite.1"))
    return img


def arm_coluna_topo():
    img = nova()
    arm_caixa(img, 1, 1, 14, 14, "latao.2")
    arm_bisel(img, 1, 1, 14, 14, "latao.3", "latao.1")
    arm_bisel(img, 2, 2, 13, 13, "latao.3", "latao.1")
    arm_caixa(img, 5, 5, 10, 10, "grafite.1")
    arm_caixa(img, 6, 6, 9, 9, "grafite.0")
    put(img, 6, 6, cor("grafite.2"))
    for p in ((7, 3), (3, 7), (12, 8), (8, 12)):
        put(img, *p, cor("latao.0"))
    return img


# ---------------------------------------------------------------------------------------------------------
# 3. Berço do Sol: o pedestal do centro. Base de porcelana, pescoço de grafite e a taça de latão com a brasa.
# ---------------------------------------------------------------------------------------------------------

def arm_berco_lado():
    img = nova()
    # paredes da taça (v 2..4) e o fundo (v 5), colunas 2..13
    for x in range(2, 14):
        put(img, x, 2, cor("latao.3"))
        put(img, x, 3, cor("latao.2"))
        put(img, x, 4, cor("latao.2"))
        put(img, x, 5, cor("latao.0"))
    for x in range(3, 13, 3):
        put(img, x, 3, cor("grafite.1"))
    put(img, 2, 3, cor("latao.3"))
    # pescoço (v 6..12), colunas 5..10
    for y in range(6, 13):
        for x in range(5, 11):
            put(img, x, y, cor("grafite.3" if x == 5 else "grafite.1" if x == 10 else "grafite.2"))
    for x in range(5, 11):
        put(img, x, 6, cor("latao.2" if x < 8 else "latao.1"))
        put(img, x, 12, cor("latao.1"))
    put(img, 6, 9, cor("grafite.4"))
    put(img, 7, 9, cor("coral.2"))
    put(img, 8, 9, cor("coral.1"))
    # base (v 13..15), colunas 1..14
    for x in range(1, 15):
        put(img, x, 13, cor("porcelana.4"))
        put(img, x, 14, cor("coral.2"))
        put(img, x, 15, cor("grafite.1"))
    return img


def arm_berco_topo():
    img = nova()
    arm_caixa(img, 1, 1, 14, 14, "porcelana.3")
    arm_bisel(img, 1, 1, 14, 14, "porcelana.4", "porcelana.1")
    arm_caixa(img, 2, 2, 13, 13, "latao.3")
    arm_caixa(img, 3, 3, 12, 12, "grafite.1")
    for i in range(3, 13):
        put(img, i, i, cor("grafite.2"))
        put(img, i, 15 - i, cor("grafite.2"))
    arm_caixa(img, 5, 5, 10, 10, "coral.2")
    arm_caixa(img, 6, 6, 9, 9, "coral.3")
    arm_caixa(img, 7, 7, 8, 8, "coral.4")
    put(img, 10, 10, cor("coral.1"))
    return img


def arm_berco_dentro():
    img = nova()
    arm_caixa(img, 0, 0, 15, 15, "grafite.1")
    for x in range(16):
        put(img, x, 2, cor("latao.1"))
        if x % 3 == 1:
            put(img, x, 4, cor("coral.1"))
    return img


def arm_brasa_lado():
    img = nova()
    arm_caixa(img, 0, 0, 15, 15, "coral.2")
    for x in range(16):
        put(img, x, 11, cor("coral.3"))
    return img


# ---------------------------------------------------------------------------------------------------------
# 4. Núcleo: a faixa da família, a placa de instrumentos com o Olho saltado na cor do tier.
# ---------------------------------------------------------------------------------------------------------

def arm_placa(img, x0=1, y0=4, x1=14, y1=12, fundo_ref="grafite.3"):
    arm_caixa(img, x0, y0, x1, y1, fundo_ref)
    arm_bisel(img, x0, y0, x1, y1, "grafite.1", "grafite.1")
    for x in range(x0 + 1, x1):
        put(img, x, y0 + 1, cor("grafite.4"))
    for y in range(y0 + 1, y1):
        put(img, x0 + 1, y, cor("grafite.4"))


def arm_nucleo_frente(tier):
    img = arm_faixa(corpo=False)
    arm_placa(img)
    res_olho_par(img, tier)
    # os dois mostradores do lado do Olho: o da carga (coral subindo) e o da escala
    for y in range(6, 11):
        put(img, 2, y, cor("grafite.0"))
        put(img, 13, y, cor("porcelana.2" if y % 2 == 0 else "grafite.0"))
    for y in range(8, 11):
        put(img, 2, y, cor("coral.2"))
    put(img, 13, 8, cor("coral.3"))
    res_marcas_tier(img, tier, 14, cor("porcelana.4"))
    return img


def arm_nucleo_topo(tier):
    img = arm_campo("porcelana", semente=5)
    arm_bisel(img, 0, 0, 15, 15, "porcelana.4", "porcelana.1")
    for (x, y, dx, dy) in ((1, 1, 1, 1), (14, 1, -1, 1), (1, 14, 1, -1), (14, 14, -1, -1)):
        for k in range(3):
            put(img, x + dx * k, y, cor("grafite.2"))
            put(img, x, y + dy * k, cor("grafite.2"))
    arm_caixa(img, 4, 4, 11, 11, "grafite.2")
    arm_bisel(img, 4, 4, 11, 11, "grafite.1", "grafite.3")
    com_olho(img, 7, 7, tier, 0.0, 3)
    for p in ((3, 7), (12, 7), (7, 3), (7, 12)):
        put(img, *p, cor("latao.2"))
    return img


# ---------------------------------------------------------------------------------------------------------
# 5. Porta de Energia: placa com o raio aceso, dois bornes de latão saltados e a calha de energia no topo.
# ---------------------------------------------------------------------------------------------------------

ARM_RAIO_ENERGIA = [".....XX.", "....XX..", "...XX...", "..XXXX..", "....XX..", "...XX...", "..XX....", ".XX....."]


def arm_energia_frente():
    img = arm_faixa(corpo=False)
    arm_placa(img, fundo_ref="grafite.2")
    for dy, linha in enumerate(ARM_RAIO_ENERGIA):
        for dx, ch in enumerate(linha):
            if ch == "X":
                put(img, 4 + dx, 4 + dy, cor("energia.3" if dx + dy < 7 else "energia.2"))
    for x0 in (2, 11):  # os bornes (a face da frente das caixas saltadas)
        arm_caixa(img, x0, 6, x0 + 2, 9, "latao.2")
        put(img, x0, 6, cor("latao.3"))
        put(img, x0 + 1, 7, cor("grafite.0"))
        put(img, x0 + 1, 8, cor("grafite.1"))
        put(img, x0 + 2, 9, cor("latao.0"))
    return img


def arm_energia_topo():
    img = arm_campo("porcelana", semente=8)
    arm_bisel(img, 0, 0, 15, 15, "porcelana.4", "porcelana.1")
    for y in range(16):
        put(img, 5, y, cor("grafite.1"))
        put(img, 10, y, cor("grafite.1"))
        for x in range(6, 10):
            put(img, x, y, cor("energia.1" if x == 9 else "energia.2"))
        if y % 4 == 1:
            put(img, 6, y, cor("energia.3"))
            put(img, 7, y, cor("energia.3"))
    for p in ((2, 3), (12, 3), (2, 12), (12, 12)):
        arm_rebite(img, *p)
    return img


# ---------------------------------------------------------------------------------------------------------
# 6. Porta de Cristais: a escotilha coral saltada com o visor e os cristais, e o visor de cima.
# ---------------------------------------------------------------------------------------------------------

def arm_cristais(img, pontos):
    for (x, y) in pontos:
        put(img, x, y, cor(SINAL_MINERIO["B"]))
        put(img, x, y - 1, cor(SINAL_MINERIO["A"]))
        put(img, x + 1, y, cor(SINAL_MINERIO["C"]))
        put(img, x, y + 1, cor(SINAL_MINERIO["D"]))
    put(img, pontos[0][0], pontos[0][1] - 1, cor(SINAL_MINERIO["H"]))


def arm_cristais_frente():
    img = arm_faixa(corpo=False)
    arm_placa(img, fundo_ref="grafite.2")
    for y in range(4, 12):
        for x in range(4, 12):
            d = math.hypot(x - 7.5, y - 7.5)
            if 2.9 < d <= 4.1:
                put(img, x, y, cor("coral.3" if x + y < 13 else "coral.2" if x + y < 17 else "coral.1"))
            elif d <= 2.9:
                put(img, x, y, cor("vidro.1" if x + y > 15 else "vidro.2"))
    arm_cristais(img, [(6, 8), (8, 7), (9, 9)])
    put(img, 6, 6, cor("vidro.3"))
    for y in range(6, 10):  # a dobradiça (a face da frente da caixa saltada)
        put(img, 3, y, cor("latao.2" if y % 2 else "latao.1"))
    for p in ((2, 5), (12, 5), (12, 10), (2, 10)):
        put(img, *p, cor("latao.2"))
    return img


def arm_cristais_topo():
    img = arm_campo("grafite", "2", "3", .04, 9)
    arm_bisel(img, 0, 0, 15, 15, "grafite.3", "grafite.1")
    arm_caixa(img, 3, 4, 12, 13, "grafite.0")
    for y in range(5, 13):
        for x in range(4, 12):
            put(img, x, y, cor("vidro.1" if (x + y) % 5 else "vidro.0"))
    for i in range(4, 8):
        put(img, i, 12 - i + 1, cor("vidro.2"))
    arm_cristais(img, [(6, 9), (9, 7), (8, 11), (5, 6)])
    for p in ((1, 2), (13, 2), (1, 13), (13, 13)):
        arm_rebite(img, *p)
    return img


# ---------------------------------------------------------------------------------------------------------
# 7. Sintonizador: o mostrador de latão saltado com o ponteiro coral, o botão e o topo de latão com a escala.
# ---------------------------------------------------------------------------------------------------------

def arm_sintonizador_frente():
    img = arm_faixa(corpo=False)
    arm_placa(img, fundo_ref="grafite.2")
    for y in range(3, 13):
        for x in range(3, 13):
            d = math.hypot(x - 7.5, y - 7.5)
            if d > 5.2:
                put(img, x, y, cor("latao.2" if x + y < 15 else "latao.1"))
            elif d > 4.3:
                put(img, x, y, cor("latao.3" if x + y < 15 else "latao.0"))
            else:
                put(img, x, y, cor("porcelana.4" if x + y < 15 else "porcelana.3"))
    for a in range(0, 360, 30):
        r = math.radians(a)
        put(img, round(7.5 + 3.6 * math.cos(r)), round(7.5 + 3.6 * math.sin(r)), cor("grafite.2"))
    for (x, y) in ((6, 6), (5, 5), (4, 5), (5, 4)):  # o ponteiro, saindo do botão para cima e à esquerda
        put(img, x, y, cor("coral.2"))
    put(img, 4, 4, cor("coral.3"))
    arm_caixa(img, 7, 7, 8, 8, "grafite.1")  # o botão
    put(img, 7, 7, cor("grafite.3"))
    return img


def arm_sintonizador_topo():
    img = nova()
    arm_caixa(img, 0, 0, 15, 15, "latao.2")
    arm_bisel(img, 0, 0, 15, 15, "latao.3", "latao.0")
    # a régua de sintonia: janela de porcelana com a escala e o cursor coral
    arm_caixa(img, 2, 4, 13, 8, "porcelana.4")
    arm_bisel(img, 2, 4, 13, 8, "latao.0", "latao.3")
    for x in range(3, 13):
        put(img, x, 5, cor("grafite.2" if x % 2 else "porcelana.4"))
        if x % 4 == 1:
            put(img, x, 6, cor("grafite.2"))
    for y in range(4, 9):
        put(img, 9, y, cor("coral.3" if y == 4 else "coral.2"))
    put(img, 10, 7, cor("coral.1"))
    # a grelha de ventilação: três fendas compridas de grafite
    for y in (10, 12):
        for x in range(3, 13):
            put(img, x, y, cor("grafite.1"))
            put(img, x, y + 1, cor("latao.3"))
    return img


def res_halo() -> Sprite:
    """O halo radial do sinal, do claro no centro ao coral na borda."""
    img = nova()
    centro, borda = (0xFF, 0xF0, 0xEA), (0xF2, 0x91, 0x7A)
    for j in range(16):
        for i in range(16):
            d = math.hypot(i - 7.5, j - 7.5) / 8
            a = max(0.0, 1 - d) ** 2
            t = min(1.0, d)
            rgb = tuple(round(c + (b - c) * t) for c, b in zip(centro, borda))
            img.putpixel((i, j), (*rgb, round(255 * a)))
    return parado(img, translucido=True)


def sprites_ressonador() -> dict[str, Sprite]:
    """O halo do sinal e as texturas dos blocos soltos; as dos formados vêm do `armilar_texturas`."""
    return {"block/resonator_halo": res_halo(), **sprites_soltos()}


def sprites_soltos() -> dict[str, Sprite]:
    """As texturas dos sete blocos soltos da Armilar: as faces do chão, da Coluna e do Berço, e a frente e o
    topo do Núcleo (por tier) e de cada porta."""
    s: dict[str, Sprite] = {
        "familia_lado": arm_familia_lado(), "fundo": arm_fundo(), "fundo_latao": arm_fundo("latao"),
        "latao_liso": arm_liso("latao", 3, 2, 0), "grafite_liso": arm_liso("grafite", 3, 2, 1),
        "coral_liso": arm_liso("coral", 3, 2, 1), "laje_topo": arm_laje_topo(),
        "coluna_lado": arm_coluna_lado(), "coluna_topo": arm_coluna_topo(),
        "berco_lado": arm_berco_lado(), "berco_topo": arm_berco_topo(), "berco_dentro": arm_berco_dentro(),
        "brasa_lado": arm_brasa_lado(),
        "energy_port_solto": arm_energia_frente(), "energy_port_topo": arm_energia_topo(),
        "crystal_port_solto": arm_cristais_frente(), "crystal_port_topo": arm_cristais_topo(),
        "tuner_solto": arm_sintonizador_frente(), "tuner_topo": arm_sintonizador_topo(),
    }
    saida = {f"block/resonator_{k}": parado(img) for k, img in s.items()}
    for t in TIERS:
        saida[f"block/resonance_core_{t}_solto"] = parado(arm_nucleo_frente(t))
        saida[f"block/resonance_core_{t}_topo"] = parado(arm_nucleo_topo(t))
    return saida


# --- Ressonador: cubos inteiros escolhidos pelo blockstate ------------------------

# As rotações do blockstate das peças e do Núcleo, pelo `facing` (o modelo olha para o norte). Em cima e
# embaixo, o `y: 180` deixa a cima da textura no norte (topo) e no sul (chão), o quadro da tabela das faces:
# o ponteiro do Sintonizador e as marcas do Núcleo de pé, como na prancha.
ROTACOES_PECA = {"north": {}, "south": {"y": 180}, "east": {"y": 90}, "west": {"y": 270},
                 "up": {"x": 270, "y": 180}, "down": {"x": 90, "y": 180}}


def girado_180(m: Modelo) -> Modelo:
    """Só para a prévia da folha: o modelo girado 180° em y (norte ↔ sul, leste ↔ oeste), para a câmera da
    prévia, que vê o sul, mostrar a frente da peça. Cada elemento troca de lado em torno do centro do bloco,
    e as faces de cima e de baixo só trocam de lado (a UV delas não gira)."""
    troca = {"north": "south", "south": "north", "east": "west", "west": "east", "up": "up", "down": "down"}
    g = Modelo(m.nome, m.particula, m.translucido, m.cubo)
    for el, dentro in zip(m.elementos, m.dentro):
        de, ate = el["from"], el["to"]
        faces = {troca[f]: (dados["texture"][1:], dados["uv"]) for f, dados in el["faces"].items()}
        g.caixa([16 - ate[0], de[1], 16 - ate[2]], [16 - de[0], ate[1], 16 - de[2]], faces, dentro=dentro)
    return g


# ---------------------------------------------------------------------------
# Armilar: os modelos dos sete blocos soltos (a prancha aprovada). O Núcleo e as portas olham para o norte.
# ---------------------------------------------------------------------------

def _cubo_solto(m: Modelo, lados: str, topo: str, frente: str | None = None, baixo: str = "resonator_fundo",
                z0: float = 0) -> None:
    """O corpo do bloco do chão, de z0 até 16 (o resto é recuo da frente): `lados` nas quatro laterais, a
    `frente` no norte (se houver) e o `baixo`."""
    m.caixa([0, 0, z0], [16, 16, 16], {"north": (frente or lados, None), "south": (lados, None),
                                      "east": (lados, None), "west": (lados, None),
                                      "up": (topo, None), "down": (baixo, None)})


def _saltado(m: Modelo, de: Vec, ate: Vec, frente: str, tex: str) -> None:
    """Uma caixa saltada para a frente: o norte leva a textura da frente do bloco e as outras faces que
    aparecem (leste, oeste, cima e baixo) a de `tex` (`*_liso`), com a UV do tamanho da face."""
    w, h, p = ate[0] - de[0], ate[1] - de[1], ate[2] - de[2]
    m.caixa(de, ate, {"north": (frente, None), "east": (tex, [0, 0, p, h]), "west": (tex, [0, 0, p, h]),
                      "up": (tex, [0, 0, w, p]), "down": (tex, [0, 0, w, p])})


def modelo_laje() -> Modelo:
    """A Laje solta: o cubo inteiro, com a faixa da família nas laterais e o topo de porcelana."""
    m = Modelo("resonator_casing_loose", "resonator_familia_lado", cubo=True)
    _cubo_solto(m, "resonator_familia_lado", "resonator_laje_topo")
    return m


def modelo_coluna() -> Modelo:
    """Base e capitel de latão sobre um fuste de grafite com a listra coral."""
    lado = "resonator_coluna_lado"
    m = Modelo("resonator_column_loose", lado)
    for de, ate, baixo in (([1, 0, 1], [15, 3, 15], True), ([4, 3, 4], [12, 13, 12], False),
                           ([2, 13, 2], [14, 16, 14], True)):
        faces = {f: (lado, None) for f in LATERAIS}
        if baixo:
            faces |= {"up": ("resonator_coluna_topo", None), "down": ("resonator_fundo_latao", None)}
        m.caixa(de, ate, faces)
    return m


def modelo_berco() -> Modelo:
    """Base de porcelana, pescoço de grafite e a taça de latão (quatro paredes) com a brasa dentro."""
    L, D = "resonator_berco_lado", "resonator_berco_dentro"
    topo = "resonator_berco_topo"
    m = Modelo("resonator_cradle_loose", L)
    lados4 = {f: (L, None) for f in LATERAIS}
    m.caixa([1, 0, 1], [15, 3, 15], {**lados4, "up": (topo, None), "down": ("resonator_fundo", None)})
    m.caixa([5, 3, 5], [11, 10, 11], lados4)
    m.caixa([2, 10, 2], [14, 11, 14], {**lados4, "up": (topo, None), "down": ("resonator_fundo_latao", None)})
    m.caixa([2, 11, 2], [14, 14, 3], {"north": (L, None), "south": (D, None), "east": (L, None),
                                      "west": (L, None), "up": (topo, None)})
    m.caixa([2, 11, 13], [14, 14, 14], {"south": (L, None), "north": (D, None), "east": (L, None),
                                        "west": (L, None), "up": (topo, None)})
    m.caixa([13, 11, 3], [14, 14, 13], {"east": (L, None), "west": (D, None), "up": (topo, None)})
    m.caixa([2, 11, 3], [3, 14, 13], {"west": (L, None), "east": (D, None), "up": (topo, None)})
    brasa = ("resonator_brasa_lado", [0, 10, 6, 11])
    m.caixa([5, 11, 5], [11, 12, 11], {**{f: brasa for f in LATERAIS}, "up": (topo, None)})
    return m


def modelo_nucleo_solto(tier: str) -> Modelo:
    """O Núcleo solto do tier: o cubo recuado 1 px e a placa do Olho saltada."""
    frente = f"resonance_core_{tier}_solto"
    m = Modelo(f"resonance_core_{tier}_loose", frente)
    _cubo_solto(m, "resonator_familia_lado", f"resonance_core_{tier}_topo", frente, z0=1)
    _saltado(m, [4, 4, 0], [12, 12, 1], frente, "resonator_grafite_liso")
    return m


def modelo_energia_solta() -> Modelo:
    """A Porta de Energia solta: o cubo recuado 2 px e os dois bornes de latão saltados."""
    frente = "resonator_energy_port_solto"
    m = Modelo("resonator_energy_port_loose", frente)
    _cubo_solto(m, "resonator_familia_lado", "resonator_energy_port_topo", frente, z0=2)
    _saltado(m, [11, 6, 0], [14, 10, 2], frente, "resonator_latao_liso")
    _saltado(m, [2, 6, 0], [5, 10, 2], frente, "resonator_latao_liso")
    return m


def modelo_cristais_solto() -> Modelo:
    """A Porta de Cristais solta: o cubo recuado 2 px, a escotilha coral saltada e a dobradiça."""
    frente = "resonator_crystal_port_solto"
    m = Modelo("resonator_crystal_port_loose", frente)
    _cubo_solto(m, "resonator_familia_lado", "resonator_crystal_port_topo", frente, z0=2)
    _saltado(m, [4, 4, 1], [12, 12, 2], frente, "resonator_coral_liso")
    m.caixa([12, 6, 1], [13, 10, 2], {"north": (frente, None), "east": ("resonator_latao_liso", [0, 0, 1, 4]),
                                      "up": ("resonator_latao_liso", [0, 0, 1, 1]),
                                      "down": ("resonator_latao_liso", [0, 0, 1, 1])})
    return m


def modelo_sintonizador_solto() -> Modelo:
    """O Sintonizador solto: o cubo recuado 2 px, o mostrador de latão saltado e o botão."""
    frente = "resonator_tuner_solto"
    m = Modelo("resonator_tuner_loose", frente)
    _cubo_solto(m, "resonator_familia_lado", "resonator_tuner_topo", frente, z0=2)
    _saltado(m, [3, 3, 1], [13, 13, 2], frente, "resonator_latao_liso")
    _saltado(m, [7, 7, 0], [9, 9, 1], frente, "resonator_grafite_liso")
    return m


def modelos_soltos() -> list[Modelo]:
    """Os modelos dos blocos soltos: a Laje, a Coluna, o Berço, o Núcleo por tier e as três peças."""
    return [modelo_laje(), modelo_coluna(), modelo_berco(), *(modelo_nucleo_solto(t) for t in TIERS),
            modelo_energia_solta(), modelo_cristais_solto(), modelo_sintonizador_solto()]


def modelos_item_ressonador() -> None:
    """Os modelos de item do Ressonador: a Laje, a Coluna, o Berço e as peças soltos (as peças olham para o
    norte, a face que a vista `gui` do `block/block` mostra à direita) e o Núcleo solto, Básico, com o override
    por tier. Os blockstates são do `armilar_modelo.blockstates_armilar`."""
    bloco = f"{NS}:block/"
    for nome in ("resonator_casing", "resonator_column", "resonator_cradle", *PECAS):
        grava_json(MODELOS / f"models/item/{nome}.json", {"parent": bloco + nome + "_loose"})
    grava_json(MODELOS / "models/item/resonance_core.json", {
        "parent": bloco + "resonance_core_basic_loose",
        "overrides": sobreposicoes(bloco + "resonance_core", "_loose")})

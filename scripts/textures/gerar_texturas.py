#!/usr/bin/env python3
"""Gera os sprites dos itens e os ícones da interface do Wireless Automate na identidade "Porcelana e
Sinal" (ver docs/identidade-visual.md), e monta a folha de sprites com tudo, inclusive os blocos.

Uso (na raiz do repositório):

    python scripts/textures/gerar_texturas.py            # grava PNGs, .mcmeta, modelos e a folha
    python scripts/textures/gerar_texturas.py --so-folha # só a folha (não toca nos assets)

Paleta, materiais, o Olho e as utilidades vêm de `identidade.py`; as texturas e os modelos dos blocos
vêm de `blocos.py` (`sprites()`, `modelos()`, `previas()`), quando o módulo existe.

Cada item é uma grade de texto (a forma, pela legenda comum `LEG`) mais código para o que muda por
quadro. Regras: 16×16, alfa só 0 ou 255, contorno de 1 px em grafite.0, luz de cima à esquerda, 3 a
5 tons por material, nada de pixel solto. Todo item é animado (quadros empilhados + `.mcmeta`).

Depois de gravar, apaga das pastas `textures/item`, `textures/gui` e `textures/block` (esta só
quando `blocos.py` gerou algo) o que não saiu desta rodada: tudo ali é gerado.
"""

from __future__ import annotations

import math
import os
import sys
import zipfile
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, str(Path(__file__).resolve().parent))

from identidade import (  # noqa: E402
    ASSETS, LENTE, MARCAS, MODELOS, PALETA, PREVIEW, RAIZ, MODO, RECURSO, TIERS, TIERS_ATM, TIERS_CARTAO,
    Cor, Sprite, animacao, contorno, cor, escurece, grava_json, legenda, mistura, olho, parado, pinta,
    salvar,
)

FOLHA = PREVIEW / "folha-de-sprites.png"

# Lentes extras para o Olho: porcelana (o Olho da capa do Guia) e o farol verde do chunk loading.
LENTE.setdefault("porcelana", (PALETA["porcelana"]["4"], PALETA["porcelana"]["3"], PALETA["porcelana"]["1"]))
LENTE.setdefault("chunk", (PALETA["chunk"]["3"], PALETA["chunk"]["2"], PALETA["chunk"]["0"]))
# Os aparelhos sem tier (Configurador, Vinculador, Tablet, Filtro) levam uma lente de vidro.
LENTE.setdefault("vidro", (PALETA["vidro"]["3"], PALETA["vidro"]["1"], PALETA["vidro"]["0"]))

# Legenda comum dos itens: porcelana W p q r s · grafite G g h i j · coral C c d e f · latão L l m n
# · vidro V v u t. 'x' é um espaço reservado (porcelana) onde o Olho entra depois do contorno.
LEG = legenda(
    W="porcelana.4", p="porcelana.3", q="porcelana.2", r="porcelana.1", s="porcelana.0",
    G="grafite.4", g="grafite.3", h="grafite.2", i="grafite.1", j="grafite.0",
    C="coral.4", c="coral.3", d="coral.2", e="coral.1", f="coral.0",
    L="latao.3", l="latao.2", m="latao.1", n="latao.0",
    V="vidro.3", v="vidro.2", u="vidro.1", t="vidro.0",
    x="porcelana.3",
)
CONTORNO = cor("grafite.0")
BRANCO = cor("porcelana.4")


def corpo(grade: list[str]) -> Image.Image:
    """Forma de um item: grade pela legenda comum, com o contorno de 1 px em grafite.0."""
    return contorno(pinta(grade, LEG), CONTORNO)


def put(img: Image.Image, x: int, y: int, c: Cor) -> None:
    if 0 <= x < img.width and 0 <= y < img.height:
        img.putpixel((x, y), c)


def pulso(img: Image.Image, cx: int, cy: int, tier: str, raio: int, quadro: int, total: int) -> None:
    """O Pulso do Olho em `total` quadros: repouso, anel aceso (e a lente clareia), onda saindo, repouso.
    Com raio 1 o `olho` só desenha a onda e o anel aceso, então a lente clara dá o piscar."""
    fases = [0.0, 0.6, 0.3, 0.0] if raio == 1 else [0.0, 0.6, 0.1, 0.3]
    fase = fases[(quadro * len(fases)) // total]
    olho(img, cx, cy, tier, fase, raio)
    if fase == 0.6:
        hi = cor(LENTE[tier][0])
        if raio == 1:
            put(img, cx, cy, hi)
        else:
            put(img, cx, cy, mistura(cor(LENTE[tier][1]), hi, 0.5))
            put(img, cx - 1, cy - 1, BRANCO if tier != "porcelana" else hi)


def lente(img: Image.Image, cx: int, cy: int, tier: str, quadro: int, total: int) -> None:
    """Olho de 3×3 sem anel próprio (o que está em volta, contorno ou painel de grafite, faz o anel).
    O piscar: a lente clareia num quadro, a onda sai no seguinte."""
    fase = [0.0, 0.6, 0.3, 0.0][(quadro * 4) // total]
    olho(img, cx, cy, tier, fase, 1, anel=False)
    if fase == 0.6:
        hi, mid, _ = (cor(c) for c in LENTE[tier])
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                put(img, cx + dx, cy + dy, hi if dx + dy <= 0 else mistura(mid, hi, 0.5))
        put(img, cx - 1, cy - 1, BRANCO)


# ---------------------------------------------------------------------------
# Itens
# ---------------------------------------------------------------------------

# Configurador: caneta-sonda na diagonal (tampa em cima à direita, ponta embaixo à esquerda).
# Tampa de grafite com o clipe coral ao lado, corpo de porcelana, pegada de grafite, ponta com o Olho.
CONFIGURADOR = [
    "................",
    "...........Gggh.",
    "..........Ggghc.",
    ".........LlmnC..",
    "........WWpqC...",
    ".......WWpq.....",
    "......WWpq......",
    ".....WWpq.......",
    "....WWpq........",
    "...Gggh.........",
    "..Gggh..........",
    ".Wppq...........",
    ".xxx............",
    ".xxx............",
    ".xxx............",
]


def configurador() -> Sprite:
    quadros = []
    for k in range(4):
        img = corpo(CONFIGURADOR)
        lente(img, 2, 13, "vidro", k, 4)
        quadros.append(img)
    return animacao(quadros, 8)


# Vinculador: transceptor de mão. Antena de grafite com bolinha, corpo de porcelana com botão,
# o Olho, a listra coral e um medidor de três barras numa janela de vidro.
VINCULADOR = [
    "................",
    ".....Gg.........",
    ".....gh.........",
    "......h.........",
    "......g.........",
    "....WWWWWWWq....",
    "....Wgggxxxq....",
    "....WgLgxxxq....",
    "....Whhhxxxq....",
    "....Cccccccd....",
    "....Wtttttpq....",
    "....Wtttttpq....",
    "....Wtttttpq....",
    "....Wtttttpq....",
    "....qqqqqqqr....",
]
BARRAS = ([3, 4, 3, 2, 1, 2], [2, 3, 4, 3, 2, 1], [1, 2, 3, 4, 3, 2])


def vinculador() -> Sprite:
    quadros = []
    seg = cor("vidro.3")
    seg_topo = cor("vidro.2")
    for k in range(6):
        img = corpo(VINCULADOR)
        olho(img, 9, 7, "vidro", 0.0, 1, anel=False)
        for i, alturas in enumerate(BARRAS):
            h = alturas[k]
            x = 5 + 2 * i
            for y in range(14 - h, 14):
                put(img, x, y, seg if y > 14 - h else seg_topo)
        quadros.append(img)
    return animacao(quadros, 5)


# Tablet de Rede: prancheta de porcelana com clipe, moldura de grafite, tela de vidro arredondada e
# o Olho embaixo. Na tela, a varredura de radar.
TABLET = [
    "................",
    "......ggg.......",
    "..WWWWgggWWWq...",
    "..Whhhhhhhhhq...",
    "..Whhttttthhq...",
    "..Whttttttthq...",
    "..Whttttttthq...",
    "..Whttttttthq...",
    "..Whttttttthq...",
    "..Whttttttthq...",
    "..Whhttttthhq...",
    "..Whhhhhhhhhq...",
    "..Wpppxxxpppq...",
    "..Wpppxxxpppq...",
    "..qqqqxxxqqqr...",
]
DIRECOES = [(0, -1), (1, -1), (1, 0), (1, 1), (0, 1), (-1, 1), (-1, 0), (-1, -1)]
BLIPS = [(5, 8), (9, 5)]


def tablet() -> Sprite:
    quadros = []
    base = cor("vidro.0")
    raio_c, rastro1, rastro2 = cor("vidro.3"), cor("vidro.2"), mistura(cor("vidro.1"), cor("vidro.0"), 0.45)
    blip = cor("vidro.1")
    cx, cy = 7, 7
    for k in range(8):
        img = corpo(TABLET)
        for bx, by in BLIPS:
            put(img, bx, by, blip)
        for atraso, c in ((2, rastro2), (1, rastro1), (0, raio_c)):
            dx, dy = DIRECOES[(k - atraso) % 8]
            for i in range(1, 4):
                x, y = cx + dx * i, cy + dy * i
                if 4 <= x <= 10 and 4 <= y <= 10 and img.getpixel((x, y)) in (base, blip, rastro2, rastro1):
                    put(img, x, y, c)
        put(img, cx, cy, raio_c)
        olho(img, 7, 13, "vidro", 0.0, 1, anel=False)
        quadros.append(img)
    return animacao(quadros, 4)


# Cartão de Filtro: cartão perfurado de porcelana com o canto cortado, o Olho, a listra coral e
# três linhas de furos de grafite. Uma faixa de luz corre pelos furos.
FILTRO = [
    "................",
    "................",
    "..WWWWWWWWWW....",
    "..Wpppppppppq...",
    "..Wxxxppcccdpq..",
    "..Wxxxpppppppq..",
    "..Wxxxpppppppq..",
    "..Wppppppppppq..",
    "..Wphphphphphq..",
    "..Wppppppppppq..",
    "..Wphphphphphq..",
    "..Wppppppppppq..",
    "..Wphphphphphq..",
    "..qqqqqqqqqqqr..",
]
FUROS_X = [4, 6, 8, 10, 12]
FUROS_Y = [8, 10, 12]


def filtro() -> Sprite:
    quadros = []
    luz, rastro = cor("energia.3"), cor("energia.2")
    for k in range(6):
        img = corpo(FILTRO)
        olho(img, 4, 5, "vidro", 0.0, 1, anel=False)
        for y in FUROS_Y:
            if k < 5:
                put(img, FUROS_X[k], y, luz)
            if 1 <= k <= 5:
                put(img, FUROS_X[k - 1], y, rastro)
        quadros.append(img)
    return animacao(quadros, 5)


# Cartões de Upgrade: válvula (tubo de vácuo). Bulbo de vidro, filamento na cor da lente do tier,
# colar de porcelana com as marcas, base de grafite e pinos de latão (ou da cor do tier, no ATM).
VALVULA = [
    "................",
    "......VVvu......",
    ".....Vvvvut.....",
    ".....Vvvvut.....",
    ".....Vvvvut.....",
    ".....Vvvvut.....",
    ".....Vvvvut.....",
    ".....Vvvvut.....",
    ".....Vvvvut.....",
    ".....VvGGut.....",
    "....pppppppq....",
    "...Gggggggghh...",
    "...ghhhhhhhi....",
    "....L..ll..m....",
    "....l..mm..n....",
]
TICKS = {0: [], 1: [7], 2: [6, 8], 3: [5, 7, 9], 4: [4, 6, 8, 10]}


def valvula(tier: str) -> Sprite:
    hi, mid, lo = (cor(c) for c in LENTE[tier])
    marca = cor("grafite.1")
    quadros = []
    for k in range(8):
        img = corpo(VALVULA)
        for x in TICKS[MARCAS[tier]]:
            put(img, x, 10, marca)
        if tier in TIERS_ATM:
            for (x, y), c in zip(((4, 13), (7, 13), (8, 13), (11, 13), (4, 14), (7, 14), (8, 14), (11, 14)),
                                 (hi, mid, mid, lo, mid, lo, lo, lo)):
                put(img, x, y, c)
        # Filamento em zigue-zague, pulsando entre a lente e o brilho.
        t = (1 + math.cos(2 * math.pi * k / 8)) / 2
        fil = mistura(mid, hi, t)
        pontos = [(7 if y % 2 else 8, y) for y in range(3, 9)]
        for x, y in pontos:
            put(img, x, y, fil)
        # O vidro em volta do filamento esquenta junto.
        for x, y in pontos:
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 5 <= nx <= 10 and 2 <= ny <= 9 and (nx, ny) not in pontos and img.getpixel((nx, ny))[3]:
                    put(img, nx, ny, mistura(img.getpixel((nx, ny)), hi, 0.45 * t))
        # Um brilho sobe pela parede esquerda do bulbo.
        put(img, 5 if k < 7 else 6, 9 - k, BRANCO)
        quadros.append(img)
    return animacao(quadros, 4)


# Upgrade de Chunk Loading: a Lanterna de Vigia, "fica acesa enquanto você está longe". Lanterna de
# mão: alça de grafite em cima, tampa de porcelana com o anel coral, gaiola de grafite (os dois montantes
# e uma barra fina no meio) em volta do vidro, base de grafite. A chama verde (`chunk`) entra por código,
# na frente da barra do meio, com o miolo claro e um halo no vidro em volta.
LANTERNA = [
    "................",
    "......GGGGG.....",
    "......Ggjgg.....",
    "....WWWppppqq...",
    "...WWpppppppqr..",
    "...Cccccccccdd..",
    "...hVvvvgvvvvh..",
    "...hvvvvgvvvvh..",
    "...hvvvvgvvvuh..",
    "...hvvvvgvvvuh..",
    "...hvvvvgvvuuh..",
    "...hvvvvgvvuuh..",
    "...ggggggggggg..",
    "....hhhhhhhhh...",
    "....iiiiiiiii...",
]
# A chama, quadro a quadro (6), ancorada na base do vidro (y = 11) e centrada em x = 8: 'a' borda
# (chunk.1), 'b' corpo (chunk.2), 'c' miolo (chunk.3). Muda de altura e inclina para os lados.
CHAMA = [
    ["..a..", "..b..", ".bcb.", ".bcb.", "abcba"],
    ["...a.", "..ab.", ".bcb.", ".bcb.", ".bcb.", "abcba"],
    ["..a..", ".ab..", ".bcb.", "abcba"],
    [".a...", ".ba..", ".bb..", ".bcb.", ".bcb.", "abcba"],
    ["..a..", "..b..", ".bcb.", "abcba"],
    ["..a..", "..b..", "..b..", ".bcb.", ".bcb.", "abcba"],
]
def lanterna() -> Sprite:
    leg = legenda(a="chunk.1", b="chunk.2", c="chunk.3")
    halo = mistura(cor("vidro.2"), cor("chunk.2"), 0.7)
    quadros = []
    for forma in CHAMA:
        img = corpo(LANTERNA)
        img.putpixel((8, 2), (0, 0, 0, 0))  # o furo da alça (o contorno já fechou em volta)
        chama: set[tuple[int, int]] = set()
        topo = 12 - len(forma)
        for dy, linha in enumerate(forma):
            for dx, ch in enumerate(linha):
                if ch != ".":
                    chama.add((6 + dx, topo + dy))
        # O halo: o vidro encostado na chama fica esverdeado (só sobre vidro, nunca sobre a gaiola).
        for x, y in chama:
            for nx, ny in ((x + 1, y), (x - 1, y), (x, y - 1), (x, y + 1)):
                if (nx, ny) not in chama and 4 <= nx <= 12 and 6 <= ny <= 11 and nx != 8:
                    put(img, nx, ny, halo)
        for dy, linha in enumerate(forma):
            for dx, ch in enumerate(linha):
                if ch != ".":
                    put(img, 6 + dx, topo + dy, leg[ch])
        quadros.append(img)
    return animacao(quadros, 5)


# Upgrade de Alcance: a Rosa, o roteador visto de cima como módulo. Casco quadrado de porcelana com os
# quatro para-choques de grafite nos cantos, o Olho no centro e quatro setas coral saindo pelos quatro
# lados, a expansão do alcance. Uma luz corre do Olho por cada haste até a ponta, as quatro ao mesmo
# tempo, e as pontas brilham quando a luz chega; o Olho pulsa no compasso.
ROSA = [
    "................",
    ".......C........",
    "......Ccd.......",
    "...GhWWcWWGh....",
    "...hhppcpphh....",
    "...Wpxxxxxpq....",
    "..CWpxxxxxpqc...",
    ".Ccccxxxxxcccd..",
    "..dWpxxxxxpqd...",
    "...Wpxxxxxpq....",
    "...GhppcppGh....",
    "...hhqqcqqhh....",
    "......ccd.......",
    ".......d........",
]
# As quatro direções (dx, dy) das setas e as posições ao longo de cada haste: i = 3, 4 dentro do
# casco, i = 5 a linha da cabeça e i = 6 a ponta.
DIRECOES_ROSA = [(0, -1), (1, 0), (0, 1), (-1, 0)]


def rosa() -> Sprite:
    luz, rastro = cor("coral.4"), mistura(cor("coral.4"), cor("coral.3"), 0.5)
    quadros = []
    for k in range(8):
        img = corpo(ROSA)
        for dx, dy in DIRECOES_ROSA:
            if k == 1:
                put(img, 7 + dx * 3, 7 + dy * 3, luz)
            elif k == 2:
                put(img, 7 + dx * 3, 7 + dy * 3, rastro)
                put(img, 7 + dx * 4, 7 + dy * 4, luz)
            elif k == 3:
                put(img, 7 + dx * 4, 7 + dy * 4, rastro)
                for j in (-1, 0, 1):
                    put(img, 7 + dx * 5 + dy * j, 7 + dy * 5 + dx * j, luz)
                put(img, 7 + dx * 6, 7 + dy * 6, BRANCO)
            elif k == 4:
                put(img, 7 + dx * 6, 7 + dy * 6, luz)
        pulso(img, 7, 7, "vidro", 2, k, 8)
        quadros.append(img)
    return animacao(quadros, 4)


# Guia: caderno de campo. Capa coral com o Olho em porcelana e uma etiqueta, páginas de porcelana
# aparecendo à direita e embaixo, espiral de grafite na lombada.
GUIA = [
    "................",
    "................",
    "..ghCCCCCCCCC...",
    "...hCcccccccdq..",
    "..ghCcccccccdq..",
    "...hCccxxxccdq..",
    "..ghCcxxxxxcdq..",
    "...hCcxxxxxcdq..",
    "..ghCcxxxxxcdq..",
    "...hCccxxxccdq..",
    "..ghCcccccccdq..",
    "...hCcWpppqcdq..",
    "..ghCcqqqqrcdq..",
    "...hCdddddddeq..",
    ".....qqqqqqqqr..",
]


def guia() -> Sprite:
    quadros = []
    for k in range(4):
        img = corpo(GUIA)
        pulso(img, 8, 7, "porcelana", 2, k, 4)
        quadros.append(img)
    return animacao(quadros, 8)


# ---------------------------------------------------------------------------
# Interface: "crachás de linha de metrô"
# ---------------------------------------------------------------------------
#
# Os ícones da interface são crachás: formas cheias, uniformes e de alto contraste, pensados para ler
# em 1× sobre o fundo claro das telas (porcelana.3 e porcelana.4). Nada de objetos miúdos: um disco na
# cor do recurso com um símbolo branco de traço grosso (tipos) e uma tecla quadrada de porcelana com
# uma seta grossa na cor do modo (portas).

BRANCO_PURO = cor("#FFFFFF")
PRANCHA_ICONES = PREVIEW / "icones-preview.png"


def _interior(forma: set[tuple[int, int]]) -> set[tuple[int, int]]:
    """Os pixels da forma que não tocam o lado de fora (vizinhança de 4)."""
    return {(x, y) for x, y in forma
            if all((x + dx, y + dy) in forma for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))}


def _sombra_baixo_direita(dentro: set[tuple[int, int]]) -> set[tuple[int, int]]:
    """A faixa de 1 px da forma que fica embaixo ou à direita (o vizinho de baixo ou da direita é
    fora da forma), sem a parte de cima/esquerda: a sombra de uma forma iluminada de cima à esquerda."""
    return {(x, y) for x, y in dentro
            if ((x + 1, y) not in dentro or (x, y + 1) not in dentro)
            and (x - 1, y) in dentro and (x, y - 1) in dentro}


# --- Portas (16×16): teclas quadradas de porcelana com a seta do modo -------------------------------
#
# A tecla ocupa de (1,1) a (14,14) com os quatro cantos cortados: fundo porcelana.4, borda de 1 px em
# grafite.2, luz branca em cima e à esquerda e sombra de tecla porcelana.1 embaixo e à direita (dentro
# da borda). No centro, a seta (3 px de haste, cabeça de 7 px) na cor do modo (`MODO`), com 1 px de
# sombra ×0,75 embaixo/à direita e contorno de 1 px em grafite.2: Extrai sobe, Insere desce, Armazém
# tem cabeça em cima e embaixo com a haste curta. Nenhum é a tecla apagada (`porta_nenhum`).
#
# Grades 16×16 das setas ('S' é a seta; o resto é a tecla).
SETAS = {
    "extract": [
        "................",
        "................",
        "................",
        "................",
        ".......S........",
        "......SSS.......",
        ".....SSSSS......",
        "....SSSSSSS.....",
        "......SSS.......",
        "......SSS.......",
        "......SSS.......",
        "......SSS.......",
        "................",
    ],
    "insert": [
        "................",
        "................",
        "................",
        "................",
        "......SSS.......",
        "......SSS.......",
        "......SSS.......",
        "......SSS.......",
        "....SSSSSSS.....",
        ".....SSSSS......",
        "......SSS.......",
        ".......S........",
        "................",
    ],
    "both": [
        "................",
        "................",
        "................",
        ".......S........",
        "......SSS.......",
        ".....SSSSS......",
        "....SSSSSSS.....",
        "......SSS.......",
        "......SSS.......",
        "....SSSSSSS.....",
        ".....SSSSS......",
        "......SSS.......",
        ".......S........",
        "................",
    ],
}
TECLA_MIN, TECLA_MAX = 1, 14


def _tecla(fundo: Cor, borda: Cor | None, luz: bool) -> Image.Image:
    """A tecla de porcelana: quadrado de (1,1) a (14,14) com os cantos cortados."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    a, b = TECLA_MIN, TECLA_MAX
    cantos = {(a, a), (a, b), (b, a), (b, b)}
    for y in range(a, b + 1):
        for x in range(a, b + 1):
            if (x, y) in cantos:
                continue
            na_borda = x in (a, b) or y in (a, b)
            if na_borda:
                if borda is not None:
                    img.putpixel((x, y), borda)
                continue
            c = fundo
            if luz:
                if y == b - 1 or x == b - 1:
                    c = cor("porcelana.1")  # sombra da tecla
                if y == a + 1 or x == a + 1:
                    c = BRANCO_PURO  # luz da tecla
                    if (x, y) in ((a + 1, b - 1), (b - 1, a + 1)):
                        c = cor("porcelana.3")  # onde a luz encontra a sombra
            img.putpixel((x, y), c)
    return img


def porta(modo: str) -> Sprite:
    img = _tecla(cor("porcelana.4"), cor("grafite.2"), luz=True)
    base = cor(MODO[modo])
    seta = {(x, y) for y, linha in enumerate(SETAS[modo]) for x, ch in enumerate(linha) if ch == "S"}
    sombra = _sombra_baixo_direita(seta)
    forma = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for x, y in seta:
        forma.putpixel((x, y), escurece(base, 0.75) if (x, y) in sombra else base)
    img.alpha_composite(contorno(forma, cor("grafite.2")))
    return parado(img)


def porta_nenhum() -> Sprite:
    """Nenhum: a tecla apagada. Fundo porcelana.2, borda tracejada em grafite.4 (traço de 2 px, vão
    de 1) e um traço horizontal de 5×2 px em porcelana.0 no centro, no lugar da seta."""
    img = _tecla(cor("porcelana.2"), None, luz=False)
    a, b = TECLA_MIN, TECLA_MAX
    tracejado = cor("grafite.4")
    # A borda percorrida no sentido horário a partir do canto de cima à esquerda (sem os cantos).
    borda = [(x, a) for x in range(a + 1, b)] + [(b, y) for y in range(a + 1, b)] + \
            [(x, b) for x in range(b - 1, a, -1)] + [(a, y) for y in range(b - 1, a, -1)]
    for n, (x, y) in enumerate(borda):
        if n % 3 != 2:
            img.putpixel((x, y), tracejado)
    traco = cor("porcelana.0")
    for y in (7, 8):
        for x in range(5, 10):
            img.putpixel((x, y), traco)
    return parado(img)


# --- Ícones de tipo (9×9): disco na cor do recurso com um símbolo branco ---------------------------
#
# O ícone é um disco cheio de 9 px de diâmetro na cor do recurso (`RECURSO`): tom cheio em cima,
# ×0,75 na faixa de baixo à direita (sombra de 1 px), um pixel de brilho em cima à esquerda e contorno
# de 1 px em grafite.1. Dentro, um símbolo branco de traço grosso, 5×5 no máximo, com 1 px de sombra do
# tom escuro logo abaixo, onde cabe no disco (a sombra em diagonal fazia o símbolo virar mancha). Ocupa o
# canto de cima à esquerda da textura 16×16 (a tela recorta 9×9).
#
# Os símbolos, grades 5×5: 'W' branco; 'd' o tom escuro do disco (detalhe interno); no cubo, 'T' a face
# de cima (branco), 'L' a da esquerda e 'R' a da direita (branco tingido pela cor do recurso, para as
# três faces lerem só pelo tom) e 'd' a aresta vertical da frente (as arestas de cima do Y, em 5 px,
# viravam dois olhos).
ICONES_TIPO = {
    "item": [  # saco amarrado: as duas orelhas do laço, o nó escuro e o corpo de fundo reto
        "W...W",
        ".WdW.",
        ".WWW.",
        "WWWWW",
        ".WWW.",
    ],
    "fluid": [  # gota: ponta fina em cima, barriga redonda embaixo
        "..W..",
        "..W..",
        ".WWW.",
        "WWWWW",
        ".WWW.",
    ],
    "energy": [  # raio: desce da direita, dá o degrau e segue para a esquerda
        "...WW",
        "..WW.",
        ".WWWW",
        "..WW.",
        ".WW..",
    ],
    "chemical": [  # frasco erlenmeyer: gargalo alto e a base larga
        "..W..",
        "..W..",
        "..W..",
        ".WWW.",
        "WWWWW",
    ],
    "source": [  # estrela de 4 pontas com o ponto no meio
        "..W..",
        ".WWW.",
        "WWdWW",
        ".WWW.",
        "..W..",
    ],
}
DISCO_RAIO = 4.5


def _disco() -> set[tuple[int, int]]:
    """O disco de 9 px de diâmetro centrado em (4, 4)."""
    return {(x, y) for x in range(9) for y in range(9) if (x - 4) ** 2 + (y - 4) ** 2 <= DISCO_RAIO ** 2}


def icone_tipo(nome: str) -> Sprite:
    grade = ICONES_TIPO[nome]
    assert len(grade) <= 5 and all(len(l) <= 5 for l in grade), f"ícone {nome} passa do 5×5"
    base = cor(RECURSO[nome])
    escuro = escurece(base, 0.75)
    disco = _disco()
    dentro = _interior(disco)
    sombra = _sombra_baixo_direita(dentro)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    grafite = cor("grafite.1")
    for x, y in disco:
        if (x, y) not in dentro:
            img.putpixel((x, y), grafite)
        else:
            img.putpixel((x, y), escuro if (x, y) in sombra else base)
    img.putpixel((2, 1), mistura(base, BRANCO_PURO, 0.6))  # o brilho
    # O símbolo, centrado no disco (o 5×5 vai de (2,2) a (6,6)), com a sombra para baixo e à direita.
    simbolo = {(x + 2, y + 2): ch for y, linha in enumerate(grade) for x, ch in enumerate(linha) if ch != "."}
    for (x, y), ch in simbolo.items():
        p = (x, y + 1)
        if p in dentro and p not in simbolo:
            img.putpixel(p, escuro)
    tons = {"W": BRANCO_PURO, "T": BRANCO_PURO, "d": escuro,
            "L": mistura(BRANCO_PURO, base, 0.3), "R": mistura(BRANCO_PURO, base, 0.6)}
    for (x, y), ch in simbolo.items():
        img.putpixel((x, y), tons[ch])
    assert all(img.getpixel((x, y))[3] == 0 for x in range(16) for y in range(16) if x > 8 or y > 8), \
        f"ícone {nome} passa do 9×9"
    return parado(img)


def prancha_icones(itens: dict[str, Sprite]) -> Image.Image:
    """A prancha dos 9 ícones da interface em 1×, 2×, 4× e 8× sobre os dois fundos das telas
    (porcelana.3 e porcelana.4); as portas também apagadas (alpha 0,5), como nos botões."""
    fonte = _fonte(13)
    fundos = [("porcelana.3  #F2EBDD", cor("porcelana.3")), ("porcelana.4  #FFFBF3", cor("porcelana.4"))]
    escalas = [1, 2, 4, 8]
    portas = ["port_extract", "port_insert", "port_both", "port_none"]
    tipos = [f"type/{n}" for n in ICONES_TIPO]
    margem, gap = 16, 10
    coluna = 16 * 8 + 3 * gap  # a célula mais larga (8×)
    largura = margem * 2 + 90 + coluna * (len(portas) + len(tipos))
    out = Image.new("RGBA", (largura, 1400), cor("porcelana.3"))
    d = ImageDraw.Draw(out)
    tinta = cor("grafite.1")
    y = margem
    d.text((margem, y), "Ícones da interface: crachás (portas 16×16 e tipos 9×9), em 1×, 2×, 4× e 8×",
           fill=tinta, font=_fonte(17))
    y += 30

    def quadro(nome: str) -> Image.Image:
        q = itens[f"gui/{nome}"].quadro(0)
        return q.crop((0, 0, 9, 9)) if nome.startswith("type/") else q

    def alfa(img: Image.Image, a: float) -> Image.Image:
        canal = img.getchannel("A").point(lambda v: int(v * a))
        saida = img.copy()
        saida.putalpha(canal)
        return saida

    for rotulo, fundo in fundos:
        for apagado in (False, True):
            bloco_alt = 24 + sum(16 * k + 4 for k in escalas) + 22
            d.rectangle([(margem, y), (largura - margem, y + bloco_alt)], fill=fundo)
            d.text((margem + 4, y + 4), rotulo + ("  ·  portas com alpha 0,5" if apagado else ""),
                   fill=tinta, font=fonte)
            x = margem + 90
            for nome in portas + tipos:
                img = quadro(nome)
                if apagado and nome in portas:
                    img = alfa(img, 0.5)
                yy = y + 24
                for k in escalas:
                    out.alpha_composite(_amplia(img, k), (x, yy))
                    yy += img.height * k + 4
                d.text((x, y + bloco_alt - 18), nome.removeprefix("port_").removeprefix("type/"),
                       fill=tinta, font=fonte)
                x += coluna
            y += bloco_alt + gap
    # Uma fila como no jogo: as portas lado a lado em botões de 16 px, com a seleção apagada.
    d.text((margem, y), "Como nos botões das faces (1× e 2×): a porta acesa e as outras apagadas",
           fill=tinta, font=fonte)
    y += 20
    for k in (1, 2):
        x = margem
        for sel in range(4):
            for n, nome in enumerate(portas):
                img = quadro(nome) if n == sel else alfa(quadro(nome), 0.5)
                out.alpha_composite(_amplia(img, k), (x, y))
                x += 18 * k
            x += 12 * k
        y += 16 * k + 8
    return out.crop((0, 0, largura, y + margem))


# ---------------------------------------------------------------------------
# Cristais do Ressonador de Sinal (o Opaco é o cristal descarregado, o Rachado o sem integridade)
# ---------------------------------------------------------------------------
#
# As três famílias (Bruto, Lapidado e os Afinados por tier) são grades da mesma legenda: `A a b c d` os
# cinco tons do cristal (do brilho à sombra), `L l m` a cinta (latão no Lapidado, a cor do tier no Afinado)
# e `K k j` as duas gemas do material do tier no Afinado. O Opaco e o Rachado trocam a paleta do cristal
# pela pedra cinza e apagam a cinta e as gemas.

CRISTAL_SINAL = {"4": "#FFF0EA", "3": "#FFC9B8", "2": "#F2917A", "1": "#C9604B", "0": "#8A3A2C"}
CRISTAL_OPACO = {"4": "#F4F0EA", "3": "#D9D2C8", "2": "#B5ADA2", "1": "#8C857C", "0": "#5E5952"}

BRUTO = ["................", ".........A......", "........Aab.....", "...A....Aabc....", "..Aab...Aabc....",
         "..Aabc..Aabc....", "..Aabc.AAabc....", "..Aabc.Aaabc....", "..Aabc.Aaabc.A..", ".AAabcAAaabcAab.",
         ".AaabcAaaabcAbc.", ".AaabcAaaabcabc.", ".bbbcdbbbbcdbcd.", ".cccddcccccddd..", "..dddddddddd....", "................"]
LAPIDADO = ["................", "................", ".......AA.......", "......AAab......", ".....AAaabb.....",
            "....AAaaabbc....", "...AAaaaabbcc...", "...LLLLLllllm...", "...aaaabbbbcc...", "....aaabbbcc....",
            ".....aabbcc.....", "......abcc......", ".......cd.......", "................", "................", "................"]
# O Afinado: a mesma gema do Lapidado, com a cinta de 2 px (o filete de luz em cima, a sombra embaixo e
# na ponta direita) e as duas gemas em cruz, uma acima e outra abaixo da cinta.
AFINADO = ["................", "................", ".......AA.......", "......AKab......", ".....AkkJb......",
           "....AAajabbc....", "...AAaaaabbcc...", "...LLLLLLLLlm...", "...lllllmmmmm...", "...aaaabbbbcc...",
           "....aaaKbbcc....", ".....akkJcc.....", "......abjc......", ".......cd.......", "................", "................"]

# A cor de cada tier de Afinado (a lente do Olho, `LENTE`, a mesma do `GuiPaint.tierColor`).
TIERS_AFINADO = ["elite", "emerald", "allthemodium", "vibranium", "unobtainium", "ultimate"]
RACHADURA = "#2A2522"


def _cinta_tier(tier: str, apagada: bool = False) -> dict[str, Cor]:
    """Os três tons da cinta e das gemas pela lente do tier: brilho, lente e sombra. Apagada (Opaco e
    Rachado), cada tom vai para o meio da pedra cinza, e as gemas ficam só na sombra."""
    hi, mid, lo = (cor(c) for c in LENTE[tier])
    if apagada:
        cinza = cor(CRISTAL_OPACO["1"])
        hi, mid, lo = mistura(hi, cinza, .55), mistura(mid, cinza, .55), mistura(lo, cinza, .45)
        return {"L": hi, "l": mid, "m": lo, "K": mid, "k": lo, "J": lo, "j": lo}
    return {"L": mistura(hi, BRANCO, .4), "l": mid, "m": lo, "K": hi, "k": mid, "J": lo, "j": lo}


def _leg_cristal(pal: dict[str, str], cinta: dict[str, Cor] | None = None) -> dict[str, Cor]:
    return {**legenda(L="latao.3", l="latao.2", m="latao.1"),
            "A": cor(pal["4"]), "a": cor(pal["3"]), "b": cor(pal["2"]), "c": cor(pal["1"]), "d": cor(pal["0"]),
            **(cinta or {})}


def _cristal_base(grade: list[str], pal: dict[str, str], cinta: dict[str, Cor] | None = None,
                  furos: tuple[tuple[int, int], ...] = ()) -> Image.Image:
    """A forma do cristal na paleta dada, menos os `furos` (a lasca que falta no Rachado), com o contorno."""
    img = pinta(grade, _leg_cristal(pal, cinta))
    for p in furos:
        img.putpixel(p, (0, 0, 0, 0))
    return contorno(img, CONTORNO)


def _interior_px(img: Image.Image) -> set[tuple[int, int]]:
    return {(x, y) for y in range(16) for x in range(16)
            if img.getpixel((x, y))[3] and img.getpixel((x, y)) != CONTORNO}


def _pedra_px(img: Image.Image, pal: dict[str, str]) -> set[tuple[int, int]]:
    """Os pixels que são o corpo do cristal (nenhum da cinta, das gemas ou do contorno)."""
    corpo_ = {cor(v) for v in pal.values()}
    return {(x, y) for y in range(16) for x in range(16) if img.getpixel((x, y)) in corpo_}


def cristal() -> Sprite:
    """Cristal de Sinal bruto: um brilho diagonal passa pelo corpo e um ponto cintila no topo (10 quadros × 4 ticks)."""
    q = []
    for k in range(10):
        img = _cristal_base(BRUTO, CRISTAL_SINAL)
        faixa = k * 3 - 6
        for (x, y) in _interior_px(img):
            d = (x - y) - faixa + 6
            if 0 <= d <= 1:
                put(img, x, y, mistura(img.getpixel((x, y)), BRANCO, .7 if d == 0 else .4))
        if k in (6, 7):
            put(img, 9, 1, BRANCO)
            if k == 6:
                for dx, dy in ((1, 0), (-1, 0), (0, -1)):
                    if img.getpixel((9 + dx, 1 + dy))[3] == 0:
                        put(img, 9 + dx, 1 + dy, cor("#FFC9B8"))
        q.append(img)
    return animacao(q, 4)


def _onda(img: Image.Image, k: int) -> None:
    """A onda que sai da pedra nos quadros 8 a 10 do Lapidado e do Afinado: um losango de luz no ar em volta."""
    if k in (8, 9, 10):
        r = {8: 1, 9: 2, 10: 2}[k]
        c = cor("coral.3") if k < 10 else cor("#FFC9B8")
        for yy in range(16):
            for xx in range(16):
                if img.getpixel((xx, yy))[3]:
                    continue
                d = abs(xx - 7.5) + abs(yy - 7)
                if 5 + r <= d < 6 + r:
                    put(img, xx, yy, c)


def lapidado() -> Sprite:
    """Cristal Lapidado: o brilho corre pela cinta de latão, o miolo pulsa e uma onda sai da pedra (12 × 3 ticks)."""
    q = []
    for k in range(12):
        img = _cristal_base(LAPIDADO, CRISTAL_SINAL)
        x = 3 + k
        if 3 <= x <= 12:
            put(img, x, 7, BRANCO)
        if 3 <= x - 1 <= 12:
            put(img, x - 1, 7, cor("latao.3"))
        t = (1 + math.cos(2 * math.pi * k / 12)) / 2
        for (px, py) in ((6, 4), (5, 5), (6, 5), (7, 3), (4, 6), (5, 6)):
            put(img, px, py, mistura(cor(CRISTAL_SINAL["3"]), BRANCO, .6 * t))
        _onda(img, k)
        q.append(img)
    return animacao(q, 3)


# O pixel de brilho de cada gema do Afinado (o de cima e o de baixo) e o miolo que pulsa.
GEMAS_AFINADO = ((7, 3), (7, 10))
MIOLO_AFINADO = ((5, 5), (6, 6), (5, 6), (4, 6), (9, 10), (9, 11))


def afinado(tier: str) -> Sprite:
    """Cristal Afinado do tier: o Lapidado com a cinta na cor do tier e duas gemas do material. O brilho corre
    pelo filete da cinta, o miolo pulsa, as gemas cintilam uma de cada vez e a onda sai da pedra (12 × 3 ticks)."""
    q = []
    cinta = _cinta_tier(tier)
    hi = cor(LENTE[tier][0])
    for k in range(12):
        img = _cristal_base(AFINADO, CRISTAL_SINAL, cinta)
        x = 3 + k
        if 3 <= x <= 12:
            put(img, x, 7, BRANCO)
        if 3 <= x - 1 <= 12:
            put(img, x - 1, 7, mistura(cinta["L"], BRANCO, .5))
        t = (1 + math.cos(2 * math.pi * k / 12)) / 2
        for (px, py) in MIOLO_AFINADO:
            put(img, px, py, mistura(img.getpixel((px, py)), BRANCO, .5 * t))
        # As gemas cintilam: a de cima nos quadros 2 e 3, a de baixo nos 8 e 9.
        if k in (2, 3):
            put(img, *GEMAS_AFINADO[0], BRANCO if k == 2 else mistura(hi, BRANCO, .5))
        if k in (8, 9):
            put(img, *GEMAS_AFINADO[1], BRANCO if k == 8 else mistura(hi, BRANCO, .5))
        _onda(img, k)
        q.append(img)
    return animacao(q, 3)


# Os pixels da brasa do Opaco, no pé de cada forma (todos dentro do contorno).
BRASA_BRUTO = ((4, 11), (9, 11), (8, 10))
BRASA_LAPIDADO = ((6, 9), (7, 10), (8, 11))
BRASA_AFINADO = ((5, 10), (6, 11), (7, 12))


def opaco(grade: list[str], brasa_px: tuple[tuple[int, int], ...], tier: str | None = None) -> Sprite:
    """O cristal sem carga, na forma da grade dada: pedra cinza com uma brasa fraca que respira (8 × 6 ticks).
    Com `tier`, o Afinado com a cinta e as gemas apagadas."""
    q = []
    cinta = _cinta_tier(tier, apagada=True) if tier else None
    for k in range(8):
        img = _cristal_base(grade, CRISTAL_OPACO, cinta)
        dentro = _interior_px(img)
        t = (1 + math.cos(2 * math.pi * k / 8)) / 2
        brasa = mistura(cor(CRISTAL_OPACO["1"]), cor("#C9604B"), .55 * t)
        for p in brasa_px:
            assert p in dentro, f"brasa fora do cristal em {p}"
            put(img, *p, brasa)
        q.append(img)
    return animacao(q, 6)


# A rachadura de cada forma: a lasca que faltou na borda (`furos`, fica transparente e o contorno fecha
# em volta) e o traço escuro que desce dela pelo corpo, só sobre a pedra (a cinta e as gemas ficam
# inteiras). De dois em dois pixels, o lado esquerdo do traço ganha um filete claro, a borda viva da fenda.
RACHADURA_BRUTO = (((11, 4), (11, 5)), ((10, 5), (10, 6), (9, 7), (9, 8), (8, 9), (8, 10), (7, 11)))
RACHADURA_LAPIDADO = (((10, 4), (11, 5)), ((9, 5), (9, 6), (8, 7), (8, 8), (7, 9), (7, 10), (6, 11)))
RACHADURA_AFINADO = (((10, 4), (11, 5)), ((9, 5), (9, 6), (8, 7), (8, 8), (7, 9), (6, 10), (6, 11)))


def rachado(grade: list[str], rachadura: tuple[tuple[tuple[int, int], ...], tuple[tuple[int, int], ...]],
            tier: str | None = None) -> Sprite:
    """O cristal sem integridade: a paleta do Opaco, lascado na borda e cortado por uma rachadura escura de
    onde vaza um brilho fraco que vai e vem (8 × 6 ticks)."""
    furos, traco = rachadura
    cinta = _cinta_tier(tier, apagada=True) if tier else None
    q = []
    for k in range(8):
        img = _cristal_base(grade, CRISTAL_OPACO, cinta, furos)
        pedra = _pedra_px(img, CRISTAL_OPACO)
        dentro = _interior_px(img)
        t = (1 + math.cos(2 * math.pi * k / 8)) / 2
        luz = mistura(cor(RACHADURA), cor("#F2917A"), .6 * t)
        for n, (x, y) in enumerate(traco):
            assert (x, y) in dentro, f"rachadura fora do cristal em {(x, y)}"
            if (x, y) not in pedra:
                continue  # a cinta e as gemas ficam inteiras: a rachadura passa por baixo delas
            # O vazamento: do meio do traço para as pontas, cada vez mais fraco.
            peso = max(0.0, 1 - abs(n - len(traco) / 2) / (len(traco) / 2))
            put(img, x, y, mistura(cor(RACHADURA), luz, peso))
            if n % 2 == 0 and (x - 1, y) in pedra and (x - 1, y) not in traco:
                put(img, x - 1, y, cor(CRISTAL_OPACO["4"]))
        q.append(img)
    return animacao(q, 6)


# ---------------------------------------------------------------------------
# As bases do vanilla: o lingote, o minério bruto e o pó, recoloridos na paleta do mod
# ---------------------------------------------------------------------------
#
# A liga crua, o lingote e os pós partem das texturas do próprio Minecraft (`raw_iron`, `iron_ingot` e
# `redstone`), lidas do jar do cliente na hora de gerar: a silhueta e o sombreado ficam os do vanilla, pixel a
# pixel, e só a cor muda, por luminância, para a rampa do material do mod (o contorno da base vira o
# `grafite.0` dos itens). O jar não entra no repositório: vem do cache do NeoForm (o `./gradlew build` o
# baixa) ou do caminho em `WA_MC_JAR`.

VERSAO_MC = "1.21.1"


def _jar_vanilla() -> Path:
    env = os.environ.get("WA_MC_JAR")
    candidatos = [Path(env)] if env else []
    candidatos += sorted(Path.home().glob(f".gradle/caches/neoformruntime/artifacts/minecraft_{VERSAO_MC}_client.jar"))
    for c in candidatos:
        if c.is_file():
            return c
    raise SystemExit(f"jar do cliente do Minecraft {VERSAO_MC} não encontrado: rode ./gradlew build antes ou "
                     "aponte WA_MC_JAR para ele")


_BASES: dict[str, Image.Image] = {}


def base_vanilla(nome: str) -> Image.Image:
    """A textura `item/<nome>` do Minecraft, 16 × 16 RGBA (guardada depois da primeira leitura)."""
    if nome not in _BASES:
        with zipfile.ZipFile(_jar_vanilla()) as jar:
            with jar.open(f"assets/minecraft/textures/item/{nome}.png") as arq:
                img = Image.open(arq).convert("RGBA")
                img.load()
        assert img.size == (16, 16), f"{nome}: {img.size}"
        _BASES[nome] = img
    return _BASES[nome].copy()


def _luminancia(c: Cor) -> float:
    return .299 * c[0] + .587 * c[1] + .114 * c[2]


def recolorir(base: Image.Image, rampa: list[Cor]) -> Image.Image:
    """A base com os tons trocados pelos da `rampa` (da luz à sombra), por luminância: o tom mais escuro da
    base (o contorno dela) vira o contorno dos itens, e os outros caem na rampa na mesma proporção em que
    estavam entre o mais claro e o segundo mais escuro."""
    tons = sorted({p for p in base.getdata() if p[3]}, key=_luminancia, reverse=True)
    assert len(tons) >= 3, "base com menos de três tons"
    claro, escuro = _luminancia(tons[0]), _luminancia(tons[-2])
    troca: dict[Cor, Cor] = {tons[-1]: CONTORNO}
    for c in tons[:-1]:
        t = (claro - _luminancia(c)) / (claro - escuro) * (len(rampa) - 1)
        i = min(int(t), len(rampa) - 2)
        troca[c] = mistura(rampa[i], rampa[i + 1], t - i)
    img = base.copy()
    for y in range(16):
        for x in range(16):
            p = img.getpixel((x, y))
            if p[3]:
                img.putpixel((x, y), troca[p])
    return img


def _pixels_do_tom(base: Image.Image, posicao: int) -> list[tuple[int, int]]:
    """Os pixels do `posicao`-ésimo tom mais claro da base, por ordem de leitura."""
    tons = sorted({p for p in base.getdata() if p[3]}, key=_luminancia, reverse=True)
    alvo = tons[posicao]
    return [(x, y) for y in range(16) for x in range(16) if base.getpixel((x, y)) == alvo]


def _espalhados(pontos: list[tuple[int, int]], n: int) -> tuple[tuple[int, int], ...]:
    """`n` pontos da lista, espalhados por ela em intervalos iguais."""
    return tuple(pontos[(i * len(pontos)) // n] for i in range(n))


# ---------------------------------------------------------------------------
# Pó de Ressonância e os Pós Carregados
# ---------------------------------------------------------------------------
#
# O monte do pó de redstone do vanilla, recolorido. As centelhas cintilam nos grãos mais claros do monte
# (`CENTELHAS_PO`, quatro deles espalhados), cada uma num quadro.


def _po(rampa: list[Cor], quadros: int, faisca: Cor) -> Sprite:
    base = base_vanilla("redstone")
    monte = recolorir(base, rampa)
    centelhas = _espalhados(_pixels_do_tom(base, 0) + _pixels_do_tom(base, 1), 4)
    corpo = set(rampa) | {troca for troca in monte.getdata() if troca[3] and troca != CONTORNO}
    q = []
    for k in range(quadros):
        img = monte.copy()
        for n, (x, y) in enumerate(centelhas):
            fase = (k - n * (quadros // len(centelhas))) % quadros
            if fase == 0:
                put(img, x, y, BRANCO)
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    if 0 <= x + dx < 16 and 0 <= y + dy < 16 and img.getpixel((x + dx, y + dy)) in corpo:
                        put(img, x + dx, y + dy, faisca)
            elif fase == 1:
                put(img, x, y, faisca)
        q.append(img)
    return animacao(q, 5)


def po() -> Sprite:
    """Pó de Ressonância: o monte de pó coral e vermelho, com as centelhas douradas cintilando (8 × 5 ticks)."""
    rampa = [cor("#FF9A80"), cor("#E5604A"), cor("#B8392F"), cor("#7E2420")]
    return _po(rampa, 8, cor("#FFE68A"))


TIERS_PO_CARREGADO = ["allthemodium", "vibranium", "unobtainium", "ultimate"]


def po_carregado(tier: str) -> Sprite:
    """Pó Carregado do tier: o mesmo monte na cor do tier (a lente do Olho), com as centelhas no brilho
    da lente, mais vivas (8 × 5 ticks)."""
    hi, mid, lo = (cor(c) for c in LENTE[tier])
    rampa = [mistura(hi, mid, .35), mid, lo, mistura(lo, cor("#000000"), .35)]
    return _po(rampa, 8, mistura(hi, BRANCO, .5))


# ---------------------------------------------------------------------------
# A cascata do Ressonador: da liga ao Núcleo Condensado, cada peça maior e mais acesa que a anterior
# ---------------------------------------------------------------------------
#
# As matérias-primas (liga crua, lingote, placa, fio, bobina) seguem a silhueta e o sombreado dos itens do
# vanilla (o minério bruto, o lingote de ferro, o pó de redstone), na liga rosada do mod (`LIGA`) e no cobre.
# Os componentes (lente, circuito, matriz, estabilizador, núcleo) têm cada um uma silhueta própria, para se
# reconhecerem de relance no inventário: o disco com aro, o cartão com pinos, a grade, o octógono com o
# giroscópio e a esfera com janelas. Cada peça é uma grade com a legenda `LEG_CASCATA` (própria, sem a comum,
# porque os materiais são outros) ou um desenho por geometria; a animação de cada uma é um brilho que
# percorre, um pulsar ou uma cintilação, no ritmo dos outros itens do mod.

FERRO = {"4": "#F2F1EC", "3": "#D3D1CA", "2": "#A9A69E", "1": "#7A776F", "0": "#4F4C46"}
LIGA = {"4": "#FFDCCF", "3": "#F2A58E", "2": "#D4785F", "1": "#A04D3B", "0": "#632C21"}
COBRE = {"4": "#FFD3B0", "3": "#F2A26E", "2": "#D27542", "1": "#9F4E27", "0": "#6A3218"}
VERDE_PLACA = {"4": "#9EDD93", "3": "#4FAE57", "2": "#2C843F", "1": "#1B5A2C", "0": "#10381B"}
OBSIDIANA = {"3": "#B98AE6", "2": "#6F3DB5", "1": "#421C74", "0": "#221038"}
DIAMANTE = {"3": "#E8FFFB", "2": "#93F1E3", "1": "#3FC8B9", "0": "#1F7E74"}
NETHERITA = {"3": "#75696F", "2": "#524850", "1": "#372F36", "0": "#221C22"}

# Legenda da cascata: porcelana W w · grafite G g h i j · coral C c d e f · latão L l m n · vidro V v u t
# · ferro E F y Y z · liga A a b B 0 · cobre S R r o O · placa P p Q q 9 · obsidiana N n M 7
# · diamante D T U Z · netherita H 3 2 1 · cristal K k J j (todos da luz à sombra).
LEG_CASCATA: dict[str, Cor] = {
    "W": cor("porcelana.4"), "w": cor("porcelana.2"),
    "G": cor("grafite.4"), "g": cor("grafite.3"), "h": cor("grafite.2"), "i": cor("grafite.1"), "j": cor("grafite.0"),
    "C": cor("coral.4"), "c": cor("coral.3"), "d": cor("coral.2"), "e": cor("coral.1"), "f": cor("coral.0"),
    "L": cor("latao.3"), "l": cor("latao.2"), "m": cor("latao.1"), "n": cor("latao.0"),
    "V": cor("vidro.3"), "v": cor("vidro.2"), "u": cor("vidro.1"), "t": cor("vidro.0"),
    "E": cor(FERRO["4"]), "F": cor(FERRO["3"]), "y": cor(FERRO["2"]), "Y": cor(FERRO["1"]), "z": cor(FERRO["0"]),
    "A": cor(LIGA["4"]), "a": cor(LIGA["3"]), "b": cor(LIGA["2"]), "B": cor(LIGA["1"]), "0": cor(LIGA["0"]),
    "S": cor(COBRE["4"]), "R": cor(COBRE["3"]), "r": cor(COBRE["2"]), "o": cor(COBRE["1"]), "O": cor(COBRE["0"]),
    "P": cor(VERDE_PLACA["4"]), "p": cor(VERDE_PLACA["3"]), "Q": cor(VERDE_PLACA["2"]), "q": cor(VERDE_PLACA["1"]),
    "9": cor(VERDE_PLACA["0"]),
    "N": cor(OBSIDIANA["3"]), "n": cor(OBSIDIANA["2"]), "M": cor(OBSIDIANA["1"]), "7": cor(OBSIDIANA["0"]),
    "D": cor(DIAMANTE["3"]), "T": cor(DIAMANTE["2"]), "U": cor(DIAMANTE["1"]), "Z": cor(DIAMANTE["0"]),
    "H": cor(NETHERITA["3"]), "3": cor(NETHERITA["2"]), "2": cor(NETHERITA["1"]), "1": cor(NETHERITA["0"]),
    "K": cor(CRISTAL_SINAL["4"]), "k": cor(CRISTAL_SINAL["3"]), "J": cor(CRISTAL_SINAL["2"]),
    "j": cor(CRISTAL_SINAL["1"]),
}
CASCATA = ["signal_alloy_raw", "signal_alloy_ingot", "resonance_plate", "resonance_wire", "resonance_coil",
           "harmonic_lens", "phase_circuit", "phase_matrix", "field_stabilizer", "condensed_core"]


def _peca(grade: list[str]) -> Image.Image:
    assert len(grade) == 16 and all(len(l) == 16 for l in grade), "grade da cascata fora de 16 × 16"
    return contorno(pinta(grade, LEG_CASCATA), CONTORNO)


def _brilho_na_linha(img: Image.Image, y: int, x: int, de: Cor, largura: int = 2) -> None:
    """Um brilho de `largura` px correndo pela linha `y`, só sobre os pixels do tom `de`."""
    for dx in range(largura):
        if 0 <= x + dx < 16 and img.getpixel((x + dx, y)) == de:
            put(img, x + dx, y, mistura(de, BRANCO, .7 if dx == largura - 1 else .4))



def _luz(x: int, y: int) -> int:
    """O tom (0 = luz, 3 = sombra) de um ponto da peça pela luz de cima à esquerda; o lado de dentro da
    moldura (o chanfro) soma um."""
    d = (x - 7.5) + (y - 7.5)
    return 0 if d < -6 else 1 if d < 0 else 2 if d < 6 else 3


def _raio(x: int, y: int) -> float:
    return math.hypot(x - 7.5, y - 7.5)


# A Liga crua: o torrão do minério de ferro bruto do vanilla, recolorido na liga rosada; três dos pontos mais
# claros, espalhados, são os veios que cintilam.
RAMPA_LIGA = [cor(LIGA[t]) for t in ("4", "3", "2", "1", "0")]


def liga_crua() -> Sprite:
    """Liga de Sinal crua: o torrão de ferro bruto na liga rosada; um dos veios cintila de cada vez (6 × 5 ticks)."""
    base = base_vanilla("raw_iron")
    torrao = recolorir(base, RAMPA_LIGA)
    veios = _espalhados(_pixels_do_tom(base, 1), 3)
    q = []
    for k in range(6):
        img = torrao.copy()
        put(img, *veios[k // 2], BRANCO if k % 2 == 0 else cor("coral.4"))
        q.append(img)
    return animacao(q, 5)


def lingote() -> Sprite:
    """Lingote de Liga de Sinal: a barra do lingote de ferro do vanilla na liga rosada; um brilho corre pela
    face de cima, da esquerda para a direita (8 × 4 ticks)."""
    base = base_vanilla("iron_ingot")
    barra = recolorir(base, RAMPA_LIGA)
    # A face de cima é o tom claro mais comum da base (o branco é só o reflexo dela).
    face = _pixels_do_tom(base, 1)
    x0, x1 = min(x for x, _ in face), max(x for x, _ in face)
    q = []
    for k in range(8):
        img = barra.copy()
        xb = x0 + (k * (x1 - x0 + 2)) // 8
        for x, y in face:
            if x - y // 2 in (xb, xb + 1):  # a faixa do brilho, inclinada como a face
                put(img, x, y, mistura(img.getpixel((x, y)), BRANCO, .7 if x - y // 2 == xb + 1 else .4))
        q.append(img)
    return animacao(q, 4)


# A Placa: a chapa de liga com o chanfro (luz em cima e à esquerda, sombra embaixo e à direita), quatro
# rebites de ferro nos cantos e a onda de ressonância gravada no meio (o sulco escuro e o relevo claro logo
# abaixo, como uma gravação).
PLACA = [
    "................",
    "................",
    "..AAAAAAAAAAAA..",
    "..AaaaaaaaaaaB0.",
    "..AaFYbbbbbFYB0.",
    "..AabbbbbbbbbB0.",
    "..AabbBBbbbbbB0.",
    "..AabBAABbbbbB0.",
    "..AaBAbbABbbbB0.",
    "..AaAbbbbABBbB0.",
    "..AabbbbbbAABB0.",
    "..AabbbbbbbbbB0.",
    "..AaFYbbbbbFYB0.",
    "..B00000000000..",
    "................",
    "................",
]
ONDA_PLACA = ((4, 8), (5, 7), (6, 6), (7, 6), (8, 7), (9, 8), (10, 9), (11, 9))
assert all(PLACA[y][x] == "B" for x, y in ONDA_PLACA), "a onda sai do sulco da placa"


def placa() -> Sprite:
    """Placa de Ressonância: a onda gravada acende da esquerda para a direita, como um sinal passando (8 × 4 ticks)."""
    q = []
    for k in range(8):
        img = _peca(PLACA)
        for n, (x, y) in enumerate(ONDA_PLACA):
            fase = (k - n) % 8
            if fase == 0:
                put(img, x, y, cor("coral.4"))
            elif fase == 1:
                put(img, x, y, cor("coral.2"))
        q.append(img)
    return animacao(q, 4)


# O Fio: uma meada de fio de cobre, o anel grosso com o furo largo no meio e as voltas do fio marcadas em
# faixas que atravessam o anel (claro, cheio, sombra), a luz em cima à esquerda; a presilha coral que amarra
# a meada em cima à esquerda e as duas pontas soltas saindo por baixo à direita.
FIO = [
    "................",
    ".....SRRRRo.....",
    "...SRRrRRrRRo...",
    "..SdRrRRrRRroo..",
    "..dRrRrrooRrroO.",
    ".SRRrro...orRoO.",
    ".RRrRo.....oroO.",
    ".RrRo.......roO.",
    ".RrRo.......roO.",
    ".rRro.......OoO.",
    ".rRRro.....orOO.",
    "..rRrroo.oorOO..",
    "..orRrRooRrOOo..",
    "...oorRorOOoRr..",
    ".....ooooo..Or..",
    "................",
]
# O caminho do brilho pela volta de fora da meada, de cima à esquerda, no sentido horário.
VOLTA_FIO = ((6, 1), (9, 1), (12, 2), (13, 4), (14, 6), (14, 9), (13, 11), (11, 13), (8, 13), (5, 13), (3, 11),
             (1, 9), (1, 6), (2, 4))


def fio() -> Sprite:
    """Fio de Ressonância: um brilho de cobre dá a volta na meada, no sentido horário (8 × 4 ticks)."""
    q = []
    for k in range(8):
        img = _peca(FIO)
        n = (k * len(VOLTA_FIO)) // 8
        for i, (x, y) in enumerate(VOLTA_FIO):
            fase = (n - i) % len(VOLTA_FIO)
            if fase <= 1:
                put(img, x, y, mistura(img.getpixel((x, y)), BRANCO, .75 if fase == 0 else .4))
        q.append(img)
    return animacao(q, 4)


# A Bobina: o carretel de pé, como o carretel de fio dos mods de engenharia: as flanges largas de ferro em cima
# e embaixo (a de cima com a luz, a de baixo na sombra), o eixo de latão saindo no topo, e o enrolamento de
# cobre em voltas de 2 px, cada volta com a luz à esquerda e a sombra à direita; a volta do meio leva a fita
# coral da ressonância.
BOBINA = [
    "................",
    "......Llmm......",
    "..EFFFFFFFFyyY..",
    "..FyyyyyyyyyYz..",
    "...SRRRrrrooO...",
    "...RRrrrroooO...",
    "...SRRRrrrooO...",
    "...ddddddeeef...",
    "...SRRRrrrooO...",
    "...RRrrrroooO...",
    "...SRRRrrrooO...",
    "...RRrrrroooO...",
    "..EFFFFFFyyyY...",
    "..FyyyyyyyYYzz..",
    "..Yzzzzzzzzzzz..",
    "................",
]


def bobina() -> Sprite:
    """Bobina de Ressonância: um brilho desce pelo enrolamento, volta a volta, e acende a fita coral ao passar
    (8 × 4 ticks)."""
    q = []
    for k in range(8):
        img = _peca(BOBINA)
        y = 4 + k
        if y == 7:
            for x in range(4, 12):
                put(img, x, y, mistura(img.getpixel((x, y)), BRANCO, .45))
        elif y <= 11:
            _brilho_na_linha(img, y, 4, LEG_CASCATA["R"] if y % 2 == 0 else LEG_CASCATA["r"], 5)
        q.append(img)
    return animacao(q, 4)


def _disco_aro(img: Image.Image, aro: list[Cor], r_fora: float, r_dentro: float, miolo) -> None:
    """Um disco com aro: o anel entre `r_dentro` e `r_fora` no material `aro` (luz de cima à esquerda) e,
    dentro, o que `miolo(x, y, r)` devolver (None deixa transparente)."""
    for y in range(16):
        for x in range(16):
            r = _raio(x, y)
            if r >= r_fora:
                continue
            if r >= r_dentro:
                put(img, x, y, aro[min(len(aro) - 1, _luz(x, y))])
            else:
                c = miolo(x, y, r)
                if c is not None:
                    put(img, x, y, c)


# A Lente: o disco de vidro num aro de latão (como as lentes de ótica dos mods de magia), quatro garras de
# latão escuro que seguram o vidro, o reflexo em arco no alto à esquerda e, no centro, o Cristal Lapidado
# engastado como um losango.
GARRAS_LENTE = ((3, 3), (12, 3), (3, 12), (12, 12))


def _lente_base() -> Image.Image:
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    latao = [cor("latao.3"), cor("latao.2"), cor("latao.1"), cor("latao.0")]
    vidro = [cor("vidro.3"), cor("vidro.2"), cor("vidro.1"), cor("vidro.0")]
    cristal = [cor(CRISTAL_SINAL[t]) for t in ("4", "3", "2", "1")]

    def miolo(x: int, y: int, r: float) -> Cor:
        d = abs(x - 7.5) + abs(y - 7.5)
        if d <= 3:
            return cristal[0 if d < 1.5 else 1 if (x < 8 and y < 8) else 2 if x < 8 or y < 8 else 3]
        if 3.6 < r < 5.4 and x < 7 and y < 7:
            return vidro[0]
        # O vidro clareia para o centro e escurece na borda de baixo à direita, como uma lente convexa.
        fundo = (x - 7.5) + (y - 7.5)
        return vidro[1 if r < 3.8 or fundo < 0 else 2 if r < 5.0 or fundo < 5 else 3]

    _disco_aro(img, latao, 7.6, 5.4, miolo)
    for x, y in GARRAS_LENTE:
        put(img, x, y, latao[3])
    return contorno(img, CONTORNO)


def lente_harmonica() -> Sprite:
    """Lente Harmônica: o cristal do centro cintila e o reflexo do vidro desliza pelo arco (8 × 4 ticks)."""
    arco = ((4, 4), (5, 3), (3, 5), (3, 6))
    base = _lente_base()
    q = []
    for k in range(8):
        img = base.copy()
        for n, (x, y) in enumerate(arco):
            if (k // 2) % 4 == n:
                put(img, x, y, BRANCO)
        if k in (5, 6):
            put(img, 7, 7, BRANCO)
            put(img, 8, 8, BRANCO if k == 5 else cor(CRISTAL_SINAL["4"]))
        q.append(img)
    return animacao(q, 4)


# O Circuito: o cartão de placa verde com o chip de grafite no meio (o ponto coral aceso), as trilhas de ouro
# que saem do chip para cima e para os lados, um resistor de cobre, um capacitor de ferro e os pinos dourados
# saindo pela borda de baixo, como as placas e processadores dos mods de tecnologia.
CIRCUITO = [
    "................",
    "..PPPPPPPPPPPP..",
    "..PpppLppLpppQ..",
    "..PRrpLppLpppQ..",
    "..PpppLppLpppQ..",
    "..PLLLgghhpppQ..",
    "..PpppgdhhpppQ..",
    "..PppphhiiLLLQ..",
    "..PppphiiipppQ..",
    "..PpppppppFYpQ..",
    "..PpLpppppppQQ..",
    "..PpppppppppQQ..",
    "..QQQQQQQQQQQQ..",
    "...lnlnlnlnln...",
    "................",
    "................",
]
TRILHA_CIRCUITO = ((6, 2), (6, 3), (6, 4), (6, 5))


def circuito() -> Sprite:
    """Circuito de Fase: o ponto do chip pulsa e um sinal corre pela trilha de cima até o chip (8 × 4 ticks)."""
    q = []
    for k in range(8):
        img = _peca(CIRCUITO)
        t = (1 + math.cos(2 * math.pi * k / 8)) / 2
        put(img, 7, 6, mistura(cor("coral.3"), BRANCO, .6 * t))
        if k < len(TRILHA_CIRCUITO):
            put(img, *TRILHA_CIRCUITO[k], BRANCO)
        q.append(img)
    return animacao(q, 4)


# A Matriz: a moldura de latão com os rebites nos cantos em volta da placa de grafite com nove células
# (lentes de vidro com o emissor coral no meio), separadas pelas trilhas escuras.
MATRIZ = [
    "................",
    ".LLLLLLLLLLLLLm.",
    ".LnhhhhhhhhhhGm.",
    ".LhVvdhVvdhVvdm.",
    ".LhvdthvdthvdtM.",
    ".Lhdtthdtthdttm.",
    ".Lhhhhhhhhhhhhm.",
    ".LhVvdhVvdhVvdm.",
    ".LhvdthvdthvdtM.",
    ".Lhdtthdtthdttm.",
    ".Lhhhhhhhhhhhhm.",
    ".LhVvdhVvdhVvdm.",
    ".LhvdthvdthvdtM.",
    ".Lhdtthdtthdttm.",
    ".mnmmmmmmmmmmmn.",
    "................",
]
CELULAS_MATRIZ = [(4 + 4 * i, 4 + 4 * j) for j in range(3) for i in range(3)]
assert all(MATRIZ[y][x] == "d" for x, y in CELULAS_MATRIZ), "a célula da matriz fora do emissor"


def matriz() -> Sprite:
    """Matriz de Fase: as células acendem em diagonal, de cima à esquerda para baixo à direita (8 × 3 ticks)."""
    q = []
    for k in range(8):
        img = _peca(MATRIZ)
        for (x, y) in CELULAS_MATRIZ:
            diag = (x - 4) // 4 + (y - 4) // 4
            fase = (k - diag) % 8
            if fase == 0:
                put(img, x, y, BRANCO)
                put(img, x - 1, y - 1, BRANCO)
            elif fase == 1:
                put(img, x, y, cor("coral.4"))
        q.append(img)
    return animacao(q, 3)


# O Estabilizador: o octógono de latão, a câmara de obsidiana, o anel do giroscópio em diamante em volta da
# gema do centro, e as quatro centelhas coral nos cantos da câmara.
CENTELHAS_ESTAB = ((4, 4), (11, 4), (4, 11), (11, 11))


def _octo(x: int, y: int) -> float:
    """A distância octogonal ao centro: a de Chebyshev, cortada nas diagonais."""
    dx, dy = abs(x - 7.5), abs(y - 7.5)
    return max(dx, dy, (dx + dy) / 1.35)


def _estabilizador_base() -> Image.Image:
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    latao = [cor("latao.3"), cor("latao.2"), cor("latao.1"), cor("latao.0")]
    obsidiana = [cor(OBSIDIANA[t]) for t in ("3", "2", "1", "0")]
    diamante = [cor(DIAMANTE[t]) for t in ("3", "2", "1", "0")]
    for y in range(16):
        for x in range(16):
            m = _octo(x, y)
            if m > 7.3:
                continue
            if m > 5.6:
                put(img, x, y, latao[min(3, _luz(x, y))])
            else:
                put(img, x, y, obsidiana[1 if _luz(x, y) < 1 else 2 if _luz(x, y) < 3 else 3])
    # O anel do giroscópio em volta da gema, de 1 px, com a luz em cima à esquerda.
    for y in range(16):
        for x in range(16):
            if 3.4 <= _raio(x, y) < 4.4:
                put(img, x, y, diamante[1 if _luz(x, y) < 2 else 2])
    # A gema do centro: o losango facetado, a luz em cima à esquerda.
    for dx in range(-2, 3):
        for dy in range(-2, 3):
            if abs(dx) + abs(dy) <= 2:
                put(img, 7 + dx, 7 + dy, diamante[0 if dx + dy < -1 else 1 if dx + dy <= 0 else 2])
    return contorno(img, CONTORNO)


def estabilizador() -> Sprite:
    """Estabilizador de Campo: as centelhas pulsam, a gema cintila e, no auge, o aro de latão reluz nas pontas
    (10 × 3 ticks)."""
    base = _estabilizador_base()
    q = []
    for k in range(10):
        img = base.copy()
        t = (1 + math.cos(2 * math.pi * k / 10)) / 2
        for p in CENTELHAS_ESTAB:
            put(img, *p, mistura(cor("coral.2"), cor("coral.4"), t))
        put(img, 7, 7, mistura(cor(DIAMANTE["3"]), BRANCO, t))
        if k in (0, 1):
            for p in ((7, 1), (1, 7), (14, 8), (8, 14)):
                put(img, *p, mistura(cor("latao.3"), BRANCO, .7))
        q.append(img)
    return animacao(q, 3)


# O Núcleo Condensado: a esfera de netherita com as duas cintas que se cruzam (o parafuso de latão no
# cruzamento) e, entre elas, as quatro janelas de onde o plasma coral vaza, com a estrela no miolo de cada
# uma; a casca na sombra embaixo à direita.
ESTRELAS_CONDENSADO = ((5, 5), (10, 5), (5, 10), (10, 10))


def _condensado_base() -> tuple[Image.Image, set[tuple[int, int]]]:
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    casca = [cor(NETHERITA[t]) for t in ("3", "2", "1", "0")]
    janelas: set[tuple[int, int]] = set()
    for y in range(16):
        for x in range(16):
            r = _raio(x, y)
            if r > 7.6:
                continue
            dx, dy = x - 7.5, y - 7.5
            cinta = abs(dx) < 1.2 or abs(dy) < 1.2
            if r > 5.6 or cinta:
                put(img, x, y, casca[min(3, _luz(x, y) + (1 if cinta and r <= 5.6 else 0))])
            else:
                janelas.add((x, y))
    for x, y in ((7, 7), (8, 7), (7, 8), (8, 8)):
        put(img, x, y, cor("latao.3") if x + y < 15 else cor("latao.1"))
    return img, janelas


def condensado() -> Sprite:
    """Núcleo Condensado: o plasma respira (clareia do miolo para fora) e as estrelas das janelas cintilam em
    volta, uma por vez, no sentido horário (12 × 2 ticks)."""
    base, janelas = _condensado_base()
    tons = [cor(CRISTAL_SINAL[t]) for t in ("4", "3", "2", "1")]
    q = []
    for k in range(12):
        img = base.copy()
        t = (1 + math.cos(2 * math.pi * k / 12)) / 2
        for x, y in janelas:
            # O plasma emite luz: o tom vem da distância ao parafuso do centro, e o miolo respira.
            d = max(abs(x - 7.5), abs(y - 7.5))
            c = tons[0 if d < 2.5 else 1 if d < 3.5 else 2 if d < 4.5 else 3]
            put(img, x, y, mistura(c, BRANCO, max(0.0, .5 * t * (1 - (d - 1.5) / 4))))
        ordem = (0, 1, 3, 2)
        n = ordem[(k // 3) % 4]
        x, y = ESTRELAS_CONDENSADO[n]
        put(img, x, y, BRANCO)
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if (x + dx, y + dy) in janelas:
                put(img, x + dx, y + dy, cor(CRISTAL_SINAL["4"]) if k % 3 != 2 else BRANCO)
        q.append(contorno(img, CONTORNO))
    return animacao(q, 2)


SPRITES_CASCATA = {"signal_alloy_raw": liga_crua, "signal_alloy_ingot": lingote, "resonance_plate": placa,
                   "resonance_wire": fio, "resonance_coil": bobina, "harmonic_lens": lente_harmonica,
                   "phase_circuit": circuito, "phase_matrix": matriz, "field_stabilizer": estabilizador,
                   "condensed_core": condensado}
assert list(SPRITES_CASCATA) == CASCATA, "a ordem da cascata e a dos sprites diferem"


# ---------------------------------------------------------------------------
# O Coração do Ressonador: o reator do Núcleo, um por tier, a mesma silhueta subindo de material
# ---------------------------------------------------------------------------
#
# Todos têm a mesma peça: a moldura octogonal de 2 px no material do tier, o aro da cor do tier (a lente do
# Olho, `LENTE`), a câmara de grafite e, no meio, o losango facetado de plasma coral que respira. O que sobe de
# tier em tier é o material da moldura e dos rebites, e o que mora na câmara: as gemas, a estrela do Nether
# Star e as centelhas; o Ultimate leva a moldura na liga Unobtainium-Vibranium (o chanfro em Vibranium) e as
# quatro estrelas. 'F' moldura, 'B' aro, 'h' câmara, 'K' núcleo.

CORACAO = [
    "................",
    ".....FFFFFF.....",
    "...FFFFFFFFFF...",
    "..FFBBBBBBBBFF..",
    "..FBhhhKKhhhBF..",
    ".FFBhhKKKKhhBFF.",
    ".FFBhKKKKKKhBFF.",
    ".FFBKKKKKKKKBFF.",
    ".FFBKKKKKKKKBFF.",
    ".FFBhKKKKKKhBFF.",
    ".FFBhhKKKKhhBFF.",
    "..FBhhhKKhhhBF..",
    "..FFBBBBBBBBFF..",
    "...FFFFFFFFFF...",
    ".....FFFFFF.....",
    "................",
]
REBITES_CORACAO = ((3, 3), (12, 3), (3, 12), (12, 12))
# Os cantos da câmara: as gemas do Esmeralda e as centelhas dos tiers do Allthemodium.
CANTOS_CORACAO = ((5, 5), (10, 5), (5, 10), (10, 10))
# A volta do brilho pela moldura, de cima à esquerda no sentido horário.
VOLTA_CORACAO = ((5, 1), (8, 1), (11, 2), (13, 3), (14, 5), (14, 8), (13, 11), (11, 13), (8, 14), (5, 14),
                 (3, 13), (1, 10), (1, 8), (2, 5), (3, 3))
assert all(CORACAO[y][x] == "F" for x, y in VOLTA_CORACAO), "a volta do brilho sai da moldura"
ESMERALDA = {"4": "#C6FFD6", "3": "#5CE48A", "2": "#1FB657", "1": "#12833C", "0": "#0B5226"}
REDSTONE = {"3": "#FF8A78", "2": "#F23D2A", "1": "#B8241A", "0": "#6E140E"}
PRETO = cor("#000000")


def _tons5(hi: str, mid: str, lo: str) -> list[Cor]:
    """Cinco tons de um material a partir da lente do tier: da luz à sombra."""
    return [mistura(cor(hi), BRANCO, .45), cor(hi), cor(mid), cor(lo), mistura(cor(lo), PRETO, .35)]


def _tons_pal(pal: dict[str, str]) -> list[Cor]:
    tons = [cor(pal[t]) for t in ("4", "3", "2", "1", "0") if t in pal]
    return tons if len(tons) == 5 else [mistura(tons[0], BRANCO, .45)] + tons


# Por tier: os tons da moldura, dos rebites e, quando difere do plasma coral, do núcleo.
MOLDURA_CORACAO: dict[str, list[Cor]] = {
    "basic": _tons_pal(FERRO),
    "advanced": _tons_pal(PALETA["latao"]),
    "elite": _tons_pal(NETHERITA),
    "emerald": _tons_pal(NETHERITA),
    "allthemodium": _tons5(*LENTE["allthemodium"]),
    "vibranium": _tons5(*LENTE["vibranium"]),
    "unobtainium": _tons5(*LENTE["unobtainium"]),
    "ultimate": _tons5(*LENTE["ultimate"]),
}
REBITE_CORACAO: dict[str, list[Cor]] = {
    "basic": _tons_pal(FERRO), "advanced": _tons_pal(FERRO), "elite": _tons_pal(PALETA["latao"]),
    "emerald": _tons_pal(ESMERALDA), "allthemodium": _tons_pal(NETHERITA), "vibranium": _tons_pal(NETHERITA),
    "unobtainium": _tons_pal(NETHERITA), "ultimate": _tons5(*LENTE["vibranium"]),
}
NUCLEO_CORACAO: dict[str, list[Cor]] = {
    "basic": [cor(REDSTONE[t]) for t in ("3", "2", "1", "0")],
}
PLASMA_CORACAO = [cor(CRISTAL_SINAL[t]) for t in ("3", "2", "1", "0")]
ESCADA_CORACAO = {t: n for n, t in enumerate(TIERS)}


def _sombreia(img: Image.Image, pontos: set[tuple[int, int]], tons: list[Cor], relevo: int = 0) -> None:
    for x, y in pontos:
        put(img, x, y, tons[_luz(x, y) + relevo])


def _estrela(img: Image.Image, x: int, y: int, braco: Cor | None) -> None:
    """A estrela do Nether Star sobre o núcleo: o miolo branco e, no auge, as quatro pontas a 2 px."""
    put(img, x, y, BRANCO)
    if braco is not None:
        for dx, dy in ((2, 0), (-2, 0), (0, 2), (0, -2)):
            put(img, x + dx, y + dy, braco)


def coracao(tier: str) -> Sprite:
    """Coração do Ressonador: o núcleo respira (clareia do miolo para fora); do Avançado em diante um brilho dá a
    volta na moldura; do Elite o aro pulsa junto; do Esmeralda a estrela do miolo cintila; do Allthemodium as
    centelhas dos cantos pulsam; no Ultimate as pontas da coroa acendem uma a uma (12 × 2 ticks)."""
    degrau = ESCADA_CORACAO[tier]
    moldura = MOLDURA_CORACAO[tier]
    aro_hi, aro_mid, aro_lo = (cor(c) for c in LENTE[tier])
    nucleo = NUCLEO_CORACAO.get(tier, PLASMA_CORACAO)
    base = pinta(CORACAO, {"F": PRETO, "B": PRETO, "h": PRETO, "K": PRETO})
    pontos = {c: {(x, y) for y in range(16) for x in range(16) if CORACAO[y][x] == c} for c in "FBhK"}
    q = []
    for k in range(12):
        img = base.copy()
        t = (1 + math.cos(2 * math.pi * k / 12)) / 2
        # A moldura, com o lado de dentro um tom mais fundo (o chanfro), e os rebites.
        _sombreia(img, pontos["F"], moldura)
        chanfro = {(x, y) for (x, y) in pontos["F"] if 2 <= x <= 13 and 2 <= y <= 13}
        _sombreia(img, chanfro, REBITE_CORACAO[tier] if tier == "ultimate" else moldura, 1)
        for x, y in REBITES_CORACAO:
            put(img, x, y, REBITE_CORACAO[tier][1 if _luz(x, y) < 2 else 3])
        # O aro da cor do tier: luz em cima à esquerda; do Elite em diante pulsa com o núcleo.
        pulsa = .35 * t if degrau >= 2 else 0.0
        for x, y in pontos["B"]:
            put(img, x, y, mistura(aro_mid if _luz(x, y) < 2 else aro_lo, aro_hi, pulsa))
        for x, y in pontos["h"]:
            put(img, x, y, cor("grafite.2") if _luz(x, y) < 2 else cor("grafite.1"))
        # O núcleo facetado: a faceta de cima à esquerda clara, a de baixo à direita na sombra, o miolo
        # respirando (o brilho sai do centro para as bordas).
        for x, y in pontos["K"]:
            faceta = (0 if y < 8 else 1) + (0 if x < 8 else 1) + (1 if x >= 8 and y >= 8 else 0)
            put(img, x, y, mistura(nucleo[faceta], BRANCO, .3 * t * (1 - faceta / 4)))
        put(img, 6, 6, mistura(nucleo[0], BRANCO, .55 + .35 * t))
        if degrau >= 3:
            # A estrela do Nether Star no miolo: as pontas aparecem no auge da respiração.
            _estrela(img, 7, 7, mistura(nucleo[0], BRANCO, .8) if t > .6 else None)
        if tier == "ultimate":
            # As quatro estrelas dos cantos: uma de cada vez cintila branca, no sentido horário.
            for i, (x, y) in enumerate(CANTOS_CORACAO):
                put(img, x, y, BRANCO if (k // 3) % 4 == (0, 1, 3, 2)[i] else mistura(aro_hi, BRANCO, .4))
        elif degrau >= 4:
            # As centelhas dos cantos da câmara, na cor do tier, pulsando em contratempo com o núcleo.
            for x, y in CANTOS_CORACAO:
                put(img, x, y, mistura(aro_mid, aro_hi, 1 - t))
        elif tier == "emerald":
            for x, y in CANTOS_CORACAO:
                put(img, x, y, cor(ESMERALDA["3"]) if _luz(x, y) < 2 else cor(ESMERALDA["1"]))
        if degrau >= 1:
            # O brilho que dá a volta na moldura, no sentido horário.
            n = (k * len(VOLTA_CORACAO)) // 12
            for i, (x, y) in enumerate(VOLTA_CORACAO):
                fase = (n - i) % len(VOLTA_CORACAO)
                if fase <= 1:
                    put(img, x, y, mistura(img.getpixel((x, y)), BRANCO, .7 if fase == 0 else .35))
        q.append(contorno(img, CONTORNO))
    return animacao(q, 2)


# ---------------------------------------------------------------------------
# A Moldura de Liga: a peça do Coração Ultimate, a receita em anel vista de frente
# ---------------------------------------------------------------------------
#
# Oito blocos de liga Unobtainium-Vibranium em volta do vão de 5 × 5 (a mesma receita: oito blocos e uma
# Nether Star no meio): os dos cantos com 4 × 4, os do meio de cada lado esticados sobre o vão (4 × 5). Cada
# bloco tem o chanfro próprio (a aresta de cima e da esquerda clara, a de baixo e da direita na sombra), e os
# de baixo à direita ficam um tom mais fundos, pela luz de cima à esquerda. O lado de dentro do anel é o
# chanfro em Vibranium, como na moldura do Coração Ultimate. O vão é a câmara de grafite com a Nether Star no
# meio. Corpo de 13 px (1 a 13), com o contorno em 0 e 14.
FAIXAS_MOLDURA = ((1, 4), (5, 9), (10, 13))
MOLDURA_VAO = range(5, 10)
# A volta do brilho pelo miolo dos blocos (um pixel para dentro da aresta de fora, onde um ponto claro não vira
# ruído sobre a sombra), pixel a pixel, de cima à esquerda no sentido horário.
VOLTA_MOLDURA = tuple([(x, 2) for x in range(2, 13)] + [(12, y) for y in range(3, 13)]
                      + [(x, 12) for x in range(11, 1, -1)] + [(2, y) for y in range(11, 2, -1)])
assert len(VOLTA_MOLDURA) == 40 and len(set(VOLTA_MOLDURA)) == 40, "a volta do brilho da moldura não fecha"
# A Nether Star no vão: o miolo, os quatro braços a 1 px, as pontas a 2 px e as diagonais.
BRACOS_ESTRELA = ((1, 0), (-1, 0), (0, 1), (0, -1))
DIAGONAIS_ESTRELA = ((1, 1), (-1, 1), (1, -1), (-1, -1))


def _bloco_liga(img: Image.Image, bx: int, by: int, tons: list[Cor]) -> None:
    """Um bloco de liga na posição (bx, by) da grade de 3 × 3: o miolo no tom cheio, a aresta de cima e a da
    esquerda que dão para fora do anel na luz, a junta com o bloco seguinte no tom mais fundo, as outras
    arestas de baixo e da direita um tom abaixo e o canto de baixo à direita dois; os blocos de baixo à
    direita da grade começam um tom mais fundos."""
    base = 0 if bx + by < 2 else 1
    (x0, x1), (y0, y1) = FAIXAS_MOLDURA[bx], FAIXAS_MOLDURA[by]
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            if (x == x1 and bx < 2) or (y == y1 and by < 2):
                tom = 4 - base  # a junta com o bloco seguinte, sempre no tom mais fundo
            elif x == x1 and y == y1:
                tom = 3
            elif x == x1 or y == y1:
                tom = 2
            elif (x == x0 and bx == 0) or (y == y0 and by == 0):
                tom = 0
            else:
                tom = 1
            put(img, x, y, tons[base + tom])


def moldura_liga() -> Sprite:
    """Moldura de Liga: um brilho dá a volta no anel, no sentido horário, e a Nether Star do vão cintila
    (o miolo respira, as pontas aparecem no auge e as diagonais apagam) (12 × 2 ticks)."""
    liga = MOLDURA_CORACAO["ultimate"]
    vibranio = REBITE_CORACAO["ultimate"]
    aro_hi = cor(LENTE["ultimate"][0])
    camara = {(x, y) for y in MOLDURA_VAO for x in MOLDURA_VAO}
    q = []
    for k in range(12):
        img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        t = (1 + math.cos(2 * math.pi * k / 12)) / 2
        for by in range(3):
            for bx in range(3):
                if (bx, by) != (1, 1):
                    _bloco_liga(img, bx, by, liga)
        # O chanfro de Vibranium no lado de dentro do anel: claro em cima e à esquerda, na sombra embaixo e à
        # direita, os cantos no meio-termo.
        for x in range(4, 11):
            for y in range(4, 11):
                if (x, y) not in camara:
                    put(img, x, y, vibranio[_luz(x, y) + 1])
        # Os quatro parafusos de Vibranium nos cantos do anel.
        for x, y in ((2, 2), (12, 2), (2, 12), (12, 12)):
            put(img, x, y, vibranio[1 if _luz(x, y) < 2 else 3])
        # A câmara de grafite, com um halo violeta que respira em volta da estrela.
        for x, y in camara:
            d = max(abs(x - 7), abs(y - 7))
            fundo = cor("grafite.2") if _luz(x, y) < 2 else cor("grafite.1")
            put(img, x, y, mistura(fundo, aro_hi, .35 * t * (1 - d / 3)))
        # A Nether Star: o miolo branco respirando, os braços pálidos, as pontas no auge e as diagonais apagando.
        put(img, 7, 7, BRANCO)
        for dx, dy in BRACOS_ESTRELA:
            put(img, 7 + dx, 7 + dy, mistura(aro_hi, BRANCO, .35 + .5 * t))
            if t > .6:
                put(img, 7 + 2 * dx, 7 + 2 * dy, aro_hi)
        for dx, dy in DIAGONAIS_ESTRELA:
            put(img, 7 + dx, 7 + dy, mistura(img.getpixel((7 + dx, 7 + dy)), aro_hi, .45 - .3 * t))
        # O brilho que dá a volta no anel, no sentido horário.
        n = (k * len(VOLTA_MOLDURA)) // 12
        for i, (x, y) in enumerate(VOLTA_MOLDURA):
            fase = (n - i) % len(VOLTA_MOLDURA)
            if fase <= 4:
                put(img, x, y, mistura(img.getpixel((x, y)), BRANCO, (.7, .55, .4, .25, .12)[fase]))
        q.append(contorno(img, CONTORNO))
    return animacao(q, 2)


# ---------------------------------------------------------------------------
# Montagem, modelos e folha
# ---------------------------------------------------------------------------

def gerar_itens() -> dict[str, Sprite]:
    sprites: dict[str, Sprite] = {
        "item/configurator": configurador(),
        "item/linker": vinculador(),
        "item/network_tablet": tablet(),
        "item/filter_card": filtro(),
    }
    for tier in TIERS_CARTAO:
        sprites[f"item/tier_core_{tier}"] = valvula(tier)
    sprites["item/chunk_loader_upgrade"] = lanterna()
    sprites["item/range_upgrade"] = rosa()
    sprites["item/guide"] = guia()
    sprites["item/signal_crystal"] = cristal()
    sprites["item/cut_signal_crystal"] = lapidado()
    sprites["item/signal_crystal_dull"] = opaco(BRUTO, BRASA_BRUTO)
    sprites["item/cut_signal_crystal_dull"] = opaco(LAPIDADO, BRASA_LAPIDADO)
    sprites["item/signal_crystal_cracked"] = rachado(BRUTO, RACHADURA_BRUTO)
    sprites["item/cut_signal_crystal_cracked"] = rachado(LAPIDADO, RACHADURA_LAPIDADO)
    for tier in TIERS_AFINADO:
        sprites[f"item/tuned_signal_crystal_{tier}"] = afinado(tier)
        sprites[f"item/tuned_signal_crystal_{tier}_dull"] = opaco(AFINADO, BRASA_AFINADO, tier)
        sprites[f"item/tuned_signal_crystal_{tier}_cracked"] = rachado(AFINADO, RACHADURA_AFINADO, tier)
    sprites["item/resonance_dust"] = po()
    for tier in TIERS_PO_CARREGADO:
        sprites[f"item/charged_dust_{tier}"] = po_carregado(tier)
    for nome, desenho in SPRITES_CASCATA.items():
        sprites[f"item/{nome}"] = desenho()
    for tier in TIERS:
        sprites[f"item/resonator_heart_{tier}"] = coracao(tier)
    sprites["item/alloy_frame"] = moldura_liga()
    for modo in ("extract", "insert", "both"):
        sprites[f"gui/port_{modo}"] = porta(modo)
    sprites["gui/port_none"] = porta_nenhum()
    for nome in ICONES_TIPO:
        sprites[f"gui/type/{nome}"] = icone_tipo(nome)
    return sprites


def modelos_itens() -> None:
    nomes_cristais = ["signal_crystal", "cut_signal_crystal"] + [f"tuned_signal_crystal_{t}" for t in TIERS_AFINADO]
    for nome in nomes_cristais:
        for variante in ("dull", "cracked"):
            grava_json(MODELOS / f"models/item/{nome}_{variante}.json", {
                "parent": "minecraft:item/generated",
                "textures": {"layer0": f"wirelessautomate:item/{nome}_{variante}"},
            })
        # O rachado vem por último para ganhar do Opaco (o Minecraft vale o último override que casa).
        grava_json(MODELOS / f"models/item/{nome}.json", {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": f"wirelessautomate:item/{nome}"},
            "overrides": [{"predicate": {"wirelessautomate:dull": 1}, "model": f"wirelessautomate:item/{nome}_dull"},
                          {"predicate": {"wirelessautomate:cracked": 1},
                           "model": f"wirelessautomate:item/{nome}_cracked"}],
        })
    grava_json(MODELOS / "models/item/resonance_dust.json", {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": "wirelessautomate:item/resonance_dust"},
    })
    for tier in TIERS_PO_CARREGADO:
        grava_json(MODELOS / f"models/item/charged_dust_{tier}.json", {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": f"wirelessautomate:item/charged_dust_{tier}"},
        })
    for nome in CASCATA:
        grava_json(MODELOS / f"models/item/{nome}.json", {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": f"wirelessautomate:item/{nome}"},
        })
    grava_json(MODELOS / "models/item/range_upgrade.json", {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": "wirelessautomate:item/range_upgrade"},
    })
    for tier in TIERS_CARTAO:
        grava_json(MODELOS / f"models/item/tier_core_{tier}.json", {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": f"wirelessautomate:item/tier_core_{tier}"},
        })
    for tier in TIERS:
        grava_json(MODELOS / f"models/item/resonator_heart_{tier}.json", {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": f"wirelessautomate:item/resonator_heart_{tier}"},
        })
    grava_json(MODELOS / "models/item/alloy_frame.json", {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": "wirelessautomate:item/alloy_frame"},
    })


def limpar(gerados: set[str], pastas: list[str]) -> int:
    """Apaga os PNGs e .mcmeta das pastas que não saíram desta rodada."""
    apagados = 0
    for pasta in pastas:
        raiz = ASSETS / pasta
        if not raiz.exists():
            continue
        for arq in sorted(raiz.rglob("*")):
            if not arq.is_file():
                continue
            nome = arq.name.removesuffix(".mcmeta")
            if not nome.endswith(".png"):
                continue
            chave = str(arq.parent.relative_to(ASSETS) / nome[:-4]).replace("\\", "/")
            if chave in gerados:
                continue
            arq.unlink()
            apagados += 1
    return apagados


def _fonte(tamanho: int = 13) -> ImageFont.ImageFont | ImageFont.FreeTypeFont:
    for caminho in ("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", "DejaVuSans.ttf"):
        try:
            return ImageFont.truetype(caminho, tamanho)
        except OSError:
            continue
    try:
        return ImageFont.load_default(size=tamanho)
    except TypeError:
        return ImageFont.load_default()


def _amplia(img: Image.Image, k: int) -> Image.Image:
    return img.resize((img.width * k, img.height * k), Image.NEAREST)


def folha(itens: dict[str, Sprite], blocos_sprites: dict[str, Sprite] | None,
          previas: list[tuple[str, Image.Image]]) -> Image.Image:
    fonte = _fonte(13)
    fonte_titulo = _fonte(17)
    fundo, tinta, tinta2 = cor("porcelana.3"), cor("grafite.1"), cor("#7A7366")
    margem, gap, escala = 16, 6, 6
    celula = 16 * escala
    largura = 1040

    out = Image.new("RGBA", (largura, 6000), fundo)
    d = ImageDraw.Draw(out)
    y = margem

    def titulo(texto: str) -> None:
        nonlocal y
        d.text((margem, y), texto, fill=tinta, font=fonte_titulo)
        y += 26
        d.line([(margem, y - 4), (largura - margem, y - 4)], fill=cor("grafite.4"), width=1)

    def tile(img: Image.Image, k: int) -> Image.Image:
        t = Image.new("RGBA", (img.width * k, img.height * k), fundo)
        t.alpha_composite(_amplia(img, k))
        return t

    titulo("Itens (quadros da animação a 6×, depois o item a 1× e 2×)")
    nomes_itens = ["configurator", "linker", "network_tablet", "filter_card"] + \
                  [f"tier_core_{t}" for t in TIERS_CARTAO] + ["chunk_loader_upgrade", "range_upgrade", "guide",
                                                "signal_crystal", "cut_signal_crystal", "signal_crystal_dull",
                                                "cut_signal_crystal_dull", "signal_crystal_cracked",
                                                "cut_signal_crystal_cracked", "resonance_dust"] + \
                  [f"charged_dust_{t}" for t in TIERS_PO_CARREGADO] + CASCATA + \
                  [f"resonator_heart_{t}" for t in TIERS] + ["alloy_frame"] + \
                  [f"tuned_signal_crystal_{t}{v}" for t in TIERS_AFINADO for v in ("", "_dull", "_cracked")]
    for nome in nomes_itens:
        sp = itens[f"item/{nome}"]
        if out.height < y + 3 * (celula + gap) + 40:
            novo = Image.new("RGBA", (largura, out.height + 2000), fundo)
            novo.paste(out, (0, 0))
            out = novo
            d = ImageDraw.Draw(out)
        d.text((margem, y), f"{nome}: {sp.quadros} quadros, frametime {sp.frametime}", fill=tinta, font=fonte)
        y += 18
        x = margem
        for n in range(sp.quadros):
            if n and n % 8 == 0:
                x = margem
                y += celula + gap
            out.alpha_composite(tile(sp.quadro(n), escala), (x, y))
            x += celula + gap
        x = largura - margem - 16 - 8 - 32 - 8 - 48
        out.alpha_composite(tile(sp.quadro(0), 1), (x, y + celula - 16))
        out.alpha_composite(tile(sp.quadro(0), 2), (x + 24, y + celula - 32))
        out.alpha_composite(tile(sp.quadro(0), 3), (x + 64, y + celula - 48))
        d.text((x, y + celula - 64), "1x 2x 3x", fill=tinta2, font=fonte)
        y += celula + gap + 6

    y += 10
    titulo("Interface (sobre o fundo claro das telas)")
    x = margem
    for nome in ["port_extract", "port_insert", "port_both", "port_none"]:
        sp = itens[f"gui/{nome}"]
        out.alpha_composite(tile(sp.quadro(0), escala), (x, y))
        out.alpha_composite(tile(sp.quadro(0), 1), (x, y + celula + 4))
        out.alpha_composite(tile(sp.quadro(0), 2), (x + 20, y + celula + 4))
        d.text((x, y + celula + 40), nome, fill=tinta, font=fonte)
        x += celula + gap + 8
    for nome in ICONES_TIPO:
        sp = itens[f"gui/type/{nome}"]
        rec = sp.quadro(0).crop((0, 0, 9, 9))
        out.alpha_composite(tile(rec, escala), (x, y))
        out.alpha_composite(tile(rec, 1), (x, y + celula + 4))
        out.alpha_composite(tile(rec, 2), (x + 14, y + celula + 4))
        d.text((x, y + celula + 40), f"type/{nome}", fill=tinta, font=fonte)
        x += 9 * escala + gap + 40
    y += celula + 60

    if blocos_sprites:
        y += 10
        titulo("Blocos: texturas (primeiro quadro, 4×)")
        x = margem
        k = 4
        for nome in sorted(blocos_sprites):
            sp = blocos_sprites[nome]
            if x + 16 * k > largura - margem:
                x = margem
                y += 16 * k + 30
            out.alpha_composite(tile(sp.quadro(0), k), (x, y))
            rot = nome.removeprefix("block/").removeprefix("resonator_").removeprefix("resonance_")
            if len(rot) > 14:
                rot = rot[:13] + "…"
            d.text((x, y + 16 * k + 2), rot, fill=tinta2, font=_fonte(9))
            x += 16 * k + 14
        y += 16 * k + 40
    if previas:
        y += 10
        titulo("Blocos: prévias montadas")
        x = margem
        alt_linha = 0
        for nome, img in previas:
            if x + img.width > largura - margem:
                x = margem
                y += alt_linha + 26
                alt_linha = 0
            if out.height < y + img.height + 60:
                novo = Image.new("RGBA", (largura, out.height + 2000), fundo)
                novo.paste(out, (0, 0))
                out = novo
                d = ImageDraw.Draw(out)
            out.alpha_composite(img.convert("RGBA"), (x, y))
            d.text((x, y + img.height + 2), nome, fill=tinta, font=fonte)
            x += img.width + gap + 8
            alt_linha = max(alt_linha, img.height)
        y += alt_linha + 30
    return out.crop((0, 0, largura, y + margem))


def main() -> None:
    so_folha = "--so-folha" in sys.argv
    itens = gerar_itens()

    blocos = None
    try:
        import blocos as _blocos  # type: ignore[import-not-found]
        blocos = _blocos
    except ImportError:
        print("aviso: scripts/textures/blocos.py não encontrado; só os itens e os ícones da interface")
    blocos_sprites: dict[str, Sprite] = blocos.sprites() if blocos else {}
    previas: list[tuple[str, Image.Image]] = blocos.previas(blocos_sprites) if blocos else []

    todos = {**itens, **blocos_sprites}
    apagados = 0
    if not so_folha:
        for nome, sp in todos.items():
            salvar(nome, sp)
        modelos_itens()
        if blocos:
            blocos.modelos()
        pastas = ["item", "gui"] + (["block"] if blocos_sprites else [])
        apagados = limpar(set(todos), pastas)

    FOLHA.parent.mkdir(parents=True, exist_ok=True)
    folha(itens, blocos_sprites, previas).convert("RGB").save(FOLHA)
    prancha_icones(itens).convert("RGB").save(PRANCHA_ICONES)
    estado = "não gravados" if so_folha else f"gravados, {apagados} arquivo(s) antigo(s) apagado(s)"
    print(f"{len(todos)} sprites ({estado}); folha em {FOLHA.relative_to(RAIZ)}, "
          f"ícones da interface em {PRANCHA_ICONES.relative_to(RAIZ)}")


if __name__ == "__main__":
    main()

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
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, str(Path(__file__).resolve().parent))

from identidade import (  # noqa: E402
    ASSETS, LENTE, MARCAS, MODELOS, PALETA, PREVIEW, RAIZ, MODO, RECURSO, TIERS_ATM, TIERS_CARTAO,
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
    sprites["item/guide"] = guia()
    for modo in ("extract", "insert", "both"):
        sprites[f"gui/port_{modo}"] = porta(modo)
    sprites["gui/port_none"] = porta_nenhum()
    for nome in ICONES_TIPO:
        sprites[f"gui/type/{nome}"] = icone_tipo(nome)
    return sprites


def modelos_itens() -> None:
    for tier in TIERS_CARTAO:
        grava_json(MODELOS / f"models/item/tier_core_{tier}.json", {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": f"wirelessautomate:item/tier_core_{tier}"},
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
                  [f"tier_core_{t}" for t in TIERS_CARTAO] + ["chunk_loader_upgrade", "guide"]
    for nome in nomes_itens:
        sp = itens[f"item/{nome}"]
        d.text((margem, y), f"{nome}: {sp.quadros} quadros, frametime {sp.frametime}", fill=tinta, font=fonte)
        y += 18
        x = margem
        for n in range(sp.quadros):
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
            rot = nome.removeprefix("block/")
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

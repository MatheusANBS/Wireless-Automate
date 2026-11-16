"""As folhas de efeito da Armilar (`textures/fx/*.png`), portadas do `fx_atlas.py` do filme do conceito.

O `gerar_texturas.py` chama `gerar()`, que grava as 19 folhas e a prévia `docs/preview/armilar-fx.png` e devolve os
nomes gravados. Cada folha é uma imagem própria com os quadros empilhados (largura × altura do quadro × quadros), no
tamanho do `FxSheets` do cliente; o quadro de cada animação sai do relógio do efeito, por isso não há `.mcmeta`.

Peças cinza (branco com alfa) são tingidas pela cor do vértice; as coloridas levam a rampa pintada da PALETA. As
folhas têm degradê de alfa de propósito (o `cinza(a)` do filme), o que o `validar` do `identidade.py` não aceita:
este módulo grava com o `Image.save` direto e confere só o tamanho e se cada quadro tem pixel visível.

As contas (Bayer, `rnd`, rampas de alfa) são as do filme, pixel a pixel; mudar uma é mudar o conceito aprovado.

Uso: `python -I scripts/textures/fx_texturas.py`.
"""

from __future__ import annotations

import math
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, str(Path(__file__).resolve().parent))

from identidade import ASSETS, PREVIEW, cor  # noqa: E402

PASTA = ASSETS / "fx"
PREVIA = PREVIEW / "armilar-fx.png"

Cor = tuple[int, int, int, int]

# Os dois tons que a PALETA não tem: o latão mais escuro (LATAO[4]) e o coral mais escuro (CORAL[5]) do fx_atlas.py.
LATAO_ESCURO = cor("#4F3B10")
CORAL_ESCURO = cor("#4A1A12")

# As rampas do filme, do claro ao escuro, pelos tons da PALETA.
CORAL = [cor(f"coral.{t}") for t in "43210"] + [CORAL_ESCURO]
LATAO = [cor(f"latao.{t}") for t in "3210"] + [LATAO_ESCURO]
ENERGIA = [cor(f"energia.{t}") for t in "3210"]
BRANCO = cor("porcelana.4")

BAYER = [[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]


def com_alfa(c: Cor, a: int) -> Cor:
    return (c[0], c[1], c[2], a)


def put(img: Image.Image, x: int, y: int, c: Cor | None) -> None:
    if 0 <= x < img.width and 0 <= y < img.height and c:
        img.load()[x, y] = c


def cinza(a: float) -> Cor:
    return (255, 255, 255, int(max(0, min(255, a))))


def rnd(i: int, j: int, s: int = 0) -> float:
    return ((i * 73856093 ^ j * 19349663 ^ s * 83492791) & 0xFFFF) / 65535


# ---------- pontos de luz
def brilho(img: Image.Image, oy: int, n: int) -> None:
    """Disco em degraus com trama Bayer na borda e miolo quente."""
    for y in range(n):
        for x in range(n):
            d = math.hypot(x - n / 2 + 0.5, y - n / 2 + 0.5) / (n / 2)
            if d >= 1:
                continue
            nivel = d * 5
            a = [255, 220, 150, 90, 40][min(4, int(nivel))]
            if 3.4 < nivel < 4.6 and BAYER[y % 4][x % 4] > 8:
                a = 0
            put(img, x, oy + y, cinza(a))


def estrela(img: Image.Image, oy: int, f: int) -> None:
    """4 quadros: as pontas crescem e giram de leve (cintila sem piscar o brilho do miolo)."""
    c = 7.5
    L = [5, 7.5, 6, 7][f]
    diag = [0, 0, 3, 4][f]
    for y in range(16):
        for x in range(16):
            dx, dy = abs(x - c), abs(y - c)
            a = 0
            if dx < 1 and dy < L:
                a = 255 if dy < 2 else 180 if dy < L * 0.6 else 90
            if dy < 1 and dx < L:
                a = max(a, 255 if dx < 2 else 180 if dx < L * 0.6 else 90)
            if diag and abs(dx - dy) < 0.8 and dx < diag:
                a = max(a, 120)
            if dx + dy < 2.2:
                a = 255
            put(img, x, oy + y, cinza(a) if a else None)


def brasa(img: Image.Image, oy: int, k: int) -> None:
    """Brasas: 3×3 cruz, 4×4 com borda, 6×6 losango com miolo (cinza, dois tons)."""
    if k == 0:
        for (x, y, a) in [(1, 0, 150), (0, 1, 150), (1, 1, 255), (2, 1, 150), (1, 2, 150)]:
            put(img, x, oy + y, cinza(a))
    elif k == 1:
        for y in range(4):
            for x in range(4):
                if x in (0, 3) and y in (0, 3):
                    continue
                put(img, x, oy + y, cinza(150 if (x in (0, 3) or y in (0, 3)) else 255))
    else:
        for y in range(6):
            for x in range(6):
                d = abs(x - 2.5) + abs(y - 2.5)
                if d < 3.2:
                    put(img, x, oy + y, cinza(255 if d < 1.2 else 190 if d < 2.2 else 110))


def risco(img: Image.Image, oy: int, n: int) -> None:
    for x in range(n):
        a = 255 if x > n * 0.6 else 170 if x > n * 0.3 else 80
        put(img, x, oy, cinza(a))
        put(img, x, oy + 1, cinza(a // 2))


# ---------- aros e choque
def aro(img: Image.Image, oy: int, raio_: float, esp: float, tracejado: bool = False) -> None:
    for y in range(32):
        for x in range(32):
            d = math.hypot(x - 15.5, y - 15.5)
            if raio_ - esp <= d < raio_:
                if tracejado:
                    ang = (math.degrees(math.atan2(y - 15.5, x - 15.5)) + 360) % 30
                    if ang > 18:
                        continue
                a = 255 if d >= raio_ - 1 else 120
                put(img, x, oy + y, cinza(a))


def choque(img: Image.Image, oy: int, f: int) -> None:
    """6 quadros: o anel nasce grosso e quente, abre, quebra em trama e some em pixels soltos."""
    r = [6, 9, 11.5, 13.5, 14.5, 15.3][f]
    esp = [4, 3.5, 3, 2.5, 2, 1.2][f]
    corte = [0, 0, 2, 6, 10, 13][f]
    for y in range(32):
        for x in range(32):
            d = math.hypot(x - 15.5, y - 15.5)
            if r - esp <= d < r:
                if BAYER[y % 4][x % 4] < corte:
                    continue
                k = (d - (r - esp)) / esp
                a = 255 if k > 0.66 else 190 if k > 0.33 else 110
                put(img, x, oy + y, cinza(a))


def clarao(img: Image.Image, oy: int, f: int) -> None:
    """4 quadros do clarão de impacto: miolo, 8 raios, raios finos com anel, estilhaços."""
    for y in range(32):
        for x in range(32):
            dx, dy = x - 15.5, y - 15.5
            d = math.hypot(dx, dy)
            ang = math.atan2(dy, dx)
            raio8 = abs(math.sin(ang * 4))
            a = 0
            if f == 0:
                a = 255 if d < 4 else 160 if d < 6 else 0
            elif f == 1:
                if d < 5:
                    a = 255
                elif raio8 < 0.18 and d < 15:
                    a = 255 if d < 9 else 170
            elif f == 2:
                if d < 3:
                    a = 220
                elif raio8 < 0.1 and 6 < d < 15:
                    a = 200
                elif 10 < d < 11.2:
                    a = 140
            else:
                if raio8 < 0.12 and 11 < d < 15 and BAYER[y % 4][x % 4] > 5:
                    a = 170
                elif 13 < d < 14 and BAYER[y % 4][x % 4] > 10:
                    a = 110
            put(img, x, oy + y, cinza(a) if a else None)


# ---------- fumaça, chama e o sol
def fumaca(img: Image.Image, oy: int, f: int) -> None:
    """4 quadros: a nuvem cresce, abre buracos e some em trama."""
    for y in range(16):
        for x in range(16):
            r = (5 + f * 0.9) / 8
            d = math.hypot(x - 7.5, y - 7.5) / 8 / r + (rnd(x // 2, y // 2, f) - 0.5) * 0.5
            if d >= 1:
                continue
            a = [170, 110, 55][min(2, int(d * 3))]
            if BAYER[y % 4][x % 4] < f * 3:
                continue
            put(img, x, oy + y, cinza(a))


def chama(img: Image.Image, oy: int, f: int) -> None:
    """6 quadros de língua de fogo (16 × 24), rampa de energia para coral."""
    for y in range(24):
        for x in range(16):
            h = 1 - y / 23
            larg = 6.5 * math.sin(min(1, (1 - h) * 1.25) * math.pi * 0.5) * (0.8 + 0.2 * math.sin(f * 1.05 + h * 6))
            cx = 7.5 + 2.2 * math.sin(f * 1.05 + h * 4.2) * h
            dx = abs(x - cx)
            if dx > larg:
                continue
            k = dx / max(larg, 0.1) * 0.6 + h * 0.6
            if k < 0.35:
                c = BRANCO
            elif k < 0.6:
                c = ENERGIA[1]
            elif k < 0.85:
                c = CORAL[1]
            else:
                c = com_alfa(CORAL[3], 200)
            if h > 0.8 and BAYER[y % 4][x % 4] < (h - 0.8) * 60:
                continue
            put(img, x, oy + y, c)


def sol(img: Image.Image, oy: int, f: int) -> None:
    """6 quadros do núcleo (32 × 32): disco com granulação que ferve, borda escura, línguas na coroa."""
    for y in range(32):
        for x in range(32):
            dx, dy = x - 15.5, y - 15.5
            d = math.hypot(dx, dy)
            ang = math.atan2(dy, dx)
            if d < 10.5:
                g = rnd(int(x + f * 2) // 2, int(y - f) // 2, 7)
                k = d / 10.5
                if k < 0.35:
                    c = BRANCO if g > 0.3 else ENERGIA[0]
                elif k < 0.65:
                    c = ENERGIA[0] if g > 0.55 else ENERGIA[1]
                elif k < 0.88:
                    c = ENERGIA[1] if g > 0.6 else CORAL[1]
                else:
                    c = CORAL[2]
                put(img, x, oy + y, c)
            else:
                lingua = 12.5 + 2.5 * max(0, math.sin(ang * 5 + f * 1.05)) * (0.6 + 0.4 * math.sin(ang * 3 - f))
                if d < lingua:
                    k = (d - 10.5) / max(lingua - 10.5, 0.1)
                    if BAYER[y % 4][x % 4] < k * 14:
                        continue
                    put(img, x, oy + y, com_alfa(CORAL[1] if k < 0.5 else CORAL[3], 220))


# ---------- tiras repetíveis (64 × 8)
def tira(img: Image.Image, oy: int, fn) -> None:
    for y in range(8):
        for x in range(64):
            put(img, x, oy + y, fn(x, y))


def miolo(x: int, y: int) -> Cor | None:
    base = [None, com_alfa(ENERGIA[2], 130), ENERGIA[1], BRANCO, BRANCO, ENERGIA[1], com_alfa(ENERGIA[2], 130), None][y]
    if y in (2, 5) and (x * 7) % 11 == 0:
        return ENERGIA[0]
    return base


def trilhos(x: int, y: int) -> Cor | None:
    if y in (1, 6):
        return ENERGIA[0] if (x % 8) < 6 else ENERGIA[1]
    if y in (0, 7):
        return com_alfa(ENERGIA[3], 150)
    g = rnd(x // 2, y, 3)
    c = CORAL[5] if g < 0.45 else CORAL[4] if g < 0.85 else CORAL[3]
    return com_alfa(c, 220)


def trilhos_rim(x: int, y: int) -> Cor | None:
    if y in (1, 6):
        return BRANCO if (x % 16) in (0, 1) else com_alfa(ENERGIA[0], 200)
    return None


def fluxo(v: int):
    def fn(x: int, y: int) -> Cor | None:
        k = (x + v * 8) % 16
        if y in (3, 4) and k < 7:
            return BRANCO if k < 2 else ENERGIA[0] if k < 4 else com_alfa(ENERGIA[1], 220)
        if y in (2, 5) and 1 <= k < 5:
            return com_alfa(ENERGIA[2], 150)
        if y in (3, 4) and k < 13:
            return com_alfa(CORAL[1], 110 - (k - 7) * 12)
        return None
    return fn


def raio(semente: int):
    """Tira dentada com ramos curtos; os extremos voltam ao meio para repetir sem emenda."""
    cam, ram = [], {}
    y = 3.5
    for x in range(64):
        if x % 4 == 0 and 2 < x < 60:
            y = 1.5 + rnd(x, semente, 11) * 5
            if rnd(x, semente, 5) > 0.72:
                ram[x] = (1 if y < 3.5 else -1)
        if x >= 60:
            y += (3.5 - y) * 0.5
        cam.append(int(round(y)))

    def fn(x: int, yy: int) -> Cor | None:
        d = abs(yy - cam[x])
        for rx, s in ram.items():
            if 0 <= x - rx < 3 and yy == cam[rx] + s * (x - rx + 1):
                return com_alfa(ENERGIA[0], 220)
        if d == 0:
            return BRANCO
        if d == 1:
            return com_alfa(ENERGIA[1], 220)
        if d == 2:
            return com_alfa(CORAL[2], 100)
        return None
    return fn


# ---------- o sigilo do mostrador (64 × 64), 4 quadros: as marcas acendem conforme carrega
def sigilo(img: Image.Image, oy: int, f: int) -> None:
    for y in range(64):
        for x in range(64):
            dx, dy = x - 31.5, y - 31.5
            d = math.hypot(dx, dy)
            ang = (math.degrees(math.atan2(dy, dx)) + 450) % 360
            c = None
            aceso = ang < (f + 1) * 90
            if 30 <= d < 31:
                c = LATAO[1]
            elif 29 <= d < 30:
                c = com_alfa(LATAO[3], 200)
            elif 22 <= d < 28.5:
                passo = ang % 10
                longo = ang % 30 < 2.2
                if passo < 1.6 and (d >= 25 or longo):
                    c = ENERGIA[0] if aceso and longo else ENERGIA[1] if aceso else LATAO[2]
            elif 19 <= d < 20.5:
                c = CORAL[2] if aceso else CORAL[3]
            elif 18 <= d < 19:
                c = com_alfa(CORAL[4], 200)
            elif 8 <= d < 9 and BAYER[y % 4][x % 4] > 7:
                c = com_alfa(LATAO[2], 200)
            elif d < 6 and ((abs(dx) < 1 or abs(dy) < 1) and d > 2):
                c = CORAL[1]
            if abs(dx) < 1.2 and dy < 0 and 6 < -dy < 17 and f >= 1:
                c = ENERGIA[0]
            if c:
                put(img, x, oy + y, c)


def _tira(fn_pixel):
    return lambda img, oy, f: tira(img, oy, fn_pixel)


def _raios(img: Image.Image, oy: int, f: int) -> None:
    tira(img, oy, raio(f * 7 + 1))


# Nome, largura, altura do quadro, quadros, e o desenho de um quadro `(img, oy, f)`. Os tamanhos são os do `FxSheets`.
FOLHAS = [
    ("fumaca", 16, 16, 4, fumaca),
    ("trilhos", 64, 8, 1, _tira(trilhos)),
    ("brilho", 16, 16, 1, lambda img, oy, f: brilho(img, oy, 16)),
    ("brilho_g", 32, 32, 1, lambda img, oy, f: brilho(img, oy, 32)),
    ("sol", 32, 32, 6, sol),
    ("chama", 16, 24, 6, chama),
    ("estrela", 16, 16, 4, estrela),
    ("aro_tracejado", 32, 32, 1, lambda img, oy, f: aro(img, oy, 15, 2, True)),
    ("brasa0", 3, 3, 1, lambda img, oy, f: brasa(img, oy, 0)),
    ("brasa1", 4, 4, 1, lambda img, oy, f: brasa(img, oy, 1)),
    ("brasa2", 6, 6, 1, lambda img, oy, f: brasa(img, oy, 2)),
    ("risco_l", 12, 2, 1, lambda img, oy, f: risco(img, oy, 12)),
    ("choque", 32, 32, 6, choque),
    ("clarao", 32, 32, 4, clarao),
    ("trilhos_rim", 64, 8, 1, _tira(trilhos_rim)),
    ("miolo", 64, 8, 1, _tira(miolo)),
    ("fluxo", 64, 8, 1, _tira(fluxo(0))),
    ("raio", 64, 8, 8, _raios),
    ("sigilo", 64, 64, 4, sigilo),
]


def folha(largura: int, altura: int, quadros: int, desenho) -> Image.Image:
    img = Image.new("RGBA", (largura, altura * quadros), (0, 0, 0, 0))
    for f in range(quadros):
        desenho(img, f * altura, f)
    return img


def conferir(nome: str, img: Image.Image, largura: int, altura: int, quadros: int) -> None:
    if img.size != (largura, altura * quadros):
        raise ValueError(f"fx/{nome}: tamanho {img.size}, esperado {(largura, altura * quadros)}")
    for f in range(quadros):
        quadro = img.crop((0, f * altura, largura, (f + 1) * altura)).getchannel("A")
        if quadro.getextrema()[1] == 0:
            raise ValueError(f"fx/{nome}: o quadro {f} não tem pixel visível")


def previa(imagens: dict[str, Image.Image]) -> Image.Image:
    """As folhas lado a lado em 4× com o nome, no fundo escuro e no claro."""
    esc, margem, largura_max, topo = 4, 16, 2200, 18
    fonte = ImageFont.load_default()
    paineis = []
    for fundo in [(24, 22, 30, 255), (236, 230, 216, 255)]:
        itens = [(n, i.resize((i.width * esc, i.height * esc), Image.NEAREST)) for n, i in imagens.items()]
        # Prateleiras: a linha cresce até a largura máxima; a altura é a da folha mais alta dela.
        linhas, atual, usado = [], [], margem
        for nome, g in itens:
            if atual and usado + g.width + margem > largura_max:
                linhas.append(atual)
                atual, usado = [], margem
            atual.append((nome, g))
            usado += max(g.width, 90) + margem
        if atual:
            linhas.append(atual)
        altura = margem + sum(max(g.height for _, g in ln) + topo + margem for ln in linhas)
        painel = Image.new("RGBA", (largura_max, altura), fundo)
        d = ImageDraw.Draw(painel)
        texto = (240, 236, 226, 255) if fundo[0] < 100 else (40, 36, 44, 255)
        y = margem
        for ln in linhas:
            x = margem
            for nome, g in ln:
                d.text((x, y), nome, fill=texto, font=fonte)
                painel.alpha_composite(g, (x, y + topo))
                x += max(g.width, 90) + margem
            y += max(g.height for _, g in ln) + topo + margem
        paineis.append(painel)
    out = Image.new("RGBA", (largura_max, sum(p.height for p in paineis)), (0, 0, 0, 255))
    y = 0
    for p in paineis:
        out.paste(p, (0, y))
        y += p.height
    return out.convert("RGB")


def gerar() -> set[str]:
    """Grava as folhas e a prévia; devolve os nomes gravados e apaga da pasta o que sobrou de antes."""
    PASTA.mkdir(parents=True, exist_ok=True)
    imagens: dict[str, Image.Image] = {}
    for nome, largura, altura, quadros, desenho in FOLHAS:
        img = folha(largura, altura, quadros, desenho)
        conferir(nome, img, largura, altura, quadros)
        img.save(PASTA / f"{nome}.png")
        imagens[nome] = img
    for arq in PASTA.glob("*.png"):
        if arq.stem not in imagens:
            arq.unlink()
    PREVIA.parent.mkdir(parents=True, exist_ok=True)
    previa(imagens).save(PREVIA)
    return set(imagens)


if __name__ == "__main__":
    print(f"{len(gerar())} folhas em {PASTA}")

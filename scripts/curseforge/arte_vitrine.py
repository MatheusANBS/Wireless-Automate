"""Gera a pixel art animada da vitrine do CurseForge (a Tela de Sinal) e o ícone do painel, no formato WA-ARTE1.

    python -I scripts/curseforge/arte_vitrine.py [pasta] [--previa <png>]

A cena, 128 x 64 em 12 quadros a 8 q/s (um laço de 1,5 s): céu noturno em degradê por rampas da paleta de 16 com
pontilhado ordenado só nas passagens, lua com halo, estrelas que piscam em quadros diferentes, colinas em duas
camadas, uma torre de sinal com o roteador do mod no alto soltando ondas em arco que se expandem quadro a quadro e
uma base no chão com as janelas acesas e a antena que pisca quando a onda chega. As ondas andam exatamente um
espaçamento por laço, então o último quadro emenda no primeiro sem salto.

O ícone, 16 x 16 em 6 quadros a 4 q/s (o mesmo laço de 1,5 s, para o GIF da parede emendar): o roteador com os
arcos acendendo um a um, para o canto do painel.

Grava `arte-vitrine.txt` e `icone-vitrine.txt` em <pasta> (padrão run/showcase/entrada), que a vitrine
(E2eShowcaseTv) lê pelo mesmo leitor do jogo. Com --previa, grava também os quadros ampliados (NEAREST) numa folha.
"""
from __future__ import annotations

import math
import random
import sys
from pathlib import Path

from PIL import Image

RAIZ = Path(__file__).resolve().parents[2]
PASTA_PADRAO = RAIZ / "run" / "showcase" / "entrada"

W, H = 128, 64
QUADROS = 12
VELOCIDADE = 8

# A paleta de 16 (a 0 é a transparente), em rampas: céu (1 a 5), silhuetas (6 e 7), lua e estrelas (8, 9 e 11),
# janela (10), ondas (12 a 14) e o coral do LED (15).
PALETA = [
    None,
    0x0B0D1F,  # 1 céu, o alto
    0x141A38,  # 2
    0x1E2756,  # 3
    0x2B3672,  # 4
    0x434A90,  # 5 céu, o brilho do horizonte
    0x05060D,  # 6 chão perto
    0x161B3B,  # 7 colinas longe
    0xF6EED4,  # 8 lua, estrela forte, corpo do roteador
    0xCFC39C,  # 9 sombra da lua, topo do roteador
    0xFFC65A,  # 10 janela acesa
    0x8A97CF,  # 11 estrela fraca
    0x92F7EE,  # 12 onda forte
    0x36BDB8,  # 13 onda média
    0x1F6E84,  # 14 onda fraca
    0xFF6A4D,  # 15 LED coral
]
CEU = [1, 2, 3, 4, 5]
CHAO, COLINA = 6, 7
LUA, LUA_SOMBRA, JANELA, ESTRELA_FRACA = 8, 9, 10, 11
ONDA = [12, 13, 14]
CORAL = 15

# Bayer 4 x 4, de 0 a 15: o limiar do pontilhado ordenado.
BAYER = [[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]

TORRE_X = 26
LUA_C = (104, 13)
LUA_R = 7
ESPACO_ONDA = 10
BASE_X0, BASE_X1 = 92, 113


def pontilha(x: int, y: int, fracao: float) -> bool:
    """Se o pixel leva a cor de baixo numa passagem com {@code fracao} (0 a 1) dela."""
    return BAYER[y % 4][x % 4] < fracao * 16


def ceu(x: int, y: int) -> int:
    """O degradê em faixas com uma passagem pontilhada de 4 linhas entre cada duas."""
    bordas = [9, 18, 27, 35]
    for i, borda in enumerate(bordas):
        if y < borda - 2:
            return CEU[i]
        if y < borda + 2:
            return CEU[i + 1] if pontilha(x, y, (y - (borda - 2) + 0.5) / 4) else CEU[i]
    return CEU[4]


def colina_longe(x: int) -> int:
    return round(43 + 2.6 * math.sin(x * 0.071 + 1.3) + 1.6 * math.sin(x * 0.19 + 0.4) + 1.2 * math.sin(x * 0.033))


def chao_perto(x: int) -> int:
    morro = 7.5 * math.exp(-((x - TORRE_X) / 15) ** 2)
    base = 3.0 * math.exp(-((x - (BASE_X0 + BASE_X1) / 2) / 22) ** 2)
    return round(55 + 2.2 * math.sin(x * 0.05 + 2.1) + 1.1 * math.sin(x * 0.14) - morro - base)


TOPO_TORRE = 15
CENTRO_ONDA = (TORRE_X, TOPO_TORRE - 2)


def fundo() -> list[list[int]]:
    """O que não anda: céu, halo e lua, colinas e chão."""
    px = [[ceu(x, y) for x in range(W)] for y in range(H)]
    cx, cy = LUA_C
    for y in range(H):
        for x in range(W):
            d = math.hypot(x - cx, y - cy)
            if LUA_R + 0.5 < d < LUA_R + 3.5 and pontilha(x, y, 0.5 if d < LUA_R + 2 else 0.25):
                px[y][x] = 3 if px[y][x] <= 2 else px[y][x] + 1
    for y in range(H):
        for x in range(W):
            d = math.hypot(x - cx + 0.3, y - cy + 0.3)
            if d <= LUA_R:
                # o terminador: a sombra é a lua deslocada para baixo à esquerda
                sombra = math.hypot(x - cx - 3.2, y - cy + 2.4) > LUA_R + 0.6
                px[y][x] = LUA_SOMBRA if sombra else LUA
    for x, y, r in ((101, 11, 1.1), (106, 16, 1.4), (103, 17, 0.8), (108, 10, 0.9)):
        for yy in range(y - 2, y + 3):
            for xx in range(x - 2, x + 3):
                if math.hypot(xx - x, yy - y) <= r and px[yy][xx] == LUA:
                    px[yy][xx] = LUA_SOMBRA
    for x in range(W):
        for y in range(colina_longe(x), H):
            px[y][x] = COLINA
        for y in range(chao_perto(x), H):
            px[y][x] = CHAO
    return px


def estrelas() -> list[tuple[int, int, int, int]]:
    """(x, y, tipo, fase): 0 fraca parada, 1 forte parada, 2 que pisca devagar, 3 a que cintila em cruz."""
    rnd = random.Random(20261027)
    lista: list[tuple[int, int, int, int]] = []
    tentativas = 0
    while len(lista) < 46 and tentativas < 5000:
        tentativas += 1
        x, y = rnd.randrange(2, W - 2), rnd.randrange(1, 38)
        if math.hypot(x - LUA_C[0], y - LUA_C[1]) < LUA_R + 5:
            continue
        if abs(x - TORRE_X) < 9 and y > TOPO_TORRE - 6:
            continue
        if y >= colina_longe(x) - 3:
            continue
        if any(abs(x - a) + abs(y - b) < 5 for a, b, *_ in lista):
            continue
        tipo = 3 if len(lista) < 7 else 2 if len(lista) < 18 else 1 if len(lista) < 26 else 0
        lista.append((x, y, tipo, rnd.randrange(QUADROS)))
    return lista


def desenha_estrelas(px: list[list[int]], lista, q: int) -> None:
    for x, y, tipo, fase in lista:
        k = (q - fase) % QUADROS
        if tipo == 0:
            px[y][x] = ESTRELA_FRACA
        elif tipo == 1:
            px[y][x] = LUA
        elif tipo == 2:
            px[y][x] = LUA if k < 6 else ESTRELA_FRACA
        elif k in (0, 1):
            px[y][x] = LUA
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                if px[y + dy][x + dx] in CEU:
                    px[y + dy][x + dx] = ESTRELA_FRACA
            if k == 0:
                for dx, dy in ((2, 0), (-2, 0), (0, 2), (0, -2)):
                    if 0 <= y + dy < H and px[y + dy][x + dx] in CEU and pontilha(x + dx, y + dy, 0.5):
                        px[y + dy][x + dx] = 4
        elif k == 2:
            px[y][x] = LUA
        else:
            px[y][x] = ESTRELA_FRACA


def desenha_ondas(px: list[list[int]], q: int) -> None:
    """Arcos para os dois lados da antena (os setores de 42 graus à esquerda e à direita), um espaçamento por laço."""
    cx, cy = CENTRO_ONDA
    for y in range(H):
        for x in range(W):
            if px[y][x] not in CEU and px[y][x] != COLINA:
                continue
            dx, dy = x - cx, y - cy
            d = math.hypot(dx, dy)
            if d < 4 or d > 56:
                continue
            angulo = abs(math.degrees(math.atan2(dy, abs(dx))))
            if angulo > 42:
                continue
            fase = (d - 4 - ESPACO_ONDA * q / QUADROS) % ESPACO_ONDA
            if not (fase < 1.15 or fase > ESPACO_ONDA - 0.15):
                continue
            borda = angulo > 35
            if d < 16:
                cor, fracao = ONDA[0], 1.0
            elif d < 30:
                cor, fracao = ONDA[1], 1.0
            elif d < 42:
                cor, fracao = ONDA[2], 1.0
            elif d < 50:
                cor, fracao = ONDA[2], 0.5
            else:
                cor, fracao = ONDA[2], 0.25
            if borda:
                fracao *= 0.5
            if px[y][x] == COLINA:
                fracao *= 0.5
            if fracao >= 1 or pontilha(x, y, fracao):
                px[y][x] = cor


def linha(px, x0: int, y0: int, x1: int, y1: int, cor: int) -> None:
    """Bresenham, sem pixel dobrado."""
    dx, dy = abs(x1 - x0), -abs(y1 - y0)
    sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
    erro = dx + dy
    while True:
        if 0 <= x0 < W and 0 <= y0 < H:
            px[y0][x0] = cor
        if x0 == x1 and y0 == y1:
            return
        e2 = 2 * erro
        if e2 >= dy:
            erro += dy
            x0 += sx
        if e2 <= dx:
            erro += dx
            y0 += sy


def desenha_torre(px: list[list[int]], q: int) -> None:
    """A torre treliçada em silhueta contra o céu, o mastro e o roteador no alto com o LED."""
    pe = chao_perto(TORRE_X) + 1
    topo_trelica = TOPO_TORRE + 6
    esq = [(TORRE_X - 5, pe), (TORRE_X - 1, topo_trelica)]
    dir_ = [(TORRE_X + 5, pe), (TORRE_X + 1, topo_trelica)]

    def em(perna, y):
        (xa, ya), (xb, yb) = perna
        return round(xa + (xb - xa) * (y - ya) / (yb - ya))

    niveis = list(range(pe - 1, topo_trelica, -6))
    for a, b in zip(niveis, niveis[1:] + [topo_trelica]):
        linha(px, em(esq, a), a, em(dir_, b), b, CHAO)
        linha(px, em(dir_, a), a, em(esq, b), b, CHAO)
        linha(px, em(esq, b), b, em(dir_, b), b, CHAO)
    linha(px, *esq[0], *esq[1], CHAO)
    linha(px, *dir_[0], *dir_[1], CHAO)
    for y in range(TOPO_TORRE + 1, topo_trelica + 1):
        px[y][TORRE_X] = CHAO
    # luz de aviso no meio da torre, piscando
    meio = (pe + topo_trelica) // 2
    if q % 6 < 3:
        px[meio][em(dir_, meio)] = CORAL
    # o roteador: 5 x 4, topo mais escuro, a lente ciano e o LED coral no canto
    x0, y0 = TORRE_X - 2, TOPO_TORRE - 3
    for y in range(y0, y0 + 4):
        for x in range(x0, x0 + 5):
            px[y][x] = LUA_SOMBRA if y == y0 else LUA
    px[y0 + 2][TORRE_X] = ONDA[0]
    px[y0 + 2][TORRE_X - 1] = ONDA[1]
    px[y0 + 2][TORRE_X + 1] = ONDA[1]
    px[y0 + 1][x0 + 4] = CORAL if q % 4 < 2 else LUA_SOMBRA
    # a antena do roteador
    px[y0 - 1][TORRE_X] = LUA_SOMBRA
    px[y0 - 2][TORRE_X] = ONDA[0] if q % 2 == 0 else ONDA[1]


def chegada() -> int:
    """O quadro em que uma onda passa pela antena da base (a distância dela à torre, no passo das ondas)."""
    ax, ay = BASE_X0 + 3, chao_perto(BASE_X0 + 3) - 9
    d = math.hypot(ax - CENTRO_ONDA[0], ay - CENTRO_ONDA[1])
    return round(((d - 4) % ESPACO_ONDA) / ESPACO_ONDA * QUADROS) % QUADROS


def desenha_base(px: list[list[int]], q: int) -> None:
    """A base no chão: um galpão com o teto em arco, as janelas acesas e a antena receptora que pisca."""
    chao = max(chao_perto(x) for x in range(BASE_X0, BASE_X1 + 1))
    topo = chao - 7
    meio = (BASE_X0 + BASE_X1) / 2
    for x in range(BASE_X0, BASE_X1 + 1):
        arco = round(2.5 * (1 - ((x - meio) / ((BASE_X1 - BASE_X0) / 2)) ** 2))
        for y in range(topo - arco, H):
            px[y][x] = CHAO
    for i, x in enumerate(range(BASE_X0 + 3, BASE_X1 - 2, 3)):
        apagada = i == 4 and q in (5, 6, 7)
        if not apagada:
            px[topo + 2][x] = JANELA
            px[topo + 3][x] = JANELA
    # a porta, mais clara
    for y in range(chao - 3, chao + 1):
        px[y][BASE_X1 - 3] = 4
    # a antena receptora: mastro e prato virado para a torre
    ax = BASE_X0 + 3
    for y in range(topo - 8, topo - 2):
        px[y][ax] = CHAO
    for dy, largura in ((-10, 2), (-9, 3), (-8, 2)):
        for dx in range(largura):
            px[topo + dy][ax - dx] = CHAO
    k = (q - chegada()) % QUADROS
    if k < 2:
        px[topo - 11][ax] = ONDA[0] if k == 0 else ONDA[1]
        if k == 0:
            px[topo - 11][ax - 1] = ONDA[2]
            px[topo - 11][ax + 1] = ONDA[2]
    # arbustos no morro da torre
    for x in (TORRE_X - 11, TORRE_X - 9, TORRE_X + 10, TORRE_X + 13, 58, 61, 76):
        y = chao_perto(x) - 1
        px[y][x] = CHAO
        px[y][x + 1] = CHAO
        px[y - 1][x] = CHAO


def cena() -> list[list[list[int]]]:
    base = fundo()
    lista = estrelas()
    quadros = []
    for q in range(QUADROS):
        px = [linha_[:] for linha_ in base]
        desenha_estrelas(px, lista, q)
        desenha_ondas(px, q)
        desenha_torre(px, q)
        desenha_base(px, q)
        quadros.append(px)
    return quadros


def icone() -> list[list[list[int]]]:
    """16 x 16, 6 quadros: o roteador com a lente acesa, três arcos de cada lado acendendo um a um, todos e nenhum."""
    arcos = [
        [(10, 3), (11, 4), (10, 5)],
        [(12, 2), (13, 3), (13, 4), (13, 5), (12, 6)],
        [(14, 1), (15, 2), (15, 3), (15, 4), (15, 5), (15, 6), (14, 7)],
    ]
    quadros = []
    for q in range(6):
        px = [[0] * 16 for _ in range(16)]
        for y in range(9, 14):
            for x in range(5, 12):
                px[y][x] = LUA_SOMBRA if y == 9 else LUA
        px[11][8] = ONDA[0]
        px[11][7] = px[11][9] = ONDA[1]
        px[10][11] = CORAL if q % 2 == 0 else LUA_SOMBRA
        for y in range(5, 9):
            px[y][8] = LUA_SOMBRA
        px[4][8] = ONDA[0]
        for k, arco in enumerate(arcos):
            if q == 4:
                cor = ONDA[1]
            elif q in (0, 5) or k > q - 1:
                cor = ONDA[2]
            else:
                cor = ONDA[0] if k == q - 1 else ONDA[1]
            for x, y in arco:
                px[y][x] = cor
                px[y][16 - x] = cor
        quadros.append(px)
    return quadros


def texto(nome: str, quadros: list[list[list[int]]], velocidade: int, cores: list) -> str:
    w, h = len(quadros[0][0]), len(quadros[0])
    paleta = " ".join("-" if c is None else f"{c:06X}" for c in cores)
    saida = ["WA-ARTE1", f"name: {nome}", f"size: {w}x{h}", f"palette: {paleta}",
             f"frames: {len(quadros)}", f"speed: {velocidade}", "mode: loop"]
    for i, px in enumerate(quadros):
        saida.append(f"frame {i + 1}:")
        saida.extend("".join("." if c == 0 else format(c, "x") for c in fila) for fila in px)
    return "\n".join(saida) + "\n"


def previa(quadros: list[list[list[int]]], destino: Path, escala: int = 6, colunas: int = 3) -> None:
    w, h = len(quadros[0][0]), len(quadros[0])
    linhas = (len(quadros) + colunas - 1) // colunas
    folha = Image.new("RGB", (colunas * (w * escala + 8) + 8, linhas * (h * escala + 8) + 8), (40, 40, 48))
    for i, px in enumerate(quadros):
        img = Image.new("RGB", (w, h))
        img.putdata([(0, 0, 0) if c == 0 else ((PALETA[c] >> 16) & 255, (PALETA[c] >> 8) & 255, PALETA[c] & 255)
                     for fila in px for c in fila])
        img = img.resize((w * escala, h * escala), Image.NEAREST)
        folha.paste(img, (8 + (i % colunas) * (w * escala + 8), 8 + (i // colunas) * (h * escala + 8)))
    folha.save(destino)


def main() -> None:
    args = sys.argv[1:]
    destino_previa = None
    if "--previa" in args:
        i = args.index("--previa")
        destino_previa = Path(args[i + 1])
        del args[i:i + 2]
    pasta = Path(args[0]) if args else PASTA_PADRAO
    pasta.mkdir(parents=True, exist_ok=True)
    quadros = cena()
    for i in range(QUADROS):
        anterior = quadros[i - 1]
        assert quadros[i] != anterior, f"o quadro {i + 1} é igual ao anterior"
    (pasta / "arte-vitrine.txt").write_text(texto("Signal Night", quadros, VELOCIDADE, PALETA), encoding="utf-8")
    (pasta / "icone-vitrine.txt").write_text(texto("Signal", icone(), 4, PALETA), encoding="utf-8")
    if destino_previa is not None:
        previa(quadros, destino_previa)
        previa(icone(), destino_previa.with_name(destino_previa.stem + "-icone.png"), escala=16, colunas=6)
    print(f"{pasta / 'arte-vitrine.txt'}: {W} x {H}, {QUADROS} quadros a {VELOCIDADE} q/s")


if __name__ == "__main__":
    main()

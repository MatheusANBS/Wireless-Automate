#!/usr/bin/env python3
"""Gera todas as texturas do Wireless Automate, pixel a pixel, a partir de paletas nomeadas.

Uso (na raiz do repositório):

    python scripts/textures/gerar_texturas.py            # grava os PNGs e a folha de sprites
    python scripts/textures/gerar_texturas.py --so-folha # só a folha (não toca nos PNGs)

Cada sprite é uma grade de 16 linhas de texto; cada caractere aponta para uma cor da legenda
do sprite ('.' é transparente). Para ajustar uma cor, mude a paleta; para mexer no desenho,
mude a grade. Regras de estilo (ver docs/pacote-de-design.md):

- 16×16, RGBA, só alfa 0 ou 255 (nada de semitransparência nem anti-aliasing);
- 3 a 5 tons por material, luz vindo de cima à esquerda (claro em cima/esquerda, escuro
  embaixo/direita);
- itens com contorno escuro; nada de pixels soltos nem ruído.

As faces do roteador só usam o canto superior esquerdo do quadro (frente/trás 14×6,
lateral 12×6, topo/base 14×12, antena: coluna 0 com 8 px e o quadrado 2×2 em x=2..3).
O resto do quadro fica transparente, menos o topo, que é preenchido com o casco para as
partículas de quebra (o modelo nunca lê essa área).
"""

from __future__ import annotations

import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

RAIZ = Path(__file__).resolve().parents[2]
ASSETS = RAIZ / "src/main/resources/assets/wirelessautomate/textures"
FOLHA = RAIZ / "docs/preview/folha-de-sprites.png"


# ---------------------------------------------------------------------------
# Paletas
# ---------------------------------------------------------------------------

def rgb(hexa: str) -> tuple[int, int, int, int]:
    hexa = hexa.lstrip("#")
    return (int(hexa[0:2], 16), int(hexa[2:4], 16), int(hexa[4:6], 16), 255)


PALETAS: dict[str, dict[str, str]] = {
    # Casco do roteador (metal escuro), do mais claro ao mais escuro.
    "casco": {"4": "#46525f", "3": "#36404b", "2": "#29313b", "1": "#1e242c", "0": "#12161b"},
    # Acento por tier do roteador (4 = brilho, 0 = sombra). Parte das cores já usadas nos rascunhos.
    "roteador_basic": {"4": "#e1e8ee", "3": "#b3bcc5", "2": "#8f99a4", "1": "#6c757e", "0": "#4c535b"},
    "roteador_advanced": {"4": "#ffe27d", "3": "#dcb257", "2": "#b88a2c", "1": "#916b1f", "0": "#644915"},
    "roteador_elite": {"4": "#a5f6ef", "3": "#5cc7c0", "2": "#2f9e98", "1": "#257d78", "0": "#185653"},
    "roteador_ultimate": {"4": "#dcc3ff", "3": "#9b7ad8", "2": "#6e44b8", "1": "#573593", "0": "#3b2368"},
    # Lentes dos LEDs apagadas (energia, rede, atividade, cheio): a camada emissiva entra depois.
    "leds": {"E": "#5a4a1c", "e": "#3a3014", "N": "#1c4f55", "n": "#133438",
             "A": "#2c5426", "a": "#1d3819", "F": "#5c2424", "f": "#3c1818"},
    # Mastro da antena (metal claro).
    "mastro": {"3": "#b9c2cc", "2": "#8c96a1", "1": "#646e79", "0": "#454d56"},

    # Placa dos cartões (upgrade e chunk loading): ardósia.
    "placa": {"4": "#6f7e90", "3": "#5a6878", "2": "#465260", "1": "#36404b", "0": "#262d36"},
    # Placa do cartão de filtro: verde de circuito.
    "placa_filtro": {"4": "#7fcfa8", "3": "#4fae86", "2": "#33896a", "1": "#246a52", "0": "#174a39"},
    "contorno": {"O": "#101318"},
    "ouro": {"4": "#fff3b8", "3": "#f7d670", "2": "#dba63a", "1": "#a8751f", "0": "#6b4712"},
    "chip": {"3": "#3a414c", "2": "#20252c", "1": "#14171c", "p": "#d3dbe3"},
    # Faixa de cor por tier nos cartões (cor de destaque da tela: GuiPaint.tierColor).
    "cartao_advanced": {"4": "#fff0b0", "3": "#ffd365", "2": "#f2b234", "1": "#c7841c", "0": "#87540f"},
    "cartao_elite": {"4": "#d2fdf8", "3": "#86efe6", "2": "#45d6cc", "1": "#2aa39b", "0": "#196b66"},
    "cartao_ultimate": {"4": "#efe2ff", "3": "#c7a3ff", "2": "#a46cff", "1": "#7a45d6", "0": "#4f2896"},
    "cartao_filtro": {"4": "#f4f7fa", "3": "#cdd5de", "2": "#a3aeba", "1": "#78838f", "0": "#525b65"},
    "funil": {"W": "#f2f8f5", "a": "#b3d3c4"},
    "cartao_chunk": {"4": "#e2ffc8", "3": "#b4f37f", "2": "#7fd34b", "1": "#509a2c", "0": "#2f641a"},

    # Configurador (varinha).
    "cristal": {"4": "#effffd", "3": "#9ff5ee", "2": "#45d6cc", "1": "#259a93", "0": "#16625e"},
    "madeira": {"3": "#b9844f", "2": "#8e5d33", "1": "#663f21", "0": "#432812"},
    # Tablet.
    "tablet": {"4": "#6a7584", "3": "#525c69", "2": "#3e4652", "1": "#2c323b", "0": "#14171c"},
    "tela": {"3": "#1d4752", "2": "#163843", "1": "#112c35", "0": "#0b1d24",
             "N": "#7ff6ea", "n": "#36c7bb", "L": "#2a8f8a", "Y": "#ffd36b", "y": "#e79a1f"},
    # Vinculador (controle laranja).
    "laranja": {"4": "#ffc890", "3": "#f7a35c", "2": "#e07a2f", "1": "#ad5320", "0": "#6e3110"},

    # Livro-guia: capa azul-ardósia, lombada escura e páginas creme.
    "livro": {"4": "#4a6b8c", "3": "#36536f", "2": "#2b4560", "1": "#20354b",
              "S": "#141f2b", "s": "#1d2c3c", "p": "#f1e8d0", "q": "#cbbf9f"},

    # Portas da tela.
    "porta_extract": {"4": "#a8cbff", "3": "#6ea6ff", "2": "#3d8bff", "1": "#2b61b3", "w": "#12284a",
                      "s": "#0a1830", "a": "#cfe0ff"},
    "porta_insert": {"4": "#ffd2a7", "3": "#ffb46e", "2": "#ff9a3c", "1": "#b36c2a", "w": "#46290f",
                     "s": "#2c1908", "a": "#ffe3c8"},
    "porta_both": {"4": "#aee9c0", "3": "#74d995", "2": "#41c96b", "1": "#2e8d4b", "w": "#123a20",
                   "s": "#0a2413", "a": "#d3f5dd"},
    "porta_none": {"d": "#aab3bd", "e": "#6f7984"},
    "branco": {"W": "#ffffff"},
}

TIERS_ROTEADOR = ["basic", "advanced", "elite", "ultimate"]
TIERS_CARTAO = ["advanced", "elite", "ultimate"]


def legenda(**mapa: str) -> dict[str, tuple[int, int, int, int]]:
    """Monta a legenda de um sprite: caractere -> 'paleta.tom' ou '#rrggbb'."""
    saida = {}
    for chave, ref in mapa.items():
        if ref.startswith("#"):
            saida[chave] = rgb(ref)
        else:
            paleta, tom = ref.split(".")
            saida[chave] = rgb(PALETAS[paleta][tom])
    return saida


def pinta(grade: list[str], leg: dict[str, tuple[int, int, int, int]]) -> Image.Image:
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    assert len(grade) <= 16, f"grade com {len(grade)} linhas"
    for y, linha in enumerate(grade):
        assert len(linha) <= 16, f"linha {y} com {len(linha)} colunas: {linha!r}"
        for x, ch in enumerate(linha):
            if ch in ". ":
                continue
            if ch not in leg:
                raise KeyError(f"caractere {ch!r} sem cor na legenda (linha {y}: {linha!r})")
            img.putpixel((x, y), leg[ch])
    return img


def contorno(img: Image.Image, cor: tuple[int, int, int, int]) -> Image.Image:
    """Contorno de 1 px (vizinhança de 4) em volta dos pixels opacos."""
    saida = img.copy()
    for y in range(16):
        for x in range(16):
            if img.getpixel((x, y))[3]:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < 16 and 0 <= ny < 16 and img.getpixel((nx, ny))[3]:
                    saida.putpixel((x, y), cor)
                    break
    return saida


# ---------------------------------------------------------------------------
# Roteador (faces de bloco)
# ---------------------------------------------------------------------------
# Legenda comum: 4..0 = acento do tier; h m p s o = casco (4..0).

def leg_roteador(tier: str) -> dict[str, tuple[int, int, int, int]]:
    p = f"roteador_{tier}"
    leg = legenda(**{t: f"{p}.{t}" for t in "43210"})
    leg.update(legenda(h="casco.4", m="casco.3", p="casco.2", s="casco.1", o="casco.0"))
    leg.update(legenda(**{c: f"leds.{c}" for c in "EeNnAaFf"}))
    return leg


# Frente (14×6): quatro LEDs (energia, rede, atividade, cheio) em x=2,4,6,8 e a porta de dados.
FRENTE = [
    "43333333333332",
    "3ssssssssssss1",
    "3pEpNpApFpo3m1",
    "3pepnpapfpo1m1",
    "3mmmmmmmmmmmm1",
    "11111111111110",
]

# Trás (14×6): três conectores e uma grade de ventilação.
TRAS = [
    "43333333333332",
    "3ssssssssssss1",
    "3pooopooopsps1",
    "3po2opo2opsps1",
    "3mmmmmmmmmmmm1",
    "11111111111110",
]

# Lateral (12×6): fendas de ventilação.
LATERAL = [
    "433333333332",
    "3ssssssssss1",
    "3psopopopsp1",
    "3psopopopsp1",
    "3mmmmmmmmmm1",
    "111111111110",
]

# Topo (14×12): encaixes das antenas em (1,1) e (12,1), placa rebaixada com o símbolo
# wireless e parafusos na frente.
TOPO = [
    "43333333333332",
    "3osppppppppos1",
    "3soooooooooos1",
    "3pos333333smp1",
    "3po3ssssss2mp1",
    "3poss3333ssmp1",
    "3pos3ssss2smp1",
    "3posss43sssmp1",
    "3posss32sssmp1",
    "3pommmmmmmmmp1",
    "3hpppppppppph1",
    "11111111111110",
]

# Base (14×12): chapa lisa com quatro parafusos e uma grade central.
BASE = [
    "22222222222221",
    "2ssssssssssss1",
    "2shssssssssms1",
    "2ssssssssssss1",
    "2sssoooooosss1",
    "2sssmmmmmmsss1",
    "2sssoooooosss1",
    "2sssmmmmmmsss1",
    "2ssssssssssss1",
    "2shssssssssms1",
    "2ssssssssssss1",
    "11111111111110",
]


def face_roteador(tier: str, grade: list[str], preencher_resto: bool = False) -> Image.Image:
    leg = leg_roteador(tier)
    img = pinta(grade, leg)
    if preencher_resto:
        largura, altura = len(grade[0]), len(grade)
        base = pinta(["p" * 16] * 16, leg)
        base.paste(img.crop((0, 0, largura, altura)), (0, 0))
        img = base
    return img


def antena(tier: str) -> Image.Image:
    leg = legenda(**{t: f"roteador_{tier}.{t}" for t in "43210"})
    leg.update(legenda(A="mastro.3", B="mastro.2", C="mastro.1", D="mastro.0", o="casco.0"))
    # Coluna 0: mastro (linha 0 em cima, junto da ponta). x=2..3, y=0..1: a ponta luminosa.
    grade = [
        "B.43",
        "A.32",
        "B",
        "2",
        "1",
        "B",
        "C",
        "D",
    ]
    return pinta(grade, leg)


# ---------------------------------------------------------------------------
# Itens
# ---------------------------------------------------------------------------

# Cartões (família): moldura 16×12 com contorno, faixa de 3 px à esquerda, área de placa com o
# ícone no centro e contatos dourados embaixo (dedos em x=6, 8, 10 e 12).
# Legenda: 4..0 = faixa; '?' = marca de nível na faixa; H P S D = placa (luz, base, sombra, fundo);
# G g y = ouro; K k j = chip; i = pinos; c d e = núcleo do chip na cor do tier; L = grade; W a = funil.

# Cartão de upgrade de tier: chip com o núcleo na cor do tier e 1 a 3 marcas acesas na faixa.
CARTAO_UPGRADE = [
    "................",
    "................",
    ".OOOOOOOOOOOOOO.",
    "O433HHHHHHHHHHPO",
    "O322HPPiPPiPPPSO",
    "O3?2HPKKKKKkPPSO",
    "O322HPKkcdkjPPSO",
    "O2?1HPKkdekjPPSO",
    "O211HPkjjjjjPPSO",
    "O2?1HPPiPPiPPPSO",
    "O110HPGPGPGPGPSO",
    "O100HPgPgPgPgPSO",
    "O000SSySySySySDO",
    ".OOOOOOOOOOOOOO.",
    "................",
    "................",
]

# Cartão de chunk loading: faixa verde e uma grade 3×3 de chunks com o do centro aceso.
CARTAO_CHUNK = [
    "................",
    "................",
    ".OOOOOOOOOOOOOO.",
    "O433HHHHHHHHHHPO",
    "O322HPLLLLLLLPSO",
    "O332HPLDLDLDLPSO",
    "O322HPLLLLLLLPSO",
    "O211HPLDL4LDLPSO",
    "O211HPLLLLLLLPSO",
    "O210HPLDLDLDLPSO",
    "O110HPGPGPGPGPSO",
    "O100HPgPgPgPgPSO",
    "O000SSySySySySDO",
    ".OOOOOOOOOOOOOO.",
    "................",
    "................",
]

# Cartão de filtro: placa verde, faixa prateada e um funil claro no lugar do chip.
CARTAO_FILTRO = [
    "................",
    "................",
    ".OOOOOOOOOOOOOO.",
    "O433HHHHHHHHHHPO",
    "O322HWWWWWWWaPSO",
    "O322HPWWWWWaSPSO",
    "O322HPPWWWaSPPSO",
    "O211HPPPWaSPPPSO",
    "O211HPPPWaSPPPSO",
    "O210HPPPPSSPPPSO",
    "O110HPGPGPGPGPSO",
    "O100HPgPgPgPgPSO",
    "O000SSySySySySDO",
    ".OOOOOOOOOOOOOO.",
    "................",
    "................",
]


def cartao(grade: list[str], faixa: str, placa: str = "placa", pips: int = 0) -> Image.Image:
    leg = legenda(O="contorno.O",
                  H=f"{placa}.4", P=f"{placa}.2", S=f"{placa}.1", D=f"{placa}.0",
                  G="ouro.3", g="ouro.2", y="ouro.1",
                  K="chip.3", k="chip.2", j="chip.1", i="chip.p",
                  W="funil.W", a="funil.a")
    leg.update({t: rgb(PALETAS[faixa][t]) for t in "43210"})
    leg["L"] = rgb(PALETAS[faixa]["1"])
    leg["c"] = rgb(PALETAS[faixa]["4"])
    leg["d"] = rgb(PALETAS[faixa]["2"])
    leg["e"] = rgb(PALETAS[faixa]["1"])
    # Marcas do nível ('?'), de cima para baixo: acesas até o nível do tier, apagadas no resto.
    leg["?"] = rgb(PALETAS[faixa]["0"])
    img = pinta(grade, leg)
    marcas = [(x, y) for y, linha in enumerate(grade) for x, ch in enumerate(linha) if ch == "?"]
    for x, y in marcas[:pips]:
        img.putpixel((x, y), rgb(PALETAS["branco"]["W"]))
    return img


def configurador() -> Image.Image:
    """Varinha: cristal ciano alongado, anel dourado e cabo de madeira, na diagonal.

    Usa coordenadas giradas a partir do centro do cristal: u ao longo da varinha (positivo para
    cima e para a direita) e v na transversal (negativo do lado de cima/esquerda, o iluminado).
    """
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    cristal, ouro, madeira = PALETAS["cristal"], PALETAS["ouro"], PALETAS["madeira"]
    cx, cy = 11, 4

    def px(u, v, cor):
        x, y = cx + (u + v) // 2, cy + (v - u) // 2
        img.putpixel((x, y), rgb(cor))

    for u in range(-20, 6):
        for v in range(-2, 3):
            if (u + v) % 2:
                continue
            if -4 <= u and abs(u) + 2 * abs(v) <= 5:  # cristal
                px(u, v, cristal[{-2: "3", -1: "3", 0: "2", 1: "1", 2: "0"}[v]])
            elif u in (-5, -6) and abs(u + 4) + abs(v) <= 3:  # anel dourado
                px(u, v, ouro[{-2: "3", -1: "3", 0: "2", 1: "1", 2: "1"}[v]])
            elif -18 <= u <= -7 and v in (0, 1):  # cabo
                cabo = ("2", "1") if u <= -15 else ("3", "2")
                px(u, v, madeira[cabo[v]])
            elif u in (-19, -20) and v in (0, 1):  # pomo
                px(u, v, ouro["2" if v == 0 else "1"])
    px(1, -1, cristal["4"])  # brilho
    px(3, -1, cristal["4"])
    return contorno(img, rgb(PALETAS["contorno"]["O"]))


TABLET = [
    "................",
    "...OOOOOOOOOO...",
    "..O4443j33332O..",
    "..O4OOOOOOOO1O..",
    "..O4ONNLLYYO1O..",
    "..O4ONn33YyO1O..",
    "..O4O2L22L2O1O..",
    "..O3O22LL22O1O..",
    "..O3O11NN11O1O..",
    "..O3O11Nn11O1O..",
    "..O3O000000O1O..",
    "..O3OOOOOOOO1O..",
    "..O32222BB221O..",
    "..O22111bb110O..",
    "...OOOOOOOOOO...",
    "................",
]


def tablet() -> Image.Image:
    leg = legenda(O="tablet.0", **{"4": "tablet.4", "3": "tablet.3", "j": "tablet.1"})
    # Moldura: 4 3 (claro/esquerda), 2 1 (sombra/direita); tela: 3..0 da paleta 'tela'.
    grade = []
    for y, linha in enumerate(TABLET):
        grade.append(linha)
    # A grade usa os mesmos dígitos para moldura e tela; separa pela posição (tela em x 5..11, y 4..10).
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    moldura = PALETAS["tablet"]
    tela = PALETAS["tela"]
    for y, linha in enumerate(grade):
        for x, ch in enumerate(linha):
            if ch == ".":
                continue
            na_tela = 5 <= x <= 10 and 4 <= y <= 10
            if ch == "O":
                cor = moldura["0"]
            elif na_tela:
                cor = tela[ch]
            elif ch == "B":
                cor = "#9aa4b2"
            elif ch == "b":
                cor = "#5d6672"
            elif ch == "j":
                cor = moldura["1"]
            else:
                cor = moldura[ch]
            img.putpixel((x, y), rgb(cor))
    return img


# Livro-guia: capa com o símbolo de wireless (arcos em ciano sobre um ponto e uma antena dourados),
# cantos dourados, lombada à esquerda e a borda das páginas à direita.
LIVRO = [
    "................",
    "..OOOOOOOOOOOO..",
    ".OSs444444444pO.",
    ".OSsg3333333gqO.",
    ".OSs33CCCCC33pO.",
    ".OSs3C33333C3qO.",
    ".OSsC33ccc33CpO.",
    ".OSs33c333c33qO.",
    ".OSs333333333pO.",
    ".OSs3333d3333qO.",
    ".OSs333ddd333pO.",
    ".OSs222222222qO.",
    ".OSsg2222222gpO.",
    ".OSs111111111qO.",
    "..OOOOOOOOOOOO..",
    "................",
]


def livro() -> Image.Image:
    leg = legenda(O="contorno.O", S="livro.S", s="livro.s", p="livro.p", q="livro.q",
                  g="ouro.3", d="ouro.2", C="cristal.3", c="cristal.2",
                  **{"4": "livro.4", "3": "livro.3", "2": "livro.2", "1": "livro.1"})
    return pinta(LIVRO, leg)


LINKER = [
    "..........O.....",
    ".........OYO....",
    ".........OBO....",
    "....OOOOOOCO....",
    "...O44444443O...",
    "...O4OOOOOO1O...",
    "...O4....LN1O...",
    "...O4...L..1O...",
    "...O3..L...1O...",
    "...O3NL....1O...",
    "...O32222221O...",
    "...O3K2YY2K1O...",
    "...O3k2yy2k1O...",
    "...O32222221O...",
    "...O21111110O...",
    "....OOOOOOOO....",
]


def vinculador() -> Image.Image:
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    lar = PALETAS["laranja"]
    tela = PALETAS["tela"]
    especiais = {
        "O": "#1a1d22", "Y": "#ffd36b", "y": "#d98a1c", "B": "#9aa3ad", "C": "#646e79",
        "K": "#4a525e", "k": "#2b3139", "N": tela["N"], "n": tela["n"], "L": tela["L"],
        "b": tela["1"], ".": tela["2"],
    }
    for y, linha in enumerate(LINKER):
        for x, ch in enumerate(linha):
            na_tela = 5 <= x <= 10 and 6 <= y <= 9
            if ch == "." and not na_tela:
                continue
            if ch in "43210" and not na_tela:
                cor = lar[ch]
            elif ch == "1" and na_tela:
                cor = lar["1"]
            else:
                cor = especiais[ch]
            img.putpixel((x, y), rgb(cor))
    return img


# ---------------------------------------------------------------------------
# Portas da tela
# ---------------------------------------------------------------------------

MOLDURA_PORTA = [
    ".OOOOOOOOOOOOOO.",
    "O44333333333333O",
    "O43222222222221O",
    "O32OOOOOOOOOO21O",
    "O32OwwwwwwwwO21O",
    "O32OwwwwwwwwO21O",
    "O32OwwwwwwwwO21O",
    "O32OwwwwwwwwO21O",
    "O32OwwwwwwwwO21O",
    "O32OwwwwwwwwO21O",
    "O32OwwwwwwwwO21O",
    "O32OwwwwwwwwO21O",
    "O32OOOOOOOOOO21O",
    "O32222222222221O",
    "O31111111111111O",
    ".OOOOOOOOOOOOOO.",
]

SETA_CIMA = [
    "........",
    "...WW...",
    "..WWWW..",
    ".WWWWWW.",
    "...WW...",
    "...WW...",
    "...WW...",
    "........",
]

SETA_DUPLA = [
    "...WW...",
    "..WWWW..",
    ".WWWWWW.",
    "...WW...",
    "...WW...",
    ".WWWWWW.",
    "..WWWW..",
    "...WW...",
]


def porta(modo: str, seta: list[str]) -> Image.Image:
    pal = f"porta_{modo}"
    leg = legenda(O="#0b0e12", w=f"{pal}.w", **{t: f"{pal}.{t}" for t in "4321"})
    img = pinta(MOLDURA_PORTA, leg)
    branco = rgb("#ffffff")
    tinta = rgb(PALETAS[pal]["a"])
    sombra = rgb(PALETAS[pal]["s"])
    # Sombra projetada (1 px para baixo e para a direita), depois a seta por cima.
    for y, linha in enumerate(seta):
        for x, ch in enumerate(linha):
            if ch != ".":
                px, py = 4 + x + 1, 4 + y + 1
                if 4 <= px <= 11 and 4 <= py <= 11:
                    img.putpixel((px, py), sombra)
    for y, linha in enumerate(seta):
        for x, ch in enumerate(linha):
            if ch != ".":
                img.putpixel((4 + x, 4 + y), branco if ch == "W" else tinta)
    return img


def porta_nenhum() -> Image.Image:
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = rgb(PALETAS["porta_none"]["d"])
    e = rgb(PALETAS["porta_none"]["e"])
    # Traços de 2 px com vãos de 2 px num quadrado de 1 a 14: simétrico nos quatro lados.
    for i in range(1, 15):
        if (i - 1) % 4 < 2:
            img.putpixel((i, 1), d)
            img.putpixel((1, i), d)
            img.putpixel((i, 14), e)
            img.putpixel((14, i), e)
    # Cantos claros em cima/esquerda, escuros embaixo/direita.
    img.putpixel((14, 1), d)
    img.putpixel((1, 14), d)
    return img


# ---------------------------------------------------------------------------
# Montagem
# ---------------------------------------------------------------------------

def gerar() -> dict[str, Image.Image]:
    sprites: dict[str, Image.Image] = {}
    sprites["item/configurator"] = configurador()
    sprites["item/network_tablet"] = tablet()
    sprites["item/filter_card"] = cartao(CARTAO_FILTRO, "cartao_filtro", placa="placa_filtro")
    sprites["item/linker"] = vinculador()
    sprites["item/chunk_loader_upgrade"] = cartao(CARTAO_CHUNK, "cartao_chunk")
    sprites["item/guide"] = livro()
    for n, tier in enumerate(TIERS_CARTAO, start=1):
        sprites[f"item/tier_core_{tier}"] = cartao(CARTAO_UPGRADE, f"cartao_{tier}", pips=n)
    for tier in TIERS_ROTEADOR:
        sprites[f"block/router_{tier}_front"] = face_roteador(tier, FRENTE)
        sprites[f"block/router_{tier}_back"] = face_roteador(tier, TRAS)
        sprites[f"block/router_{tier}_side"] = face_roteador(tier, LATERAL)
        sprites[f"block/router_{tier}_top"] = face_roteador(tier, TOPO, preencher_resto=True)
        sprites[f"block/router_{tier}_bottom"] = face_roteador(tier, BASE)
        sprites[f"block/router_{tier}_antenna"] = antena(tier)
    sprites["gui/port_extract"] = porta("extract", SETA_CIMA)
    sprites["gui/port_insert"] = porta("insert", SETA_CIMA[::-1])
    sprites["gui/port_both"] = porta("both", SETA_DUPLA)
    sprites["gui/port_none"] = porta_nenhum()
    for nome, img in sprites.items():
        assert img.size == (16, 16), nome
        alfas = set(img.getchannel("A").tobytes())
        assert alfas <= {0, 255}, f"{nome}: alfa parcial {alfas}"
    return sprites


# --- Prévia do roteador montado (projeção oblíqua) ---

def _escurece(cor, f):
    return (int(cor[0] * f), int(cor[1] * f), int(cor[2] * f), 255)


def roteador_montado(tier: str, sprites: dict[str, Image.Image], s: int = 10) -> Image.Image:
    """Desenha o corpo 14×6×12 e as antenas em projeção oblíqua (profundidade pela metade)."""
    k = s // 2
    W, H = 14 * s + 12 * k + 40, 16 * s + 12 * k + 20
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    ox, oy = 20, H - 10

    def P(x, y, z):  # x 0..14 (esq.->dir.), y para cima, z 0 (trás)..12 (frente)
        return (ox + x * s + (12 - z) * k, oy - y * s - (12 - z) * k)

    def quad(pts, cor):
        d.polygon([P(*p) for p in pts], fill=cor[:3])

    top = sprites[f"block/router_{tier}_top"]
    front = sprites[f"block/router_{tier}_front"]
    side = sprites[f"block/router_{tier}_side"]
    ant = sprites[f"block/router_{tier}_antenna"]
    for v in range(12):
        for u in range(14):
            z = v
            quad([(u, 6, z), (u + 1, 6, z), (u + 1, 6, z + 1), (u, 6, z + 1)], top.getpixel((u, v)))
    for v in range(6):
        for u in range(14):
            quad([(u, 6 - v, 12), (u + 1, 6 - v, 12), (u + 1, 5 - v, 12), (u, 5 - v, 12)],
                 _escurece(front.getpixel((u, v)), 0.82))
    for v in range(6):
        for u in range(12):
            z = 12 - u
            quad([(14, 6 - v, z), (14, 6 - v, z - 1), (14, 5 - v, z - 1), (14, 5 - v, z)],
                 _escurece(side.getpixel((u, v)), 0.62))
    for ax in (1, 12):
        for v in range(8):
            y = 14 - v
            c = ant.getpixel((0, v))
            quad([(ax, y, 2), (ax + 1, y, 2), (ax + 1, y - 1, 2), (ax, y - 1, 2)], _escurece(c, 0.82))
            quad([(ax + 1, y, 2), (ax + 1, y, 1), (ax + 1, y - 1, 1), (ax + 1, y - 1, 2)], _escurece(c, 0.62))
        x0, x1, z0, z1 = ax - 0.5, ax + 1.5, 0.5, 2.5
        for i in range(2):
            for j in range(2):
                c = ant.getpixel((2 + i, j))
                quad([(x0 + i, 16 - j, z1), (x0 + i + 1, 16 - j, z1), (x0 + i + 1, 15 - j, z1), (x0 + i, 15 - j, z1)],
                     _escurece(c, 0.82))
                quad([(x1, 16 - j, z1 - i), (x1, 16 - j, z1 - i - 1), (x1, 15 - j, z1 - i - 1), (x1, 15 - j, z1 - i)],
                     _escurece(c, 0.62))
                quad([(x0 + i, 16, z0 + j), (x0 + i + 1, 16, z0 + j), (x0 + i + 1, 16, z0 + j + 1), (x0 + i, 16, z0 + j + 1)],
                     c)
    return img


def folha(sprites: dict[str, Image.Image]) -> Image.Image:
    escala, celula, rotulo = 8, 128, 22
    try:
        fonte = ImageFont.load_default(size=13)
    except TypeError:
        fonte = ImageFont.load_default()
    fundo, xadrez1, xadrez2, texto = (24, 28, 35), (34, 39, 48), (28, 33, 41), (210, 218, 228)

    def tile(img):
        t = Image.new("RGBA", (celula, celula), fundo + (255,))
        dd = ImageDraw.Draw(t)
        for yy in range(0, celula, 16):
            for xx in range(0, celula, 16):
                if (xx // 16 + yy // 16) % 2 == 0:
                    dd.rectangle([xx, yy, xx + 15, yy + 15], fill=xadrez1)
                else:
                    dd.rectangle([xx, yy, xx + 15, yy + 15], fill=xadrez2)
        big = img.resize((16 * escala, 16 * escala), Image.NEAREST)
        t.alpha_composite(big)
        return t

    linhas: list[tuple[str, list[tuple[str, Image.Image]]]] = []
    itens = ["configurator", "network_tablet", "linker", "filter_card", "chunk_loader_upgrade",
             "tier_core_advanced", "tier_core_elite", "tier_core_ultimate", "guide"]
    linhas.append(("Itens", [(n, tile(sprites[f"item/{n}"])) for n in itens]))
    # Itens em tamanho real (1× e 2×), como no inventário.
    linhas.append(("Portas da tela", [(n, tile(sprites[f"gui/{n}"]))
                                      for n in ["port_extract", "port_insert", "port_both", "port_none"]]))
    for tier in TIERS_ROTEADOR:
        faces = [(f"{tier}_{f}", tile(sprites[f"block/router_{tier}_{f}"]))
                 for f in ["front", "back", "side", "top", "bottom", "antenna"]]
        linhas.append((f"Roteador {tier}", faces))

    margem, gap = 16, 12
    colunas = 9
    largura = margem * 2 + colunas * celula + (colunas - 1) * gap
    montados = [roteador_montado(t, sprites) for t in TIERS_ROTEADOR]
    alt_montado = max(m.height for m in montados)
    altura = margem + sum(20 + celula + rotulo + gap for _ in linhas) + 20 + alt_montado + rotulo + 40 + 64
    out = Image.new("RGBA", (largura, altura), fundo + (255,))
    d = ImageDraw.Draw(out)
    y = margem
    for titulo, cels in linhas:
        d.text((margem, y), titulo, fill=texto, font=fonte)
        y += 20
        for i, (nome, t) in enumerate(cels):
            x = margem + i * (celula + gap)
            out.alpha_composite(t, (x, y))
            d.text((x, y + celula + 4), nome, fill=texto, font=fonte)
        y += celula + rotulo + gap
    d.text((margem, y), "Roteador montado (frente + topo + lateral)", fill=texto, font=fonte)
    y += 20
    x = margem
    for tier, m in zip(TIERS_ROTEADOR, montados):
        out.alpha_composite(m, (x, y))
        d.text((x + 20, y + m.height + 2), tier, fill=texto, font=fonte)
        x += m.width + gap
    y += alt_montado + rotulo + 10
    # Tira em tamanho real (1× e 2×) dos itens, para julgar a leitura no inventário.
    d.text((margem, y), "Itens em 1x e 2x", fill=texto, font=fonte)
    y += 18
    x = margem
    for n in itens:
        img = sprites[f"item/{n}"]
        out.alpha_composite(img, (x, y + 8))
        out.alpha_composite(img.resize((32, 32), Image.NEAREST), (x + 20, y))
        x += 64
    return out


def main() -> None:
    so_folha = "--so-folha" in sys.argv
    sprites = gerar()
    if not so_folha:
        for nome, img in sprites.items():
            destino = ASSETS / f"{nome}.png"
            destino.parent.mkdir(parents=True, exist_ok=True)
            img.save(destino)
    FOLHA.parent.mkdir(parents=True, exist_ok=True)
    folha(sprites).convert("RGB").save(FOLHA)
    print(f"{len(sprites)} sprites{' (não gravados)' if so_folha else ''}; folha em {FOLHA.relative_to(RAIZ)}")


if __name__ == "__main__":
    main()

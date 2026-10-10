"""Blocos do Wireless Automate na identidade "Porcelana e Sinal": modelos 3D (elementos) e texturas.

Contrato (o `gerar_texturas.py` importa este módulo):

- `sprites()`: `block/<nome>` -> `Sprite` (texturas dos blocos; as animadas vêm de `animacao`, o vidro é
  `translucido=True`). Quem chama grava com `salvar`.
- `modelos()`: grava `models/block/*.json`, `blockstates/*.json` e os modelos de item dos blocos, todos
  gerados daqui (o `router_basic.json` deixou de ser um molde à mão).
- `previas(sprites)`: prévias isométricas (nome, imagem) desenhadas a partir dos próprios elementos dos
  modelos, para a folha de sprites.

Convenções: x para leste, y para cima, z para sul; "frente" = sul no modelo `up` do roteador. Uma textura
serve a vários elementos pelo UV; o que não depende do tier (grafite, porcelana, vidro, fluidos) é
compartilhado e só o que leva a lente ou a etiqueta existe por tier.
"""

from __future__ import annotations

import math
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, str(Path(__file__).resolve().parent))

from identidade import (ASSETS, LENTE, MARCAS, MODELOS, PALETA, PREVIEW, TIERS, Cor, Sprite, animacao,  # noqa: E402
                        cor, grava_json, mistura, olho, parado, salvar)

NS = "wirelessautomate"
LATERAIS = ("north", "south", "east", "west")
QUADROS = 8
ARMAZENAMENTOS = ["storage_chest", "storage_tank", "storage_battery", "storage_chemical_tank"]
ROTACOES_ROTEADOR = {"up": {}, "down": {"x": 180}, "north": {"x": 90}, "south": {"x": 90, "y": 180},
                     "east": {"x": 90, "y": 90}, "west": {"x": 90, "y": 270}}


# ---------------------------------------------------------------------------
# Pincéis
# ---------------------------------------------------------------------------

def C(mat: str, tom: int | str, alfa: int = 255) -> Cor:
    return cor(f"{mat}.{tom}", alfa)


def nova() -> Image.Image:
    return Image.new("RGBA", (16, 16), (0, 0, 0, 0))


def ret(img: Image.Image, x0: int, y0: int, x1: int, y1: int, c: Cor) -> None:
    """Retângulo [x0, x1) × [y0, y1)."""
    for x in range(max(0, x0), min(16, x1)):
        for y in range(max(0, y0), min(16, y1)):
            img.putpixel((x, y), c)


def painel(img: Image.Image, x0: int, y0: int, x1: int, y1: int, mat: str, hi: int, mid: int, lo: int) -> None:
    """Placa com luz em cima e à esquerda e sombra embaixo e à direita."""
    ret(img, x0, y0, x1, y1, C(mat, mid))
    ret(img, x0, y0, x1, y0 + 1, C(mat, hi))
    ret(img, x0, y0, x0 + 1, y1, C(mat, hi))
    ret(img, x0, y1 - 1, x1, y1, C(mat, lo))
    ret(img, x1 - 1, y0, x1, y1, C(mat, lo))


def porcelana(img: Image.Image, x0: int, y0: int, x1: int, y1: int) -> None:
    painel(img, x0, y0, x1, y1, "porcelana", 4, 3, 1)


def grafite(img: Image.Image, x0: int, y0: int, x1: int, y1: int) -> None:
    painel(img, x0, y0, x1, y1, "grafite", 3, 2, 1)


def etiqueta(img: Image.Image, x0: int, y0: int, x1: int, y1: int, tier: str) -> None:
    """Etiqueta na cor da lente do tier, com o brilho no canto e a sombra embaixo."""
    hi, mid, lo = (cor(x) for x in LENTE[tier])
    ret(img, x0, y0, x1, y1, mid)
    ret(img, x0, y1 - 1, x1, y1, lo)
    img.putpixel((x0, y0), hi)


def com_olho(img: Image.Image, cx: int, cy: int, tier: str, fase: float, raio: int) -> None:
    """O Olho (com o Pulso) sobre um fundo opaco: desenha numa camada transparente e compõe."""
    camada = nova()
    olho(camada, cx, cy, tier, fase, raio)
    img.alpha_composite(camada)


def marcas(img: Image.Image, cx: int, y: int, n: int, c: Cor) -> None:
    """`n` (0 a 4) traços de 1×2 centrados em `cx`, de 2 em 2 px."""
    for i in range(n):
        x = cx - n + 1 + 2 * i
        ret(img, x, y, x + 1, y + 2, c)


def onda(x: int, y: int, k: int, lateral: bool) -> float:
    """Valor -1..1 de uma ondulação que anda com o quadro `k` (0..7), periódica em 8 quadros."""
    t = 2 * math.pi * k / QUADROS
    if lateral:
        return math.sin(x * 0.9 + y * 0.6 + t) * 0.7 + math.sin(x * 0.4 - t) * 0.3
    return math.sin(x * 0.8 + t) * math.cos(y * 0.8 - t)


def tons_liquido(mat: str, v: float) -> Cor:
    if v > 0.6:
        return C(mat, 3)
    if v < -0.55:
        return C(mat, 1)
    return C(mat, 2)


# ---------------------------------------------------------------------------
# Texturas compartilhadas (sem tier)
# ---------------------------------------------------------------------------

def tex_porcelana() -> Image.Image:
    img = nova()
    porcelana(img, 0, 0, 16, 16)
    for x, y in ((4, 9), (11, 5), (7, 13), (13, 11)):  # esmalte com um leve grão
        img.putpixel((x, y), C("porcelana", 2))
    return img


def tex_grafite() -> Image.Image:
    img = nova()
    grafite(img, 0, 0, 16, 16)
    for x, y in ((3, 3), (12, 12)):
        img.putpixel((x, y), C("grafite", 4))
    return img


def tex_vidro() -> Image.Image:
    img = nova()
    ret(img, 0, 0, 16, 16, C("vidro", 2, 110))
    ret(img, 0, 0, 16, 1, C("vidro", 3, 170))
    ret(img, 0, 0, 1, 16, C("vidro", 3, 170))
    ret(img, 15, 0, 16, 16, C("vidro", 1, 150))
    ret(img, 0, 15, 16, 16, C("vidro", 0, 170))
    for i in range(2, 14):  # reflexo diagonal
        img.putpixel((i, i - 1), C("vidro", 3, 150))
    return img


def tex_liquido(mat: str, lateral: bool) -> Sprite:
    quadros = []
    for k in range(QUADROS):
        img = nova()
        for y in range(16):
            for x in range(16):
                img.putpixel((x, y), tons_liquido(mat, onda(x, y, k, lateral)))
        if lateral:
            ret(img, 0, 13, 16, 16, C(mat, 1))
            ret(img, 0, 15, 16, 16, C(mat, 0))
        quadros.append(img)
    return animacao(quadros, 4)


def tex_quimico() -> Sprite:
    """Gás que gira: o padrão corre 2 px por quadro, e dá a volta nas quatro laterais."""
    quadros = []
    for k in range(QUADROS):
        img = nova()
        for y in range(16):
            for x in range(16):
                u = x + 2 * k
                v = math.sin(2 * math.pi * u / 16 + y * 0.5) * 0.65 + math.cos(2 * math.pi * u / 8 - y * 0.7) * 0.35
                tom = 3 if v > 0.72 else 2 if v > 0.1 else 1 if v > -0.6 else 0
                img.putpixel((x, y), C("quimico", tom))
        quadros.append(img)
    return animacao(quadros, 3)


def tex_gema() -> Sprite:
    """Gema do Tanque de Source, 3×2 em (0, 0) (os lados da gema de 3×3×2 px): três facetas, da clara à
    escura, com o brilho pulsando; e a ponta 3×3 em (4, 0) (o topo), um losango claro no meio."""
    quadros = []
    for k in range(QUADROS):
        pulso = 0.5 * (1 + math.sin(2 * math.pi * k / QUADROS))
        img = nova()
        brilho = mistura(C("source", 2), C("source", 3), pulso)
        for i, tom in enumerate((3, 2, 1)):
            ret(img, i, 0, i + 1, 2, C("source", tom))
        img.putpixel((0, 0), mistura(C("source", 3), cor("porcelana.4"), 0.4 * pulso))
        img.putpixel((1, 0), brilho)
        img.putpixel((2, 1), C("source", 0))
        # Ponta: anel escuro com o miolo claro.
        ret(img, 4, 0, 7, 3, C("source", 1))
        ret(img, 5, 1, 6, 2, brilho)
        img.putpixel((4, 0), C("source", 2))
        img.putpixel((6, 2), C("source", 0))
        quadros.append(img)
    return animacao(quadros, 4)


def tex_bateria_visor() -> Sprite:
    """Visor 4×11 (u 0..4, v 0..11): moldura de grafite, cinco segmentos que acendem de baixo para cima."""
    quadros = []
    acesos = [1, 2, 3, 4, 5, 5, 5, 0]
    for k in range(QUADROS):
        img = nova()
        grafite(img, 0, 0, 4, 11)
        ret(img, 1, 1, 3, 10, C("grafite", 0))
        for seg in range(5):  # segmento 0 embaixo (v 9), 4 em cima (v 1)
            v = 9 - 2 * seg
            if seg < acesos[k]:
                c = C("energia", 3) if seg == acesos[k] - 1 else C("energia", 2)
            else:
                c = C("energia", 0)
            ret(img, 1, v, 3, v + 1, c)
        quadros.append(img)
    return animacao(quadros, 4)


# Regiões da textura `detalhes` (u0, v0): faixa de perigo, listra coral, lado da tampa, régua, terminal.
DET = {"perigo": (0, 0), "listra": (0, 2), "tampa": (0, 4), "regua": (0, 7), "terminal": (4, 6)}


def tex_detalhes() -> Image.Image:
    img = nova()
    # Faixa de perigo (16×2): coral e grafite alternados de 2 em 2, a segunda linha deslocada.
    for y in (0, 1):
        for x in range(16):
            img.putpixel((x, y), C("coral", 2) if ((x + y) // 2) % 2 == 0 else C("grafite", 1))
    # Listra coral (16×2) e lado da tampa (16×2: porcelana clara sobre coral).
    ret(img, 0, 2, 16, 3, C("coral", 3))
    ret(img, 0, 3, 16, 4, C("coral", 1))
    ret(img, 0, 4, 16, 5, C("porcelana", 4))
    ret(img, 0, 5, 16, 6, C("coral", 2))
    # Régua (1×9, u 0, v 7..16): grafite com traços de porcelana.
    ret(img, 0, 6, 2, 16, C("grafite", 2))
    for v in range(7, 16, 2):
        img.putpixel((0, v), C("porcelana", 4) if v in (7, 11, 15) else C("porcelana", 1))
    # Terminal de latão (2×2).
    img.putpixel((4, 6), C("latao", 3))
    img.putpixel((5, 6), C("latao", 2))
    img.putpixel((4, 7), C("latao", 2))
    img.putpixel((5, 7), C("latao", 1))
    return img


# ---------------------------------------------------------------------------
# Texturas por tier
# ---------------------------------------------------------------------------

def tex_topo(tier: str) -> Sprite:
    """Tampa de porcelana com o Olho (raio 2) em (7, 7) pulsando e as marcas do tier embaixo."""
    quadros = []
    for k in range(QUADROS):
        img = tex_porcelana()
        marcas(img, 7, 12, MARCAS[tier], C("grafite", 2))
        com_olho(img, 7, 7, tier, k / QUADROS, 2)
        quadros.append(img)
    return animacao(quadros, 4)


def tex_router_frente(tier: str) -> Sprite:
    """Corpo do roteador: frente 10×4 (v 0..4) com o Olho que varre numa fenda; lados 10×4 (v 4..8)."""
    posicoes = [2, 3, 4, 5, 6, 5, 4, 3]
    quadros = []
    for k in range(QUADROS):
        img = nova()
        # Frente: listra coral em cima, grafite, fenda de vidro escuro de x 1..8.
        grafite(img, 0, 0, 10, 4)
        ret(img, 0, 0, 10, 1, C("coral", 2))
        ret(img, 1, 1, 8, 4, C("vidro", 0))
        ret(img, 1, 3, 8, 4, C("grafite", 0))
        olho(img, posicoes[k], 2, tier, 0.0, 1)
        img.putpixel((8, 2), C("latao", 2))  # parafuso
        # Lados e trás: a mesma listra e um friso.
        grafite(img, 0, 4, 10, 8)
        ret(img, 0, 4, 10, 5, C("coral", 2))
        ret(img, 1, 6, 9, 7, C("grafite", 1))
        quadros.append(img)
    return animacao(quadros, 4)


# Regiões da textura da antena (u0, v0): interior do prato 6×6, costas 6×6, latão 2×2 (receptor e pontas).
ANT = {"prato": (0, 0), "costas": (8, 0), "latao": (0, 8)}


def tex_router_antena(tier: str) -> Image.Image:
    """Antena: interior do prato 6×6 (u 0..6) na cor da lente, com aro de porcelana; costas 6×6 de grafite
    (u 8..14); latão 2×2 em (0, 8), para o receptor e as pontas das hastes. Tudo opaco: nada de canto
    transparente (o prato é um elemento sólido)."""
    hi, mid, lo = (cor(x) for x in LENTE[tier])
    img = nova()
    p, c, l = ANT["prato"], ANT["costas"], ANT["latao"]
    # Interior: aro de porcelana (claro em cima e à esquerda), lente do tier com o foco escuro no meio.
    painel(img, p[0], p[1], p[0] + 6, p[1] + 6, "porcelana", 4, 3, 1)
    ret(img, p[0] + 1, p[1] + 1, p[0] + 5, p[1] + 5, mid)
    ret(img, p[0] + 2, p[1] + 2, p[0] + 4, p[1] + 4, lo)
    img.putpixel((p[0] + 1, p[1] + 1), hi)
    img.putpixel((p[0] + 4, p[1] + 4), lo)
    # Costas: grafite com um friso de porcelana no meio (a nervura do prato).
    grafite(img, c[0], c[1], c[0] + 6, c[1] + 6)
    ret(img, c[0] + 1, c[1] + 2, c[0] + 5, c[1] + 4, C("grafite", 1))
    ret(img, c[0] + 2, c[1] + 2, c[0] + 4, c[1] + 4, C("porcelana", 1))
    # Latão 2×2.
    ret(img, l[0], l[1], l[0] + 2, l[1] + 2, C("latao", 2))
    img.putpixel((l[0], l[1]), C("latao", 3))
    img.putpixel((l[0] + 1, l[1] + 1), C("latao", 1))
    return img


def tex_chest(tier: str) -> Image.Image:
    """Frente de gaveta 12×5 (u 0..12, v 0..5): porcelana, etiqueta do tier e puxador de grafite."""
    img = nova()
    porcelana(img, 0, 0, 12, 5)
    ret(img, 11, 0, 12, 5, C("porcelana", 2))
    etiqueta(img, 1, 1, 4, 3, tier)
    ret(img, 5, 2, 9, 3, C("grafite", 2))
    img.putpixel((5, 2), C("grafite", 4))
    ret(img, 5, 3, 9, 4, C("porcelana", 1))  # sombra do puxador
    return img


def tex_tank(tier: str) -> Image.Image:
    """Tanque: base 12×3 (v 0..3, com a etiqueta e a listra coral) e tampa 12×2 (v 4..6)."""
    img = nova()
    porcelana(img, 0, 0, 12, 3)
    etiqueta(img, 4, 0, 8, 2, tier)
    ret(img, 0, 2, 12, 3, C("coral", 2))
    porcelana(img, 0, 4, 12, 6)
    return img


def tex_battery(tier: str) -> Image.Image:
    """Bateria: três células 12×3 (v 0..3, 4..7, 8..11; a do meio com a etiqueta)."""
    img = nova()
    for n, v in enumerate((0, 4, 8)):
        porcelana(img, 0, v, 12, v + 3)
        ret(img, 11, v, 12, v + 3, C("porcelana", 2))
        if n == 1:
            etiqueta(img, 2, v, 5, v + 2, tier)
            img.putpixel((8, v + 1), C("grafite", 2))  # os polos
            img.putpixel((9, v + 1), C("coral", 2))
    return img


def tex_chemical(tier: str) -> Image.Image:
    """Tanque Químico: equador 12×6 (v 0..6) com a vigia de vidro (translúcida) e a etiqueta."""
    img = nova()
    porcelana(img, 0, 0, 12, 6)
    # Vigia: anel de grafite 6×6 com os cantos cortados, vidro dentro.
    for i in range(6):
        for j in range(6):
            x, y = 3 + i, j
            canto = (i in (0, 5)) and (j in (0, 5))
            borda = i in (0, 5) or j in (0, 5)
            if canto:
                continue
            if borda:
                img.putpixel((x, y), C("grafite", 2))
            else:
                img.putpixel((x, y), C("vidro", 2 if i + j < 6 else 1, 120))
    etiqueta(img, 0, 2, 2, 4, tier)
    img.putpixel((10, 2), C("latao", 2))
    img.putpixel((10, 3), C("latao", 1))
    return img


def tex_source_tank(tier: str) -> Image.Image:
    """Tanque de Source: base 10×2 (v 0..2), lado da tampa 10×1 (v 2), topo da gema 3×3 (u 13..16, v 0..3)."""
    img = nova()
    porcelana(img, 0, 0, 10, 2)
    etiqueta(img, 4, 0, 6, 1, tier)
    ret(img, 0, 1, 10, 2, C("coral", 2))
    porcelana(img, 0, 2, 10, 3)
    olho(img, 14, 1, tier, 0.0, 1)
    return img


def sprites() -> dict[str, Sprite]:
    s: dict[str, Sprite] = {
        "block/porcelana": parado(tex_porcelana()),
        "block/grafite": parado(tex_grafite()),
        "block/vidro": parado(tex_vidro(), translucido=True),
        "block/detalhes": parado(tex_detalhes()),
        "block/fluido": tex_liquido("fluido", True),
        "block/fluido_superficie": tex_liquido("fluido", False),
        "block/source": tex_liquido("source", True),
        "block/source_superficie": tex_liquido("source", False),
        "block/quimico": tex_quimico(),
        "block/gema": tex_gema(),
        "block/bateria_visor": tex_bateria_visor(),
    }
    for t in TIERS:
        s[f"block/topo_{t}"] = tex_topo(t)
        s[f"block/router_{t}_frente"] = tex_router_frente(t)
        s[f"block/router_{t}_antena"] = parado(tex_router_antena(t))
        s[f"block/storage_chest_{t}"] = parado(tex_chest(t))
        s[f"block/storage_tank_{t}"] = parado(tex_tank(t))
        s[f"block/storage_battery_{t}"] = parado(tex_battery(t))
        s[f"block/storage_chemical_tank_{t}"] = parado(tex_chemical(t), translucido=True)
        s[f"block/storage_source_tank_{t}"] = parado(tex_source_tank(t))
    return s


# ---------------------------------------------------------------------------
# Modelos: um construtor pequeno de elementos
# ---------------------------------------------------------------------------

Vec = list[float]
Faces = dict[str, tuple[str, list[float] | None]]


def uv_padrao(face: str, de: Vec, ate: Vec) -> list[float]:
    """O UV que o jogo usaria sem `uv` (a projeção da caixa na textura)."""
    (x0, y0, z0), (x1, y1, z1) = de, ate
    return {
        "south": [x0, 16 - y1, x1, 16 - y0], "north": [16 - x1, 16 - y1, 16 - x0, 16 - y0],
        "east": [16 - z1, 16 - y1, 16 - z0, 16 - y0], "west": [z0, 16 - y1, z1, 16 - y0],
        "up": [x0, z0, x1, z1], "down": [x0, 16 - z1, x1, 16 - z0],
    }[face]


def lados(tex: str, u0: float, v0: float, de: Vec, ate: Vec) -> Faces:
    """As quatro laterais numa faixa da textura que começa em (u0, v0), do tamanho de cada face."""
    w, h, d = ate[0] - de[0], ate[1] - de[1], ate[2] - de[2]
    ns = [u0, v0, u0 + w, v0 + h]
    ew = [u0, v0, u0 + d, v0 + h]
    return {"north": (tex, ns), "south": (tex, ns), "east": (tex, ew), "west": (tex, ew)}


def lados_cheios(tex: str) -> Faces:
    return {f: (tex, None) for f in LATERAIS}


class Modelo:
    """Um modelo de bloco por elementos. Regras de construção (conferidas por `validar_geometria`):

    - elementos **encostam, não se atravessam** (volume de interseção zero), salvo um `dentro=True`, que
      fica dentro de um translúcido (fluido na coluna de vidro, gás no vaso) e só por isso é visível;
    - duas faces desenhadas nunca dividem o mesmo plano com área em comum (é o que pisca no jogo);
    - toda face que não é desenhada está inteiramente coberta por um elemento encostado (senão é buraco).
    """

    def __init__(self, nome: str, particula: str, translucido: bool = False):
        self.nome = nome
        self.particula = particula
        self.translucido = translucido
        self.elementos: list[dict] = []
        self.dentro: list[bool] = []
        self.texturas: set[str] = {particula}

    def caixa(self, de: Vec, ate: Vec, faces: Faces, rot: tuple[str, float, Vec] | None = None,
              dentro: bool = False) -> None:
        el: dict = {"from": list(de), "to": list(ate), "faces": {}}
        if rot:
            eixo, angulo, origem = rot
            el["rotation"] = {"origin": list(origem), "axis": eixo, "angle": angulo}
        for face, (tex, uv) in faces.items():
            dados: dict = {"uv": [float(v) for v in (uv or uv_padrao(face, de, ate))], "texture": "#" + tex}
            if not rot and _no_limite(face, de, ate):
                dados["cullface"] = face
            el["faces"][face] = dados
            self.texturas.add(tex)
        self.elementos.append(el)
        self.dentro.append(dentro)

    def json(self) -> dict:
        texturas = {t: f"{NS}:block/{t}" for t in sorted(self.texturas)}
        texturas["particle"] = f"{NS}:block/{self.particula}"
        return {
            "parent": "minecraft:block/block",
            "render_type": "minecraft:translucent" if self.translucido else "minecraft:cutout",
            "ambientocclusion": False,
            "textures": texturas,
            "elements": self.elementos,
        }


def _no_limite(face: str, de: Vec, ate: Vec) -> bool:
    return {"down": de[1] == 0, "up": ate[1] == 16, "north": de[2] == 0, "south": ate[2] == 16,
            "west": de[0] == 0, "east": ate[0] == 16}[face]


def todas(tex: str, menos: tuple[str, ...] = ()) -> Faces:
    """As seis faces (menos as de `menos`, cobertas por um vizinho encostado) com a textura inteira."""
    return {f: (tex, None) for f in ("up", "down", *LATERAIS) if f not in menos}


def pes(m: Modelo, recuo: float, lado: float, altura: float, topo: bool = False) -> None:
    """Quatro pés de grafite nos cantos, de `lado` px, `recuo` px para dentro do cubo. Sem `topo`, o pé fica
    debaixo do corpo (que cobre a face de cima); com `topo`, é um para-choque solto e mostra a face de cima."""
    for x in (recuo, 16 - recuo - lado):
        for z in (recuo, 16 - recuo - lado):
            m.caixa([x, 0, z], [x + lado, altura, z + lado], todas("grafite", () if topo else ("up",)))


def anel(m: Modelo, de: Vec, ate: Vec, menos: tuple[str, ...] = (), tex: str = "grafite") -> None:
    m.caixa(de, ate, todas(tex, menos))


# --- Roteador ---------------------------------------------------------------

# Hastes traseiras: dentro das caixas de colisão de RouterShapes.UP_BOXES (x 1,5..3,5 e 12,5..14,5, z 2,5..4,5).
HASTES_X = (2, 13)
PRATO_ROT = ("x", -45.0, [8, 9, 7])  # gira em torno da aresta de trás do pé do prato, no topo do mastro


def modelo_router(t: str) -> Modelo:
    """Prato de porcelana (y 0..1), para-choques nos cantos, corpo baixo de grafite recuado (y 1..5) com a
    listra coral e o Olho que varre na frente (sul), mastro, e a antena parabólica: um prato sólido 6×6×1
    que, em pé e virado para o sul, cai 45° para trás (rotação −45° em x, pela aresta de baixo e de trás),
    de modo que o interior, na cor do tier, olha para a frente e para cima. Um braço de 1 px sai do centro
    do prato até o receptor de latão. Hastes finas de grafite nos cantos de trás, com a ponta de latão."""
    m = Modelo(f"router_{t}", "porcelana")
    frente, antena = f"router_{t}_frente", f"router_{t}_antena"
    latao = (antena, [ANT["latao"][0], ANT["latao"][1], ANT["latao"][0] + 1, ANT["latao"][1] + 1])
    # Base e os quatro para-choques (soltos: só encostam no prato).
    m.caixa([1, 0, 2], [15, 1, 14], todas("porcelana"))
    for x in (1, 13):
        for z in (2, 12):
            m.caixa([x, 1, z], [x + 2, 3, z + 2], todas("grafite", ("down",)))
    # Corpo baixo; a frente (sul) tem a listra coral e a fenda do Olho na própria textura.
    m.caixa([3, 1, 4], [13, 5, 12], {"south": (frente, [0, 0, 10, 4]), "north": (frente, [0, 4, 10, 8]),
                                     "east": (frente, [0, 4, 8, 8]), "west": (frente, [0, 4, 8, 8]),
                                     "up": ("grafite", None)})
    # Hastes finas sobre os para-choques de trás, com a ponta de latão de 1 px.
    for x in HASTES_X:
        m.caixa([x, 3, 2.5], [x + 1, 11, 3.5], lados_cheios("grafite"))
        m.caixa([x, 11, 2.5], [x + 1, 12, 3.5], {**{f: latao for f in LATERAIS}, "up": latao})
    # Mastro e a antena.
    m.caixa([7, 5, 7], [9, 9, 9], todas("grafite", ("down",)))
    p, c = ANT["prato"], ANT["costas"]
    m.caixa([5, 9, 7], [11, 15, 8], {"south": (antena, [p[0], p[1], p[0] + 6, p[1] + 6]),
                                     "north": (antena, [c[0], c[1], c[0] + 6, c[1] + 6]),
                                     **{f: ("grafite", None) for f in ("up", "down", "east", "west")}},
            rot=PRATO_ROT)
    m.caixa([7.5, 11.5, 8], [8.5, 12.5, 10], {f: ("grafite", None) for f in ("up", "down", "east", "west")},
            rot=PRATO_ROT)
    m.caixa([7.5, 11.5, 10], [8.5, 12.5, 11], {f: latao for f in ("up", "down", "east", "west", "south")},
            rot=PRATO_ROT)
    return m


# --- Baú ---------------------------------------------------------------------

def modelo_chest(t: str) -> Modelo:
    """Arquivo de gavetas: pés debaixo do corpo, corpo de porcelana recuado 1 px, tampa cheia com o Olho e,
    em cada lado, duas gavetas salientes de 1 px (a face de dentro fica contra o corpo)."""
    m = Modelo(f"storage_chest_{t}", "porcelana")
    gaveta, topo = f"storage_chest_{t}", f"topo_{t}"
    pes(m, 1, 3, 2)
    m.caixa([1, 2, 1], [15, 14, 15], todas("porcelana", ("up",)))
    m.caixa([0, 14, 0], [16, 16, 16], {**lados("detalhes", 0, DET["tampa"][1], [0, 14, 0], [16, 16, 16]),
                                       "up": (topo, [0, 0, 16, 16]), "down": ("porcelana", None)})
    oposta = {"south": "north", "north": "south", "east": "west", "west": "east"}
    for y0 in (2, 8):
        uv = [0, 0, 12, 5]
        for de, ate, face in (([2, y0, 15], [14, y0 + 5, 16], "south"), ([2, y0, 0], [14, y0 + 5, 1], "north"),
                              ([15, y0, 2], [16, y0 + 5, 14], "east"), ([0, y0, 2], [1, y0 + 5, 14], "west")):
            faces = todas("porcelana", (oposta[face],))
            faces[face] = (gaveta, uv)
            m.caixa(de, ate, faces)
    return m


# --- Tanque -------------------------------------------------------------------

def modelo_tank(t: str) -> Modelo:
    """Base e tampa de porcelana com anéis de grafite, uma coluna de vidro (uma caixa só: duas se
    atravessando piscam) com o fluido dentro e quatro réguas de nível encostadas no vidro."""
    m = Modelo(f"storage_tank_{t}", "porcelana", translucido=True)
    tex, topo = f"storage_tank_{t}", f"topo_{t}"
    pes(m, 2, 3, 1)
    m.caixa([2, 1, 2], [14, 4, 14], {**lados(tex, 0, 0, [2, 1, 2], [14, 4, 14]), "up": ("porcelana", None), "down": ("porcelana", None)})
    anel(m, [3, 4, 3], [13, 5, 13], ("down",))
    m.caixa([5, 5, 5], [11, 12, 11], {**lados_cheios("fluido"), "up": ("fluido_superficie", None)}, dentro=True)
    m.caixa([4, 5, 4], [12, 13, 12], lados_cheios("vidro"))
    r = DET["regua"]
    regua = ("detalhes", [r[0], r[1], r[0] + 1, r[1] + 8])
    for de, ate, dentro_face in (([7.5, 5, 12], [8.5, 13, 13], "north"), ([7.5, 5, 3], [8.5, 13, 4], "south"),
                                 ([12, 5, 7.5], [13, 13, 8.5], "west"), ([3, 5, 7.5], [4, 13, 8.5], "east")):
        faces = todas("grafite", (dentro_face, "up", "down"))  # as faces de cima e de baixo ficam nos anéis
        for f in LATERAIS:
            if f != dentro_face:
                faces[f] = regua
        m.caixa(de, ate, faces)
    anel(m, [3, 13, 3], [13, 14, 13], ("up",))
    m.caixa([2, 14, 2], [14, 16, 14], {**lados(tex, 0, 4, [2, 14, 2], [14, 16, 14]), "up": (topo, [2, 2, 14, 14]), "down": ("porcelana", None)})
    return m


# --- Bateria ------------------------------------------------------------------

def modelo_battery(t: str) -> Modelo:
    """Três células empilhadas com separadores de grafite rentes, plinto e cornija de grafite, tampa com a
    listra e o Olho, dois terminais de latão e um visor de carga saliente em cada lado, encostado nas células."""
    m = Modelo(f"storage_battery_{t}", "porcelana")
    tex, topo = f"storage_battery_{t}", f"topo_{t}"
    pes(m, 1, 3, 1)
    anel(m, [1, 1, 1], [15, 2, 15])
    for n, y0 in enumerate((2, 6, 10)):
        m.caixa([2, y0, 2], [14, y0 + 3, 14], lados(tex, 0, 4 * n, [2, y0, 2], [14, y0 + 3, 14]))
        if n < 2:
            m.caixa([2, y0 + 3, 2], [14, y0 + 4, 14], lados_cheios("grafite"))
    anel(m, [1, 13, 1], [15, 14, 15])
    m.caixa([2, 14, 2], [14, 15, 14], {**lados("detalhes", 0, DET["listra"][1] + 1, [2, 14, 2], [14, 15, 14]),
                                       "up": (topo, [2, 2, 14, 14])})
    tm = DET["terminal"]
    for x, z in ((3, 3), (11, 11)):
        uv = [tm[0], tm[1], tm[0] + 2, tm[1] + 2]
        m.caixa([x, 15, z], [x + 2, 16, z + 2], {f: ("detalhes", uv) for f in ("up", *LATERAIS)})
    uv = [0, 0, 4, 11]
    oposta = {"south": "north", "north": "south", "east": "west", "west": "east"}
    for de, ate, face in (([6, 2, 14], [10, 13, 15], "south"), ([6, 2, 1], [10, 13, 2], "north"),
                          ([14, 2, 6], [15, 13, 10], "east"), ([1, 2, 6], [2, 13, 10], "west")):
        faces = {f: ("grafite", None) for f in LATERAIS if f != face and f != oposta[face]}
        faces[face] = ("bateria_visor", uv)
        m.caixa(de, ate, faces)
    return m


# --- Tanque Químico -----------------------------------------------------------

def modelo_chemical(t: str) -> Modelo:
    """Vaso de pressão por caixas empilhadas e encostadas (sem rotações: as caixas a 45° atravessavam o
    corpo): pés, anel, ombro de baixo com a faixa de perigo, equador com a vigia (o gás gira dentro, visto
    pelo vidro), ombro de cima, calota com o Olho e, no topo, o volante: um aro vazado de quatro barras,
    pelo qual o Olho aparece."""
    m = Modelo(f"storage_chemical_tank_{t}", "porcelana", translucido=True)
    tex, topo = f"storage_chemical_tank_{t}", f"topo_{t}"
    pes(m, 3, 2, 1)
    anel(m, [3, 1, 3], [13, 2, 13])
    m.caixa([4, 2, 4], [12, 3, 12], lados_cheios("porcelana"))
    perigo = DET["perigo"]
    faixa = {f: ("detalhes", [perigo[0], perigo[1], perigo[0] + 10, perigo[1] + 2]) for f in LATERAIS}
    m.caixa([3, 3, 3], [13, 5, 13], {**faixa, "down": ("porcelana", None)})
    m.caixa([3, 5, 3], [13, 11, 13], lados_cheios("quimico"), dentro=True)
    m.caixa([2, 5, 2], [14, 11, 14], {**lados(tex, 0, 0, [2, 5, 2], [14, 11, 14]), "up": ("porcelana", None), "down": ("porcelana", None)})
    m.caixa([3, 11, 3], [13, 13, 13], todas("porcelana", ("down",)))
    m.caixa([4, 13, 4], [12, 14, 12], {**lados_cheios("porcelana"), "up": (topo, [4, 4, 12, 12])})
    # Volante: barras norte e sul inteiras, barras leste e oeste entre elas (sem as faces contra as outras).
    m.caixa([4, 14, 4], [12, 15, 5], todas("grafite", ("down",)))
    m.caixa([4, 14, 11], [12, 15, 12], todas("grafite", ("down",)))
    m.caixa([4, 14, 5], [5, 15, 11], todas("grafite", ("down", "north", "south")))
    m.caixa([11, 14, 5], [12, 15, 11], todas("grafite", ("down", "north", "south")))
    return m


# --- Tanque de Source -----------------------------------------------------------

def altura_source(fill: int) -> int:
    """Topo (em pixels do bloco) da Source no nível 1..10; cresce com `fill`, 10 = cheio (1 px abaixo da tampa)."""
    return 2 + max(1, (fill * 18 + 10) // 20)


def modelo_source_tank(t: str, fill: int) -> Modelo:
    """Jarra: para-choques nos cantos da base, base e tampa de porcelana, quatro trilhos de grafite nos
    cantos de fora da coluna de vidro (uma caixa só), a Source dentro no nível `fill`, e no topo um colar
    de grafite com a gema pequena (3×3×2 px)."""
    m = Modelo(f"storage_source_tank_{t}_{fill}", "porcelana", translucido=True)
    tex = f"storage_source_tank_{t}"
    pes(m, 2, 1, 1, topo=True)
    m.caixa([3, 0, 3], [13, 2, 13], {**lados(tex, 0, 0, [3, 0, 3], [13, 2, 13]), "up": ("porcelana", None), "down": ("porcelana", None)})
    for x in (3, 12):
        for z in (3, 12):
            m.caixa([x, 2, z], [x + 1, 12, z + 1], lados_cheios("grafite"))
    if fill > 0:
        m.caixa([5, 2, 5], [11, altura_source(fill), 11], {**lados_cheios("source"), "up": ("source_superficie", None)}, dentro=True)
    m.caixa([4, 2, 4], [12, 12, 12], lados_cheios("vidro"))
    m.caixa([3, 12, 3], [13, 13, 13], {**lados(tex, 0, 2, [3, 12, 3], [13, 13, 13]), "up": ("porcelana", None), "down": ("porcelana", None)})
    m.caixa([6, 13, 6], [10, 14, 10], todas("grafite", ("down",)))
    m.caixa([6.5, 14, 6.5], [9.5, 16, 9.5], {**{f: ("gema", [0, 0, 3, 2]) for f in LATERAIS}, "up": ("gema", [4, 0, 7, 3])})
    return m


def construir_modelos() -> dict[str, Modelo]:
    ms: dict[str, Modelo] = {}
    for t in TIERS:
        for m in (modelo_router(t), modelo_chest(t), modelo_tank(t), modelo_battery(t), modelo_chemical(t)):
            ms[m.nome] = m
        for fill in range(11):
            m = modelo_source_tank(t, fill)
            ms[m.nome] = m
    return ms


# ---------------------------------------------------------------------------
# Validação e gravação
# ---------------------------------------------------------------------------

ANGULOS = {-45.0, -22.5, 0.0, 22.5, 45.0}


def validar_modelo(dados: dict, texturas: set[str]) -> None:
    for chave, ref in dados["textures"].items():
        assert ref.startswith(f"{NS}:block/"), (chave, ref)
        assert ref.split("/", 1)[1] in texturas, f"textura inexistente: {ref}"
    for el in dados["elements"]:
        for k in ("from", "to"):
            assert len(el[k]) == 3 and all(-16 <= v <= 32 for v in el[k]), el[k]
        assert all(a <= b for a, b in zip(el["from"], el["to"])), (el["from"], el["to"])
        if "rotation" in el:
            r = el["rotation"]
            assert r["axis"] in ("x", "y", "z") and float(r["angle"]) in ANGULOS and len(r["origin"]) == 3, r
        for face, f in el["faces"].items():
            assert face in ("up", "down", *LATERAIS), face
            assert f["texture"][1:] in dados["textures"], f["texture"]
            assert len(f["uv"]) == 4 and all(0 <= v <= 16 for v in f["uv"]), f["uv"]


# Eixo normal de cada face e qual canto da caixa fixa a coordenada do plano.
FACE_EIXO = {"west": (0, "from"), "east": (0, "to"), "down": (1, "from"), "up": (1, "to"),
             "north": (2, "from"), "south": (2, "to")}
EPS = 1e-6


def _chave_rot(el: dict) -> tuple | None:
    r = el.get("rotation")
    return (r["axis"], float(r["angle"]), tuple(r["origin"])) if r else None


def _face_plana(el: dict, face: str, local: bool) -> tuple[int, float, list[tuple[float, float]]] | None:
    """(eixo, coordenada do plano, polígono 2D) da face: no referencial do próprio elemento (`local`) ou do
    bloco, quando a face continua num plano alinhado a um eixo depois da rotação (senão None)."""
    eixo, lado = FACE_EIXO[face]
    cantos = _cantos(face, el["from"], el["to"])
    rot = el.get("rotation")
    if rot and not local:
        if rot["axis"] != "xyz"[eixo]:
            return None
        cantos = [_rot(p, rot) for p in cantos]
    coord = cantos[0][eixo]
    outros = [i for i in range(3) if i != eixo]
    return eixo, coord, [(p[outros[0]], p[outros[1]]) for p in cantos]


def _area(poly: list[tuple[float, float]]) -> float:
    return sum(poly[i][0] * poly[(i + 1) % len(poly)][1] - poly[(i + 1) % len(poly)][0] * poly[i][1]
               for i in range(len(poly))) / 2


def _no_poligono(p: tuple[float, float], poly: list[tuple[float, float]], folga: float) -> bool:
    """Ponto dentro do polígono convexo; `folga` > 0 aceita a borda, < 0 exige estar bem dentro."""
    sinal = 1 if _area(poly) > 0 else -1
    for i in range(len(poly)):
        (ax, ay), (bx, by) = poly[i], poly[(i + 1) % len(poly)]
        comp = math.hypot(bx - ax, by - ay) or 1
        if sinal * ((bx - ax) * (p[1] - ay) - (by - ay) * (p[0] - ax)) / comp < -folga:
            return False
    return True


def _amostras(poly: list[tuple[float, float]], passo: float) -> list[tuple[float, float]]:
    """Centros de uma grade de `passo` px que caem bem dentro do polígono."""
    xs, ys = [p[0] for p in poly], [p[1] for p in poly]
    pontos = []
    x = math.floor(min(xs) / passo) * passo + passo / 2
    while x < max(xs):
        y = math.floor(min(ys) / passo) * passo + passo / 2
        while y < max(ys):
            if _no_poligono((x, y), poly, -EPS):
                pontos.append((x, y))
            y += passo
        x += passo
    return pontos


def _sobrepoe(a: list[tuple[float, float]], b: list[tuple[float, float]]) -> bool:
    """Os dois polígonos coplanares têm área em comum (algum ponto bem dentro dos dois)."""
    return any(_no_poligono(p, b, -1e-3) for p in _amostras(a, 0.25))


def _coberta(face: list[tuple[float, float]], cobertas: list[list[tuple[float, float]]]) -> bool:
    return all(any(_no_poligono(p, c, EPS) for c in cobertas) for p in _amostras(face, 0.5))


def _volume_comum(a: dict, b: dict) -> float:
    v = 1.0
    for i in range(3):
        v *= max(0.0, min(a["to"][i], b["to"][i]) - max(a["from"][i], b["from"][i]))
    return v


def _erros_do_grupo(m: Modelo, indices: list[int], local: bool) -> list[str]:
    """As três checagens entre os elementos `indices`, num referencial comum: o do bloco (só as faces que
    ficam alinhadas aos eixos) ou o local de um grupo com a mesma rotação."""
    erros: list[str] = []
    planas: dict[int, dict[str, tuple[int, float, list]]] = {}
    for i in indices:
        planas[i] = {}
        for face in FACE_EIXO:
            fp = _face_plana(m.elementos[i], face, local)
            if fp and abs(_area(fp[2])) > EPS:
                planas[i][face] = fp
    for n, i in enumerate(indices):
        a = m.elementos[i]
        for j in indices[n + 1:]:
            b = m.elementos[j]
            if (local or not a.get("rotation") and not b.get("rotation")) and not (m.dentro[i] or m.dentro[j]):
                if _volume_comum(a, b) > EPS:
                    erros.append(f"elementos {i} e {j} se atravessam: {a['from']}-{a['to']} × {b['from']}-{b['to']}")
            for fa, (ea, ca, pa) in planas[i].items():
                if fa not in a["faces"]:
                    continue
                for fb, (eb, cb, pb) in planas[j].items():
                    if fb in b["faces"] and ea == eb and abs(ca - cb) < EPS and _sobrepoe(pa, pb):
                        erros.append(f"faces coplanares que piscam: {i}.{fa} e {j}.{fb} no plano {'xyz'[ea]}={ca}")
    return erros


def _faces_cobertas(m: Modelo, indices: list[int], local: bool) -> set[tuple[int, str]]:
    """As faces não desenhadas dos elementos `indices` que algum vizinho encostado cobre inteiras."""
    cobertas: set[tuple[int, str]] = set()
    for i in indices:
        for face in FACE_EIXO:
            if face in m.elementos[i]["faces"]:
                continue
            fp = _face_plana(m.elementos[i], face, local)
            if not fp or abs(_area(fp[2])) <= EPS:
                continue
            eixo, coord, poly = fp
            vizinhas = []
            for j in indices:
                if j == i:
                    continue
                for f2 in FACE_EIXO:
                    fq = _face_plana(m.elementos[j], f2, local)
                    if fq and fq[0] == eixo and abs(fq[1] - coord) < EPS:
                        vizinhas.append(fq[2])
            if _coberta(poly, vizinhas):
                cobertas.add((i, face))
    return cobertas


def validar_geometria(m: Modelo) -> None:
    """Nenhum par de faces coplanares desenhadas com área em comum, nenhum par de caixas que se atravessa
    (salvo `dentro`) e nenhuma face omitida sem um vizinho encostado que a cubra (buraco)."""
    erros: list[str] = []
    grupos: dict[tuple | None, list[int]] = {}
    for i, el in enumerate(m.elementos):
        grupos.setdefault(_chave_rot(el), []).append(i)
    todos_idx = list(range(len(m.elementos)))
    erros += _erros_do_grupo(m, todos_idx, local=False)
    cobertas = _faces_cobertas(m, todos_idx, local=False)
    for chave, indices in grupos.items():
        if chave is not None:
            erros += _erros_do_grupo(m, indices, local=True)
            cobertas |= _faces_cobertas(m, indices, local=True)
    for i, el in enumerate(m.elementos):
        for face in FACE_EIXO:
            if face in el["faces"] or (i, face) in cobertas:
                continue
            de, ate = el["from"], el["to"]
            eixo = FACE_EIXO[face][0]
            if any(ate[k] - de[k] <= EPS for k in range(3) if k != eixo):
                continue  # face sem área
            erros.append(f"buraco: elemento {i} ({de}-{ate}) sem a face {face} e sem vizinho que a cubra")
    if erros:
        raise AssertionError(f"{m.nome}:\n  " + "\n  ".join(erros))


def _sobreposicoes(modelo: str, sufixo: str = "") -> list[dict]:
    """Overrides do modelo do item: o predicado wirelessautomate:tier é a posição do tier no enum."""
    return [{"predicate": {f"{NS}:tier": n}, "model": f"{modelo}_{t}{sufixo}"}
            for n, t in enumerate(TIERS[1:], start=1)]


def modelos(texturas: set[str] | None = None) -> None:
    """Grava os modelos de bloco (e os filhos de giro do roteador), os blockstates e os modelos de item."""
    texturas = texturas or {n.split("/", 1)[1] for n in sprites()}
    pasta = MODELOS / "models/block"
    gerados: set[str] = set()
    for nome, m in construir_modelos().items():
        dados = m.json()
        validar_modelo(dados, texturas)
        validar_geometria(m)
        grava_json(pasta / f"{nome}.json", dados)
        gerados.add(nome)
    variantes: dict[str, dict] = {}
    for t in TIERS:
        for spin in range(1, 4):
            # Giro em torno de Y antes da rotação do blockstate, pela transformação raiz do NeoForge. O ângulo é
            # negativo porque o y positivo gira no anti-horário visto de cima; o spin gira no horário.
            nome = f"router_{t}_spin{spin}"
            grava_json(pasta / f"{nome}.json", {"parent": f"{NS}:block/router_{t}",
                                                "transform": {"origin": "center", "rotation": {"y": -90 * spin}}})
            gerados.add(nome)
        for face, rot in ROTACOES_ROTEADOR.items():
            for spin in range(4):
                modelo = f"{NS}:block/router_{t}" + (f"_spin{spin}" if spin else "")
                variantes[f"facing={face},spin={spin},tier={t}"] = {"model": modelo, **rot}
    grava_json(MODELOS / "blockstates/router.json", {"variants": variantes})
    grava_json(MODELOS / "models/item/router.json", {"parent": f"{NS}:block/router_basic",
                                                     "overrides": _sobreposicoes(f"{NS}:block/router")})
    for nome in ARMAZENAMENTOS:
        grava_json(MODELOS / f"blockstates/{nome}.json",
                   {"variants": {f"tier={t}": {"model": f"{NS}:block/{nome}_{t}"} for t in TIERS}})
        grava_json(MODELOS / f"models/item/{nome}.json", {"parent": f"{NS}:block/{nome}_basic",
                                                          "overrides": _sobreposicoes(f"{NS}:block/{nome}")})
    grava_json(MODELOS / "blockstates/storage_source_tank.json", {"variants": {
        f"fill={fill},tier={t}": {"model": f"{NS}:block/storage_source_tank_{t}_{fill}"}
        for t in TIERS for fill in range(11)}})
    grava_json(MODELOS / "models/item/storage_source_tank.json", {
        "parent": f"{NS}:block/storage_source_tank_basic_6",
        "overrides": _sobreposicoes(f"{NS}:block/storage_source_tank", "_6")})
    # Tudo em models/block é gerado: apaga o que deixou de existir.
    for antigo in pasta.glob("*.json"):
        if antigo.stem not in gerados:
            antigo.unlink()


def limpar_texturas_antigas(nomes: set[str]) -> list[str]:
    """Apaga em textures/block os PNGs (e .mcmeta) que não estão mais em `sprites()`."""
    pasta = ASSETS / "block"
    apagados = []
    for arq in sorted(pasta.iterdir()):
        base = arq.name.split(".")[0]
        if f"block/{base}" not in nomes:
            arq.unlink()
            apagados.append(arq.name)
    return apagados


# ---------------------------------------------------------------------------
# Prévia isométrica a partir dos elementos
# ---------------------------------------------------------------------------

def _rot(p: Vec, rot: dict | None) -> Vec:
    if not rot:
        return list(p)
    ox, oy, oz = rot["origin"]
    a = math.radians(rot["angle"])
    c, s = math.cos(a), math.sin(a)
    x, y, z = p[0] - ox, p[1] - oy, p[2] - oz
    if rot["axis"] == "x":
        y, z = y * c - z * s, y * s + z * c
    elif rot["axis"] == "y":
        x, z = x * c + z * s, -x * s + z * c
    else:
        x, y = x * c - y * s, x * s + y * c
    return [x + ox, y + oy, z + oz]


def _cantos(face: str, de: Vec, ate: Vec) -> list[Vec]:
    """Os quatro cantos da face na ordem (u0,v0), (u1,v0), (u1,v1), (u0,v1)."""
    (x0, y0, z0), (x1, y1, z1) = de, ate
    return {
        "south": [[x0, y1, z1], [x1, y1, z1], [x1, y0, z1], [x0, y0, z1]],
        "north": [[x1, y1, z0], [x0, y1, z0], [x0, y0, z0], [x1, y0, z0]],
        "east": [[x1, y1, z1], [x1, y1, z0], [x1, y0, z0], [x1, y0, z1]],
        "west": [[x0, y1, z0], [x0, y1, z1], [x0, y0, z1], [x0, y0, z0]],
        "up": [[x0, y1, z0], [x1, y1, z0], [x1, y1, z1], [x0, y1, z1]],
        "down": [[x0, y0, z1], [x1, y0, z1], [x1, y0, z0], [x0, y0, z0]],
    }[face]


def render(m: Modelo, texturas: dict[str, Image.Image], s: int = 8) -> Image.Image:
    """Projeção isométrica (câmera em +x, +y, +z), um polígono por texel, do mais fundo ao mais perto."""
    W, H = 34 * s, 34 * s
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    ox, oy = W // 2, 25 * s

    def proj(p: Vec) -> tuple[float, float]:
        return (ox + (p[0] - p[2]) * s, oy + (p[0] + p[2] - 16) * s / 2 - p[1] * s)

    texels: list[tuple[float, list[tuple[float, float]], Cor]] = []
    for el in m.elementos:
        rot = el.get("rotation")
        for face, f in el["faces"].items():
            cantos = [_rot(p, rot) for p in _cantos(face, el["from"], el["to"])]
            a = [cantos[1][i] - cantos[0][i] for i in range(3)]
            b = [cantos[3][i] - cantos[0][i] for i in range(3)]
            n = [b[1] * a[2] - b[2] * a[1], b[2] * a[0] - b[0] * a[2], b[0] * a[1] - b[1] * a[0]]
            norma = math.sqrt(sum(v * v for v in n)) or 1
            n = [v / norma for v in n]
            if n[0] + n[1] + n[2] <= 1e-6:
                continue
            sombra = max(0.35, min(1.0, 0.6 + 0.4 * n[1] + 0.24 * n[2] + 0.06 * n[0]))
            tex = texturas[f["texture"][1:]]
            u0, v0, u1, v1 = f["uv"]
            nu, nv = max(1, round(abs(u1 - u0))), max(1, round(abs(v1 - v0)))
            for i in range(nu):
                for j in range(nv):
                    tu = min(15, max(0, int(u0 + (u1 - u0) * (i + 0.5) / nu)))
                    tv = min(15, max(0, int(v0 + (v1 - v0) * (j + 0.5) / nv)))
                    c = tex.getpixel((tu, tv))
                    if c[3] == 0:
                        continue
                    pts = []
                    for (ia, ja) in ((i, j), (i + 1, j), (i + 1, j + 1), (i, j + 1)):
                        fa, fb = ia / nu, ja / nv
                        p = [cantos[0][k] + a[k] * fa + b[k] * fb for k in range(3)]
                        pts.append(p)
                    centro = [sum(p[k] for p in pts) / 4 for k in range(3)]
                    prof = centro[0] + centro[1] + centro[2] + 0.001 * n[1]
                    cc = (int(c[0] * sombra), int(c[1] * sombra), int(c[2] * sombra), c[3])
                    texels.append((prof, [proj(p) for p in pts], cc))
    texels.sort(key=lambda t: t[0])
    for _, pts, c in texels:
        if c[3] == 255:
            d.polygon(pts, fill=c)
        else:
            xs, ys = [p[0] for p in pts], [p[1] for p in pts]
            bx0, by0 = int(min(xs)), int(min(ys))
            bx1, by1 = int(max(xs)) + 2, int(max(ys)) + 2
            camada = Image.new("RGBA", (bx1 - bx0, by1 - by0), (0, 0, 0, 0))
            ImageDraw.Draw(camada).polygon([(x - bx0, y - by0) for x, y in pts], fill=c)
            img.alpha_composite(camada, (bx0, by0))
    return img


def previas(spr: dict[str, Sprite], s: int = 8) -> list[tuple[str, Image.Image]]:
    """Um roteador e cada armazenamento por tier, mais o Tanque de Source em alguns níveis (quadro 0)."""
    tex = {n.split("/", 1)[1]: sp.quadro(0) for n, sp in spr.items() if n.startswith("block/")}
    ms = construir_modelos()
    nomes = [f"router_{t}" for t in TIERS]
    for kind in ARMAZENAMENTOS:
        nomes += [f"{kind}_{t}" for t in TIERS]
    nomes += [f"storage_source_tank_{t}_6" for t in TIERS]
    nomes += [f"storage_source_tank_basic_{fill}" for fill in (0, 3, 10)]
    return [(nome, render(ms[nome], tex, s)) for nome in nomes]


def previa_arquivo(spr: dict[str, Sprite] | None = None, destino: Path | None = None) -> Path:
    """Monta a folha das prévias (`docs/preview/blocos-preview.png`)."""
    spr = spr or sprites()
    destino = destino or PREVIEW / "blocos-preview.png"
    lista = previas(spr)
    try:
        fonte = ImageFont.load_default(size=12)
    except TypeError:
        fonte = ImageFont.load_default()
    colunas, cel, rotulo = 8, 34 * 8, 18
    linhas = (len(lista) + colunas - 1) // colunas
    folha = Image.new("RGBA", (colunas * cel, linhas * (cel + rotulo)), (120, 128, 136, 255))
    d = ImageDraw.Draw(folha)
    for n, (nome, img) in enumerate(lista):
        x, y = (n % colunas) * cel, (n // colunas) * (cel + rotulo)
        folha.alpha_composite(img, (x, y))
        d.text((x + 4, y + cel), nome, fill=cor("grafite.1"), font=fonte)
    destino.parent.mkdir(parents=True, exist_ok=True)
    folha.save(destino)
    return destino


if __name__ == "__main__":
    spr = sprites()
    for nome, sp in spr.items():
        salvar(nome, sp)
    apagados = limpar_texturas_antigas(set(spr))
    modelos({n.split("/", 1)[1] for n in spr})
    animadas = sorted(n for n, sp in spr.items() if sp.frametime)
    print(f"{len(spr)} texturas ({len(animadas)} animadas); {len(apagados)} antigas apagadas")
    print("prévia:", previa_arquivo(spr))

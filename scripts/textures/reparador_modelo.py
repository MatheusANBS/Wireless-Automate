"""O modelo 3D do Reparador de Cristais, gerado do `.bbmodel` aprovado (`docs/preview/reparador-de-cristais.bbmodel`).

O `gerar_texturas.py` chama `gerar(raiz_assets)` depois do `blocos.modelos()`. Não usa a classe `Modelo` do
`blocos.py`, que proíbe elementos que se atravessam (o modelo aprovado tem vários), e grava tudo em subpastas:
`models/block/repairer/` (o `blocos.modelos()` só apaga os `models/block/*.json` da raiz) e
`textures/block/repairer/palette.png` (o `limpar` do `gerar_texturas.py` varre as subpastas, por isso `gerar`
devolve a chave da paleta para entrar no conjunto que ele preserva).

A textura do projeto no Blockbench é uma paleta de 128 × 128: cada material é uma coluna de blocos de 8 × 8 px e
cada tom (a direção da face no projeto) uma linha. Aqui ela vira uma paleta de 16 × 16 com 1 px por material e
tom (x = material, y = tom), lida da própria textura embutida no `.bbmodel` e conferida contra as cores base e os
fatores de tom do desenho. Cada face aponta para um retângulo de 0,5 px no centro do seu texel, para o filtro
não puxar a cor do vizinho.

O vidro (o elemento `glass`) vai num modelo à parte, translúcido; o blockstate junta os dois por `multipart`.
O JSON de bloco só aceita ângulos de -45 a 45 em passos de 22,5; os pedestais e as runas do anel giram em
múltiplos de 22,5 até -405, então o giro é dividido em quartos de volta, aplicados na própria caixa (troca de
faces e de eixos, exata), e o resto de -45 a 45, que fica como a rotação do elemento.

Uso isolado (na raiz): `python scripts/textures/reparador_modelo.py`.
"""

from __future__ import annotations

import base64
import io
import json
import math
import sys
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))

from identidade import ASSETS, MODELOS, RAIZ, grava_json  # noqa: E402

NS = "wirelessautomate"
BBMODEL = RAIZ / "docs/preview/reparador-de-cristais.bbmodel"
PASTA = "repairer"
SOLIDO = "crystal_repairer"
VIDRO = "crystal_repairer_glass"
TEXTURA = f"block/{PASTA}/palette"
ELEMENTO_VIDRO = "glass"
ELEMENTOS_ESPERADOS = 105

# Os materiais na ordem das colunas da paleta, com a cor base (o tom "north") do desenho aprovado.
MATERIAIS = ("slab", "step", "bronze", "steel", "post", "mull", "cap", "floor", "ped", "dark", "coral", "glow",
             "led", "glass")
BASES = ("#3f3b4c", "#4d495a", "#a67c38", "#6b6779", "#5c5869", "#76728a", "#58546a", "#24212c", "#7d788f",
         "#1d1b24", "#e8573a", "#ff9a66", "#ffc27a", "#a9cbe8")
# Os tons na ordem das linhas, com o fator que o desenho aplica sobre a cor base.
TONS = ("up", "north", "south", "east", "west", "down")
FATORES = (1.22, 1.0, 0.9, 0.84, 1.06, 0.62)
# Brilho, luz e vidro não têm tom por face no desenho; os elementos só com eles saem com "shade": false.
SEM_SOMBRA = frozenset(MATERIAIS.index(m) for m in ("glow", "led", "glass"))
ALFA_VIDRO = 51
# A textura embutida passa pelo PNG com alfa 51: a cor do vidro volta com até 3 de erro por canal.
TOLERANCIA = 3
BLOCO_PX = 8

ANGULOS_JSON = (-45.0, -22.5, 0.0, 22.5, 45.0)
EIXOS = ("x", "y", "z")
NORMAIS = {"east": (1, 0, 0), "west": (-1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0), "south": (0, 0, 1),
           "north": (0, 0, -1)}
# O blockstate gira o modelo (que olha para o norte) no sentido horário visto de cima, como o `ROTACOES_PECA`.
GIRO_FACING = {"north": 0, "east": 90, "south": 180, "west": 270}


class ModeloInvalido(ValueError):
    pass


def _paleta_embutida(projeto: dict) -> Image.Image:
    fonte = projeto["textures"][0]["source"]
    if not fonte.startswith("data:image/png;base64,"):
        raise ModeloInvalido("a textura do .bbmodel não está embutida como PNG")
    img = Image.open(io.BytesIO(base64.b64decode(fonte.split(",", 1)[1]))).convert("RGBA")
    if img.size != (128, 128):
        raise ModeloInvalido(f"a textura do .bbmodel tem {img.size}, esperado 128 × 128")
    return img


def _esperada(material: int, tom: int) -> tuple[int, int, int, int]:
    hexa = BASES[material].lstrip("#")
    base = [int(hexa[i:i + 2], 16) for i in (0, 2, 4)]
    fator = 1.0 if material in SEM_SOMBRA else FATORES[tom]
    alfa = ALFA_VIDRO if MATERIAIS[material] == "glass" else 255
    return (*(min(255, round(c * fator)) for c in base), alfa)


def paleta(projeto: dict) -> Image.Image:
    """A paleta de 16 × 16 lida do centro de cada bloco da textura do projeto, conferida contra o desenho."""
    origem = _paleta_embutida(projeto)
    out = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for m in range(len(MATERIAIS)):
        for t in range(len(TONS)):
            px = origem.getpixel((m * BLOCO_PX + BLOCO_PX // 2, t * BLOCO_PX + BLOCO_PX // 2))
            esperada = _esperada(m, t)
            if px[3] != esperada[3] or any(abs(a - b) > TOLERANCIA for a, b in zip(px[:3], esperada[:3])):
                raise ModeloInvalido(f"cor de {MATERIAIS[m]}/{TONS[t]} no .bbmodel é {px}, o desenho dá {esperada}")
            out.putpixel((m, t), px)
    return out


def _quarto(v: tuple[float, float, float], eixo: int, quartos: int) -> tuple[float, float, float]:
    """Gira `v` em `quartos` × 90° em torno do eixo (convenção do Blockbench e do jogo: mão direita)."""
    c, s = ((1, 0), (0, 1), (-1, 0), (0, -1))[quartos % 4]
    x, y, z = v
    if eixo == 0:
        return x, y * c - z * s, y * s + z * c
    if eixo == 1:
        return x * c + z * s, y, -x * s + z * c
    return x * c - y * s, x * s + y * c, z


def _gira(v, eixo: int, graus: float, origem) -> list[float]:
    a = math.radians(graus)
    c, s = math.cos(a), math.sin(a)
    x, y, z = (v[i] - origem[i] for i in range(3))
    if eixo == 0:
        r = (x, y * c - z * s, y * s + z * c)
    elif eixo == 1:
        r = (x * c + z * s, y, -x * s + z * c)
    else:
        r = (x * c - y * s, x * s + y * c, z)
    return [r[i] + origem[i] for i in range(3)]


def _cantos(de, ate) -> list[list[float]]:
    return [[(de, ate)[i][0], (de, ate)[j][1], (de, ate)[k][2]] for i in (0, 1) for j in (0, 1) for k in (0, 1)]


def _num(v: float) -> float | int:
    r = round(v, 5)
    return int(r) if r == int(r) else r


def _face(nome_el: str, direcao: str, face: dict) -> tuple[dict, int]:
    if face.get("texture") is None:
        raise ModeloInvalido(f"{nome_el}.{direcao}: face sem textura")
    u0, v0, u1, v1 = face["uv"]
    material = int(((u0 + u1) / 2) // BLOCO_PX)
    tom = int(((v0 + v1) / 2) // BLOCO_PX)
    if not (0 <= material < len(MATERIAIS) and 0 <= tom < len(TONS)):
        raise ModeloInvalido(f"{nome_el}.{direcao}: uv {face['uv']} fora da paleta")
    uv = [material + 0.25, tom + 0.25, material + 0.75, tom + 0.75]
    return {"uv": uv, "texture": "#palette"}, material


def converter(el: dict) -> dict:
    """Um elemento do .bbmodel no formato do JSON de bloco, com o giro dividido em quartos e resto."""
    nome = el["name"]
    de, ate = list(el["from"]), list(el["to"])
    origem = list(el.get("origin", [8, 8, 8]))
    rot = list(el.get("rotation", [0, 0, 0]))
    eixos = [i for i in range(3) if rot[i] != 0]
    if len(eixos) > 1:
        raise ModeloInvalido(f"{nome}: rotação em mais de um eixo {rot}")
    faces_bb = el.get("faces", {})
    if not faces_bb:
        raise ModeloInvalido(f"{nome}: elemento sem face")

    eixo = eixos[0] if eixos else 1
    graus = rot[eixo] if eixos else 0.0
    normal = (graus + 180) % 360 - 180
    quartos = round(normal / 90)
    resto = normal - 90 * quartos
    if resto not in ANGULOS_JSON:
        raise ModeloInvalido(f"{nome}: ângulo {graus} não se divide em quartos e -45..45 em passos de 22,5")

    cantos = [_quarto(tuple(p[i] - origem[i] for i in range(3)), eixo, quartos) for p in _cantos(de, ate)]
    novo_de = [min(c[i] for c in cantos) + origem[i] for i in range(3)]
    novo_ate = [max(c[i] for c in cantos) + origem[i] for i in range(3)]
    por_normal = {v: k for k, v in NORMAIS.items()}
    faces: dict[str, dict] = {}
    materiais: set[int] = set()
    for direcao, face in faces_bb.items():
        nova = por_normal[tuple(int(c) for c in _quarto(NORMAIS[direcao], eixo, quartos))]
        faces[nova], material = _face(nome, direcao, face)
        materiais.add(material)

    # A caixa com o resto do giro tem de cair exatamente nos cantos da original com o giro inteiro.
    alvo = sorted(tuple(round(c, 4) for c in _gira(p, eixo, graus, origem)) for p in _cantos(de, ate))
    obtido = sorted(tuple(round(c, 4) for c in _gira(p, eixo, resto, origem)) for p in _cantos(novo_de, novo_ate))
    if alvo != obtido:
        raise ModeloInvalido(f"{nome}: o giro dividido não reproduz o original")

    saida: dict = {"name": nome, "from": [_num(v) for v in novo_de], "to": [_num(v) for v in novo_ate]}
    if resto:
        saida["rotation"] = {"origin": [_num(v) for v in origem], "axis": EIXOS[eixo], "angle": resto}
    if materiais <= SEM_SOMBRA:
        saida["shade"] = False
    saida["faces"] = {d: faces[d] for d in NORMAIS if d in faces}
    return saida


def validar(elementos: list[dict]) -> None:
    for el in elementos:
        nome = el["name"]
        for i in range(3):
            if not el["from"][i] < el["to"][i]:
                raise ModeloInvalido(f"{nome}: from {el['from']} não é menor que to {el['to']}")
            for v in (el["from"][i], el["to"][i]):
                if not -16 <= v <= 32:
                    raise ModeloInvalido(f"{nome}: coordenada {v} fora de -16..32")
        rot = el.get("rotation")
        if rot is not None and (rot["axis"] not in EIXOS or rot["angle"] not in ANGULOS_JSON):
            raise ModeloInvalido(f"{nome}: rotação inválida {rot}")
        if not el["faces"]:
            raise ModeloInvalido(f"{nome}: elemento sem face")
        for direcao, face in el["faces"].items():
            if face.get("texture") != "#palette":
                raise ModeloInvalido(f"{nome}.{direcao}: face sem a textura da paleta")
            if not all(0 <= v <= 16 for v in face["uv"]):
                raise ModeloInvalido(f"{nome}.{direcao}: uv {face['uv']} fora de 0..16")


def _modelo(render_type: str, elementos: list[dict]) -> dict:
    textura = f"{NS}:{TEXTURA}"
    return {
        "parent": "minecraft:block/block",
        "render_type": render_type,
        "ambientocclusion": False,
        "textures": {"palette": textura, "particle": textura},
        "elements": elementos,
    }


def gerar(raiz_assets: Path = MODELOS) -> set[str]:
    """Grava a paleta, os dois modelos, o blockstate e o modelo de item; devolve as chaves das texturas gravadas
    (relativas a `textures/`, como as do `limpar`)."""
    projeto = json.loads(BBMODEL.read_text(encoding="utf-8"))
    img = paleta(projeto)
    elementos = [converter(el) for el in projeto["elements"]]
    vidro = [el for el in elementos if el["name"] == ELEMENTO_VIDRO]
    solido = [el for el in elementos if el["name"] != ELEMENTO_VIDRO]
    if len(solido) + len(vidro) != ELEMENTOS_ESPERADOS or len(projeto["elements"]) != ELEMENTOS_ESPERADOS:
        raise ModeloInvalido(f"{len(solido)} sólidos + {len(vidro)} de vidro, esperado {ELEMENTOS_ESPERADOS}")
    if not vidro:
        raise ModeloInvalido("o .bbmodel não tem o elemento do vidro")
    validar(elementos)
    vidro_cols = {int(f["uv"][0]) for el in vidro for f in el["faces"].values()}
    if vidro_cols != {MATERIAIS.index("glass")}:
        raise ModeloInvalido("o vidro usa outro material além do vidro")
    if any(img.getpixel((int(f["uv"][0]), int(f["uv"][1])))[3] != 255 for el in solido for f in el["faces"].values()):
        raise ModeloInvalido("o modelo sólido usa um texel com alfa")

    textura = ASSETS / f"{TEXTURA}.png"
    textura.parent.mkdir(parents=True, exist_ok=True)
    img.save(textura)
    modelos = raiz_assets / "models/block" / PASTA
    grava_json(modelos / f"{SOLIDO}.json", _modelo("minecraft:solid", solido))
    grava_json(modelos / f"{VIDRO}.json", _modelo("minecraft:translucent", vidro))
    partes = []
    for facing, y in GIRO_FACING.items():
        for nome in (SOLIDO, VIDRO):
            aplica: dict = {"model": f"{NS}:block/{PASTA}/{nome}"}
            if y:
                aplica["y"] = y
            partes.append({"when": {"facing": facing}, "apply": aplica})
    grava_json(raiz_assets / f"blockstates/{SOLIDO}.json", {"multipart": partes})
    grava_json(raiz_assets / f"models/item/{SOLIDO}.json", {"parent": f"{NS}:block/{PASTA}/{SOLIDO}"})
    return {TEXTURA}


if __name__ == "__main__":
    print("texturas:", sorted(gerar()))

"""O modelo 3D do Reparador de Cristais, gerado do `.bbmodel` aprovado (`docs/preview/reparador-de-cristais.bbmodel`).

O `gerar_texturas.py` chama `gerar(raiz_assets)` depois do `blocos.modelos()`. Não usa a classe `Modelo` do
`blocos.py`, que proíbe elementos que se atravessam (o modelo aprovado tem vários), e grava tudo em subpastas:
`models/block/repairer/` (o `blocos.modelos()` só apaga os `models/block/*.json` da raiz) e
`textures/block/repairer/repairer.png` (o `limpar` do `gerar_texturas.py` varre as subpastas, por isso `gerar`
devolve a chave da textura para entrar no conjunto que ele preserva).

A textura é o atlas pintado no Blockbench (256 × 256, embutido no `.bbmodel`): cada face tem o seu retângulo, com
1 px de folga entre eles. Ao gravar, a borda de cada retângulo é repetida 1 px para fora, na folga, para o mipmap
não misturar a face com o fundo transparente nem com a vizinha. O uv do JSON de bloco vai de 0 a 16, então é o uv
em pixels × 16 / 256.

O vidro (o elemento `glass`) vai num modelo à parte, translúcido, e as luzes (os elementos do `_sem_sombra`) em
dois modelos à parte, escolhidos pelo `lit` do blockstate: o aceso, com o atlas como foi pintado e a luz cheia
(`neoforge_data`, o `ExtraFaceData` do NeoForge, lido por elemento no `BlockElement`), e o apagado, com a textura
`repairer_off`, em que só os retângulos das luzes vão metade para o cinza e a 30% do brilho. Trocar o `lit` só
remonta a seção no cliente: nada por quadro e visível a qualquer distância. O blockstate junta as partes por
`multipart` e o modelo de item (aceso) pelo `neoforge:composite`. O JSON de bloco só aceita ângulos de -45 a 45 em passos de 22,5; os
pedestais e as runas do anel giram em múltiplos de 22,5 até -405, então o giro é dividido em quartos de volta,
aplicados na própria caixa (troca de faces e de eixos, exata), e o resto de -45 a 45, que fica como a rotação do
elemento. A face que muda de lugar no quarto de volta ganha a `rotation` de face que põe cada canto da textura no
mesmo canto do mundo, achada pela regra de uv do jogo (`FaceInfo` e `BlockFaceUV`).

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
BRILHO = "crystal_repairer_glow"
BRILHO_OFF = "crystal_repairer_glow_off"
TEXTURA = f"block/{PASTA}/repairer"
TEXTURA_OFF = f"block/{PASTA}/repairer_off"
ELEMENTO_VIDRO = "glass"
ELEMENTOS_ESPERADOS = 104
ATLAS = 256

ANGULOS_JSON = (-45.0, -22.5, 0.0, 22.5, 45.0)
EIXOS = ("x", "y", "z")
NORMAIS = {"east": (1, 0, 0), "west": (-1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0), "south": (0, 0, 1),
           "north": (0, 0, -1)}
# Os cantos de cada face na ordem do `FaceInfo` do jogo: 0 = mínimo, 1 = máximo, por eixo (x, y, z).
CANTOS_FACE = {
    "down": ((0, 0, 1), (0, 0, 0), (1, 0, 0), (1, 0, 1)),
    "up": ((0, 1, 0), (0, 1, 1), (1, 1, 1), (1, 1, 0)),
    "north": ((1, 1, 0), (1, 0, 0), (0, 0, 0), (0, 1, 0)),
    "south": ((0, 1, 1), (0, 0, 1), (1, 0, 1), (1, 1, 1)),
    "west": ((0, 1, 0), (0, 0, 0), (0, 0, 1), (0, 1, 1)),
    "east": ((1, 1, 1), (1, 0, 1), (1, 0, 0), (1, 1, 0)),
}
# O blockstate gira o modelo (que olha para o norte) no sentido horário visto de cima, como o `ROTACOES_PECA`.
GIRO_FACING = {"north": 0, "east": 90, "south": 180, "west": 270}


class ModeloInvalido(ValueError):
    pass


def _sem_sombra(nome: str) -> bool:
    """Luzes e brilhos ficam chapados: o jogo não escurece a face pela direção. Os brilhos repetidos levam o índice
    depois do `_glow` (`rune_glow_0`, `fin_tip_glow_a`)."""
    return nome.endswith("_glow") or "_glow_" in nome or nome == "led_glow"


def _atlas(projeto: dict) -> Image.Image:
    fonte = projeto["textures"][0]["source"]
    if not fonte.startswith("data:image/png;base64,"):
        raise ModeloInvalido("a textura do .bbmodel não está embutida como PNG")
    img = Image.open(io.BytesIO(base64.b64decode(fonte.split(",", 1)[1]))).convert("RGBA")
    if img.size != (ATLAS, ATLAS):
        raise ModeloInvalido(f"a textura do .bbmodel tem {img.size}, esperado {ATLAS} × {ATLAS}")
    return img


def _retangulo(uv) -> tuple[int, int, int, int]:
    x0, y0, x1, y1 = (int(round(v)) for v in uv)
    return min(x0, x1), min(y0, y1), max(x0, x1), max(y0, y1)


def com_folga(img: Image.Image, retangulos: list[tuple[int, int, int, int]]) -> Image.Image:
    """Repete a borda de cada retângulo 1 px para fora, só nos pixels que não são de nenhuma face."""
    ocupado = set()
    for x0, y0, x1, y1 in retangulos:
        ocupado.update((x, y) for x in range(x0, x1) for y in range(y0, y1))
    out = img.copy()
    escrito = set()
    for x0, y0, x1, y1 in retangulos:
        for x in range(x0 - 1, x1 + 1):
            for y in range(y0 - 1, y1 + 1):
                if not (0 <= x < ATLAS and 0 <= y < ATLAS) or (x, y) in ocupado or (x, y) in escrito:
                    continue
                fonte = (min(max(x, x0), x1 - 1), min(max(y, y0), y1 - 1))
                out.putpixel((x, y), img.getpixel(fonte))
                escrito.add((x, y))
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


def _uv_cantos(direcao: str, de, ate, uv, rotacao: int) -> list[tuple[tuple, tuple]]:
    """O uv que o jogo dá a cada canto da face: `BlockFaceUV.getU/getV` com o índice deslocado pela rotação."""
    saida = []
    for indice, canto in enumerate(CANTOS_FACE[direcao]):
        pos = tuple((de, ate)[canto[i]][i] for i in range(3))
        k = (indice + rotacao // 90) % 4
        saida.append((pos, (uv[0] if k in (0, 1) else uv[2], uv[1] if k in (0, 3) else uv[3])))
    return saida


def _mesmos_cantos(a: list[tuple[tuple, tuple]], b: list[tuple[tuple, tuple]]) -> bool:
    """Cada canto de `a` tem em `b` um canto na mesma posição (até 1e-3, o erro do giro) com o mesmo uv."""
    def perto(p, q):
        return all(abs(p[i] - q[i]) < 1e-3 for i in range(len(p)))
    return all(any(perto(pa, pb) and perto(ua, ub) for pb, ub in b) for pa, ua in a)


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
    for direcao, face in faces_bb.items():
        if face.get("texture") is None:
            raise ModeloInvalido(f"{nome}.{direcao}: face sem textura")
        if face.get("rotation"):
            raise ModeloInvalido(f"{nome}.{direcao}: rotação de face no .bbmodel não é tratada")
        uv = [v * 16 / ATLAS for v in face["uv"]]
        nova = por_normal[tuple(int(c) for c in _quarto(NORMAIS[direcao], eixo, quartos))]
        # Onde cada canto da textura cai no mundo depois do quarto de volta; a rotação de face que repete isso.
        alvo = []
        for pos, coord in _uv_cantos(direcao, de, ate, uv, 0):
            girado = _quarto(tuple(pos[i] - origem[i] for i in range(3)), eixo, quartos)
            alvo.append((tuple(girado[i] + origem[i] for i in range(3)), coord))
        rotacao = next((r for r in (0, 90, 180, 270)
                        if _mesmos_cantos(alvo, _uv_cantos(nova, novo_de, novo_ate, uv, r))), None)
        if rotacao is None:
            raise ModeloInvalido(f"{nome}.{direcao}: nenhuma rotação de face reproduz a textura girada")
        faces[nova] = {"uv": [_num(v) for v in uv], "texture": "#repairer"}
        if rotacao:
            faces[nova]["rotation"] = rotacao

    # A caixa com o resto do giro tem de cair exatamente nos cantos da original com o giro inteiro.
    alvo_cantos = sorted(tuple(round(c, 4) for c in _gira(p, eixo, graus, origem)) for p in _cantos(de, ate))
    obtido = sorted(tuple(round(c, 4) for c in _gira(p, eixo, resto, origem)) for p in _cantos(novo_de, novo_ate))
    if alvo_cantos != obtido:
        raise ModeloInvalido(f"{nome}: o giro dividido não reproduz o original")

    saida: dict = {"name": nome, "from": [_num(v) for v in novo_de], "to": [_num(v) for v in novo_ate]}
    if resto:
        saida["rotation"] = {"origin": [_num(v) for v in origem], "axis": EIXOS[eixo], "angle": resto}
    if _sem_sombra(nome):
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
            if face.get("texture") != "#repairer":
                raise ModeloInvalido(f"{nome}.{direcao}: face sem a textura do atlas")
            if not all(0 <= v <= 16 for v in face["uv"]):
                raise ModeloInvalido(f"{nome}.{direcao}: uv {face['uv']} fora de 0..16")


def _alfas(img: Image.Image, faces: list[dict]) -> list[int]:
    alfas = []
    for face in faces:
        x0, y0, x1, y1 = _retangulo([v * ATLAS / 16 for v in face["uv"]])
        if x1 <= x0 or y1 <= y0:
            raise ModeloInvalido(f"face de área zero no atlas: {face['uv']}")
        alfas.extend(img.getpixel((x, y))[3] for x in range(x0, x1) for y in range(y0, y1))
    return alfas


def apagar(img: Image.Image, retangulos: list[tuple[int, int, int, int]]) -> Image.Image:
    """As luzes apagadas: cada canal vai metade para o cinza (0,3r + 0,59g + 0,11b) e fica a 30%; o alfa não muda."""
    out = img.copy()
    for x0, y0, x1, y1 in retangulos:
        for x in range(x0, x1):
            for y in range(y0, y1):
                r, g, b, a = img.getpixel((x, y))
                cinza = 0.3 * r + 0.59 * g + 0.11 * b
                out.putpixel((x, y), tuple(int(round((c + (cinza - c) * 0.5) * 0.30)) for c in (r, g, b)) + (a,))
    return out


def _acesa(el: dict) -> dict:
    """A luz acesa: luz de bloco e de céu cheias no elemento, para brilhar no escuro sem `lightEmission`."""
    saida = dict(el)
    faces = saida.pop("faces")
    saida["neoforge_data"] = {"block_light": 15, "sky_light": 15}
    saida["faces"] = faces
    return saida


def _apagada(el: dict) -> dict:
    """A luz apagada recebe a sombra e a luz do mundo como o resto do bloco."""
    return {k: v for k, v in el.items() if k != "shade"}


def _modelo(render_type: str, elementos: list[dict], textura_chave: str = TEXTURA) -> dict:
    textura = f"{NS}:{textura_chave}"
    return {
        "parent": "minecraft:block/block",
        "render_type": render_type,
        "ambientocclusion": False,
        "textures": {"repairer": textura, "particle": textura},
        "elements": elementos,
    }


def gerar(raiz_assets: Path = MODELOS) -> set[str]:
    """Grava os atlas aceso e apagado, os quatro modelos, o blockstate e o modelo de item; devolve as chaves das
    texturas gravadas
    (relativas a `textures/`, como as do `limpar`)."""
    projeto = json.loads(BBMODEL.read_text(encoding="utf-8"))
    atlas = _atlas(projeto)
    elementos = [converter(el) for el in projeto["elements"]]
    vidro = [el for el in elementos if el["name"] == ELEMENTO_VIDRO]
    luzes = [el for el in elementos if _sem_sombra(el["name"])]
    solido = [el for el in elementos if el["name"] != ELEMENTO_VIDRO and not _sem_sombra(el["name"])]
    total = len(solido) + len(vidro) + len(luzes)
    if total != ELEMENTOS_ESPERADOS or len(projeto["elements"]) != ELEMENTOS_ESPERADOS:
        raise ModeloInvalido(f"{len(solido)} sólidos + {len(vidro)} de vidro + {len(luzes)} luzes, esperado "
                             f"{ELEMENTOS_ESPERADOS}")
    if not vidro:
        raise ModeloInvalido("o .bbmodel não tem o elemento do vidro")
    if not luzes:
        raise ModeloInvalido("o .bbmodel não tem nenhuma luz")
    validar(elementos)
    if any(a != 255 for a in _alfas(atlas, [f for el in solido + luzes for f in el["faces"].values()])):
        raise ModeloInvalido("o modelo sólido ou as luzes usam um texel com alfa")
    alfas_vidro = _alfas(atlas, [f for el in vidro for f in el["faces"].values()])
    if any(a == 255 for a in alfas_vidro) or not any(alfas_vidro):
        raise ModeloInvalido("o vidro precisa de alfa parcial em todas as faces")

    retangulos = [_retangulo(face["uv"]) for el in projeto["elements"] for face in el["faces"].values()]
    retangulos_luzes = [_retangulo(face["uv"]) for el in projeto["elements"] if _sem_sombra(el["name"])
                        for face in el["faces"].values()]
    textura = ASSETS / f"{TEXTURA}.png"
    textura.parent.mkdir(parents=True, exist_ok=True)
    com_folga(atlas, retangulos).save(textura)
    com_folga(apagar(atlas, retangulos_luzes), retangulos).save(ASSETS / f"{TEXTURA_OFF}.png")
    modelos = raiz_assets / "models/block" / PASTA
    grava_json(modelos / f"{SOLIDO}.json", _modelo("minecraft:solid", solido))
    grava_json(modelos / f"{VIDRO}.json", _modelo("minecraft:translucent", vidro))
    grava_json(modelos / f"{BRILHO}.json", _modelo("minecraft:solid", [_acesa(el) for el in luzes]))
    grava_json(modelos / f"{BRILHO_OFF}.json",
               _modelo("minecraft:solid", [_apagada(el) for el in luzes], TEXTURA_OFF))
    partes = []
    for facing, y in GIRO_FACING.items():
        for nome, lit in ((SOLIDO, None), (VIDRO, None), (BRILHO, "true"), (BRILHO_OFF, "false")):
            aplica: dict = {"model": f"{NS}:block/{PASTA}/{nome}"}
            if y:
                aplica["y"] = y
            quando = {"facing": facing}
            if lit:
                quando["lit"] = lit
            partes.append({"when": quando, "apply": aplica})
    grava_json(raiz_assets / f"blockstates/{SOLIDO}.json", {"multipart": partes})
    # O item junta as partes pelo modelo composto do NeoForge: cada filho desenha com o próprio render_type, na
    # ordem dos filhos (o sólido e as luzes acesas antes do vidro), e o `display` vem do `block/block`.
    grava_json(raiz_assets / f"models/item/{SOLIDO}.json", {
        "parent": "minecraft:block/block",
        "loader": "neoforge:composite",
        "textures": {"particle": f"{NS}:{TEXTURA}"},
        "children": {
            "solido": {"parent": f"{NS}:block/{PASTA}/{SOLIDO}"},
            "brilho": {"parent": f"{NS}:block/{PASTA}/{BRILHO}"},
            "vidro": {"parent": f"{NS}:block/{PASTA}/{VIDRO}"},
        },
    })
    return {TEXTURA, TEXTURA_OFF}


if __name__ == "__main__":
    print("texturas:", sorted(gerar()))

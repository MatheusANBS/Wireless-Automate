"""A conversão comum de um `.bbmodel` do Blockbench (formato `java_block`, atlas embutido) para JSON de bloco.

Usada pelo `reparador_modelo.py` (Reparador de Cristais) e pelo `tela_modelo.py` (Tela de Sinal). Cada conversor
diz o tamanho do atlas e a chave da textura nas faces (`#repairer`, `#screen`); o resto é igual:

- a textura é o atlas pintado no Blockbench, embutido como PNG. Cada face tem o seu retângulo, com folga entre
  eles; ao gravar, `com_folga` repete a borda de cada retângulo 1 px para fora, na folga, para o mipmap não
  misturar a face com o fundo transparente nem com a vizinha. O uv do JSON de bloco vai de 0 a 16, então é o uv em
  pixels × 16 / tamanho do atlas;
- o JSON de bloco só aceita ângulos de -45 a 45 em passos de 22,5: um giro maior é dividido em quartos de volta,
  aplicados na própria caixa (troca de faces e de eixos, exata), e o resto de -45 a 45, que fica como a rotação do
  elemento. A face que muda de lugar no quarto de volta ganha a `rotation` de face que põe cada canto da textura
  no mesmo canto do mundo, achada pela regra de uv do jogo (`FaceInfo` e `BlockFaceUV`);
- as luzes acesas levam o `neoforge_data` (o `ExtraFaceData` do NeoForge, lido por elemento no `BlockElement`) com
  a luz de bloco e de céu cheias, para brilhar no escuro sem `lightEmission`.
"""

from __future__ import annotations

import base64
import io
import math

from PIL import Image

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


def atlas(projeto: dict, tamanho: int) -> Image.Image:
    """A primeira textura do projeto, embutida como PNG, quadrada de `tamanho` px."""
    fonte = projeto["textures"][0]["source"]
    if not fonte.startswith("data:image/png;base64,"):
        raise ModeloInvalido("a textura do .bbmodel não está embutida como PNG")
    img = Image.open(io.BytesIO(base64.b64decode(fonte.split(",", 1)[1]))).convert("RGBA")
    if img.size != (tamanho, tamanho):
        raise ModeloInvalido(f"a textura do .bbmodel tem {img.size}, esperado {tamanho} × {tamanho}")
    return img


def retangulo(uv) -> tuple[int, int, int, int]:
    x0, y0, x1, y1 = (int(round(v)) for v in uv)
    return min(x0, x1), min(y0, y1), max(x0, x1), max(y0, y1)


def com_folga(img: Image.Image, retangulos: list[tuple[int, int, int, int]]) -> Image.Image:
    """Repete a borda de cada retângulo 1 px para fora, só nos pixels que não são de nenhuma face."""
    largura, altura = img.size
    ocupado = set()
    for x0, y0, x1, y1 in retangulos:
        ocupado.update((x, y) for x in range(x0, x1) for y in range(y0, y1))
    out = img.copy()
    escrito = set()
    for x0, y0, x1, y1 in retangulos:
        for x in range(x0 - 1, x1 + 1):
            for y in range(y0 - 1, y1 + 1):
                if not (0 <= x < largura and 0 <= y < altura) or (x, y) in ocupado or (x, y) in escrito:
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


def num(v: float) -> float | int:
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


def converter(el: dict, tamanho: int, chave: str, sem_sombra, pula_sem_textura: bool = False) -> dict:
    """Um elemento do .bbmodel no formato do JSON de bloco, com o giro dividido em quartos e resto.

    `chave` é a textura das faces (`#repairer`); `sem_sombra(nome)` diz quais elementos levam `shade: false`.
    Com `pula_sem_textura`, a face desligada no Blockbench (textura nula) fica de fora; sem ele, é erro."""
    nome = el["name"]
    de, ate = list(el["from"]), list(el["to"])
    origem = list(el.get("origin", [8, 8, 8]))
    rot = list(el.get("rotation", [0, 0, 0]))
    eixos = [i for i in range(3) if rot[i] != 0]
    if len(eixos) > 1:
        raise ModeloInvalido(f"{nome}: rotação em mais de um eixo {rot}")
    faces_bb = el.get("faces", {})
    if pula_sem_textura:
        faces_bb = {d: f for d, f in faces_bb.items() if f.get("texture") is not None}
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
        uv = [v * 16 / tamanho for v in face["uv"]]
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
        faces[nova] = {"uv": [num(v) for v in uv], "texture": chave}
        if rotacao:
            faces[nova]["rotation"] = rotacao

    # A caixa com o resto do giro tem de cair exatamente nos cantos da original com o giro inteiro.
    alvo_cantos = sorted(tuple(round(c, 4) for c in _gira(p, eixo, graus, origem)) for p in _cantos(de, ate))
    obtido = sorted(tuple(round(c, 4) for c in _gira(p, eixo, resto, origem)) for p in _cantos(novo_de, novo_ate))
    if alvo_cantos != obtido:
        raise ModeloInvalido(f"{nome}: o giro dividido não reproduz o original")

    saida: dict = {"name": nome, "from": [num(v) for v in novo_de], "to": [num(v) for v in novo_ate]}
    if resto:
        saida["rotation"] = {"origin": [num(v) for v in origem], "axis": EIXOS[eixo], "angle": resto}
    if sem_sombra(nome):
        saida["shade"] = False
    saida["faces"] = {d: faces[d] for d in NORMAIS if d in faces}
    return saida


def validar(elementos: list[dict], chave: str) -> None:
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
            if face.get("texture") != chave:
                raise ModeloInvalido(f"{nome}.{direcao}: face sem a textura do atlas")
            if not all(0 <= v <= 16 for v in face["uv"]):
                raise ModeloInvalido(f"{nome}.{direcao}: uv {face['uv']} fora de 0..16")


def alfas(img: Image.Image, faces: list[dict]) -> list[int]:
    """O alfa de cada texel das faces (uv de 0 a 16), para conferir o sólido e o vidro."""
    tamanho = img.size[0]
    saida = []
    for face in faces:
        x0, y0, x1, y1 = retangulo([v * tamanho / 16 for v in face["uv"]])
        if x1 <= x0 or y1 <= y0:
            raise ModeloInvalido(f"face de área zero no atlas: {face['uv']}")
        saida.extend(img.getpixel((x, y))[3] for x in range(x0, x1) for y in range(y0, y1))
    return saida


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


def acesa(el: dict) -> dict:
    """A luz acesa: luz de bloco e de céu cheias no elemento, para brilhar no escuro sem `lightEmission`."""
    saida = dict(el)
    faces = saida.pop("faces")
    saida["neoforge_data"] = {"block_light": 15, "sky_light": 15}
    saida["faces"] = faces
    return saida


def apagada(el: dict) -> dict:
    """A luz apagada recebe a sombra e a luz do mundo como o resto do bloco."""
    return {k: v for k, v in el.items() if k != "shade"}


def modelo(render_type: str, elementos: list[dict], chave: str, textura: str) -> dict:
    """Um modelo de bloco com uma textura só (a `chave` sem o `#`) e o `particle` nela."""
    return {
        "parent": "minecraft:block/block",
        "render_type": render_type,
        "ambientocclusion": False,
        "textures": {chave.lstrip("#"): textura, "particle": textura},
        "elements": elementos,
    }

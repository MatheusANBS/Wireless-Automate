"""O modelo estático da Doca de Padrões e as texturas por tier, gerados do `.bbmodel` aprovado
(`docs/preview/doca-de-padroes.bbmodel`).

O `gerar_texturas.py` chama `gerar(raiz_assets)` depois do `blocos.modelos()`, como o do Reparador, e grava em
subpastas: `models/block/pattern_dock/` (o `blocos.modelos()` só apaga os `models/block/*.json` da raiz) e
`textures/block/pattern_dock/<tier>.png` (o `limpar` do `gerar_texturas.py` varre as subpastas, por isso `gerar`
devolve as chaves das texturas para ele preservar). A conversão de cada caixa é a comum, do `bbmodel_json.py`; o
atlas é de 64 × 64, a 4 px por unidade do modelo.

Entram só as partes que são modelo de bloco na spec (a tabela "O modelo" da proposta do AE2 e do RS2): o esqueleto
(o grupo `base`: as 12 vigas de porcelana com a linha coral nas de cima), os seis lados (`lado_*`: o painel escuro
com o circuito em volta da janela, a vigia com a moldura, os parafusos, o lábio, as dobradiças e o trinco) e os
contatos (`garras_*`, quatro pastilhas de latão por lado). O giroscópio, o pacote e os fragmentos são do renderer
(`client/dock/render/`). As coordenadas do `.bbmodel` têm o bloco centrado em x e z; aqui somam 8.

Nenhum par de caixas se junta numa caixa só (nem encostadas com as mesmas medidas, nem uma por cima da outra:
conferido nas 174), então o número de elementos fica o do `.bbmodel`; o que baixa é o de faces. Sai toda face
coberta por outra caixa (a parte de trás da moldura contra o painel, as pontas das vigas umas contra as outras), e
a que fica na borda do bloco leva o `cullface` do lado, para sumir contra um vizinho opaco.

A textura de cada tier é o atlas pintado no Blockbench com duas trocas:
- o circuito foi pintado na rampa da lente do Elite (sombra, lente e brilho do `LENTE`, mais os tons do meio da
  onda); cada texel perto dessa rampa vai para o mesmo ponto da rampa do tier;
- as etiquetas dos lados (a casa de 4 × 4 da face de fora da pastilha de cima de cada lado, inteira, para o mipmap
  não misturar a cor com a vizinha) recebem a cor do lado, lida do `dock/DockColors.java`, a fonte única delas.

O circuito anima num sprite à parte, `circuit_<tier>` (o recorte de 16 × 16 do painel no atlas, `PAINEL`): a face
de fora de cada peça do painel usa a chave `#circuit`, o resto do bloco o `#dock`. Cada texel do circuito oscila
entre a sombra, a lente e o brilho do tier (`1 + cos`, os pontos 0 a 2 da rampa) numa onda plana que cruza a face
de baixo à esquerda para cima à direita, um comprimento de onda por face, com período de 3 s: `QUADROS` quadros
de `TICKS_QUADRO` ticks com a interpolação do jogo (medido no GIF aprovado; ver "A arte no jogo" na spec da Doca).
Um sprite de 16 × 16 em vez do atlas inteiro animado: o jogo mistura o quadro do sprite inteiro a cada tick.
A face de fora de cada peça do painel leva a luz cheia (`neoforge_data` só nessa face), para o circuito brilhar no
escuro; o resto do bloco recebe a luz do mundo. A luz só nos traços pediria uma camada a mais por cima do painel
(como o ME Controller do AE2), que o modelo não leva.

As etiquetas ficam maiores que no `.bbmodel` (`ETIQUETA_*`), para se lerem de longe: a pastilha de cima de cada
lado cresce no plano da face, entre a moldura da vigia e a borda do painel.

O miolo é escuro, como o fundo do Blockbench: atrás de cada vigia, logo para dentro do lábio, uma placa fina
(`fundo_<lado>`) com uma face só, virada para dentro, no grafite escuro do painel (a casa `FUNDO` do atlas, pintada
inteira). Quem olha por uma vigia vê o giroscópio contra a placa da vigia oposta, e não o céu e o chão do outro
lado; de fora, a placa da própria vigia está de costas e não aparece (o jogo não desenha a face de trás). Seis
faces a mais, nenhuma no caminho do giroscópio (o anel maior vai até 4,5 do centro; a placa fica a 6,2).

Um modelo só com os elementos (`pattern_dock`, nas texturas do Básico) e um filho por tier que só troca as texturas;
o blockstate escolhe pelo `tier` e o modelo do item pelas sobreposições do predicado `wirelessautomate:tier` (a
posição do tier no enum). A colisão é a do bloco cheio, no Java.

Uso isolado (na raiz): `python scripts/textures/doca_modelo.py`.
"""

from __future__ import annotations

import copy
import json
import math
import re
import sys
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))

import bbmodel_json as bb  # noqa: E402
from identidade import ASSETS, LENTE, MODELOS, RAIZ, TIERS, grava_json, rgb  # noqa: E402

NS = "wirelessautomate"
BBMODEL = RAIZ / "docs/preview/doca-de-padroes.bbmodel"
CORES_JAVA = RAIZ / "src/main/java/io/github/matheusanbs/wirelessautomate/dock/DockColors.java"
PASTA = "pattern_dock"
BASE = "pattern_dock"
ATLAS = 64
CHAVE = "#dock"
CIRCUITO = "#circuit"
ELEMENTOS_BBMODEL = 193
ELEMENTOS_ESPERADOS = 174
# As faces depois do corte, com as seis do fundo do miolo.
FACES_ESPERADAS = 800
# O recorte do painel no atlas (x0, y0), de 16 × 16: o sprite do circuito.
PAINEL = (32, 32)
LADO_SPRITE = 16
# A onda do circuito: 20 quadros de 3 ticks (3 s), interpolados pelo jogo.
QUADROS = 20
TICKS_QUADRO = 3
# A etiqueta no plano da face (unidades do modelo, a partir do centro da face): meia largura, a folga até a moldura
# da vigia e a folga até a borda do painel (6).
ETIQUETA_MEIA_LARGURA = 2.5
ETIQUETA_FOLGA_MOLDURA = 0.3
ETIQUETA_FOLGA_BORDA = 0.3
# A distância da borda de fora da moldura ao centro da face.
MOLDURA = 3.5
# O fundo do miolo: a casa de 4 × 4 do atlas (livre no .bbmodel) pintada no grafite mais escuro do painel e a
# espessura da placa atrás de cada vigia.
FUNDO = (0, 52)
FUNDO_COR = (15, 14, 18, 255)
FUNDO_ESPESSURA = 0.1
# A rampa em que o circuito foi pintado e a distância (em RGB) até ela que ainda conta como circuito: os tons do
# circuito ficam a menos de 1, o fundo escuro do painel a mais de 40.
RAMPA_PINTADA = "elite"
PERTO_DA_RAMPA = 4.0
# A ordem do `DockColors.SIDE` (o `Direction.ordinal()`), com o nome dos lados no `.bbmodel`.
LADOS = ("down", "up", "north", "south", "west", "east")
EIXO = {"east": (0, 1), "west": (0, -1), "up": (1, 1), "down": (1, -1), "south": (2, 1), "north": (2, -1)}
ModeloInvalido = bb.ModeloInvalido


def _textura(tier: str) -> str:
    return f"block/{PASTA}/{tier}"


def _circuito(tier: str) -> str:
    return f"block/{PASTA}/circuit_{tier}"


def _do_bloco(grupo: str) -> bool:
    return grupo == "base" or grupo.startswith(("lado_", "garras_"))


def _grupos(projeto: dict) -> dict[str, str]:
    """O grupo mais de dentro de cada elemento, pelo uuid."""
    grupos = {g["uuid"]: g["name"] for g in projeto["groups"]}
    saida: dict[str, str] = {}

    def anda(filhos: list, nome: str) -> None:
        for filho in filhos:
            if isinstance(filho, dict):
                anda(filho.get("children", []), grupos[filho["uuid"]])
            else:
                saida[filho] = nome

    anda(projeto["outliner"], "")
    return saida


def _no_bloco(el: dict) -> dict:
    """O elemento com o bloco de 0 a 16 em x e z."""
    saida = dict(el)
    for chave in ("from", "to", "origin"):
        v = list(el.get(chave, [0, 0, 0]))
        saida[chave] = [v[0] + 8, v[1], v[2] + 8]
    return saida


def _coberta(el: dict, direcao: str, outros: list[dict]) -> bool:
    """A face inteira encosta no volume de outras caixas: cada célula da grade formada pelas bordas das caixas tem o
    centro dentro de alguma, logo depois do plano da face."""
    k, sinal = EIXO[direcao]
    plano = el["to"][k] if sinal > 0 else el["from"][k]
    a, b = (i for i in range(3) if i != k)

    def atras(o: dict) -> bool:
        return o["from"][k] <= plano < o["to"][k] if sinal > 0 else o["from"][k] < plano <= o["to"][k]

    vizinhos = [o for o in outros if o is not el and atras(o)]
    if not vizinhos:
        return False

    def cortes(i: int) -> list[float]:
        lo, hi = el["from"][i], el["to"][i]
        return sorted({lo, hi} | {v for o in vizinhos for v in (o["from"][i], o["to"][i]) if lo < v < hi})

    ca, cb = cortes(a), cortes(b)
    for pa in zip(ca, ca[1:]):
        for pb in zip(cb, cb[1:]):
            meio_a, meio_b = sum(pa) / 2, sum(pb) / 2
            if not any(o["from"][a] < meio_a < o["to"][a] and o["from"][b] < meio_b < o["to"][b] for o in vizinhos):
                return False
    return True


def _na_borda(el: dict, direcao: str) -> bool:
    k, sinal = EIXO[direcao]
    return (el["to"][k] == 16) if sinal > 0 else (el["from"][k] == 0)


def _plano(lado: str) -> tuple[int, int]:
    """Os dois eixos no plano da face do lado (o terceiro é a normal)."""
    k = EIXO[lado][0]
    return tuple(i for i in range(3) if i != k)


def _uv_circuito(el: dict, direcao: str) -> list[float]:
    """O uv da face de fora de uma peça do painel no sprite do circuito, pela posição dela: o sprite cobre a face
    como o jogo a desenha (o canto 0 do `FaceInfo` no alto à esquerda, o 3 à direita, o 1 embaixo), um texel por
    unidade, então o desenho e a onda continuam de uma peça para a outra em todos os lados."""
    cantos = bb.CANTOS_FACE[direcao]

    def canto(i: int) -> list[float]:
        return [(el["from"], el["to"])[cantos[i][j]][j] for j in range(3)]

    c0, c1, c3 = canto(0), canto(1), canto(3)
    eu = next(j for j in range(3) if c0[j] != c3[j])
    ev = next(j for j in range(3) if c0[j] != c1[j])

    def no_sprite(ponto: list[float], eixo: int, rumo: float) -> float:
        return ponto[eixo] if rumo > 0 else LADO_SPRITE - ponto[eixo]

    su, sv = c3[eu] - c0[eu], c1[ev] - c0[ev]
    return [bb.num(no_sprite(c0, eu, su)), bb.num(no_sprite(c0, ev, sv)),
            bb.num(no_sprite(c3, eu, su)), bb.num(no_sprite(c1, ev, sv))]


def _etiqueta(el: dict, lado: str) -> None:
    """A pastilha da etiqueta cresce no plano da face: `ETIQUETA_MEIA_LARGURA` para cada lado no eixo comprido e, no
    curto, da moldura da vigia (com folga) até a borda do painel (com folga), do lado em que ela já estava."""
    a, b = _plano(lado)
    curto = a if el["to"][a] - el["from"][a] < el["to"][b] - el["from"][b] else b
    comprido = b if curto == a else a
    sinal = math.copysign(1.0, (el["from"][curto] + el["to"][curto]) / 2 - 8)
    perto, longe = MOLDURA + ETIQUETA_FOLGA_MOLDURA, 6 - ETIQUETA_FOLGA_BORDA
    el["from"][curto], el["to"][curto] = sorted((8 + sinal * perto, 8 + sinal * longe))
    el["from"][comprido], el["to"][comprido] = 8 - ETIQUETA_MEIA_LARGURA, 8 + ETIQUETA_MEIA_LARGURA


def _fundo(convertidos: list[dict], lado: str) -> dict:
    """A placa escura atrás da vigia do lado: logo para dentro do lábio, do tamanho da abertura dentro dele, com a
    face só para dentro do bloco."""
    k, sinal = EIXO[lado]
    labios = [el for el in convertidos if el["name"].startswith(f"labio_{lado}_")]
    if len(labios) != 4:
        raise ModeloInvalido(f"lado {lado}: {len(labios)} peças do lábio, esperado 4")
    de, ate = [0.0] * 3, [0.0] * 3
    for i in _plano(lado):
        espessura = min(el["to"][i] - el["from"][i] for el in labios)
        de[i] = min(el["from"][i] for el in labios) + espessura
        ate[i] = max(el["to"][i] for el in labios) - espessura
    dentro = min(el["from"][k] for el in labios) if sinal > 0 else max(el["to"][k] for el in labios)
    de[k], ate[k] = sorted((dentro, dentro - sinal * FUNDO_ESPESSURA))
    contra = next(d for d, (eixo, s) in EIXO.items() if eixo == k and s == -sinal)
    x, y = FUNDO
    uv = [x / 4, y / 4, x / 4 + 1, y / 4 + 1]
    return {"name": f"fundo_{lado}", "from": [bb.num(v) for v in de], "to": [bb.num(v) for v in ate],
            "faces": {contra: {"uv": uv, "texture": CHAVE}}}


def elementos(projeto: dict) -> list[dict]:
    """As caixas do modelo de bloco já no formato do JSON, sem as faces cobertas e com o `cullface`, a luz e o
    circuito na face de fora do painel, e o fundo escuro do miolo."""
    if len(projeto["elements"]) != ELEMENTOS_BBMODEL:
        raise ModeloInvalido(f"o .bbmodel tem {len(projeto['elements'])} caixas, esperado {ELEMENTOS_BBMODEL}")
    grupo = _grupos(projeto)
    fontes = [_no_bloco(copy.deepcopy(el)) for el in projeto["elements"] if _do_bloco(grupo[el["uuid"]])]
    if len(fontes) != ELEMENTOS_ESPERADOS:
        raise ModeloInvalido(f"{len(fontes)} caixas do modelo de bloco, esperado {ELEMENTOS_ESPERADOS}")
    if any(any(el.get("rotation", [0, 0, 0])) for el in fontes):
        raise ModeloInvalido("uma caixa do modelo de bloco está girada; o corte das faces cobertas supõe caixas retas")
    rotulos = {uuid: lado for lado, (_, uuid) in _etiquetas(projeto).items()}
    for el in fontes:
        if el["uuid"] in rotulos:
            _etiqueta(el, rotulos[el["uuid"]])
    convertidos = [bb.converter(el, ATLAS, CHAVE, lambda nome: False) for el in fontes]
    saida = []
    for el in convertidos:
        faces = {}
        lado = el["name"].split("_")[1] if el["name"].startswith("painel_") else None
        for direcao, face in el["faces"].items():
            if _coberta(el, direcao, convertidos):
                continue
            face = dict(face)
            if _na_borda(el, direcao):
                face["cullface"] = direcao
            if direcao == lado:
                face["texture"] = CIRCUITO
                face["uv"] = _uv_circuito(el, direcao)
                face["neoforge_data"] = {"block_light": 15, "sky_light": 15}
            faces[direcao] = face
        if not faces:
            raise ModeloInvalido(f"{el['name']}: todas as faces cobertas; tirar a caixa do .bbmodel")
        saida.append({**el, "faces": faces})
    saida.extend(_fundo(convertidos, lado) for lado in LADOS)
    faces_total = sum(len(el["faces"]) for el in saida)
    if faces_total != FACES_ESPERADAS:
        raise ModeloInvalido(f"{faces_total} faces depois do corte, esperado {FACES_ESPERADAS}")
    bb.validar([{**el, "faces": {d: {**f, "texture": CHAVE} for d, f in el["faces"].items()}} for el in saida], CHAVE)
    for el in saida:
        if not all(0 <= v <= 16 for v in el["from"] + el["to"]):
            raise ModeloInvalido(f"{el['name']}: fora do bloco ({el['from']}..{el['to']})")
    return saida


def cores_dos_lados() -> dict[str, tuple[int, int, int, int]]:
    """As cores do `DockColors.SIDE`, na ordem do `Direction.ordinal()`."""
    texto = CORES_JAVA.read_text(encoding="utf-8")
    bloco = re.search(r"SIDE = \{(.*?)\};", texto, re.S)
    cores = re.findall(r"0xFF([0-9A-Fa-f]{6})", bloco.group(1)) if bloco else []
    if len(cores) != len(LADOS):
        raise ModeloInvalido(f"{len(cores)} cores no DockColors.SIDE, esperado {len(LADOS)}")
    return {lado: rgb("#" + cor) for lado, cor in zip(LADOS, cores)}


def _etiquetas(projeto: dict) -> dict[str, tuple[tuple[int, int, int, int], str]]:
    """A etiqueta de cada lado: o retângulo no atlas da face de fora da pastilha que não usa o latão das outras e o
    uuid dessa pastilha."""
    grupo = _grupos(projeto)
    saida = {}
    for lado in LADOS:
        pastilhas = [el for el in projeto["elements"] if grupo[el["uuid"]] == f"garras_{lado}"]
        latao = pastilhas[1]["faces"][lado]["uv"]
        marcadas = [el for el in pastilhas if el["faces"][lado]["uv"] != latao]
        if len(marcadas) != 1:
            raise ModeloInvalido(f"lado {lado}: {len(marcadas)} etiquetas nas pastilhas, esperado 1")
        saida[lado] = (bb.retangulo(marcadas[0]["faces"][lado]["uv"]), marcadas[0]["uuid"])
    if len({r for r, _ in saida.values()}) != len(LADOS):
        raise ModeloInvalido("dois lados dividem o retângulo da etiqueta no atlas")
    return saida


def etiquetas(projeto: dict) -> dict[str, tuple[int, int, int, int]]:
    """A casa de 4 × 4 do atlas em que cai a etiqueta de cada lado (pintada inteira, para o mipmap)."""
    casas = {}
    for lado, ((x0, y0, x1, y1), _) in _etiquetas(projeto).items():
        cx, cy = x0 // 4 * 4, y0 // 4 * 4
        if x1 > cx + 4 or y1 > cy + 4:
            raise ModeloInvalido(f"lado {lado}: a etiqueta passa da casa de 4 × 4 do atlas")
        casas[lado] = (cx, cy, cx + 4, cy + 4)
    if len(set(casas.values())) != len(LADOS):
        raise ModeloInvalido("dois lados dividem a casa da etiqueta no atlas")
    return casas


def _rampa(tier: str) -> tuple[tuple[int, ...], ...]:
    hi, mid, lo = LENTE[tier]
    return tuple(rgb(c)[:3] for c in (lo, mid, hi))


def _na_rampa(cor: tuple[int, ...], rampa) -> tuple[float, float]:
    """O ponto da rampa (0 = sombra, 1 = lente, 2 = brilho) mais perto de `cor` e a distância até ele."""
    melhor = (0.0, float("inf"))
    for i in (0, 1):
        a, b = rampa[i], rampa[i + 1]
        ab = [b[j] - a[j] for j in range(3)]
        t = sum(ab[j] * (cor[j] - a[j]) for j in range(3)) / sum(v * v for v in ab)
        t = min(1.0, max(0.0, t))
        dist = sum((cor[j] - a[j] - t * ab[j]) ** 2 for j in range(3)) ** 0.5
        if dist < melhor[1]:
            melhor = (i + t, dist)
    return melhor


def _no_ponto(rampa, s: float) -> tuple[int, int, int]:
    i = min(int(s), 1)
    t = s - i
    a, b = rampa[i], rampa[i + 1]
    return tuple(int(round(a[j] + (b[j] - a[j]) * t)) for j in range(3))


def textura(atlas: Image.Image, tier: str, rotulos: dict, cores: dict) -> Image.Image:
    """O atlas do tier: o circuito na rampa da lente dele e as etiquetas nas cores dos lados."""
    pintada, nova = _rampa(RAMPA_PINTADA), _rampa(tier)
    out = atlas.copy()
    trocados = 0
    for x in range(ATLAS):
        for y in range(ATLAS):
            r, g, b, a = atlas.getpixel((x, y))
            if not a:
                continue
            s, dist = _na_rampa((r, g, b), pintada)
            if dist < PERTO_DA_RAMPA:
                out.putpixel((x, y), _no_ponto(nova, s) + (a,))
                trocados += 1
    if not trocados:
        raise ModeloInvalido("nenhum texel do circuito na rampa pintada")
    for lado, (x0, y0, x1, y1) in rotulos.items():
        for x in range(x0, x1):
            for y in range(y0, y1):
                out.putpixel((x, y), cores[lado])
    return out


def _fase(x: int, y: int) -> float:
    """O atraso da onda no texel (x, y) do sprite, em voltas: cresce de baixo à esquerda para cima à direita, uma volta
    a cada 16 texels na diagonal."""
    return (x + (LADO_SPRITE - 1 - y)) / (LADO_SPRITE * math.sqrt(2))


def circuito(atlas: Image.Image, tier: str) -> Image.Image:
    """A tira de `QUADROS` quadros do circuito do tier: o recorte do painel com cada texel do circuito (perto da rampa
    pintada) no ponto `1 + cos` da rampa do tier, e o resto (o fundo do painel) como está."""
    x0, y0 = PAINEL
    base = atlas.crop((x0, y0, x0 + LADO_SPRITE, y0 + LADO_SPRITE))
    pintada, nova = _rampa(RAMPA_PINTADA), _rampa(tier)
    traco = {(x, y) for x in range(LADO_SPRITE) for y in range(LADO_SPRITE)
             if _na_rampa(base.getpixel((x, y))[:3], pintada)[1] < PERTO_DA_RAMPA}
    if not traco:
        raise ModeloInvalido("nenhum texel do circuito no recorte do painel")
    tira = Image.new("RGBA", (LADO_SPRITE, LADO_SPRITE * QUADROS))
    for q in range(QUADROS):
        quadro = base.copy()
        for x, y in traco:
            s = 1 + math.cos(2 * math.pi * (q / QUADROS - _fase(x, y)))
            quadro.putpixel((x, y), _no_ponto(nova, s) + (255,))
        tira.paste(quadro, (0, q * LADO_SPRITE))
    return tira


def _grava_circuito(tier: str, tira: Image.Image) -> None:
    destino = ASSETS / f"{_circuito(tier)}.png"
    destino.parent.mkdir(parents=True, exist_ok=True)
    tira.save(destino)
    meta = {"animation": {"frametime": TICKS_QUADRO, "interpolate": True}}
    destino.with_name(destino.name + ".mcmeta").write_text(json.dumps(meta, indent=2) + "\n", encoding="utf-8",
                                                          newline="\n")


def _pinta_fundo(atlas: Image.Image) -> None:
    """A casa do fundo do miolo, inteira na cor dele (para o mipmap não misturar com a vizinha); só numa casa vazia."""
    x0, y0 = FUNDO
    casa = [(x, y) for x in range(x0, x0 + 4) for y in range(y0, y0 + 4)]
    if any(atlas.getpixel(p)[3] for p in casa):
        raise ModeloInvalido(f"a casa do fundo {FUNDO} já está pintada no atlas do .bbmodel")
    for p in casa:
        atlas.putpixel(p, FUNDO_COR)


def recortes_do_renderer(projeto: dict) -> list[tuple[int, int, int, int]]:
    """Os recortes do atlas (em pixels) das caixas do `.bbmodel` fora do modelo de bloco, que o renderer desenha."""
    grupo = _grupos(projeto)
    return [bb.retangulo(f["uv"]) for el in projeto["elements"] if not _do_bloco(grupo[el["uuid"]])
            for f in el["faces"].values() if f.get("texture") is not None]


def _confere_recortes(pintada: Image.Image, final: Image.Image, recortes: list, tier: str) -> None:
    """A folga não mudou nenhum texel dos recortes do renderer."""
    for x0, y0, x1, y1 in recortes:
        for x in range(x0, x1):
            for y in range(y0, y1):
                if pintada.getpixel((x, y)) != final.getpixel((x, y)):
                    raise ModeloInvalido(f"{tier}: a folga pintou o texel ({x}, {y}) de um recorte do renderer")


def gerar(raiz_assets: Path = MODELOS) -> set[str]:
    """Grava as texturas dos tiers (o atlas e o circuito), o modelo base, um por tier, o blockstate e o modelo de
    item; devolve as chaves das texturas gravadas (relativas a `textures/`, como as do `limpar`)."""
    projeto = json.loads(BBMODEL.read_text(encoding="utf-8"))
    atlas = bb.atlas(projeto, ATLAS)
    _pinta_fundo(atlas)
    els = elementos(projeto)
    faces_atlas = [f for el in els for f in el["faces"].values() if f["texture"] == CHAVE]
    if any(a != 255 for a in bb.alfas(atlas, faces_atlas)):
        raise ModeloInvalido("o modelo usa um texel com alfa")
    retangulos = [bb.retangulo([v * ATLAS / 16 for v in f["uv"]]) for f in faces_atlas]
    # Os recortes que o renderer lê (anéis, cristal, pacote e fragmentos) também são faces: a folga não escreve neles.
    do_renderer = recortes_do_renderer(projeto)
    retangulos += do_renderer
    rotulos, cores = etiquetas(projeto), cores_dos_lados()
    for tier in TIERS:
        destino = ASSETS / f"{_textura(tier)}.png"
        destino.parent.mkdir(parents=True, exist_ok=True)
        pintada = textura(atlas, tier, rotulos, cores)
        final = bb.com_folga(pintada, retangulos)
        _confere_recortes(pintada, final, do_renderer, tier)
        final.save(destino)
        _grava_circuito(tier, circuito(atlas, tier))

    modelos = raiz_assets / "models/block" / PASTA
    base = bb.modelo("minecraft:solid", els, CHAVE, f"{NS}:{_textura(TIERS[0])}")
    base["textures"][CIRCUITO.lstrip("#")] = f"{NS}:{_circuito(TIERS[0])}"
    grava_json(modelos / f"{BASE}.json", base)
    for tier in TIERS:
        ref = f"{NS}:{_textura(tier)}"
        grava_json(modelos / f"{BASE}_{tier}.json", {"parent": f"{NS}:block/{PASTA}/{BASE}", "textures": {
            CHAVE.lstrip("#"): ref, CIRCUITO.lstrip("#"): f"{NS}:{_circuito(tier)}", "particle": ref}})
    grava_json(raiz_assets / "blockstates/pattern_dock.json", {"variants": {
        f"tier={t}": {"model": f"{NS}:block/{PASTA}/{BASE}_{t}"} for t in TIERS}})
    grava_json(raiz_assets / "models/item/pattern_dock.json", {
        "parent": f"{NS}:block/{PASTA}/{BASE}_{TIERS[0]}",
        "overrides": [{"predicate": {f"{NS}:tier": n}, "model": f"{NS}:block/{PASTA}/{BASE}_{t}"}
                      for n, t in enumerate(TIERS[1:], start=1)],
    })
    return {_textura(t) for t in TIERS} | {_circuito(t) for t in TIERS}


if __name__ == "__main__":
    print("texturas:", sorted(gerar()))

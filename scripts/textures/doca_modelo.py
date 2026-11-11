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
(etapa 3): até lá, a vigia mostra o fundo do painel do lado oposto. As coordenadas do `.bbmodel` têm o bloco
centrado em x e z; aqui somam 8.

Nenhum par de caixas se junta numa caixa só (nem encostadas com as mesmas medidas, nem uma por cima da outra:
conferido nas 174), então o número de elementos fica o do `.bbmodel`; o que baixa é o de faces. Sai toda face
coberta por outra caixa (a parte de trás da moldura contra o painel, as pontas das vigas umas contra as outras), e
a que fica na borda do bloco leva o `cullface` do lado, para sumir contra um vizinho opaco.

A textura de cada tier é o atlas pintado no Blockbench com duas trocas:
- o circuito foi pintado na rampa da lente do Elite (sombra, lente e brilho do `LENTE`, mais os tons do meio da
  onda); cada texel perto dessa rampa vai para o mesmo ponto da rampa do tier. Estático nesta etapa (a onda da
  `.mcmeta` é da etapa 3);
- as etiquetas dos lados (o retângulo da face de fora da pastilha de cima de cada lado) recebem a cor do lado, lida
  do `dock/DockColors.java`, a fonte única delas.
A face de fora de cada peça do painel leva a luz cheia (`neoforge_data` só nessa face), para o circuito brilhar no
escuro; o resto do bloco recebe a luz do mundo.

Um modelo só com os elementos (`pattern_dock`, na textura do Básico) e um filho por tier que só troca a textura;
o blockstate escolhe pelo `tier` e o modelo do item pelas sobreposições do predicado `wirelessautomate:tier` (a
posição do tier no enum). A colisão é a do bloco cheio, no Java.

Uso isolado (na raiz): `python scripts/textures/doca_modelo.py`.
"""

from __future__ import annotations

import json
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
ELEMENTOS_BBMODEL = 193
ELEMENTOS_ESPERADOS = 174
FACES_ESPERADAS = 794
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


def elementos(projeto: dict) -> list[dict]:
    """As caixas do modelo de bloco já no formato do JSON, sem as faces cobertas e com o `cullface` e a luz."""
    if len(projeto["elements"]) != ELEMENTOS_BBMODEL:
        raise ModeloInvalido(f"o .bbmodel tem {len(projeto['elements'])} caixas, esperado {ELEMENTOS_BBMODEL}")
    grupo = _grupos(projeto)
    fontes = [_no_bloco(el) for el in projeto["elements"] if _do_bloco(grupo[el["uuid"]])]
    if len(fontes) != ELEMENTOS_ESPERADOS:
        raise ModeloInvalido(f"{len(fontes)} caixas do modelo de bloco, esperado {ELEMENTOS_ESPERADOS}")
    if any(any(el.get("rotation", [0, 0, 0])) for el in fontes):
        raise ModeloInvalido("uma caixa do modelo de bloco está girada; o corte das faces cobertas supõe caixas retas")
    convertidos = [bb.converter(el, ATLAS, CHAVE, lambda nome: False) for el in fontes]
    saida = []
    for el in convertidos:
        faces = {}
        for direcao, face in el["faces"].items():
            if _coberta(el, direcao, convertidos):
                continue
            face = dict(face)
            if _na_borda(el, direcao):
                face["cullface"] = direcao
            lado = el["name"].split("_")[1] if el["name"].startswith("painel_") else None
            if direcao == lado:
                face["neoforge_data"] = {"block_light": 15, "sky_light": 15}
            faces[direcao] = face
        if not faces:
            raise ModeloInvalido(f"{el['name']}: todas as faces cobertas; tirar a caixa do .bbmodel")
        saida.append({**el, "faces": faces})
    faces_total = sum(len(el["faces"]) for el in saida)
    if faces_total != FACES_ESPERADAS:
        raise ModeloInvalido(f"{faces_total} faces depois do corte, esperado {FACES_ESPERADAS}")
    bb.validar(saida, CHAVE)
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


def etiquetas(projeto: dict) -> dict[str, tuple[int, int, int, int]]:
    """O retângulo da etiqueta de cada lado no atlas: a face de fora da pastilha que não usa o latão das outras."""
    grupo = _grupos(projeto)
    saida = {}
    for lado in LADOS:
        pastilhas = [el for el in projeto["elements"] if grupo[el["uuid"]] == f"garras_{lado}"]
        latao = pastilhas[1]["faces"][lado]["uv"]
        marcadas = {bb.retangulo(el["faces"][lado]["uv"]) for el in pastilhas if el["faces"][lado]["uv"] != latao}
        if len(marcadas) != 1:
            raise ModeloInvalido(f"lado {lado}: {len(marcadas)} etiquetas nas pastilhas, esperado 1")
        saida[lado] = marcadas.pop()
    if len(set(saida.values())) != len(LADOS):
        raise ModeloInvalido("dois lados dividem o retângulo da etiqueta no atlas")
    return saida


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


def gerar(raiz_assets: Path = MODELOS) -> set[str]:
    """Grava as texturas dos tiers, o modelo base, um por tier, o blockstate e o modelo de item; devolve as chaves
    das texturas gravadas (relativas a `textures/`, como as do `limpar`)."""
    projeto = json.loads(BBMODEL.read_text(encoding="utf-8"))
    atlas = bb.atlas(projeto, ATLAS)
    els = elementos(projeto)
    if any(a != 255 for a in bb.alfas(atlas, [f for el in els for f in el["faces"].values()])):
        raise ModeloInvalido("o modelo usa um texel com alfa")
    retangulos = [bb.retangulo([v * ATLAS / 16 for v in f["uv"]]) for el in els for f in el["faces"].values()]
    rotulos, cores = etiquetas(projeto), cores_dos_lados()
    for tier in TIERS:
        destino = ASSETS / f"{_textura(tier)}.png"
        destino.parent.mkdir(parents=True, exist_ok=True)
        bb.com_folga(textura(atlas, tier, rotulos, cores), retangulos).save(destino)

    modelos = raiz_assets / "models/block" / PASTA
    grava_json(modelos / f"{BASE}.json", bb.modelo("minecraft:solid", els, CHAVE, f"{NS}:{_textura(TIERS[0])}"))
    for tier in TIERS:
        ref = f"{NS}:{_textura(tier)}"
        grava_json(modelos / f"{BASE}_{tier}.json",
                   {"parent": f"{NS}:block/{PASTA}/{BASE}", "textures": {CHAVE.lstrip("#"): ref, "particle": ref}})
    grava_json(raiz_assets / "blockstates/pattern_dock.json", {"variants": {
        f"tier={t}": {"model": f"{NS}:block/{PASTA}/{BASE}_{t}"} for t in TIERS}})
    grava_json(raiz_assets / "models/item/pattern_dock.json", {
        "parent": f"{NS}:block/{PASTA}/{BASE}_{TIERS[0]}",
        "overrides": [{"predicate": {f"{NS}:tier": n}, "model": f"{NS}:block/{PASTA}/{BASE}_{t}"}
                      for n, t in enumerate(TIERS[1:], start=1)],
    })
    return {_textura(t) for t in TIERS}


if __name__ == "__main__":
    print("texturas:", sorted(gerar()))
